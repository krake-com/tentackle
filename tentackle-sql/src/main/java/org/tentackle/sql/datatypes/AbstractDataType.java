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
import org.tentackle.sql.DataType;

import java.util.Optional;

/**
 * Implements some common methods for datatypes.
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

  @Override
  public Optional<String> getColumnSuffix(int index) {
    if (index < 0 || index >= getColumnCount()) {
      throw new IndexOutOfBoundsException();
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
  public boolean isBool() {
    return false;
  }

  @Override
  public boolean isScaleInSecondColumn() {
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



  // in case someone uses equals or hashCode.
  // == is sufficient since datatypes are singletons.

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
