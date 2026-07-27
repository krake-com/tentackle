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

package org.tentackle.persist;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.pdo.Pdo;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link Join}.
 * <p>
 * Joins are pure model objects: they describe how two PDOs are joined within one select and collect
 * the joined PDOs while the result set is read. Neither needs a database.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JoinTest {

  /** the joins performed by the joiner of the test joins. */
  private final List<String> joinedPairs = new ArrayList<>();

  private final BiConsumer<DemoEntity, DemoComponent> joiner =
      (entity, component) -> joinedPairs.add(entity.getPersistenceDelegate().getId() + "<-" +
                                             component.getPersistenceDelegate().getId());


  @BeforeMethod
  public void clearJoinedPairs() {
    joinedPairs.clear();    // TestNG shares the instance among all test methods
  }


  /**
   * Creates a list join: the components reference the entity.
   *
   * @return the join
   */
  private Join<DemoEntity, DemoComponent> createListJoin() {
    return new Join<>(JoinType.LEFT, "dem.id", "e_1." + AbstractPersistentObject.CN_ROOTID,
                      DemoComponent.class, "e_1", joiner);
  }

  /**
   * Creates a PDO with the given object ID.
   *
   * @param id the object id
   * @return the PDO
   */
  private DemoEntity createEntity(long id) {
    DemoEntity entity = Pdo.create(DemoEntity.class);
    ((DemoEntityPersistenceImpl) entity.getPersistenceDelegate()).setId(id);
    return entity;
  }

  /**
   * Creates a PDO with the given object ID.
   *
   * @param id the object id
   * @return the PDO
   */
  private DemoComponent createComponent(long id) {
    DemoComponent component = Pdo.create(DemoComponent.class);
    ((DemoComponentPersistenceImpl) component.getPersistenceDelegate()).setId(id);
    return component;
  }


  @Test
  public void testJoinAttributes() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    assertEquals(join.getType(), JoinType.LEFT);
    assertEquals(join.getColumnName(), "dem.id");
    assertEquals(join.getJoinedColumnName(), "e_1.rootid");
    assertSame(join.getJoinedClass(), DemoComponent.class);
    assertEquals(join.getJoinAlias(), "e_1");
    assertSame(join.getJoiner(), joiner);
    assertNull(join.getExtraSql(), "no extra SQL unless given");
    assertTrue(join.getJoins().isEmpty(), "no cascaded joins yet");
  }

  @Test
  public void testExtraSqlIsKept() {
    Join<DemoEntity, DemoComponent> join =
        new Join<>(JoinType.INNER, "dem.id", "e_1.rootid", DemoComponent.class, "e_1",
                   " AND e_1.ctext IS NOT NULL", joiner);
    assertEquals(join.getExtraSql(), " AND e_1.ctext IS NOT NULL");
    assertEquals(join.getType(), JoinType.INNER);
  }

  @Test
  public void testColumnNameEndingWithIdMakesAListJoin() {
    assertTrue(createListJoin().isListJoin(), "joining to the entity's ID column is a list join");
  }

  @Test
  public void testColumnNameOfAnObjectJoin() {
    // the entity references the component: an object join
    Join<DemoEntity, DemoComponent> join =
        new Join<>(JoinType.LEFT, "dem.componentid", "e_1.id", DemoComponent.class, "e_1", joiner);
    assertFalse(join.isListJoin());
    assertFalse(join.isExplicitIdAliasRequired(),
                "the joined entity maps to a single table, hence no extra ID alias is necessary");
  }

  @Test
  public void testListJoinNeedsNoExplicitIdAlias() {
    assertFalse(createListJoin().isExplicitIdAliasRequired(), "never for list joins");
  }

  @Test
  public void testCascadedJoins() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    Join<DemoComponent, DemoComponent> subJoin =
        new Join<>(JoinType.LEFT, "e_1.id", "e_2.rootid", DemoComponent.class, "e_2", (a, b) -> { });

    assertSame(join.addJoin(subJoin), join, "fluent style");
    assertEquals(join.getJoins(), List.of(subJoin));
  }

  @Test
  public void testJoiningTwiceKeepsTheFirstPdo() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    DemoEntity entity = createEntity(1);
    DemoComponent component = createComponent(10);
    DemoComponent sameIdComponent = createComponent(10);

    assertSame(join.join(entity, component), component, "the first one is joined");
    assertSame(join.join(entity, sameIdComponent), component,
               "a component with an already joined ID must not be joined twice");
    assertEquals(joinedPairs, List.of("1<-10"), "the joiner is invoked once per joined PDO");
  }

  @Test
  public void testJoiningDifferentComponents() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    DemoEntity entity = createEntity(1);

    join.join(entity, createComponent(10));
    join.join(entity, createComponent(11));
    assertEquals(joinedPairs, List.of("1<-10", "1<-11"));
  }

  @Test
  public void testJoinsAreTrackedPerPdo() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    DemoEntity first = createEntity(1);
    DemoEntity second = createEntity(2);
    DemoComponent component = createComponent(10);

    join.join(first, component);
    assertSame(join.join(second, component), component,
               "the same component ID joined to another entity is a new join");
    assertEquals(joinedPairs, List.of("1<-10", "2<-10"));
  }

  @Test
  public void testInitializeForgetsTheJoinedPdos() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    Join<DemoComponent, DemoComponent> subJoin =
        new Join<>(JoinType.LEFT, "e_1.id", "e_2.rootid", DemoComponent.class, "e_2",
                   (component, subComponent) -> joinedPairs.add("sub"));
    join.addJoin(subJoin);

    DemoEntity entity = createEntity(1);
    DemoComponent component = createComponent(10);
    join.join(entity, component);
    subJoin.join(component, createComponent(20));

    join.initialize();    // must recursively clear the cascaded joins as well

    assertSame(join.join(entity, component), component, "joined again after initialize()");
    subJoin.join(component, createComponent(20));
    assertEquals(joinedPairs, List.of("1<-10", "sub", "1<-10", "sub"));
  }

  @Test
  public void testCreateJoinedPdoCreatesThePdoOfTheJoinedClass() {
    Join<DemoEntity, DemoComponent> join = createListJoin();
    DemoComponent component = join.createJoinedPdo(createEntity(1));
    assertTrue(component.getPersistenceDelegate() instanceof DemoComponentPersistenceImpl);
    assertTrue(component.getPersistenceDelegate().isNew(), "a fresh PDO without an ID");
  }

}
