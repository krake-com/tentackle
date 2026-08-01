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

import org.tentackle.log.Logger;
import org.tentackle.reflect.IndexedMethodCache;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteDelegate;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.remote.DelegateDescriptor;
import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The default implementation of a remote delegate.
 * <p>
 * Although this class is thread-safe, invocations on the target object
 * are <em>not</em> synchronized!
 * This must be done by the implementation of the remote object.
 *
 * @param <T> the type of the remote object
 */
public class DefaultRemoteDelegate<T extends Remote> implements RemoteDelegate<T> {

  private static final Logger LOGGER = Logger.get(DefaultRemoteDelegate.class);

  private final Registry registry;
  private final UUID remoteId;
  private final T target;
  private final IndexedMethodCache methodCache;
  private final List<WeakReference<RemoteDelegate<?>>> registeredDelegates;

  /**
   * Creates a new remote delegate.
   *
   * @param registry the registry
   * @param remoteId the ID of the remote object
   * @param target the target object
   * @param methodCache the method cache for the target object
   */
  public DefaultRemoteDelegate(Registry registry, UUID remoteId, T target, IndexedMethodCache methodCache) {
    this.registry = registry;
    this.remoteId = remoteId;
    this.target = target;
    this.methodCache = methodCache;
    registeredDelegates = new ArrayList<>();
  }

  @Override
  public Registry getRegistry() {
    return registry;
  }

  @Override
  public UUID getRemoteId() {
    return remoteId;
  }

  @Override
  public T getTarget() {
    return target;
  }

  @Override
  public InitialRemoteResponse invoke(InitialRemoteCall call) {
    try {
      Method method = target.getClass().getMethod(call.methodName(), call.parameterTypes());
      int methodIndex = methodCache.add(method);
      try {
        return new InitialRemoteResponse(methodIndex,
                                         processResult(method.invoke(target, call.parameters())), null);
      }
      catch (InvocationTargetException e) {
        return new InitialRemoteResponse(methodIndex, null, e.getCause());
      }
      catch (IllegalAccessException e) {
        return new InitialRemoteResponse(methodIndex, null, e);
      }
    }
    catch (NoSuchMethodException e) {
      return new InitialRemoteResponse(-1, null, e);
    }
  }

  @Override
  public IdentifiedRemoteResponse invoke(IdentifiedRemoteCall call) {
    Method method = methodCache.get(call.methodIndex());
    if (method == null) {
      // this can't happen, but just in case
      return new IdentifiedRemoteResponse(null, new TripRuntimeException("unknown method index " + call.methodIndex() + " for " + this));
    }
    try {
      return new IdentifiedRemoteResponse(processResult(method.invoke(target, call.parameters())), null);
    }
    catch (InvocationTargetException e) {
      return new IdentifiedRemoteResponse(null, e.getCause());
    }
    catch (IllegalAccessException e) {
      return new IdentifiedRemoteResponse(null, e);
    }
  }

  @Override
  public String toString() {
    return "DefaultRemoteDelegate{" +
           "target=" + target.getClass().getName() +
           ", remoteId=" + remoteId +
           '}';
  }

  @Override
  public synchronized void unregistered() {
    // must not throw an exception or abort unregistration
    try {
      registeredDelegates.forEach(ref -> {
        RemoteDelegate<?> remoteDelegate = ref.get();
        if (remoteDelegate != null) {
          try {
            registry.unregister(remoteDelegate.getRemoteId());
          }
          catch (RuntimeException e) {
            LOGGER.severe("error unregistering delegate " + remoteDelegate, e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      LOGGER.severe("error unregistering delegate " + this, e);
    }
  }


  private Object processResult(Object result) {
    if (result instanceof Remote[] remotes) {
      // several remote objects returned in a single roundtrip
      DelegateDescriptor[] descriptors = new DelegateDescriptor[remotes.length];
      for (int i = 0; i < remotes.length; i++) {
        descriptors[i] = remotes[i] == null ? null : (DelegateDescriptor) processResult(remotes[i]);
      }
      return descriptors;
    }
    if (result instanceof Remote remote) {
      RemoteDelegate<Remote> delegate;
      UUID id = registry.lookup(remote);
      if (id == null) {
        // new delegate -> register unnamed
        delegate = registry.register(remote);
        synchronized (this) {
          registeredDelegates.add(new WeakReference<>(delegate));
        }
      }
      else {
        delegate = registry.lookup(id);
        if (delegate == null) {
          // should never happen, but just in case
          throw new TripRuntimeException("no such delegate " + id + " (registry corrupted?)");
        }
      }
      return new DelegateDescriptor(delegate.getRemoteId(), result);
    }
    return result;
  }

}
