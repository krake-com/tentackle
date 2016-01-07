/*
 * Tentackle - http://www.tentackle.org
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
   * the bind options (currently only AUTOSELECT for numbers, date & time).
   * @return the bind options
   */
  String getBindOptions();

  /**
   * true if [NODECLARE]-option set.
   * @return the NoDeclare
   */
  boolean isNoDeclare();

  /**
   * true if [NOMETHOD]-option set.
   * @return the NoMethod
   */
  boolean isNoMethod();

  /**
   * true if [NOCONSTANT]-option set.
   * @return the NoConstant
   */
  boolean isNoConstant();

  /**
   * true if [DERIVED]-option set.
   * @return the Derived
   */
  boolean isDerived();

  /**
   * true if [SUPER]-option set.
   * @return the FromSuper
   */
  boolean isFromSuper();

  /**
   * true if [READONLY]-option set.
   * @return the ReadOnly
   */
  boolean isReadOnly();

  /**
   * true if [WRITEONLY]-option set.
   * @return the WriteOnly
   */
  boolean isWriteOnly();

  /**
   * true if [PACKAGE]-option set.
   * @return the AccessPackage
   */
  AccessScope getAccessScope();

  /**
   * true if [TRIMREAD]-option set.
   * @return the TrimRead
   */
  boolean isTrimRead();

  /**
   * true if [TRIMWRITE]-option set.
   * @return the TrimWrite
   */
  boolean isTrimWrite();

  /**
   * true if [MAPNULL]-option set.
   * @return the MapNull
   */
  boolean isMapNull();

  /**
   * true if [SETGET]-option set.
   * @return the SetGet
   */
  boolean isSetGet();

  /**
   * true if [BIND]-option set.
   * @return the Bind
   */
  boolean isBind();

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
