/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.persist;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.JoinType;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Database-backed tests for {@link JoinedSelect}.
 * <p>
 * {@link JoinedSelectTest} covers the in-memory bookkeeping of a joined select; what needs a
 * session is {@code JoinedSelect#createJoinedSql}, which takes the {@link org.tentackle.sql.Backend}
 * from the PDO's session, and the row collapsing of {@link ResultSetCursor#get()}, which turns the
 * n rows of a 1:n join back into one PDO carrying n joined PDOs.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class JoinedSelectDbTest extends AbstractPersistenceDbTest {

  /** the alias of the joined table. */
  private static final String JOIN_ALIAS = "e_1";

  /** the alias of the table joined to the joined table. */
  private static final String NESTED_JOIN_ALIAS = "e_1_1";

  /** the components collected by the joiner, per entity ID. */
  private final List<String> joinedPairs = new ArrayList<>();

  private final BiConsumer<DemoEntity, DemoComponent> joiner =
      (entity, component) ->
          joinedPairs.add(((DemoEntityPersistenceImpl) entity.getPersistenceDelegate()).getName() + "<-" +
                          ((DemoComponentPersistenceImpl) component.getPersistenceDelegate()).getText());


  /** the roots collected by the cascaded joiner. */
  private final List<String> backJoinedPairs = new ArrayList<>();

  private final BiConsumer<DemoComponent, DemoEntity> backJoiner =
      (component, entity) ->
          backJoinedPairs.add(((DemoComponentPersistenceImpl) component.getPersistenceDelegate()).getText() + "->" +
                              ((DemoEntityPersistenceImpl) entity.getPersistenceDelegate()).getName());


  @BeforeMethod
  public void clearJoinedPairs() {
    backJoinedPairs.clear();
    joinedPairs.clear();    // TestNG shares the instance among all test methods
  }


  /**
   * Creates the list join from an entity to its components.
   *
   * @param type the join type
   * @return the join
   */
  private Join<DemoEntity, DemoComponent> listJoin(JoinType type) {
    return new Join<>(type, DemoEntityPersistenceImpl.CLASSVARIABLES.tableAlias + "." + AbstractPersistentObject.CN_ID,
                      JOIN_ALIAS + "." + AbstractPersistentObject.CN_ROOTID,
                      DemoComponent.class, JOIN_ALIAS, joiner);
  }

  /**
   * Creates the cascaded object join from a component back to its root entity.
   *
   * @return the join
   */
  private Join<DemoComponent, DemoEntity> backJoin() {
    return new Join<>(JoinType.LEFT, JOIN_ALIAS + "." + AbstractPersistentObject.CN_ROOTID,
                      NESTED_JOIN_ALIAS + "." + AbstractPersistentObject.CN_ID,
                      DemoEntity.class, NESTED_JOIN_ALIAS, backJoiner);
  }

  private DemoEntityPersistenceImpl entity(String name) {
    DemoEntityPersistenceImpl po =
        (DemoEntityPersistenceImpl) Pdo.create(DemoEntity.class, getDomainContext()).getPersistenceDelegate();
    po.setName(name);
    return po;
  }

  private void component(DemoEntityPersistenceImpl root, String text) {
    DomainContext rootContext = getDomainContext().getRootContext(root.getPdo());
    DemoComponentPersistenceImpl po =
        (DemoComponentPersistenceImpl) Pdo.create(DemoComponent.class, rootContext).getPersistenceDelegate();
    po.setText(text);
    po.saveObject();
  }


  /**
   * Builds the joined SQL for a list join from the entity to its components.
   *
   * @param pdo the entity PDO the select starts from
   * @param js the joined select
   * @return the SQL
   */
  private String joinedSql(DemoEntity pdo, JoinedSelect<DemoEntity> js) {
    DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
    StringBuilder sql = new StringBuilder(po.createSelectAllSql(po.getBackend()));
    js.createJoinedSql(pdo, sql);
    return sql.toString();
  }


  @Test
  public void testCreateJoinedSqlOfAListJoin() {
    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT)));
    /*
     * The joined select becomes a derived table under the join alias, its columns are added to the
     * select list, and the outer WHERE moves behind the join.
     */
    assertEquals(joinedSql(pdo, js),
                 "SELECT dem.*,e_1.* FROM demoentity dem" +
                 " LEFT JOIN (SELECT dcm.* FROM democomponent dcm WHERE dcm.rootid=?) e_1" +
                 " ON dem.id=e_1.rootid WHERE 1=1");
  }

  /**
   * The eager joins of the class variables may cascade, so the SQL builder has to descend into the
   * sub-joins of a join. Here the components are joined back to their root entity, which is the
   * shape a cascading {@code select = eager:2} produces.
   */
  @Test
  public void testCreateJoinedSqlDescendsIntoCascadedJoins() {
    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT).addJoin(backJoin())));
    assertEquals(joinedSql(pdo, js),
                 "SELECT dem.*,e_1.*,e_1_1.* FROM demoentity dem" +
                 " LEFT JOIN (SELECT dcm.* FROM democomponent dcm WHERE dcm.rootid=?) e_1" +
                 " ON dem.id=e_1.rootid" +
                 " LEFT JOIN (SELECT dem.* FROM demoentity dem) e_1_1" +
                 " ON e_1.rootid=e_1_1.id WHERE 1=1");
  }

  /**
   * A cascaded join must be read from the same result set, i.e. all levels are populated by one
   * statement -- this is what a cascading eager relation relies on.
   */
  @Test
  public void testCascadedJoinsArePopulatedFromTheSameRow() {
    DemoEntityPersistenceImpl root = entity("root");
    root.save();
    component(root, "a");
    component(root, "b");

    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT).addJoin(backJoin())));

    List<DemoEntity> list = new ArrayList<>();
    try (ResultSetWrapper rs = joinedStatement(pdo, js, root).executeQuery()) {
      po.executeQueryToList(rs, js, list);
    }

    assertEquals(list.size(), 1, "two joined rows, one root entity");
    assertEquals(joinedPairs, List.of("root<-a", "root<-b"));
    // and the second level rode along in the very same statement
    assertEquals(backJoinedPairs, List.of("a->root", "b->root"));
  }

  @Test
  public void testInnerAndLeftDifferOnlyInTheJoinKeyword() {
    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    String inner = joinedSql(pdo, new JoinedSelect<>(List.of(listJoin(JoinType.INNER))));
    String left = joinedSql(pdo, new JoinedSelect<>(List.of(listJoin(JoinType.LEFT))));

    assertTrue(inner.contains(JoinType.INNER.toString()), inner);
    assertTrue(left.contains(JoinType.LEFT.toString()), left);
    assertEquals(left.replace(JoinType.LEFT.toString(), JoinType.INNER.toString()), inner);
  }

  @Test
  public void testUnsupportedJoinTypesAreRejected() {
    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();

    for (JoinType type : List.of(JoinType.RIGHT, JoinType.FULL)) {
      JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(type)));
      StringBuilder sql = new StringBuilder(po.createSelectAllSql(po.getBackend()));
      assertTrue(expectThrows(PersistenceException.class, () -> js.createJoinedSql(pdo, sql))
                     .getMessage().contains("unsupported JoinType: " + type.name()));
    }
  }

  /**
   * Creates a root entity with three components and prepares the joined select over them.
   *
   * @param pdo the entity PDO the select starts from
   * @param js the joined select
   * @param root the saved root entity
   * @return the executable statement
   */
  private PreparedStatementWrapper joinedStatement(DemoEntity pdo, JoinedSelect<DemoEntity> js,
                                                   DemoEntityPersistenceImpl root) {
    String sql = joinedSql(pdo, js) + " ORDER BY " + JOIN_ALIAS + "." + DemoComponentPersistenceImpl.CN_TEXT;
    PreparedStatementWrapper st = getSession().createPreparedStatement(_ -> sql);
    // the joined select of a component carries its own context condition, hence this parameter
    st.setLong(1, root.getId());
    return st;
  }

  /**
   * Like {@link #joinedStatement}, but scrollable so a {@link ResultSetCursor} built over it can
   * rewind via {@code beforeFirst()}.
   *
   * @param pdo the entity PDO the select starts from
   * @param js the joined select
   * @param root the saved root entity
   * @return the executable statement
   */
  private PreparedStatementWrapper scrollableJoinedStatement(DemoEntity pdo, JoinedSelect<DemoEntity> js,
                                                              DemoEntityPersistenceImpl root) {
    String sql = joinedSql(pdo, js) + " ORDER BY " + JOIN_ALIAS + "." + DemoComponentPersistenceImpl.CN_TEXT;
    PreparedStatementWrapper st = getSession().createPreparedStatement(
        _ -> sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
    // the joined select of a component carries its own context condition, hence this parameter
    st.setLong(1, root.getId());
    return st;
  }

  @Test
  public void testTheJoinedSqlIsAcceptedByTheDatabaseAndCollapsesTheRows() {
    DemoEntityPersistenceImpl root = entity("root");
    root.save();
    component(root, "a");
    component(root, "b");
    component(root, "c");

    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT)));

    List<DemoEntity> list = new ArrayList<>();
    try (ResultSetWrapper rs = joinedStatement(pdo, js, root).executeQuery()) {
      po.executeQueryToList(rs, js, list);
    }

    // three joined rows, one root entity
    assertEquals(list.size(), 1);
    assertEquals(list.get(0).getPersistenceDelegate().getId(), root.getId());
    // and the joiner has seen every component exactly once
    assertEquals(joinedPairs, List.of("root<-a", "root<-b", "root<-c"));
  }

  @Test
  public void testAJoinedCursorCollapsesTheRowsOfOneRoot() {
    DemoEntityPersistenceImpl root = entity("root");
    root.save();
    component(root, "a");
    component(root, "b");
    component(root, "c");

    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT)));

    try (ResultSetCursor<DemoEntity> cursor =
             new ResultSetCursor<>(pdo, joinedStatement(pdo, js, root).executeQuery(), js)) {
      List<DemoEntity> found = cursor.toList();
      assertEquals(found.size(), 1, "three joined rows, one root PDO");
      assertEquals(((DemoEntityPersistenceImpl) found.get(0).getPersistenceDelegate()).getName(), "root");
    }
    // every component is joined exactly once -- in particular the first one, which the cursor
    // used to drop because get() re-read the row first() had positioned on
    assertEquals(joinedPairs, List.of("root<-a", "root<-b", "root<-c"));
  }

  @Test
  public void testAJoinedCursorSeparatesTheRoots() {
    DemoEntityPersistenceImpl rootA = entity("rootA");
    rootA.save();
    component(rootA, "a1");
    component(rootA, "a2");

    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT)));

    /*
     * Two roots in one result set. The joined select of a component carries the context condition
     * of its root, so the statement can only be bound to one of them -- the second root therefore
     * comes back through the LEFT JOIN without components, which is exactly the case that makes
     * the cursor switch from one root PDO to the next.
     */
    DemoEntityPersistenceImpl rootB = entity("rootB");
    rootB.save();

    try (ResultSetCursor<DemoEntity> cursor =
             new ResultSetCursor<>(pdo, joinedStatement(pdo, js, rootA).executeQuery(), js)) {
      List<DemoEntity> found = cursor.toList();
      assertEquals(found.stream()
                        .map(e -> ((DemoEntityPersistenceImpl) e.getPersistenceDelegate()).getName())
                        .sorted()
                        .toList(),
                   List.of("rootA", "rootB"));
    }
    assertEquals(joinedPairs, List.of("rootA<-a1", "rootA<-a2"));
  }

  @Test
  public void testBeforeFirstRewindsAJoinedCursor() {
    DemoEntityPersistenceImpl root = entity("root");
    root.save();
    component(root, "a");
    component(root, "b");
    component(root, "c");

    DemoEntity pdo = Pdo.create(DemoEntity.class, getDomainContext());
    JoinedSelect<DemoEntity> js = new JoinedSelect<>(List.of(listJoin(JoinType.LEFT)));

    try (ResultSetCursor<DemoEntity> cursor =
             new ResultSetCursor<>(pdo, scrollableJoinedStatement(pdo, js, root).executeQuery(), js)) {
      assertTrue(cursor.next());
      assertNotNull(cursor.get(), "the first row must be readable before any rewind");

      cursor.beforeFirst();
      assertTrue(cursor.isBeforeFirst());
      assertEquals(cursor.getRow(), 0);

      assertTrue(cursor.next(), "a row exists after rewinding a joined cursor");
      DemoEntity afterRewind = cursor.get();
      assertNotNull(afterRewind, "get() must return the row next() just reported after beforeFirst()");
      assertEquals(((DemoEntityPersistenceImpl) afterRewind.getPersistenceDelegate()).getName(), "root");
    }
    // every component must be joined exactly once per pass, before and after the rewind
    assertEquals(joinedPairs, List.of("root<-a", "root<-b", "root<-c", "root<-a", "root<-b", "root<-c"));
  }

}
