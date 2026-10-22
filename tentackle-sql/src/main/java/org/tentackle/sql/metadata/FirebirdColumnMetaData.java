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

/**
 * Column metadata for Firebird.
 * <p>
 * Firebird stores the default value as the source text of the column definition,
 * i.e. including the leading {@code DEFAULT} keyword. Depending on the driver version,
 * the keyword is reported as well and must be removed.
 */
public class FirebirdColumnMetaData extends ColumnMetaData {

  private static final String DEFAULT_KEYWORD = "DEFAULT";

  /**
   * Creates column metadata.
   *
   * @param tableMetaData the table metadata this column belongs to
   */
  public FirebirdColumnMetaData(TableMetaData tableMetaData) {
    super(tableMetaData);
  }

  @Override
  public void validate() {
    super.validate();
    String defaultValue = getDefaultValue();
    if (defaultValue != null) {
      defaultValue = defaultValue.trim();
      int length = DEFAULT_KEYWORD.length();
      if (defaultValue.regionMatches(true, 0, DEFAULT_KEYWORD, 0, length) &&
          (defaultValue.length() == length || Character.isWhitespace(defaultValue.charAt(length)))) {
        defaultValue = defaultValue.substring(DEFAULT_KEYWORD.length()).trim();
      }
      setDefaultValue(defaultValue.isEmpty() ? null : defaultValue);
    }
  }

}
