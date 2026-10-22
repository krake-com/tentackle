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
 * Dictionary entry.<br>
 * Maps an ordinal to an object class along with optional components defining a contract for a stream.<br>
 * Sent over the wire as a simple DTO.<br>
 * Only concrete classes and records become part of the dictionary, no abstract classes and no interfaces.
 * For enums the components are the enum names.
 * For dynamic proxies the classname is always <code>java.lang.reflect.Proxy</code> and the
 * components are the names of the interfaces implemented by the invocation handler.
 * Otherwise, the components correspond to the declared {@link java.lang.reflect.Field}s
 * or {@link java.lang.reflect.RecordComponent}s.<br>
 * Components are referenced in the stream via their index.
 */
public interface DictionaryEntry {

  /**
   * Gets the ordinal.
   *
   * @return the ordinal unique to a stream
   */
  int getOrdinal();

  /**
   * Returns whether the client created the entry.<br>
   * Client entries are sent from the client to the server if the object's type
   * wasn't used by the server so far.<br>
   * When sent back from the server to the client, the entry is replaced by the
   * server's one.
   *
   * @return true if client-only
   */
  boolean isClientOnly();

  /**
   * Returns whether this entry has been replaced by another entry for the same class.
   * <p>
   * Streams cache the entries by class. A client-only entry, however, may be replaced by the
   * server's entry later, so a stream may keep it only as long as it is not superseded.
   *
   * @return true if superseded or not tracked, false if still the dictionary's entry for its class
   * @see #markSuperseded()
   */
  boolean isSuperseded();

  /**
   * Marks this entry as replaced by another entry for the same class.<br>
   * Invoked by the dictionary after the new entry has been added.
   *
   * @see #isSuperseded()
   */
  void markSuperseded();

  /**
   * Returns whether the entry describes a dynamic proxy.
   *
   * @return true if dynamic proxy, else false
   */
  boolean isProxy();

  /**
   * Gets the interfaces implemented by the dynamic proxy.
   *
   * @return the interfaces, never null
   */
  Class<?>[] getProxyInterfaces();

  /**
   * Gets the serializable full qualified classname.
   *
   * @return the serializable FQCN
   * @see org.tentackle.reflect.ReflectionHelper#getSerializableClassName(Class)
   */
  String getClassName();

  /**
   * Gets the optional components of this type.<br>
   * Components can be fields, record components, or the interfaces of a dynamic proxy.
   * The ordinal of a component is its index in the string array.
   *
   * @return the components, empty if none, never null
   */
  DictionaryEntryComponent[] getComponents();

  /**
   * Gets the entry's component for the given ordinal.
   *
   * @param ordinal the component ordinal
   * @return the component, never null
   * @throws TripRuntimeException if the ordinal is invalid
   */
  DictionaryEntryComponent getComponent(int ordinal);

  /**
   * Gets the trip type corresponding to this entry.
   *
   * @return the type, never null
   */
  TripType<?> getType();

  /**
   * Gets the trip type's component for the entry's component ordinal.<br>
   * Notice that the entry's ordinal need not necessarily correspond to the type's component ordinals.
   * <p>
   * Returns null if this side does not provide the sender's component, which is tolerated:
   * unknown components are skipped. An ordinal outside the entry's components is not, since the
   * ordinals are read from the stream: it throws a {@link TripRuntimeException}, just like
   * {@link #getComponent(int)}.
   *
   * @param ordinal the ordinal
   * @return the trip component, null if this side does not provide it
   */
  TripComponent<?, ?> getTypeComponent(int ordinal);

}
