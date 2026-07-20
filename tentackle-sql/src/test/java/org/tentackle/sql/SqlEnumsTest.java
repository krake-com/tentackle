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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the simple SQL enums that carry an SQL text.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SqlEnumsTest {

  @Test
  public void testJoinType() {
    assertEquals(JoinType.INNER.toString(), "JOIN");
    assertEquals(JoinType.LEFT.toString(), "LEFT JOIN");
    assertEquals(JoinType.RIGHT.toString(), "RIGHT JOIN");
    assertEquals(JoinType.FULL.toString(), "FULL JOIN");
  }

  @Test
  public void testSqlNameType() {
    assertEquals(SqlNameType.TABLE_NAME.toString(), "table name");
    assertEquals(SqlNameType.TABLE_ALIAS.toString(), "table alias");
    assertEquals(SqlNameType.COLUMN_NAME.toString(), "column name");
    assertEquals(SqlNameType.INDEX_NAME.toString(), "index name");
    assertEquals(SqlNameType.FOREIGN_KEY_NAME.toString(), "foreign key name");
  }

  @Test
  public void testMigrationStrategyRoundTrip() {
    assertEquals(MigrationStrategy.valueOf("NAME"), MigrationStrategy.NAME);
    assertEquals(MigrationStrategy.valueOf("NAME_AND_TYPE"), MigrationStrategy.NAME_AND_TYPE);
    assertEquals(MigrationStrategy.values().length, 7);
  }
}
