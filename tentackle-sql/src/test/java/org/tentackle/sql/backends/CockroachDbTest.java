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

package org.tentackle.sql.backends;

import org.testng.annotations.Test;

import org.tentackle.sql.BackendException;
import org.tentackle.sql.metadata.Deferrability;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for the CockroachDB backend.
 * <p>
 * CockroachDB is a postgres dialect, so only the deviations are tested here: the selection by name,
 * the missing deferrable constraints and the index names, which are unique per table only.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CockroachDbTest {

  private final CockroachDb backend = new CockroachDb();


  /**
   * The URL is the one of the postgres driver and must not select CockroachDB.
   */
  @Test
  public void testNeverMatchesUrl() {
    assertFalse(backend.isMatchingUrl("jdbc:postgresql://localhost:26257/tentackle"));
    assertTrue(backend.isMatchingName("cockroachdb"));
  }

  @Test
  public void testNoDeferrableConstraints() {
    assertFalse(backend.isSetConstraintsDeferredSupported());
    expectThrows(BackendException.class, () -> backend.sqlSetConstraintsDeferred(true));
    assertEquals(backend.sqlCreateUniqueConstraint("customer", "customer_udk", Deferrability.DEFERRABLE_DEFERRED, "code"),
                 "ALTER TABLE customer ADD CONSTRAINT customer_udk UNIQUE (code);\n");
    assertEquals(backend.sqlCreateForeignKey("invoice", "customer_id", "customer", "id", "invoice_customer_id_fkey",
                                             false, Deferrability.DEFERRABLE_DEFERRED),
                 "ALTER TABLE invoice ADD CONSTRAINT invoice_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES customer (id);\n");
  }

  /**
   * An index is referred to as table@index.
   */
  @Test
  public void testIndexesAreQualifiedByTable() {
    assertEquals(backend.sqlDropIndex("md", "customer", "customer_udk"), "DROP INDEX md.customer@customer_udk;\n");
    assertEquals(backend.sqlDropIndex(null, "customer", "customer_udk"), "DROP INDEX customer@customer_udk;\n");
    assertEquals(backend.sqlRenameIndex("md.customer", "md.customer_old", "customer_new"),
                 "ALTER INDEX md.customer@customer_old RENAME TO customer_new;\n");
    assertEquals(backend.sqlCreateIndexComment("md.customer", "customer_udk", "by code"),
                 "COMMENT ON INDEX md.customer@customer_udk IS 'by code';\n");
    assertEquals(backend.sqlCreateIndexComment("customer", "customer_udk", null),
                 "COMMENT ON INDEX customer@customer_udk IS NULL;\n");

    backend.setDropIfExistsEnabled(true);
    try {
      assertEquals(backend.sqlDropIndex(null, "customer", "customer_udk"), "DROP INDEX IF EXISTS customer@customer_udk;\n");
    }
    finally {
      backend.setDropIfExistsEnabled(false);
    }
  }

  @Test
  public void testSequences() {
    assertEquals(backend.sqlCreateSequenceComment("numpool_seq", "a comment"), "", "sequences cannot be commented");
    assertEquals(backend.sqlNextFromSequence("numpool_seq"), "SELECT NEXTVAL('numpool_seq')");
  }

  @Test
  public void testReservedSchemas() {
    assertTrue(backend.isReservedSchemaName("crdb_internal"));
    assertTrue(backend.isReservedSchemaName("pg_catalog"));
    assertFalse(backend.isReservedSchemaName("public"));
    assertFalse(backend.isReservedSchemaName("md"));
  }

  /**
   * Everything else is postgres.
   */
  @Test
  public void testInheritsPostgres() {
    assertTrue(backend.isUUIDSupported());
    assertTrue(backend.isFilteredIndexSupported());
    assertEquals(backend.buildSelectSql("* FROM foo", true, 10, 20), "SELECT * FROM foo FOR UPDATE LIMIT ? OFFSET ?");
  }

}
