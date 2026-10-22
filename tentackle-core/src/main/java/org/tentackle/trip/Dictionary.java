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

import java.util.UUID;

/**
 * Dictionary of the classes sent over the wire.
 * <p>
 * Maps classes to ordinals, so that a class definition is sent only once per transport.
 * The server is the master assigning the permanent ordinals; clients create temporary entries
 * for classes the server doesn't know yet.
 */
public interface Dictionary {

  /**
   * Gets the unique ID of this dictionary.<br>
   * For servers, this is the same as the client's transport ID read from the input stream.
   * For clients, this is the same as the client's transport ID written to the output stream.
   * As a result, the server maintains one dictionary per client transport and the
   * client maintains one dictionary per server URI. Since each transport can have
   * multiple connections, the dictionary is shared among those connections.
   *
   * @return the dictionary ID
   */
  UUID getId();

  /**
   * Returns whether this is the dictionary master.
   *
   * @return true if server, else client side
   */
  boolean isServer();

  /**
   * Returns whether this dictionary does not hold any entry yet.
   * <p>
   * A client tells the server in the connection handshake whether its dictionary is still empty.
   * Only then may the server create a new dictionary for the client's transport. A client that
   * already holds entries — permanent ones assigned by the server as well as its own temporary
   * ones — would send ordinals a fresh server-side dictionary never learned.
   *
   * @return true if there is no entry at all
   * @see Transport#getServerDictionary(UUID, boolean)
   */
  boolean isEmpty();

  /**
   * Creates a new entry for the given class.
   *
   * @param clazz the class to create an entry for
   * @return the entry
   */
  DictionaryEntry createEntry(Class<?> clazz);

  /**
   * Creates a temporary entry for the given class.<br>
   * Only used by the client side.
   *
   * @param clazz the class to create an entry for
   * @return the entry
   */
  DictionaryEntry createTemporaryEntry(Class<?> clazz);

  /**
   * Creates an entry with components.
   *
   * @param ordinal the ordinal, &lt; 0 for temporary
   * @param className the FQCN
   * @param isProxy true if this is an entry for a dynamic proxy, false otherwise
   * @param components the components
   * @return the entry
   */
  DictionaryEntry createEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components);

  /**
   * Creates a new component of this entry.
   *
   * @param componentName the component name
   * @param type the compile-time type of the component
   * @return the component
   */
  DictionaryEntryComponent createComponent(String componentName, TripType<?> type);

  /**
   * Adds an entry to the dictionary.<br>
   * If a temporary entry for the given class name already exists,
   * the entry will be replaced.<br>
   * with the same ordinal or class name.
   *
   * @param entry the entry to add
   * @throws TripRuntimeException if a non-temporary entry already exists
   */
  void addEntry(DictionaryEntry entry);

  /**
   * Returns the entry for the given ordinal.<br>
   *
   * @param ordinal the ordinal of the entry
   * @return the entry
   * @throws TripRuntimeException if the ordinal is invalid
   */
  DictionaryEntry getEntry(int ordinal);

  /**
   * Returns the entry for the given class name.
   *
   * @param className the FQCN of the class
   * @return the entry, null if no such entry exists
   */
  DictionaryEntry getEntry(String className);

}
