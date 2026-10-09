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

package org.tentackle.sql.datatypes;

import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.DataType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implements some common methods for data types.
 *
 * @param <T> the java type
 */
public abstract class AbstractDataType<T> implements DataType<T> {

  /**
   * Maximum number of lines in literal.<br>
   * Unlimited if &le; 0 (default).
   */
  public static int maxLinesInLiteral = -1;

  /**
   * Default sortable columns.
   */
  private static final int[] SORTABLE_COLUMNS = { 0 };


  /**
   * Parent constructor.
   */
  public AbstractDataType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getModelType() {
    return getJavaType();
  }

  @Override
  public String getMethodTypeName() {
    return getJavaType();
  }

  @Override
  public boolean isPredefined() {
    return true;
  }

  @Override
  public String getVariant() {
    return "";
  }

  @Override
  public boolean isColumnCountBackendSpecific() {
    return false;
  }

  @Override
  public int getColumnCount(Backend backend) {
    assertColumnCountNotBackendSpecific(backend);
    return 1;
  }

  @Override
  public int[] getSortableColumns() {
    assertColumnCountNotBackendSpecific(null);
    return SORTABLE_COLUMNS;
  }

  /**
   * Predefined DataTypes must not override this method!<br>
   * See {@code ResultSetWrapper#COL2_SUFFIX}.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public Optional<String> getColumnSuffix(Backend backend, int index) {
    if (index < 0 || index >= getColumnCount(backend)) {
      throw new IndexOutOfBoundsException(index);
    }
    return index == 0 ? Optional.empty() : Optional.of("_" + (index + 1));    // none or _2, _3, etc...
  }

  @Override
  public Optional<String> getCommentSuffix(Backend backend, int index) {
    Optional<String> columnSuffix = getColumnSuffix(backend, index);
    if (columnSuffix.isPresent()) {
      String suffix = columnSuffix.get();
      if (suffix.length() > 1 && suffix.startsWith("_")) {
        suffix = suffix.substring(1);
      }
      return Optional.of(" (" + suffix + ")");
    }
    return Optional.empty();
  }

  @Override
  public List<String> createColumnNames(Backend backend, String columnName) {
    List<String> columns = new ArrayList<>();
    int columnCount = getColumnCount(backend);
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      columns.add(columnName + getColumnSuffix(backend, columnIndex).orElse(""));
    }
    return Collections.unmodifiableList(columns);
  }

  @Override
  public String createColumnNamesAsString(Backend backend, String columnName, String separator) {
    List<String> columnNames = createColumnNames(backend, columnName);
    StringBuilder buf = new StringBuilder();
    boolean first = true;
    for (String name : columnNames) {
      if (first) {
        first = false;
      }
      else {
        buf.append(separator);
      }
      buf.append(name);
    }
    return buf.toString();
  }

  @Override
  public int getSize(Backend backend, int index, Integer size) {
    return size == null ? 0 : size;
  }

  @Override
  public int getScale(Backend backend, int index, Integer scale) {
    return scale == null ? 0 : scale;
  }

  @Override
  public Object getColumnValue(Backend backend, int index, T value) {
    assertColumnCountNotBackendSpecific(backend);
    return value;
  }

  @Override
  public String getColumnGetter(int index, String varName) {
    if (getColumnCount(null) == 1) {
      return "";
    }
    throw new UnsupportedOperationException("getColumnGetter not yet implemented for multi-column datatype " + this);
  }

  @Override
  public String getColumnAlias(int index) {
    if (getColumnCount(null) == 1) {
      return "";
    }
    throw new UnsupportedOperationException("getColumnAlias not yet implemented for multi-column datatype " + this);
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public DataType<?> toNonPrimitive() {
    return this;
  }

  @Override
  public Optional<DataType<?>> toPrimitive() {
    return Optional.empty();
  }

  @Override
  public boolean isMutable() {
    return false;
  }

  @Override
  public boolean isComparable() {
    return true;
  }

  @Override
  public boolean isNumeric() {
    return false;
  }

  @Override
  public boolean isDateOrTime() {
    return false;
  }

  @Override
  public boolean isTimezoneApplicable() {
    return false;
  }

  @Override
  public boolean isBool() {
    return false;
  }

  @Override
  public boolean isMapNullSupported() {
    return false;
  }

  @Override
  public Object getMappedNullValue(Backend backend, int index) {
    if (!isMapNullSupported()) {
      throw new BackendException(this + " does not support the MAPNULL-option");
    }
    throw new UnsupportedOperationException("getMappedNullValue not yet implemented for " + this);
  }

  @Override
  public boolean isUTCSupported() {
    return false;
  }

  @Override
  public boolean isModelProvidingInnerType() {
    return isJavaTypeGenerified();
  }

  @Override
  public boolean isJavaTypeGenerified() {
    return false;
  }

  @Override
  public boolean isDowncastNecessary() {
    return false;
  }

  @Override
  public boolean isLiteralSupported(Integer index) {
    return getColumnCount(null) == 1 || index != null;
  }

  @Override
  public String toLiteral(String str, Integer index) {
    if (index == null && !isNumeric() && !str.isEmpty() && str.charAt(0) != '\'') {
      str = "'" + str + "'";
    }
    return str;
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if (isLiteralSupported(index)) {
      return str;
    }
    if (index == null && !str.isEmpty() && str.charAt(0) != '"') {
      str = StringHelper.toDoubleQuotes(str);
    }
    return (isPredefined() ? getJavaType() : getDataTypeConstant()) + ".valueOf(" + str + ")";
  }

  @Override
  public String toString(T object) {
    return StringHelper.toParsableString(object.toString());
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getJavaType());
    if (isJavaTypeGenerified()) {
      buf.append("<?>");
    }
    return buf.toString();
  }

  @Override
  public String getDataTypeConstant() {
    StringBuilder buf = new StringBuilder();
    buf.append("DT_").append(getMethodTypeName().toUpperCase(Locale.ROOT));
    if (!getVariant().isEmpty()) {
      buf.append('_').append(getVariant().toUpperCase(Locale.ROOT));
    }
    return buf.toString();
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, T object, int index, boolean mapNull, Integer size) throws SQLException {
    if (getColumnCount(backend) == 1) {
      return set(backend, statement, pos, object, mapNull, size);
    }
    throw new UnsupportedOperationException("not yet implemented for multi-column datatype " + this);
  }


  // in case someone uses equals or hashCode.
  // == is sufficient since data types are singletons.

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractDataType<?> that = (AbstractDataType<?>) o;

    if (!getJavaType().equals(that.getJavaType())) return false;
    return getVariant().equals(that.getVariant());
  }

  @Override
  public int hashCode() {
    int result = getJavaType().hashCode();
    result = 31 * result + getVariant().hashCode();
    return result;
  }


  /**
   * Checks that this type has a fixed number of columns or
   * the backend is specified.
   *
   * @param backend the backend, null if backend-agnostic
   * @throws BackendException if the column count is backend-specific and no backend is given
   */
  protected void assertColumnCountNotBackendSpecific(Backend backend) {
    if (backend == null && isColumnCountBackendSpecific()) {
      throw new BackendException("backend-specific type " + this + " cannot be used in a backend-agnostic context");
    }
  }

  /**
   * Transforms the value string of an object to java code.
   *
   * @param str the value string
   * @return the java code
   */
  protected String valueStringToCode(String str) {
    if (str != null && !str.isEmpty() && str.charAt(0) != '"') {
      if (str.contains("\n")) {   // multi-line string
        StringBuilder buf = new StringBuilder();
        buf.append("\"\"\"\n");
        long numLines = str.lines().count();
        Stream<String> lines;
        boolean truncated = (maxLinesInLiteral > 0 && numLines > maxLinesInLiteral);
        if (truncated) {
          lines = str.lines().limit(maxLinesInLiteral);
        }
        else {
          lines = str.lines();
        }
        lines.forEach(line -> {
          if (line.endsWith(" ")) {
            buf.append(line, 0, line.length() - 1).append("\\s");
          }
          else {
            buf.append(line);
          }
          buf.append('\n');
        });
        if (truncated) {
          buf.append("... <").append(maxLinesInLiteral).append('/').append(numLines).append("> ...\n");
        }
        buf.append("\"\"\"");
        str = buf.toString();
      }
      else {
        str = StringHelper.toDoubleQuotes(str);
      }
    }
    return str;
  }

}
