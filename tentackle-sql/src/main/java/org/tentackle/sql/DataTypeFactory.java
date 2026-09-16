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

package org.tentackle.sql;

import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.datatypes.ConvertibleType;

interface DataTypeFactoryHolder {
  DataTypeFactory INSTANCE = ServiceFactory.createService(DataTypeFactory.class, DefaultDataTypeFactory.class);
}

/**
 * Loads all data types and creates singletons of each type.
 */
public interface DataTypeFactory {

  /**
   * Large variant of a type.<br>
   * For example, to distinguish standard VARCHAR Strings from CLOBs.
   */
  String LARGE_VARIANT = "Large";

  /**
   * SQLXML variant of a type.<br>
   * Distinguishes a {@code String} bridged through the JDBC {@link java.sql.SQLXML} interface to a
   * native XML column from a plain VARCHAR or CLOB-backed one.
   */
  String SQLXML_VARIANT = "Sqlxml";

  /**
   * Type name for the wrapper type implementing <code>org.tentackle.misc.Convertible</code>.
   */
  String CONVERTIBLE_TYPE = "implementing Convertible";


  /**
   * The singleton.
   *
   * @return the singleton
   */
  static DataTypeFactory getInstance() {
    return DataTypeFactoryHolder.INSTANCE;
  }


  /**
   * Gets the datatype singleton.
   *
   * @param name the type name
   * @param variant the type variant, empty string if none
   * @return the datatype, null if no such type
   */
  DataType<?> get(String name, String variant);


  /**
   * Gets the datatype singleton.
   *
   * @param <T> the java type
   * @param clazz the java class
   * @param variant the variant, empty string if none
   * @return the datatype, null if no such type
   */
  @SuppressWarnings("unchecked")
  default <T> DataType<T> get(Class<T> clazz, String variant) {
    return (DataType<T>) get(StringHelper.lastAfter(clazz.getName(), '.'), variant);
  }


  /**
   * Gets the datatype singleton.
   *
   * @param javaType the java type
   * @return the datatype, null if no such type
   */
  default DataType<?> get(String javaType) {
    return get(javaType, "");
  }


  /**
   * Gets the datatype singleton.
   *
   * @param <T> the java type
   * @param clazz the java class
   * @return the datatype, null if no such type
   */
  default <T> DataType<T> get(Class<T> clazz) {
    return get(clazz, "");
  }


  /**
   * Gets the special convertible type singleton.
   *
   * @return the application-specific wrapper type
   */
  ConvertibleType getConvertibleType();

}
