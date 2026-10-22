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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * The data type.<br>
 * Describes a type from the model perspective and maps it to one or more {@link SqlType}s, each corresponding to
 * a database column.
 * <p>
 * Implementations must be annotated with <code>@Service(DataType.class)</code>.
 * DataTypes are singletons and loaded by the {@link DataTypeFactory}. Hence, they must
 * not maintain any state and the <code>==</code> operator is logically equivalent to equals().
 * <p>
 * A predefined set of commonly used Java- and Tentackle-types is already provided, but the application may define
 * its own types (that's why <code>DataType</code> is not an enum).<br>
 * The combination of {@link #getJavaType()} and {@link #getVariant()} is unique.
 * The java type is the plain classname without the package, since we don't want the model
 * to refer to classes with the same name, but in different packages (usually a bad idea anyway).
 * <p>
 * The use of <code>DataType</code>s is threefold:
 * <ol>
 *   <li>for the model's attributes that refer to known types</li>
 *   <li>for the wurblets to generate type-specific code</li>
 *   <li>for application-specific types used at runtime by the persistence layer</li>
 * </ol>
 * Notes for implementors of application-specific data types:<br>
 * The method {@link #isPredefined()} must return false. Otherwise, the wurblets assume that type-specific methods exist
 * and generate uncompilable code.<br>
 * Furthermore, since <code>DataType</code>s refer to the corresponding Java type,
 * they should reside in a separate module the java type's module does not depend on. Otherwise,
 * all application modules would refer to the tentackle-sql module, which is not desired for the PDO module(s).
 * <p>
 * Finally, the <code>DataType</code>'s module must be made known to the following maven plugins:
 * <ul>
 *   <li>wurbelizer plugin: add as <code>wurbletDependency</code> (see the wurbelizer docs)</li>
 *   <li>tentackle sql plugin: add as plugin dependency</li>
 * </ul>
 *
 * @param <T> the java type described by this data type
 */
public interface DataType<T> {

  /**
   * Gets the name of the Java type.
   *
   * @return the type
   */
  String getJavaType();

  /**
   * Gets the model type string.<br>
   * This is usually identical to the java type,
   * but some variants (such as JSON or XML) may differ.
   *
   * @return the model type string
   */
  String getModelType();

  /**
   * Gets the type name used to build method names, such as the getters and setters of the JDBC wrappers.<br>
   * Usually the java type, but must be a valid java identifier (e.g. {@code Bytes} for {@code byte[]}).
   *
   * @return the method type name
   */
  String getMethodTypeName();

  /**
   * Gets the optional variant of the type.<br>
   * If a variant is not empty, it replaces the java type suffix for setters and getters.
   *
   * @return the variant, empty string if none, never null
   */
  String getVariant();


  /**
   * Returns whether this is a predefined type.
   *
   * @return true if explicit methods provided by the persistence layer, false if generic methods must be used
   */
  boolean isPredefined();

  /**
   * Returns whether the type is a primitive.
   *
   * @return true if primitive
   */
  boolean isPrimitive();

  /**
   * Gets the non-primitive type if this is a primitive.
   *
   * @return the non-primitive type, this type if it is already a non-primitive
   */
  DataType<?> toNonPrimitive();

  /**
   * Gets the primitive type if this is a non-primitive.
   *
   * @return the primitive type if there is one
   */
  Optional<DataType<?>> toPrimitive();

  /**
   * Returns whether the type is a mutable java object.<br>
   * Mutable objects may change their state and must
   * implement {@link org.tentackle.common.Freezable}.
   *
   * @return true if mutable and freezable
   */
  boolean isMutable();

  /**
   * Returns whether this is a numeric type.
   *
   * @return true if numeric
   */
  boolean isNumeric();

  /**
   * Returns whether this is a date and/or time type.
   *
   * @return true if time, date or timestamp
   */
  boolean isDateOrTime();

  /**
   * Returns whether a {@link java.util.Calendar}-timezone can be applied to this date and/or time-related type.<br>
   * Only for types where methods with Calendar argument are provided in ResultSet and PreparedStatement.
   *
   * @return true if methods exist with timezone correction
   */
  boolean isTimezoneApplicable();

  /**
   * Returns whether this is a boolean or Boolean type.
   *
   * @return true if bool
   */
  boolean isBool();

  /**
   * Returns whether the number of columns is backend-specific.<br>
   * For such types the following restrictions apply:
   * <ul>
   *   <li>no <code>CN_...</code> will be generated for the individual columns, just the column basename</li>
   *   <li>they cannot be used as wurblet arguments (to generate the WHERE-clause)</li>
   *   <li>the application cannot refer to a column in a backend-agnostic way</li>
   * </ul>
   *
   * @return false if not backend-specific (default), true if varying
   */
  boolean isColumnCountBackendSpecific();

  /**
   * Gets the number of database columns.<br>
   * A tentackle type may be mapped to more than one column for multi-column types such as {@link org.tentackle.common.BMoney}.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @return default is 1
   */
  int getColumnCount(Backend backend);

  /**
   * Returns whether this type can be compared, e.g., for equality or ordering.<br>
   * Some types have no backend-provided comparator at all, for example, XML or JSON
   * documents: comparing or ordering by such a column is meaningless, and such types
   * cannot be part of an index or used as a query/sort argument.
   *
   * @return true if comparable (default), false if the backend provides no comparator
   */
  boolean isComparable();

  /**
   * Gets the indexes to the sortable columns.<br>
   * The number and order of the indexes is reflected in the generated order-by SQL clause.
   *
   * @return the column indexes, null or empty if datatype is not sortable in a meaningful manner by the database
   */
  int[] getSortableColumns();

  /**
   * Gets the SQL type.<br>
   * For predefined types, the type must be the same for all backends and
   * the backend argument may be null.<br>
   * Application-specific types, however, can use different SQL types for different backends,
   * and for those types the backend argument is always valid.
   *
   * @param backend the backend, not used by predefined types
   * @param index the column index
   * @return the SQL type
   */
  SqlType getSqlType(Backend backend, int index);

  /**
   * Gets the column size.<br>
   * Sizes may be differently aligned according to the backend.
   *
   * @param backend the backend
   * @param index the column index
   * @param size the size from the model
   * @return the column size
   */
  int getSize(Backend backend, int index, Integer size);

  /**
   * Gets the column scale.<br>
   * The scale may be differently aligned according to the backend.
   *
   * @param backend the backend
   * @param index the column index
   * @param scale the scale from the model
   * @return the column scale
   */
  int getScale(Backend backend, int index, Integer scale);

  /**
   * Gets the value of a column.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @param index the column index
   * @param value the datatype's value according to the model
   * @return the column's value
   */
  Object getColumnValue(Backend backend, int index, T value);

  /**
   * Returns the java code to get the value of a column.
   *
   * @param index the column index
   * @param varName the java variable name
   * @return Java code, empty string if this is a single column type
   */
  String getColumnGetter(int index, String varName);

  /**
   * Gets the alias name of a column.
   *
   * @param index the column index
   * @return the column's alias
   */
  String getColumnAlias(int index);

  /**
   * Gets the optional suffix.<br>
   * Suffixes usually begin with an underscore.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @param index   the column index
   * @return the optional suffix
   */
  Optional<String> getColumnSuffix(Backend backend, int index);

  /**
   * Gets the optional comment suffix.<br>
   * If present, it will be appended to the comment.
   * <p>
   * Important: for datatypes with more than one column, none or exactly
   * one should be without suffix. See WurbletArgument in tentackle-wurblets.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @param index   the column index
   * @return the optional suffix
   */
  Optional<String> getCommentSuffix(Backend backend, int index);

  /**
   * Creates a list of column names.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @param columnName the column basename
   * @return the names as an unmodifiable list
   */
  List<String> createColumnNames(Backend backend, String columnName);

  /**
   * Creates a list of column names as a concatenated string.<br>
   * The string can be used in SQL INSERT and UPDATE statements.
   *
   * @param backend the backend, ignored if !{@link #isColumnCountBackendSpecific()}
   * @param columnName the column basename
   * @param separator the separator string between names
   * @return the name list
   */
  String createColumnNamesAsString(Backend backend, String columnName, String separator);

  /**
   * Returns whether this type supports mapping null values to some well-defined constant.
   *
   * @return true if mapNull supported
   */
  boolean isMapNullSupported();

  /**
   * Gets the mapped non-null value that represents the model's null value.<br>
   * The method throws a {@link BackendException}, if !{@link #isMapNullSupported()}.
   *
   * @param backend the backend, null if not backend-specific
   * @param index   the column index
   * @return the non-null mapped value
   */
  Object getMappedNullValue(Backend backend, int index);

  /**
   * Returns whether this type supports the UTC option.
   *
   * @return true if UTC supported
   */
  boolean isUTCSupported();

  /**
   * For certain numeric types, a downcast is necessary when assigning literals.
   *
   * @return true if downcast is necessary
   */
  boolean isDowncastNecessary();

  /**
   * Returns whether the model provides an inner type.
   *
   * @return true if the inner type is specified within angle brackets, false if standard type
   */
  boolean isModelProvidingInnerType();

  /**
   * Returns whether the inner type describes a generified java type.<br>
   * Implies {@link #isModelProvidingInnerType()}.
   *
   * @return true if javaType&lt;T&gt;, else simple type
   */
  boolean isJavaTypeGenerified();

  /**
   * Returns whether this type can be used literally in a query String.
   *
   * @param index column index, null if applies to the object of this type
   * @return true if literal String supported, else false
   */
  boolean isLiteralSupported(Integer index);

  /**
   * Takes the string representation of a value and converts it to an SQL literal.<br>
   * Some types need single quotes, for example.
   *
   * @param str the value string
   * @param index column index, null if applies to the object of this type
   * @return the SQL literal
   */
  String toLiteral(String str, Integer index);

  /**
   * Parses a string and converts to the value of this type.<br>
   * The method is used to parse a literal (for example, the default value defined in the model).
   * <p>
   * Notice: the method doesn't use any locale, so the results are
   * always the same regardless of the JVM's locale.
   *
   * @param str the source string
   * @return the value
   */
  T valueOf(String str);

  /**
   * Creates the java code to apply the valueOf method to a String.
   *
   * @param str the literal to be parsed
   * @param index column index, null if applies to the object of this type
   * @return Java code
   */
  String valueOfLiteralToCode(String str, Integer index);

  /**
   * Takes an object and converts it to a string that can in turn be parsed with {@link #valueOf(String)}.<br>
   * The method is used to print a literal (for example, the default value of a dumped attribute).
   * <p>
   * Notice: the method doesn't use any locale, so the results are
   * always the same regardless of the JVM's locale.
   *
   * @param object the object of this DataType
   * @return the printable string
   */
  String toString(T object);

  /**
   * Gets the name of the datatype constant.
   *
   * @return the DT_...
   */
  String getDataTypeConstant();

  /**
   * Sets the object into a prepared statement.<br>
   * Must be implemented if {@link #isPredefined()} returns false.
   * Not invoked by the framework otherwise.
   *
   * @param backend the database backend
   * @param statement the prepared statement
   * @param pos the position of the first SQL value
   * @param object the object; may be null
   * @param mapNull true if map null-values to non-null values
   * @param size the optional size specified in the model
   * @return the values stored in the prepared statement
   * @throws SQLException if failed
   */
  Object[] set(Backend backend, PreparedStatement statement, int pos, T object, boolean mapNull, Integer size) throws SQLException;

  /**
   * Sets a column of an object into a prepared statement.<br>
   * Must be implemented if {@link #isPredefined()} returns false.
   * Not invoked by the framework otherwise.
   *
   * @param backend the database backend
   * @param statement the prepared statement
   * @param pos the position of the first SQL value
   * @param object the object; may be null
   * @param index the column index
   * @param mapNull true if map null-values to non-null values
   * @param size the optional size specified in the model
   * @return the object stored in the prepared statement
   * @throws SQLException if failed
   */
  Object set(Backend backend, PreparedStatement statement, int pos, T object, int index, boolean mapNull, Integer size) throws SQLException;

  /**
   * Gets the object from a result set.<br>
   * Must be implemented if {@link #isPredefined()} returns false.
   * Not invoked by the framework otherwise.
   *
   * @param backend the database backend
   * @param resultSet the result set
   * @param pos the column positions in the result set
   * @param mapNull true if unmap null-values from non-null values
   * @param size the optional size specified in the model
   * @return the object or null if column(s) IS NULL
   * @throws SQLException if failed
   */
  T get(Backend backend, ResultSet resultSet, int[] pos, boolean mapNull, Integer size) throws SQLException;

}
