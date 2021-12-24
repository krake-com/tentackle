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
import org.tentackle.sql.DataType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Implements some common methods for data types.
 */
public abstract class AbstractDataType<T> implements DataType<T> {

  @Override
  public boolean isPredefined() {
    return true;
  }

  @Override
  public String getVariant() {
    return "";
  }

  @Override
  public int getColumnCount() {
    return 1;
  }

  /**
   * Predefined DataTypes must not override this method!<br>
   * See {@code ResultSetWrapper#COL2_SUFFIX}.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public Optional<String> getColumnSuffix(int index) {
    if (index < 0 || index >= getColumnCount()) {
      throw new IndexOutOfBoundsException(index);
    }
    return index == 0 ? Optional.empty() : Optional.of("_" + (index + 1));    // none or _2, _3, etc...
  }

  @Override
  public Optional<String> getCommentSuffix(int index) {
    Optional<String> columnSuffix = getColumnSuffix(index);
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
  public int getSize(int index, Integer size) {
    return size == null ? 0 : size;
  }

  @Override
  public int getScale(int index, Integer scale) {
    return scale == null ? 0 : scale;
  }

  @Override
  public Object getColumnValue(int index, T value) {
    return value;
  }

  @Override
  public String getColumnGetter(int index) {
    if (getColumnCount() == 1) {
      return "";
    }
    throw new UnsupportedOperationException("getColumnGetter not yet implemented for datatype " + this);
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
  public boolean isLiteralSupported() {
    return getColumnCount() == 1;
  }

  @Override
  public String toLiteral(String str) {
    if (!isNumeric() && !str.isEmpty() && str.charAt(0) != '\'') {
      str = "'" + str + "'";
    }
    return str;
  }

  @Override
  public String valueOfLiteralToCode(String str) {
    if (isLiteralSupported()) {
      return str;
    }
    if (str.length() > 0 && str.charAt(0) != '"') {
      str = "\"" + str + "\"";
    }
    return isPredefined() ? getJavaType() : getDataTypeConstant() + ".valueOf(" + str + ")";
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
    buf.append("DT_").append(getJavaType().toUpperCase());
    if (!getVariant().isEmpty()) {
      buf.append('_').append(getVariant().toUpperCase());
    }
    return buf.toString();
  }

  @Override
  public Object set(Backend backend, PreparedStatement statement, int pos, T object, int index, boolean mapNull, Integer size) throws SQLException {
    if (getColumnCount() == 1) {
      return set(backend, statement, pos, object, mapNull, size);
    }
    throw new UnsupportedOperationException("not yet implemented for datatype " + this);
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

}
