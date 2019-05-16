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

/**
 * Options common for attributes and entities.
 *
 * @author harald
 */
public interface CommonOptions {


  /**
   * The comment.
   *
   * @return the comment
   */
  String getComment();

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
   * @return true if don't generate accessor methods
   */
  boolean isNoMethod();

  /**
   * No constant strings.
   *
   * @return true if don't generate constants for names
   */
  boolean isNoConstant();

  /**
   * Element is mute and only provided for external system.
   *
   * @return true if unused by application
   */
  boolean isMute();

  /**
   * Declared in super class.
   *
   * @return true if element is derived from the super class
   */
  boolean isFromSuper();

  /**
   * Element is readonly.
   *
   * @return true if cannot be modified
   */
  boolean isReadOnly();

  /**
   * Element is writeonly.
   *
   * @return true if cannot be read by application
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
   * Returns whether lowercase conversion requested.
   *
   * @return true if lowercase
   */
  boolean isLowerCase();

  /**
   * Returns true whether the maxcol binding option should be applied.
   *
   * @return true if maxcol
   */
  boolean isMaxCol();

  /**
   * Returns whether uppercase conversion requested.
   *
   * @return true if uppercase
   */
  boolean isUpperCase();

  /**
   * Validates the options.
   *
   * @throws ModelException if validation failed
   */
  void validate() throws ModelException;

}
