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

import java.util.Properties;

/**
 * Loads properties from some resource and applies them to a {@link java.util.Properties} object.<br>
 * Implementations must be annotated with &#64;{@link PropertiesResourceService}.
 * @see PropertiesUtilities
 */
public interface PropertiesResource {

  /**
   * Applies the loaded properties.<br>
   * The method must not throw an exception if the resource is not found,
   * but just return false.
   * Exceptions must be thrown, however, if the resource was found but could not be loaded for
   * whatever reason.
   *
   * @param name the resource name
   * @param properties the properties object to apply the loaded properties to
   * @return true if properties loaded, false if no such resource found
   */
  boolean apply(String name, Properties properties);

}
