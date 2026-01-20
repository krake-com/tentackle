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

import java.util.function.Consumer;

/**
 * A serializable data type.<br>
 * The trip type applies to the local JVM only and is never
 * sent over the wire.<br>
 * Notice: <code>TripTypes</code> are singletons and must not maintain any state
 * to provide thread-safety!
 *
 * @param <T> the type
 * @see DictionaryEntry
 */
public interface TripType<T> {

  /**
   * Gets the serviced data class.
   *
   * @return the class
   */
  Class<T> getObjectClass();

  /**
   * Returns whether instances of this type can be back-referenced.<br>
   * Most application-specific objects can be referenced, but some
   * types should be serialized separately, for example, numeric values
   * like integers or floats.
   *
   * @param value the object
   * @return true to check for references, false to serialize separately
   */
  boolean isReferencable(T value);

  /**
   * Returns whether instances of this type are immutable and subject to deduplication.<br>
   * Important: those objects must conform to the equals() and hashCode() contract!
   *
   * @param value the object
   * @return true if deduplicateable
   */
  boolean isDeduplicateable(T value);

  /**
   * Returns whether the type's class is final.<br>
   * Non-final types also include the dictionary ordinal or the dictionary entry
   * in the serialized data.<br>
   * If final, the corresponding OpCode must provide this type as its fixed type!
   *
   * @return true if final, false if the class can be extended or is an interface
   */
  boolean isFinal();

  /**
   * Returns whether objects of the type's class can be instantiated.<br>
   *
   * @return true if instantiable, false if not
   */
  boolean isInstantiable();

  /**
   * Returns whether the type sends a dictionary entry or ordinal.<br>
   *
   * @return true if dictionary-based, false for simple objects (String, Integer, ...)
   */
  boolean isDictionaryBased();

  /**
   * Gets the type category.
   *
   * @return the category
   */
  TripTypeCategory getCategory();

  /**
   * Gets the optional components of this type.
   *
   * @return the component fields, empty if none, never null
   */
  TripComponent<T, ?>[] getComponents();

  /**
   * Writes the given object to the output stream.
   *
   * @param value the object
   * @param stream the trip stream
   */
  void write(T value, TripStream stream);

  /**
   * Reads an object from the stream.
   *
   * @param stream the trip stream
   * @param opCode the op code (already read from the stream)
   * @return the object read from the stream
   */
  T read(TripStream stream, OpCode opCode);

  /**
   * Reads an object from the stream.
   *
   * @param stream the trip stream
   * @param opCode the op code (already read from the stream)
   * @param initializer the object initializer invoked after the new object is created
   * @return the object read from the stream
   */
  T read(TripStream stream, OpCode opCode, Consumer<T> initializer);

}
