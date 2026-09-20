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

package org.tentackle.maven.plugin.sql.reveng;

import java.util.Locale;

/**
 * Converts database table- and column names (snake_case, any case) to
 * the Java names used by the Tentackle model DSL (PascalCase entity names,
 * camelCase attribute names).
 *
 * @author harald
 */
public final class TableNaming {

  private TableNaming() {
    // no instances
  }

  /**
   * Converts a table name to a PascalCase entity name.<br>
   * For example {@code order_item} or {@code ORDER_ITEM} becomes {@code OrderItem}.
   *
   * @param tableName the table name, without schema
   * @return the entity name
   */
  public static String toEntityName(String tableName) {
    return toIdentifier(tableName, true);
  }

  /**
   * Converts a column name to a camelCase Java attribute name.<br>
   * For example {@code customer_id} becomes {@code customerId}.
   *
   * @param columnName the column name
   * @return the java attribute name
   */
  public static String toJavaName(String columnName) {
    return toIdentifier(columnName, false);
  }

  private static String toIdentifier(String name, boolean firstUpper) {
    StringBuilder buf = new StringBuilder();
    boolean upperNext = firstUpper;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_' || c == '-' || c == ' ' || c == '.') {
        upperNext = true;
      }
      else if (Character.isLetterOrDigit(c)) {
        buf.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upperNext = false;
      }
    }
    if (!buf.isEmpty() && Character.isDigit(buf.charAt(0))) {
      buf.insert(0, '_');
    }
    return buf.toString();
  }

  /**
   * Strips a trailing {@code _id} (any case) from a column name and
   * converts the remainder to a PascalCase name.<br>
   * Used to derive a relation's default name from the foreign key column,
   * e.g. {@code shipping_customer_id} becomes {@code ShippingCustomer}.
   *
   * @param columnName the foreign key column name
   * @return the derived name, never empty (falls back to the full column name if it is only "id")
   */
  public static String toRelationBaseName(String columnName) {
    String stripped = columnName;
    String lower = columnName.toLowerCase(Locale.ROOT);
    if (lower.endsWith("_id") && lower.length() > 3) {
      stripped = columnName.substring(0, columnName.length() - 3);
    }
    String result = toIdentifier(stripped, true);
    return result.isEmpty() ? toIdentifier(columnName, true) : result;
  }

}
