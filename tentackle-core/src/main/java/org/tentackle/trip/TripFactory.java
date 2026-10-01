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

package org.tentackle.trip;

import org.tentackle.common.ServiceFactory;
import org.tentackle.trip.impl.DefaultTripFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;


interface TripFactoryHolder {
  TripFactory INSTANCE = ServiceFactory.createService(TripFactory.class, DefaultTripFactory.class);
}


/**
 * Factory for creating various TRIP objects.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}. The default implementation is
 * {@link org.tentackle.trip.impl.DefaultTripFactory}.
 */
public interface TripFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static TripFactory getInstance() {
    return TripFactoryHolder.INSTANCE;
  }

  /**
   * Creates a new stream.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param serializer the serializer to use for serialization
   * @param deserializer the deserializer to use for deserialization
   * @return the stream
   */
  TripStream createStream(Dictionary dictionary, Serializer serializer, Deserializer deserializer);

  /**
   * Creates a new serializer.
   *
   * @param out the output stream to write to
   * @return the serializer
   */
  Serializer createSerializer(OutputStream out);

  /**
   * Creates a new deserializer.
   *
   * @param in the input stream to read from
   * @return the deserializer
   */
  Deserializer createDeserializer(InputStream in);

  /**
   * Creates a new dictionary.
   *
   * @param id the UUID of the dictionary
   * @param server true if server side, false if client side
   * @return the dictionary
   */
  Dictionary createDictionary(UUID id, boolean server);

  /**
   * Creates a new component for the given field within an object.
   *
   * @param parentType the trip type of the parent object
   * @param ordinal the ordinal of the field within the object, starting from 0
   * @param field the field to create a component for
   * @param hasDefaultValue true if the field has a default value, false otherwise
   * @param defaultValue the default value for the component
   * @return the component
   * @param <P> the object type
   * @param <T> the field type
   */
  <P, T> TripComponent<P, T> createComponent(TripType<P> parentType, int ordinal, Field field, boolean hasDefaultValue, T defaultValue);

  /**
   * Creates a new component for the given getter within an object.
   *
   * @param parentType the trip type of the parent object
   * @param ordinal the ordinal of the field within the object, starting from 0
   * @param getter the getter to create a component for
   * @param setter the setter to create a component for, null if not applicable
   * @param hasDefaultValue true if the field has a default value, false otherwise
   * @param defaultValue the default value for the component
   * @return the component
   * @param <P> the object type
   * @param <T> the getter's return type
   */
  <P, T> TripComponent<P, T> createComponent(TripType<P> parentType, int ordinal, Method getter, Method setter, boolean hasDefaultValue, T defaultValue);

  /**
   * Creates a new component for the given record component.
   *
   * @param parentType the trip type of the parent record
   * @param ordinal the ordinal of the field within the record, starting from 0
   * @param recordComponent the record component to create a component for
   * @return the component
   * @param <P> the record type
   */
  <P extends Record> TripComponent<P, ?> createComponent(TripType<P> parentType, int ordinal, RecordComponent recordComponent);

  /**
   * Creates a new component for the given class or interface.
   *
   * @param parentType the trip type of the proxy
   * @param ordinal the ordinal of the class, starting from 0
   * @param clazz the class to create a component for
   * @return the component
   * @param <P> the parent type
   */
  <P> TripComponent<P, ?> createComponent(TripType<P> parentType, int ordinal, Class<?> clazz);

  /**
   * Creates a new component for the given enum constant.
   *
   * @param tripType the trip type of the enum constant
   * @param enumConstant the enum constant to create a component for
   * @return the component
   * @param <T> the enum type
   */
  <T extends Enum<T>> TripComponent<T, T> createComponent(TripType<T> tripType, T enumConstant);

  /**
   * Gets the OpCode for the given numeric code.
   *
   * @param code the numeric code
   * @return the OpCode
   * @throws TripRuntimeException if no matching OpCode could be found
   */
  OpCode getOpCode(int code);

  /**
   * Gets all the OpCodes.
   *
   * @return the OpCodes, never null
   */
  Collection<OpCode> getAllOpCodes();

  /**
   * Gets the TripType for the given FQCN.<br>
   * If there is a predefined or application-specific TripType, that is returned.
   * Otherwise, the type will be created dynamically by the best matching TripTypeBuilder.
   *
   * @param className the FQCN of the data type
   * @return the trip type, never null
   * @see org.tentackle.reflect.ReflectionHelper#getSerializableClassName(Class)
   * @throws TripRuntimeException if no matching TripType could be found
   */
  TripType<?> getType(String className);

  /**
   * Gets the TripType for the given class.<br>
   * If there is a predefined or application-specific TripType, that is returned.
   * Otherwise, the type will be created dynamically by the best matching TripTypeBuilder.
   *
   * @param clazz the class of the data type
   * @return the trip type, never null
   * @param <T> the data type
   * @throws TripRuntimeException if no matching TripType could be found
   */
  <T> TripType<T> getType(Class<? extends T> clazz);

  /**
   * Gets the TripComponentProvider for the given class.<br>
   * Providers must be annotated with {@link TripComponentProviderService}.
   *
   * @param clazz the class to get the provider for
   * @return the provider, null if none
   * @param <T> the class type
   */
  <T> TripComponentProvider<T> getComponentProvider(Class<T> clazz);

  /**
   * Gets the TripInstanceCreator for the given class.<br>
   * Creators must be annotated with {@link TripInstanceCreatorService}.
   *
   * @param clazz the class to get the creator for
   * @return the creator, null if none
   * @param <T> the class type
   */
  <T> TripInstanceCreator<T> getInstanceCreator(Class<T> clazz);

  /**
   * Creates a new registry.
   *
   * @return the registry
   */
  Registry createRegistry();

  /**
   * Finds the class implementing the given remote interface.<br>
   * The implementing class must be annotated with {@link RemoteService}.
   *
   * @param clazz the remote interface to find the implementation for
   * @return the implementation class
   * @param <T> the remote interface type
   * @throws TripRuntimeException if no matching implementation could be found
   */
  <T extends Remote> Class<T> findRemoteImplementation(Class<T> clazz);

  /**
   * Creates a new {@code Transport} for the given URI.
   *
   * @param uri the URI of the transport
   * @return the transport
   */
  Transport createTransport(URI uri);

  /**
   * Creates a new {@code TransportManager}.
   *
   * @return the client transport manager
   */
  TransportManager createTransportManager();

  /**
   * Creates a new remote proxy for the given class and remote id.<br>
   * This method is invoked at the transmitter side, i.e., the client.
   *
   * @param remoteId the ID of the remote delegate
   * @param transport the transport to use
   * @param clazz the interface(s) to create the proxy for
   * @return the remote proxy
   * @param <T> the interface type
   */
  <T> T createRemoteProxy(UUID remoteId, Transport transport, Class<?>... clazz);

  /**
   * Creates a new invocation handler for the given class and remote id.<br>
   * This method is invoked from {@link #createRemoteProxy(UUID, Transport, Class[])}.
   *
   * @param remoteId the ID of the remote delegate
   * @param transport the transport to use
   * @param clazz the interface(s) to create the invocation handler for
   * @return the invocation handler
   */
  InvocationHandler createInvocationHandler(UUID remoteId, Transport transport, Class<?>... clazz);

}
