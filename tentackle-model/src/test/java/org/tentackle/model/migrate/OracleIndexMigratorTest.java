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

package org.tentackle.model.migrate;

import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.impl.IndexImpl;
import org.tentackle.model.parse.Document;
import org.tentackle.sql.Backend;
import org.tentackle.sql.backends.Oracle;
import org.tentackle.sql.metadata.IndexMetaData;
import org.tentackle.sql.metadata.ModelMetaData;
import org.tentackle.sql.metadata.OracleIndexMetaData;
import org.tentackle.sql.metadata.TableMetaData;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that the indexes oracle derives from the model are recognized as unchanged.
 * <p>
 * Oracle implements filtered, function-based and descending indexes as an index over a
 * hidden virtual column. Unless the backend resolves that column back to the model's view,
 * the migrator would drop and re-create such an index on every run.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class OracleIndexMigratorTest {

  /** a minimal model holding the three kinds of indexes oracle implements as virtual columns. */
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
      unique index filtered := code | type = 0
      index descending := -code
      index functional := upper(name)
      """;


  /**
   * A filtered index is emulated by a CASE-expression.
   *
   * @throws ModelException if the test model is invalid
   */
  @Test
  public void testFilteredIndex() throws ModelException {
    // CASE WHEN (type = 0) THEN code END, as rendered by oracle in ALL_IND_EXPRESSIONS
    assertUnchanged("filtered", "CASE WHEN (\"TYPE\"=0) THEN \"CODE\" END", "A");
  }

  /**
   * A descending index is a function-based index in oracle.
   *
   * @throws ModelException if the test model is invalid
   */
  @Test
  public void testDescendingIndex() throws ModelException {
    assertUnchanged("descending", "\"CODE\"", "D");
  }

  /**
   * A function-based index.
   *
   * @throws ModelException if the test model is invalid
   */
  @Test
  public void testFunctionalIndex() throws ModelException {
    assertUnchanged("functional", "UPPER(\"NAME\")", "A");
  }

  /**
   * Without resolving the expression the migrator drops and re-creates the index on every run.
   *
   * @throws ModelException if the test model is invalid
   */
  @Test
  public void testUnresolvedExpression() throws ModelException {
    Backend backend = new Oracle();
    Index index = getIndex("filtered");
    IndexMetaData indexMetaData = createIndexMetaData(backend, index, null, "A");
    assertTrue(IndexMigrator.isLogicallyDifferent(backend, index, indexMetaData, null),
               "the unresolved virtual column must not match the model");
  }


  /**
   * The whole way from the model to the DDL.
   *
   * @throws ModelException if the test model is invalid
   */
  @Test
  public void testCreateIndexFromModel() throws ModelException {
    Backend backend = new Oracle();
    Entity entity = createEntity();
    assertEquals(getIndex(entity, "filtered").sqlCreateIndex(backend, entity),
                 "CREATE UNIQUE INDEX foo_filtered ON foo (CASE WHEN (type = 0) THEN code END);\n");
    assertEquals(getIndex(entity, "descending").sqlCreateIndex(backend, entity),
                 "CREATE INDEX foo_descending ON foo (code DESC);\n");
    assertEquals(getIndex(entity, "functional").sqlCreateIndex(backend, entity),
                 "CREATE INDEX foo_functional ON foo (UPPER(name));\n");
  }


  /**
   * Asserts that a model index matches the index metadata oracle reports for it.
   *
   * @param indexName the name of the index within the model
   * @param columnExpression the expression as stored in {@code ALL_IND_EXPRESSIONS}
   * @param ascOrDesc the sort order as reported by {@code getIndexInfo}
   * @throws ModelException if the test model is invalid
   */
  private void assertUnchanged(String indexName, String columnExpression, String ascOrDesc) throws ModelException {
    Backend backend = new Oracle();
    Index index = getIndex(indexName);
    IndexMetaData indexMetaData = createIndexMetaData(backend, index, columnExpression, ascOrDesc);
    StringBuilder difference = new StringBuilder();
    assertFalse(IndexMigrator.isLogicallyDifferent(backend, index, indexMetaData, difference),
                "index " + indexName + " considered changed: " + difference);
  }


  /**
   * Creates the index metadata as oracle reports it for a single-column index.
   *
   * @param backend the oracle backend
   * @param index the model index
   * @param columnExpression the expression as stored in {@code ALL_IND_EXPRESSIONS}, null if not resolvable
   * @param ascOrDesc the sort order as reported by {@code getIndexInfo}
   * @return the index metadata
   */
  private IndexMetaData createIndexMetaData(Backend backend, Index index, String columnExpression, String ascOrDesc) {
    TableMetaData tableMetaData = new TableMetaData(new ModelMetaData(backend, new DatabaseMetaData[0], null), "foo");
    OracleIndexMetaData indexMetaData = new OracleIndexMetaData(tableMetaData) {
      @Override
      public String getColumnExpression(int position) {
        return columnExpression;    // instead of querying oracle's data dictionary
      }
    };
    indexMetaData.setIndexName("foo_idx");    // irrelevant for the comparison, but keep it plausible
    indexMetaData.setUnique(index.isUnique());
    try {
      // oracle reports the hidden virtual column instead of the indexed expression
      indexMetaData.addIndexColumnFromMetaData(indexColumnResultSet("SYS_NC00007$", ascOrDesc));
    }
    catch (Exception ex) {
      throw new AssertionError("cannot set up the index metadata", ex);
    }
    return indexMetaData;
  }


  /**
   * Gets an index from the test model.
   *
   * @param indexName the name of the index
   * @return the parsed index
   * @throws ModelException if the test model is invalid
   */
  private Index getIndex(String indexName) throws ModelException {
    return getIndex(createEntity(), indexName);
  }

  /**
   * Gets an index of an entity.
   *
   * @param entity the entity
   * @param indexName the name of the index
   * @return the index
   */
  private Index getIndex(Entity entity, String indexName) {
    for (Index index: entity.getIndexes()) {
      if (index.getName().equals(indexName)) {
        return index;
      }
    }
    throw new AssertionError("no such index '" + indexName + "' in the test model");
  }

  /**
   * Creates the entity of the test model with all its indexes parsed.
   *
   * @return the entity
   * @throws ModelException if the test model is invalid
   */
  private Entity createEntity() throws ModelException {
    Entity entity = Model.getInstance().getEntityFactory().createEntity(new Document(MODEL), null);
    for (Index index: entity.getIndexes()) {
      ((IndexImpl) index).parse(entity);      // due to delayed parsing
    }
    return entity;
  }


  /**
   * Creates a result set as returned by {@link DatabaseMetaData#getIndexInfo} for a single index column.
   *
   * @param columnName the column name
   * @param ascOrDesc the sort order
   * @return the result set
   */
  private ResultSet indexColumnResultSet(String columnName, String ascOrDesc) {
    return (ResultSet) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getString" -> switch ((String) args[0]) {
            case "COLUMN_NAME" -> columnName;
            case "ASC_OR_DESC" -> ascOrDesc;
            default -> null;
          };
          case "getShort" -> (short) 1;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "ResultSet[" + columnName + "]";
          default -> null;
        });
  }

}
