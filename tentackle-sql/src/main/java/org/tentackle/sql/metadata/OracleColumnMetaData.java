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
 * Oracle column data.<br>
 * Oracle returns the default value exactly as specified in the ALTER TABLE statement.
 * Hence "MODIFY ... DEFAULT NULL" will <em>not</em> drop the default value, but
 * simply use {@code NULL} as default. Furthermore, any attempt to fool the dbms
 * (using (null), for example) will not work either.
 *
 * @author harald
 */
public class OracleColumnMetaData extends ColumnMetaData {

  /**
   * Creates column meta data.
   *
   * @param tableMetaData the table meta data this column belongs to
   */
  public OracleColumnMetaData(TableMetaData tableMetaData) {
    super(tableMetaData);
  }

  @Override
  public void validate() {
    super.validate();
    String deflt = getDefaultValue();
    if (deflt != null &&
        ("NULL".equals(deflt) || "(NULL)".equals(deflt) || "null".equals(deflt) || "(null)".equals(deflt))) {
      setDefaultValue(null);
    }
  }

}
