/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.sql.metadata;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Some helper methods.
 *
 * @author harald
 */
public class MetaDataHelper {

  private static Map<Integer,String> typeMap;


  /**
   * Converts a jdbc type to a string.
   *
   * @param jdbcType the jdbc type
   * @return the string, never null
   */
  public static synchronized String jdbcTypeToString(int jdbcType) {
    if (typeMap == null) {
      // build map once
      typeMap = new HashMap<>();
      for (Field field: Types.class.getDeclaredFields()) {
        if (field.getType() == Integer.TYPE && (field.getModifiers() & Modifier.STATIC) != 0) {
          try {
            typeMap.put((Integer) field.get(null), field.getName());
          }
          catch (IllegalArgumentException | IllegalAccessException ex) {
            // whatsoever...
          }
        }
      }
    }
    String str = typeMap.get(jdbcType);
    if (str == null) {
      str = "UNKNOWN";
    }
    return str;
  }

  private MetaDataHelper() {
  }

}
