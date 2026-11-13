/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.trip.impl;

import org.tentackle.reflect.IndexedMethodCache;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteDelegate;
import org.tentackle.trip.RemoteRoot;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.transport.TransportClientInfo;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default implementation of {@link Registry}.
 * <p>
 * The registry is thread-safe.
 */
public class DefaultRegistry implements Registry {

  private final Map<UUID, RemoteDelegate<?>> idMap;           // ID -> RemoteObject
  private final Map<Remote, UUID> reverseTargetMap;           // target -> ID
  private final Map<String, UUID> nameMap;                    // name -> ID
  private final Map<UUID, String> reverseNameMap;             // ID -> name
  private final Map<Class<?>, IndexedMethodCache> classMap;   // map of class -> method index -> method

  /**
   * Creates a new registry.
   */
  public DefaultRegistry() {
    idMap = new ConcurrentHashMap<>();    // lookup by id must be fast!
    reverseTargetMap = new IdentityHashMap<>();
    nameMap = new HashMap<>();
    reverseNameMap = new HashMap<>();
    classMap = new HashMap<>();
    // the registry itself is also a remote object!
    register(this);
  }

  @Override
  public synchronized <T extends Remote> RemoteDelegate<T> register(T remoteObject, String name) {
    UUID id = nameMap.get(name);
    if (id != null) {
      throw new TripRuntimeException("name '" + name + "' already registered for ID " + id);
    }
    // a named remote object is a public entry point: no owner
    RemoteDelegate<T> remoteDelegate = register(remoteObject, (UUID) null);
    id = remoteDelegate.getRemoteId();
    nameMap.put(name, id);
    reverseNameMap.put(id, name);
    return remoteDelegate;
  }

  @Override
  public synchronized <T extends Remote> RemoteDelegate<T> register(T remoteObject) {
    // an unnamed remote object is private to the client transport that registers it, if any
    return register(remoteObject, currentOwnerId());
  }

  /**
   * Registers a remote object with an explicit owner.
   *
   * @param remoteObject the remote object
   * @param ownerId the owning client transport UUID, or null if the delegate is public
   * @return the remote delegate
   * @param <T> the type of the remote object
   */
  private synchronized <T extends Remote> RemoteDelegate<T> register(T remoteObject, UUID ownerId) {
    if (remoteObject == null) {
      throw new TripRuntimeException("cannot register null object");
    }
    if (remoteObject.getClass().isInterface()) {
      throw new TripRuntimeException("interfaces are not allowed: " + remoteObject.getClass());
    }
    UUID id = reverseTargetMap.get(remoteObject);
    if (id != null) {
      String name = reverseNameMap.get(id);
      if (name != null) {
        throw new TripRuntimeException("object already registered with ID " + id + " and name '" + name + "'");
      }
      throw new TripRuntimeException("object already registered with ID " + id);
    }
    if (remoteObject instanceof Registry) {
      id = Registry.REGISTRY_ID;
      ownerId = null;   // the registry is always public
    }
    else {
      id = UUID.randomUUID();
    }

    RemoteDelegate<T> remoteDelegate = createRemoteDelegate(
      id, ownerId, remoteObject,
      classMap.computeIfAbsent(remoteObject.getClass(),
                               _ -> new IndexedMethodCache(remoteObject.getClass())));  // one thread-safe method map per class

    idMap.put(id, remoteDelegate);
    reverseTargetMap.put(remoteObject, id);
    return remoteDelegate;
  }

  /**
   * Determines the owner for an unnamed registration.
   * <p>
   * While a client call is being served, the current {@link TransportClientInfo} identifies the
   * calling client; its transport UUID becomes the owner. Outside a client call (e.g. during server
   * setup) there is no owner and the delegate is public.
   *
   * @return the owning client transport UUID, or null if no client call is in progress
   */
  private UUID currentOwnerId() {
    TransportClientInfo info = TransportClientInfo.get();
    return info == null ? null : info.clientId();
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T extends Remote> RemoteDelegate<T> unregister(UUID id) {
    RemoteDelegate<?> remoteDelegate = idMap.remove(id);
    if (remoteDelegate != null) {
      reverseTargetMap.remove(remoteDelegate.getTarget());
      String name = reverseNameMap.remove(id);
      if (name != null) {
        nameMap.remove(name);
      }
      remoteDelegate.unregistered();
    }
    return (RemoteDelegate<T>) remoteDelegate;
  }

  @Override
  public synchronized <T extends Remote> RemoteDelegate<T> unregister(String name) {
    UUID id = nameMap.remove(name);
    return id == null ? null : unregister(id);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Remote> RemoteDelegate<T> lookup(UUID id) {
    // No synchronized because this method is used heavily and must be fast.
    // That's why idMap is a ConcurrentHashMap!
    return (RemoteDelegate<T>) idMap.get(id);
  }

  @Override
  public synchronized UUID lookup(String name) {
    return nameMap.get(name);
  }

  @Override
  public synchronized String lookupName(UUID id) {
    return reverseNameMap.get(id);
  }

  @Override
  public synchronized UUID lookup(Remote remoteObject) {
    // Unlike lookup(UUID), this must be synchronized: reverseTargetMap is a plain IdentityHashMap
    // mutated by register() and unregister(). Reading it concurrently to a mutation could not only
    // return a stale ID, but observe a partially rehashed table, since IdentityHashMap resolves
    // collisions by linear probing over a single array.
    return reverseTargetMap.get(remoteObject);
  }

  /**
   * Creates a new {@link RemoteDelegate}.
   *
   * @param remoteId the ID of the remote delegate
   * @param ownerId the owning client transport UUID, or null if the delegate is public
   * @param remoteObject the target object of the remote delegate
   * @param methodCache the method cache for the target object (must be thread-safe!)
   * @return the new remote delegate
   * @param <T> the type of the target object
   */
  protected <T extends Remote> RemoteDelegate<T> createRemoteDelegate(UUID remoteId, UUID ownerId, T remoteObject, IndexedMethodCache methodCache) {
    if (remoteObject instanceof RemoteRoot remoteRoot) {
      remoteRoot.setRegistryCoordinates(this, remoteId);
    }
    return new DefaultRemoteDelegate<>(this, remoteId, ownerId, remoteObject, methodCache);
  }

}
