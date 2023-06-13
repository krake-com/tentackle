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
 * An attribute of an index.
 *
 * @author harald
 */
public interface IndexAttribute {

  /**
   * Gets the attribute.
   *
   * @return the attribute
   */
  Attribute getAttribute();

  /**
   * Gets the column name.<br>
   * For single-column data types: the column name of the attribute.
   * For multi-column data types: the corresponding column name within the data type.
   *
   * @return the column name
   */
  String getColumnName();

  /**
   * Gets the name of the function.
   *
   * @return the function name, null if no function-based index
   */
  String getFunctionName();

  /**
   * Returns whether the attribute is descending.
   *
   * @return true if descending, else ascending (default)
   */
  boolean isDescending();

}
