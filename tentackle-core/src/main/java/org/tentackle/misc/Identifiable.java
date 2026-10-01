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


package org.tentackle.misc;

import org.tentackle.reflect.NotInterceptable;

import java.io.Serializable;

/**
 * Objects that can be identified by an ID.
 *
 * @author harald
 */
public interface Identifiable extends Serializable {

  /**
   * Gets the identification number.
   *
   * @return the ID, 0 if invalid
   */
  long getId();

  /**
   * Gets the generic string.<br>
   * This is usually the classname followed by the id in square brackets.
   *
   * @return the generic string value
   */
  @NotInterceptable
  String toGenericString();

}
