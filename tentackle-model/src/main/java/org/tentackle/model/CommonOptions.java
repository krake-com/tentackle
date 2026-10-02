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

package org.tentackle.model;

import org.tentackle.common.StripMode;

import java.util.List;

/**
 * Options common for attributes and entities.
 *
 * @author harald
 */
public interface CommonOptions extends Commented {

  /**
   * Gets the list of custom stereotypes.
   *
   * @return the stereotypes
   */
  List<String> getStereotypes();

  /**
   * The bind options.
   *
   * @return the bind options
   */
  String getBindOptions();

  /**
   * No declaration code.
   *
   * @return true if don't generate declaration
   */
  boolean isNoDeclare();

  /**
   * No getter and setter methods.
   *
   * @return true if no accessor methods must be generated
   */
  boolean isNoMethod();

  /**
   * No constant strings.
   *
   * @return true if no name constants must be generated
   */
  boolean isNoConstant();

  /**
   * Element is mute.
   *
   * @return true if not part of the PDO
   */
  boolean isMute();

  /**
   * Declared in super class.
   *
   * @return true if element is derived from the super class
   */
  boolean isFromSuper();

  /**
   * Element is read-only.
   *
   * @return true if element cannot be modified
   */
  boolean isReadOnly();

  /**
   * Element is write-only.
   *
   * @return true if element cannot be read by application
   */
  boolean isWriteOnly();

  /**
   * The access scope.
   *
   * @return the scope
   */
  AccessScope getAccessScope();

  /**
   * Trim on read from database.
   *
   * @return true if string is trimmed when read
   */
  boolean isTrimRead();

  /**
   * Trim on write to database.
   *
   * @return true if string is trimmed on write
   */
  boolean isTrimWrite();

  /**
   * Map null values to constant in database.
   *
   * @return true if map null to non-null value
   */
  boolean isMapNull();

  /**
   * Generate bindable annotation.
   *
   * @return true if bindable element
   */
  boolean isBindable();

  /**
   * Returns whether autoselect should be turned on.
   *
   * @return true if autoselect
   */
  boolean isAutoSelect();

  /**
   * Returns whether lowercase conversion is requested.
   *
   * @return true if lowercase
   */
  boolean isLowerCase();

  /**
   * Returns whether the maxcol binding option should be applied.
   *
   * @return true if maxcol
   */
  boolean isMaxCol();

  /**
   * Returns whether uppercase conversion is requested.
   *
   * @return true if uppercase
   */
  boolean isUpperCase();

  /**
   * Gets the strip mode.
   *
   * @return the strip mode, null if default
   */
  StripMode getStripMode();

  /**
   * Validates the options.
   *
   * @throws ModelException if validation failed
   */
  void validate() throws ModelException;

}
