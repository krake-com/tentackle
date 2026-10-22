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

package org.tentackle.model;

import org.testng.annotations.Test;

import org.tentackle.model.impl.IndexImpl;
import org.tentackle.model.parse.Document;
import org.tentackle.sql.Backend;
import org.tentackle.sql.backends.H2;
import org.tentackle.sql.backends.Postgres;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Verifies the {@code deferred}/{@code deferrable}/{@code immediate} index-line keywords:
 * parsing, validation, and DDL generation.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class IndexDeferrabilityTest {

  private static final String MODEL =
      """
      ## Test entity.
      name := Foo
      id := 1
      table := foo

      ## attributes
      int              type            type              the type
      String(10)       code            code              the code
      String(20)       name            name              the name

      ## indexes
      unique index plain := code
      unique deferred index defidx := code
      unique deferrable index deferrableidx := code
      unique immediate index immediateidx := code
      index notunique := name
      unique index filtered := code | type = 0
      """;

  private Entity createEntity() throws ModelException {
    Entity entity = Model.getInstance().getEntityFactory().createEntity(new Document(MODEL), null);
    for (Index index : entity.getIndexes()) {
      ((IndexImpl) index).parse(entity);
    }
    return entity;
  }

  private Index getIndex(Entity entity, String indexName) {
    for (Index index : entity.getIndexes()) {
      if (index.getName().equals(indexName)) {
        return index;
      }
    }
    throw new AssertionError("no such index '" + indexName + "' in the test model");
  }

  @Test
  public void testPlainIndexIsNotDeferrable() throws ModelException {
    Index index = getIndex(createEntity(), "plain");
    assertEquals(index.getDeferrability(), Deferrability.NOT_DEFERRABLE);
    index.validate();
  }

  @Test
  public void testDeferredKeyword() throws ModelException {
    Index index = getIndex(createEntity(), "defidx");
    assertEquals(index.getDeferrability(), Deferrability.DEFERRABLE_DEFERRED);
    index.validate();
  }

  @Test
  public void testDeferrableKeyword() throws ModelException {
    Index index = getIndex(createEntity(), "deferrableidx");
    assertEquals(index.getDeferrability(), Deferrability.DEFERRABLE_IMMEDIATE);
    index.validate();
  }

  @Test
  public void testImmediateKeyword() throws ModelException {
    Index index = getIndex(createEntity(), "immediateidx");
    assertEquals(index.getDeferrability(), Deferrability.NOT_DEFERRABLE);
    assertTrue(((IndexImpl) index).isDeferrabilityExplicit(), "'immediate' must be explicit, not just the default");
    index.validate();
  }

  @Test
  public void testExplicitFlagOnlySetWhenKeywordPresent() throws ModelException {
    Index index = getIndex(createEntity(), "plain");
    assertFalse(((IndexImpl) index).isDeferrabilityExplicit(), "no keyword present -> not explicit");
  }

  @Test
  public void testDeferredRejectedOnNonUniqueIndex() throws ModelException {
    Entity entity = createEntity();
    IndexImpl index = (IndexImpl) getIndex(entity, "notunique");
    index.setDeferrability(Deferrability.DEFERRABLE_DEFERRED);
    ModelException ex = expectThrows(ModelException.class, index::validate);
    assertTrue(ex.getMessage().contains("unique"), "message should mention unique: " + ex.getMessage());
  }

  @Test
  public void testDeferredRejectedOnFilteredIndex() throws ModelException {
    Entity entity = createEntity();
    IndexImpl index = (IndexImpl) getIndex(entity, "filtered");
    index.setDeferrability(Deferrability.DEFERRABLE_DEFERRED);
    expectThrows(ModelException.class, index::validate);
  }

  @Test
  public void testDdlNotDeferrableUnchangedAsPlainIndex() throws ModelException {
    Entity entity = createEntity();
    Backend backend = new Postgres();
    assertEquals(getIndex(entity, "plain").sqlCreateIndex(backend, entity),
                 "CREATE UNIQUE INDEX foo_plain ON foo (code);\n");
  }

  @Test
  public void testDdlDeferredUsesConstraintOnSupportedBackend() throws ModelException {
    Entity entity = createEntity();
    Backend backend = new Postgres();
    assertEquals(getIndex(entity, "defidx").sqlCreateIndex(backend, entity),
                 "ALTER TABLE foo ADD CONSTRAINT foo_defidx UNIQUE (code) DEFERRABLE INITIALLY DEFERRED;\n");
  }

  @Test
  public void testDdlDeferrableUsesConstraintOnSupportedBackend() throws ModelException {
    Entity entity = createEntity();
    Backend backend = new Postgres();
    assertEquals(getIndex(entity, "deferrableidx").sqlCreateIndex(backend, entity),
                 "ALTER TABLE foo ADD CONSTRAINT foo_deferrableidx UNIQUE (code) DEFERRABLE INITIALLY IMMEDIATE;\n");
  }

  @Test
  public void testDdlFallsBackToPlainIndexOnUnsupportedBackend() throws ModelException {
    // H2 does not support deferred constraints -> graceful degradation, no build failure
    Entity entity = createEntity();
    Backend backend = new H2();
    assertEquals(getIndex(entity, "defidx").sqlCreateIndex(backend, entity),
                 "CREATE UNIQUE INDEX foo_defidx ON foo (code);\n");
  }

}
