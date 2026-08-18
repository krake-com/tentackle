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

import org.testng.annotations.Test;

import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.JoinType;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link JoinedSelect}.
 * <p>
 * A joined select keeps track of the PDOs while a result set with joined rows is read: rows
 * belonging to the same primary PDO must not create that PDO more than once. This bookkeeping is
 * pure in-memory logic, only the SQL creation (which needs the backend of a session) is left out.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JoinedSelectTest {

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

  private Join<DemoEntity, DemoComponent> createJoin() {
    return new Join<>(JoinType.LEFT, "dem.id", "e_1.rootid", DemoComponent.class, "e_1", (a, b) -> { });
  }


  @Test
  public void testJoinsAreCollected() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    assertTrue(js.getJoins().isEmpty());

    Join<DemoEntity, DemoComponent> join = createJoin();
    assertSame(js.addJoin(join), js, "fluent style");
    assertEquals(js.getJoins(), List.of(join));
  }

  @Test
  public void testCreatedFromJoinList() {
    List<Join<? super DemoEntity, ?>> joins = new ArrayList<>();
    joins.add(createJoin());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(joins);
    assertSame(js.getJoins(), joins, "the list is taken as is");
  }

  @Test
  public void testNoPdoBeforeTheFirstRow() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    assertNull(js.currentPdo());
    assertNull(js.getLastPdo());
  }

  @Test
  public void testNextPdoWithoutListJustTracksTheCurrentPdo() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    js.initialize(null);    // no list: used by the single-object selects

    DemoEntity first = createEntity(1);
    DemoEntity second = createEntity(2);

    assertSame(js.nextPdo(first), first);
    assertSame(js.currentPdo(), first);
    assertNull(js.getLastPdo(), "no predecessor for the first PDO");

    assertSame(js.nextPdo(second), second);
    assertSame(js.currentPdo(), second);
    assertSame(js.getLastPdo(), first);
  }

  @Test
  public void testNextPdoFillsTheListAndSkipsKnownIds() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    List<DemoEntity> list = new ArrayList<>();
    js.initialize(list);

    DemoEntity first = createEntity(1);
    DemoEntity second = createEntity(2);
    DemoEntity firstAgain = createEntity(1);    // the next joined row of the first PDO

    js.nextPdo(first);
    js.nextPdo(second);
    assertSame(js.nextPdo(firstAgain), first, "a known ID must be replaced by the PDO already in the list");

    assertEquals(list, List.of(first, second), "each PDO is added exactly once");
    assertSame(js.currentPdo(), first);
    assertSame(js.getLastPdo(), second);
  }

  @Test
  public void testNextPdoAcceptsNull() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    List<DemoEntity> list = new ArrayList<>();
    js.initialize(list);

    DemoEntity entity = createEntity(1);
    js.nextPdo(entity);
    assertNull(js.nextPdo(null), "null finishes the query");
    assertSame(js.getLastPdo(), entity);
    assertEquals(list, List.of(entity), "null is not added to the list");
  }

  @Test
  public void testInitializeResetsTheState() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    List<DemoEntity> list = new ArrayList<>();
    js.initialize(list);

    DemoEntity entity = createEntity(1);
    js.nextPdo(entity);
    js.nextPdo(createEntity(2));

    List<DemoEntity> nextList = new ArrayList<>();
    js.initialize(nextList);
    assertNull(js.currentPdo());
    assertNull(js.getLastPdo());

    DemoEntity sameId = createEntity(1);
    assertSame(js.nextPdo(sameId), sameId, "the IDs of the previous query are forgotten");
    assertEquals(nextList, List.of(sameId));
  }

  @Test
  public void testInitializeRejectsANonEmptyList() {
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    List<DemoEntity> list = new ArrayList<>();
    list.add(createEntity(1));
    PersistenceException ex = expectThrows(PersistenceException.class, () -> js.initialize(list));
    assertTrue(ex.getMessage().contains("list must be empty"), ex.getMessage());
  }

  @Test
  public void testInitializeInitializesTheJoins() {
    Join<DemoEntity, DemoComponent> join = createJoin();
    JoinedSelect<DemoEntity> js = new JoinedSelect<>();
    js.addJoin(join);

    DemoEntity entity = createEntity(1);
    DemoComponent component = Pdo.create(DemoComponent.class);
    ((DemoComponentPersistenceImpl) component.getPersistenceDelegate()).setId(10);
    join.join(js.getJoinState(), entity, component);

    js.initialize(new ArrayList<>());

    DemoComponent sameIdComponent = Pdo.create(DemoComponent.class);
    ((DemoComponentPersistenceImpl) sameIdComponent.getPersistenceDelegate()).setId(10);
    assertSame(join.join(js.getJoinState(), entity, sameIdComponent), sameIdComponent,
               "the joins must be initialized along with the joined select");
  }

  @Test
  public void testEachJoinedSelectHasItsOwnJoinState() {
    // the joins are shared (static class variables), the state must not be
    Join<DemoEntity, DemoComponent> join = createJoin();
    JoinedSelect<DemoEntity> first = new JoinedSelect<DemoEntity>().addJoin(join);
    JoinedSelect<DemoEntity> second = new JoinedSelect<DemoEntity>().addJoin(join);

    assertNotSame(first.getJoinState(), second.getJoinState());

    DemoEntity entity = createEntity(1);
    DemoComponent component = Pdo.create(DemoComponent.class);
    ((DemoComponentPersistenceImpl) component.getPersistenceDelegate()).setId(10);
    DemoComponent sameIdComponent = Pdo.create(DemoComponent.class);
    ((DemoComponentPersistenceImpl) sameIdComponent.getPersistenceDelegate()).setId(10);

    assertSame(join.join(first.getJoinState(), entity, component), component);
    assertSame(join.join(second.getJoinState(), entity, sameIdComponent), sameIdComponent,
               "a concurrent select must not observe the PDOs joined by another one");
  }

}
