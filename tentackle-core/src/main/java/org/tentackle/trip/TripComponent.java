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
 * Describes a component within a class, record, or enum.
 *
 * @param <P> the parent type
 * @param <T> the component type
 */
public interface TripComponent<P, T> {

  /**
   * Gets the trip type of the parent.
   *
   * @return the parent type
   */
  TripType<P> getParentType();

  /**
   * Gets the class of the component.
   *
   * @return the class
   */
  Class<T> getComponentClass();

  /**
   * Gets the ordinal of the component within the parent class.
   * <p>
   * For the sender, the ordinal is always identical to the dictionary
   * entry component's ordinal.<br>
   * For the receiver, the ordinal is the dictionary entry component's ordinal,
   * and the trip component's ordinal may be different!
   *
   * @return the ordinal starting from 0
   */
  int getOrdinal();

  /**
   * Gets the compile-time trip type of the field.<br>
   * If the type is not final, the runtime type may differ!
   *
   * @return the type
   */
  TripType<T> getType();

  /**
   * Gets the field- or record component name.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the value of the field or record component.
   *
   * @param parent the parent object or record
   * @return the value
   */
  T getValue(P parent);

  /**
   * Sets the value of the field or record component.
   *
   * @param parent the parent object or record
   * @param value the value to be set
   */
  void setValue(P parent, T value);

  /**
   * Returns true if the component has a default value.<br>
   * Records don't have default values.
   *
   * @return true if the component has a default value, else false
   */
  boolean hasDefaultValue();

  /**
   * Gets the default value.<br>
   * For enums, the method returns the enum constant.<br>
   * For proxies, the method returns the interface class.
   *
   * @return the default value
   */
  T getDefaultValue();

}
