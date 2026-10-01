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

/**
 * The stream to read and write trip objects.
 */
public interface TripStream {

  /**
   * Gets the dictionary.
   *
   * @return the dictionary
   */
  Dictionary getDictionary();

  /**
   * Returns the dictionary entry for the given ordinal.<br>
   *
   * @param ordinal the ordinal of the entry
   * @return the entry
   * @throws TripRuntimeException if the ordinal is invalid
   */
  DictionaryEntry getDictionaryEntry(int ordinal);

  /**
   * Returns the entry for the given class.
   *
   * @param clazz the class of the entry
   * @return the entry, null if no such entry exists
   */
  DictionaryEntry getDictionaryEntry(Class<?> clazz);

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
   * Gets the serializer.
   *
   * @return the serializer
   */
  Serializer getSerializer();

  /**
   * Gets the deserializer.
   *
   * @return the deserializer
   */
  Deserializer getDeserializer();

  /**
   * Gets an existing or creates a new reference for an object.<br>
   * If de-duplicatable, the reference will be mapped by the object's equals() method.
   * Otherwise, the reference will be mapped by an IdentityHashMap.
   *
   * @param <T> the object's type
   * @param object the object to check
   * @param deduplicate true if the object is de-duplicatable, false otherwise
   * @return the reference, never null
   * @see TripType#isReferencable(Object)
   * @see TripType#isDeduplicateable(Object)
   */
  <T> Reference<T> getReference(T object, boolean deduplicate);

  /**
   * Adds a reference to the stream.<br>
   * The method is invoked by the receiver to add an object received from the sender.
   *
   * @param <T> the object's type
   * @param reference the reference to add
   * @param object the object the reference belongs to
   * @param deduplicate true if the object is de-duplicatable, false otherwise
   */
  <T> void addReference(Reference<T> reference, T object, boolean deduplicate);

  /**
   * Gets the reference for the given reference id.
   *
   * @param <T> the reference type
   * @param clazz the referenced object's class
   * @param referenceId the reference id
   * @return the reference, never null
   * @throws TripRuntimeException if the reference id is invalid
   */
  <T> Reference<T> getReference(Class<T> clazz, int referenceId);

  /**
   * Gets the class for the given class name.
   *
   * @param className the class name
   * @return the class, never null
   */
  Class<?> getClass(String className);

  /**
   * Clears the stream's state.<br>
   * The sender and receiver should invoke this method to
   * mark the boundary between messages.
   */
  void clear();

  /**
   * Clears all references that belong to modifiable objects.<br>
   * Invoked in the middle of a roundtrip, i.e., at the client side,
   * after the request was sent to the server and at the server side,
   * before the response is sent back to the client.<br>
   * This keeps the references of immutable objects on both sides and
   * reduces the size of the response since for those objects,
   * only the reference number is sent back without the referenced value.
   */
  void clearModifiable();

  /**
   * Sets the filter deciding which components of an object are serialized.<br>
   * The filter applies to writing only and is not reset by {@link #clear()}.
   * Since the stream is usually shared, a filter set for a single object or message
   * should be removed afterward, preferably in a {@code finally} block.
   *
   * @param filter the filter, null to serialize all components (default)
   */
  void setFilter(TripFilter filter);

  /**
   * Gets the filter deciding which components of an object are serialized.
   *
   * @return the filter, null if all components are serialized
   */
  TripFilter getFilter();

  /**
   * Writes a component ordinal to the stream.
   *
   * @param ordinal the ordinal to write
   */
  void writeComponentOrdinal(int ordinal);

  /**
   * Reads a component ordinal from the stream.
   *
   * @param opCode the ordinal OpCode
   * @return the ordinal
   */
  int readComponentOrdinal(OpCode opCode);

  /**
   * Writes the next ordinal to the stream, using optimization.
   *
   * @param lastOrdinal the last ordinal written, -1 for none
   * @param component the component to write the ordinal for
   * @return the component ordinal
   */
  int writeNextComponentOrdinal(int lastOrdinal, TripComponent<?, ?> component);

  /**
   * Reads the next ordinal from the stream, using optimization.
   *
   * @param lastOrdinal the last ordinal read, -1 for none
   * @param opCode the ordinal OpCode
   * @return the ordinal
   */
  int readNextComponentOrdinal(int lastOrdinal, OpCode opCode);

  /**
   * Writes a dictionary ordinal to the stream.
   *
   * @param ordinal the ordinal to write
   */
  void writeDictionaryOrdinal(int ordinal);

  /**
   * Reads a dictionary ordinal from the stream.
   *
   * @param opCode the ordinal OpCode
   * @return the ordinal
   */
  int readDictionaryOrdinal(OpCode opCode);

  /**
   * Updates the dictionary entry if the class is new for the stream
   * and sends the class definition.<br>
   * Otherwise, sends the ordinal of the dictionary entry.
   *
   * @param clazz the object's class
   */
  void sendDictionary(Class<?> clazz);

  /**
   * Receives the dictionary entry and adds it to the dictionary.
   *
   * @param opCode the opcode to read (already read from the stream)
   * @return the dictionary entry
   */
  DictionaryEntry receiveDictionary(OpCode opCode);

  /**
   * Receives the dictionary entry and adds it to the dictionary.<br>
   * Same as {@link #receiveDictionary(OpCode)} but reads the opcode.
   *
   * @return the dictionary entry
   */
  DictionaryEntry receiveDictionary();

  /**
   * Derives the concrete type from the given OpCode.<br>
   * If the returned derived type is dictionary-based, the next opcode must
   * be read from the stream, which is either a reference or an ordinal.
   *
   * @param opCode the opcode (already read from the stream)
   * @return the type
   */
  TripType<?> deriveType(OpCode opCode);

  /**
   * Derives the concrete type from the given OpCode.<br>
   * Same as {@link #deriveType(OpCode)} but reads the opcode.
   *
   * @return the type
   */
  TripType<?> deriveType();

  /**
   * Receives the next OpCode from the stream.
   * <p>
   * If the OpCode denotes an error, the remote stack trace is read and thrown as a
   * {@link org.tentackle.trip.impl.TripError}.
   *
   * @return the OpCode
   */
  OpCode receiveOpCode();

  /**
   * Sends a TRIP error.<br>
   * The error opcode is sent first, followed by the throwable's stack trace.
   *
   * @param errorOpCode the error opcode
   * @param t the throwable
   */
  void sendError(OpCode errorOpCode, Throwable t);

}
