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


package org.tentackle.bind;

import java.util.SortedMap;

/**
 * Cache for bindable elements per class.<br>
 *
 * @author harald
 */
public interface BindableCache {

  /**
   * Gets the map of bindables for given class.
   *
   * @param clazz the class to retrieve the bindables for
   * @param declaredOnly true if only declared elements, else inherited as well
   * @return the tree map (camelName -&gt; bindable element).
   */
  SortedMap<String,BindableElement> getBindableMap(Class<?> clazz, boolean declaredOnly);

}
