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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.ClassMapperFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteLookup;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProvider;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeBuilderFactory;
import org.tentackle.trip.remote.RemoteInvocationHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of a trip factory.
 */
@Service(TripFactory.class)
public class DefaultTripFactory implements TripFactory {

  private final ClassMapper typeClassMapper;              // maps base classes to trip types
  private final ClassMapper providerClassMapper;          // maps component providers to classes
  private final ClassMapper creatorClassMapper;           // maps instance creators to classes
  private final ClassMapper remoteClassMapper;            // maps remote interfaces to remote implementations
  private final Collection<OpCode> allOpCodes;            // all opcodes
  private final Map<Class<?>, TripType<?>> typeMap;       // map of concrete classes to trip types
  private final Map<String, Class<Transport>> transports; // protocol::transport map


  /**
   * Creates the factory.
   */
  @SuppressWarnings("unchecked")
  public DefaultTripFactory() {
    allOpCodes = List.of(DefaultOpCode.values());
    typeMap = new ConcurrentHashMap<>();
    typeClassMapper = ClassMapperFactory.getInstance().create("TripType", TripType.class);
    providerClassMapper = ClassMapperFactory.getInstance().create("TripComponentProvider", TripComponentProvider.class);
    creatorClassMapper = ClassMapperFactory.getInstance().create("TripInstanceCreator", TripInstanceCreator.class);
    remoteClassMapper = ClassMapperFactory.getInstance().create("Remote", Remote.class);
    transports = new HashMap<>();
    for (Map.Entry<String, String> entry : ServiceFactory.getServiceFinder()
                                                         .createNameMap(Transport.class.getName()).entrySet()) {
      String protocol = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      try {
        Class<Transport> transportClass = (Class<Transport>) Class.forName(entry.getValue());
        Class<Transport> duplicateTransport = transports.put(protocol, transportClass);
        if (duplicateTransport != null) {
          throw new TripRuntimeException("duplicate transport for protocol " + protocol + ": " +
                                         transportClass + " and " + duplicateTransport);
        }
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public TripStream createStream(Dictionary dictionary, Serializer serializer, Deserializer deserializer) {
    return new DefaultTripStream(dictionary, serializer, deserializer);
  }

  @Override
  public Serializer createSerializer(OutputStream out) {
    return new DefaultSerializer(out);
  }

  @Override
  public Deserializer createDeserializer(InputStream in) {
    return new DefaultDeserializer(in);
  }

  @Override
  public Dictionary createDictionary(UUID id, boolean server) {
    return new DefaultDictionary(id, server);
  }

  @Override
  public TripType<?> getType(String className) {
    try {
      return getType(ReflectionHelper.loadClass(className));
    }
    catch (ClassNotFoundException e) {
      throw new TripRuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripType<T> getType(Class<? extends T> clazz) {
    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null && superClass.isEnum()) {
      // anonymous subclass of an enum constant with a class body -> use the enum class itself
      clazz = (Class<? extends T>) superClass;
    }
    return (TripType<T>) typeMap.computeIfAbsent(clazz, this::createType);
  }

  @Override
  public OpCode getOpCode(int code) {
    return DefaultOpCode.valueOf(code);
  }

  @Override
  public Collection<OpCode> getAllOpCodes() {
    return allOpCodes;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripComponentProvider<T> getComponentProvider(Class<T> clazz) {
    try {
      Class<?> providerClass = providerClassMapper.mapLenient(clazz);
      return (TripComponentProvider<T>) providerClass.getDeclaredConstructor().newInstance();
    }
    catch (ClassNotFoundException c) {
      return null;
    }
    catch (NoSuchMethodException | InstantiationException |
           IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot create component provider for " + clazz, e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripInstanceCreator<T> getInstanceCreator(Class<T> clazz) {
    try {
      Class<?> creatorClass = creatorClassMapper.mapLenient(clazz);
      return (TripInstanceCreator<T>) creatorClass.getDeclaredConstructor().newInstance();
    }
    catch (ClassNotFoundException c) {
      return null;
    }
    catch (NoSuchMethodException | InstantiationException |
           IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot create instance creator for " + clazz, e);
    }
  }

  @Override
  public <P, T> TripComponent<P, T> createComponent(TripType<P> parentType, int ordinal, Field field, boolean hasDefaultValue, T defaultValue) {
    if (field.getType().isPrimitive()) {
      // keeps the value primitive end to end, instead of boxing it on the way out and in
      return new PrimitiveFieldTripComponent<>(parentType, ordinal, field, hasDefaultValue, defaultValue);
    }
    return new FieldTripComponent<>(parentType, ordinal, field, hasDefaultValue, defaultValue);
  }

  @Override
  public <P, T> TripComponent<P, T> createComponent(TripType<P> parentType, int ordinal, Method getter, Method setter, boolean hasDefaultValue, T defaultValue) {
    return new MethodTripComponent<>(parentType, ordinal, getter, setter, hasDefaultValue, defaultValue);
  }

  @Override
  public <P extends Record> TripComponent<P, ?> createComponent(TripType<P> parentType, int ordinal, RecordComponent recordComponent) {
    return new RecordTripComponent<>(parentType, ordinal, recordComponent);
  }

  @Override
  public <P> TripComponent<P, ?> createComponent(TripType<P> parentType, int ordinal, Class<?> clazz) {
    return new ClassTripComponent<>(parentType, ordinal, clazz);
  }

  @Override
  public <T extends Enum<T>> TripComponent<T, T> createComponent(TripType<T> tripType, T enumConstant) {
    return new EnumTripComponent<>(tripType, enumConstant);
  }

  @Override
  public Registry createRegistry() {
    return new DefaultRegistry();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Remote> Class<T> findRemoteImplementation(Class<T> clazz) {
    try {
      return (Class<T>) remoteClassMapper.mapLenient(clazz);
    }
    catch (ClassNotFoundException c) {
      throw new TripRuntimeException("no implementation annotated with @RemoteService found for " + clazz);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T createRemoteProxy(UUID remoteId, Transport transport, Class<?>... clazz) {
    boolean isRegistryId = Registry.REGISTRY_ID.equals(remoteId);
    for (Class<?> iFace : clazz) {
      if (Registry.class.isAssignableFrom(iFace)) {
        throw new TripRuntimeException("Registry provides remote access only via RemoteLookup");
      }
      if (isRegistryId && !RemoteLookup.class.isAssignableFrom(iFace)) {
        throw new TripRuntimeException("remoteId must not be Registry.REGISTRY_ID for non-Registry classes");
      }
      else if (!isRegistryId && RemoteLookup.class.isAssignableFrom(iFace)) {
        throw new TripRuntimeException("remoteId must be Registry.REGISTRY_ID for the RemoteLookup");
      }
    }
    return (T) Proxy.newProxyInstance(clazz[0].getClassLoader(), clazz,
                                      createInvocationHandler(remoteId, transport, clazz));
  }

  @Override
  public InvocationHandler createInvocationHandler(UUID remoteId, Transport transport, Class<?>... clazz) {
    return new RemoteInvocationHandler(remoteId, transport);
  }

  @Override
  public Transport createTransport(URI uri) {
    Class<Transport> transportClass = transports.get(uri.getScheme());
    if (transportClass == null) {
      throw new TripRuntimeException("no transport found for protocol " + uri.getScheme());
    }
    try {
      return transportClass.getDeclaredConstructor(URI.class).newInstance(uri);
    }
    catch (NoSuchMethodException | InstantiationException |
           IllegalAccessException | InvocationTargetException e) {
      throw new TripRuntimeException("cannot create transport for " + uri, e);
    }
  }

  @Override
  public TransportManager createTransportManager() {
    return new DefaultTransportManager();
  }


  /**
   * Creates a trip type for the given class.
   *
   * @param clazz the class
   * @return the trip type
   * @param <T> the type of the class
   */
  @SuppressWarnings("unchecked")
  protected <T> TripType<T> createType(Class<T> clazz) {
    try {
      Class<?> tripTypeClass = typeClassMapper.mapLenient(clazz);
      return (TripType<T>) tripTypeClass.getDeclaredConstructor().newInstance();
    }
    catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
           IllegalAccessException | InvocationTargetException e) {
      // no predefined tripType: use a generic type builder
      return TripTypeBuilderFactory.getInstance().getBuilder(clazz).build(clazz);
    }
  }

}
