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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


interface MetaDataUtilitiesHolder {
  MetaDataUtilities INSTANCE = ServiceFactory.createService(MetaDataUtilities.class, MetaDataUtilities.class);
}


/**
 * Database metadata utility methods.<br>
 * Replaceable by application-specific implementation via {@link Service} annotation.
 *
 * @author harald
 */
@Service(MetaDataUtilities.class)
public class MetaDataUtilities {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static MetaDataUtilities getInstance() {
    return MetaDataUtilitiesHolder.INSTANCE;
  }


  /**
   * Reserved table names used by tentackle.
   */
  private static final String[] TENTACKLE_TABLE_NAMES = {
    "bundle",
    "bundlekey",
    "modification",
    "modlog",
    "numpool",
    "numrange",
    "prefkey",
    "prefnode",
    "secrules",
    "tokenlock"
  };


  private final Map<Integer,String> typeMap;    // maps JDBC type numbers to strings


  /**
   * Creates the utilities.<br>
   * Builds the map of JDBC type names.
   */
  public MetaDataUtilities() {
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


  /**
   * Converts a JDBC type to a string.
   *
   * @param jdbcType the JDBC type
   * @return the name of the {@link Types} constant, {@code UNKNOWN} if no such constant
   */
  public String jdbcTypeToString(int jdbcType) {
    String str = typeMap.get(jdbcType);
    if (str == null) {
      str = "UNKNOWN";
    }
    return str;
  }


  /**
   * Returns whether the given table name is reserved by Tentackle.<br>
   * The check is case-insensitive and ignores the schema.
   *
   * @param tableName the table name, with or without schema
   * @return true if reserved
   */
  public boolean isReservedTable(String tableName) {
    if (tableName != null) {
      tableName = tableName.toLowerCase(Locale.ROOT);
      int ndx = tableName.indexOf('.');
      if (ndx >= 0) {
        tableName = tableName.substring(ndx + 1);
      }
      for (String tentackleName: TENTACKLE_TABLE_NAMES) {
        if (tentackleName.equals(tableName)) {
          return true;
        }
      }
    }
    return false;
  }

}
