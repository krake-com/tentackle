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

package org.tentackle.maven.plugin.mcp;

import org.tentackle.maven.plugin.mcp.render.Entities;
import org.tentackle.model.Entity;
import org.tentackle.model.Relation;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Tests rendering a relation whose target is not in the model.
 * <p>
 * A relation names a class, and the entity behind it is filled in when the relations are resolved. A relation
 * that was resolved against an incomplete model keeps the class name and no entity, which every reader has to
 * survive: the model is what an agent asked about, and a stack trace answers nothing.
 */
public class EntitiesTest {

  /**
   * Builds a model interface whose methods answer from a map.
   *
   * @param <T> the interface type
   * @param type the interface
   * @param answers the answers by method name; every other method answers false, 0 or null
   * @return the instance
   */
  private static <T> T stub(Class<T> type, Map<String, Object> answers) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
        (proxy, method, args) -> {
          if (answers.containsKey(method.getName())) {
            return answers.get(method.getName());
          }
          Class<?> returnType = method.getReturnType();
          if (returnType == boolean.class) {
            return false;
          }
          return returnType.isPrimitive() ? 0 : null;
        }));
  }

  private Relation danglingRelation() {
    Entity entity = stub(Entity.class, Map.of("getName", "Order"));
    return stub(Relation.class, Map.of("getName", "supplier",
                                       "getClassName", "Supplier",
                                       "getEntity", entity,
                                       "getStereotypes", List.of()));
  }


  @Test
  public void describesARelationByTheClassItNames() {
    assertEquals(Entities.describe(danglingRelation()), "Order.supplier -> Supplier");
  }

  @Test
  public void describesAPathThroughARelationWithoutAnEntity() {
    assertEquals(Entities.describePath(List.of(danglingRelation())), "Order --supplier--> Supplier");
  }

}
