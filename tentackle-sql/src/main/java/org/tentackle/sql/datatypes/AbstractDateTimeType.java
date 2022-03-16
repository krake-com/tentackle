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

/**
 * Base class for date- or time-related data types.
 *
 * @param <T> the java type
 */
public abstract class AbstractDateTimeType<T> extends AbstractDataType<T> {

  @Override
  public boolean isDateOrTime() {
    return true;
  }

  @Override
  public String valueOfLiteralToCode(String str, Integer index) {
    if ("null".equals(str)) {
      return str;
    }
    if (index == null && !str.isEmpty() && Character.isDigit(str.charAt(0))) {
      str = "\"" + str + "\"";
    }
    return (isPredefined() ? getJavaType() : getDataTypeConstant()) + ".valueOf(" + str + ")";
  }

}
