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
import org.tentackle.reflect.ReflectionHelper;
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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The default implementation of a remote delegate.
 * <p>
 * Although this class is thread-safe, invocations on the target object
 * are <em>not</em> synchronized!
 * This must be done by the implementation of the remote object.
 * <p>
 * Only the methods declared by the target's remote interfaces are invocable, see
 * {@link #findRemoteMethod(String, Class[])}.
 *
 * @param <T> the type of the remote object
 */
public class DefaultRemoteDelegate<T extends Remote> implements RemoteDelegate<T> {

  private static final Logger LOGGER = Logger.get(DefaultRemoteDelegate.class);

  /**
   * The methods of {@link Object} a dynamic proxy forwards to its invocation handler, and thus the
   * only ones a client can invoke without them being declared by a remote interface.
   */
  private static final Set<String> FORWARDED_OBJECT_METHODS = Set.of("toString", "hashCode", "equals");

  /**
   * The remote interfaces per target class.<br>
   * They only depend on the class, while there may be many remote objects of the same class, so
   * the interface hierarchy is walked only once. A {@link ClassValue} is used instead of a map to
   * not keep the classes and their classloaders alive.
   */
  private static final ClassValue<Collection<Class<? extends Remote>>> REMOTE_INTERFACES = new ClassValue<>() {
    @Override
    protected Collection<Class<? extends Remote>> computeValue(Class<?> type) {
      // immutable, since it is shared by all delegates of that class
      return List.copyOf(ReflectionHelper.findAllInterfaces(type, Remote.class));
    }
  };

  private final Registry registry;
  private final UUID remoteId;
  private final T target;
  private final IndexedMethodCache methodCache;
  private final List<WeakReference<RemoteDelegate<?>>> registeredDelegates;
  private final Collection<Class<? extends Remote>> remoteInterfaces;

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
    remoteInterfaces = REMOTE_INTERFACES.get(target.getClass());
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
      Method method = findRemoteMethod(call.methodName(), call.parameterTypes());
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


  /**
   * Looks up an invocable method by the name and parameter types received from the client.
   * <p>
   * Only the methods of the target's remote interfaces are invocable, not every public method of
   * its implementation class. A client can only hold a proxy for those interfaces, but the call
   * carries the method name and its parameter types, so a peer is free to name any other method.
   * Without this restriction it would reach everything the implementation happens to expose
   * publicly beyond its remote interfaces, such as lifecycle or configuration methods.
   * <p>
   * The three {@link Object} methods a dynamic proxy forwards to its invocation handler are
   * invocable as well, since a client may legitimately call them on its proxy.
   *
   * @param methodName the name of the method
   * @param parameterTypes the parameter types
   * @return the method, never null
   * @throws NoSuchMethodException if no remote interface declares such a method
   */
  protected Method findRemoteMethod(String methodName, Class<?>[] parameterTypes) throws NoSuchMethodException {
    for (Class<? extends Remote> remoteInterface : remoteInterfaces) {
      try {
        // also finds the methods of the superinterfaces
        return remoteInterface.getMethod(methodName, parameterTypes);
      }
      catch (NoSuchMethodException nx) {
        // not this one, try the next interface
      }
    }
    if (FORWARDED_OBJECT_METHODS.contains(methodName)) {
      return Object.class.getMethod(methodName, parameterTypes);   // verifies the signature as well
    }
    throw new NoSuchMethodException(methodName + " is not provided by the remote interfaces " +
                                    remoteInterfaces + " of " + target.getClass().getName());
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
