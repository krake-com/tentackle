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

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Verifies that attributes with a non-comparable data type (JSON, XML) cannot be part of an index,
 * since databases provide no comparator for such types.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class IndexComparableAttributeTest {

  private static final String MODEL =
      """
      ## Test entity.
      name := Foo
      id := 1
      table := foo

      ## attributes
      String(10)       code            code              the code
      JSON             payload         payload           the payload
      XML              document        document          the document

      ## indexes
      index comparableidx := code
      index jsonidx := payload
      index xmlidx := document
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
  public void testComparableAttributeIsAccepted() throws ModelException {
    getIndex(createEntity(), "comparableidx").validate();
  }

  @Test
  public void testJsonAttributeIsRejected() throws ModelException {
    Index index = getIndex(createEntity(), "jsonidx");
    ModelException ex = expectThrows(ModelException.class, index::validate);
    assertTrue(ex.getMessage().contains("non-comparable"), "message should mention non-comparable: " + ex.getMessage());
  }

  @Test
  public void testSqlxmlAttributeIsRejected() throws ModelException {
    Index index = getIndex(createEntity(), "xmlidx");
    ModelException ex = expectThrows(ModelException.class, index::validate);
    assertTrue(ex.getMessage().contains("non-comparable"), "message should mention non-comparable: " + ex.getMessage());
  }

}
