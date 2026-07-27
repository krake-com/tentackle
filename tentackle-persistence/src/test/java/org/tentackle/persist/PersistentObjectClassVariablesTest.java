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

import org.tentackle.sql.JoinType;

import java.lang.reflect.Method;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PersistentObjectClassVariables} and {@link PersistentClassVariablesFactory}.
 * <p>
 * The class variables hold everything the persistence layer knows about an entity <em>before</em> any
 * session exists: the PDO class, the table it maps to, its alias and the inheritance chain.
 * Tests that must not be disturbed by the lazily cached values create their own instances instead of
 * using the {@code CLASSVARIABLES} singletons of the persistence implementations.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PersistentObjectClassVariablesTest {

  /**
   * Creates a fresh class variable for {@link DemoEntity}.
   *
   * @return the class variables
   */
  private PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> createEntityVariables() {
    return PersistentObjectClassVariables.create(DemoEntity.class, DemoEntityPersistenceImpl.class, "dem");
  }

  /**
   * Creates a fresh class variable for {@link DemoSub}, inheriting the table of {@link DemoBase}.
   *
   * @return the class variables
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> createSubVariables() {
    return PersistentObjectClassVariables.create(DemoSub.class, DemoSubPersistenceImpl.class, null,
                                                 (PersistentObjectClassVariables) DemoBasePersistenceImpl.CLASSVARIABLES,
                                                 null);
  }


  @Test
  public void testTheFactoryCreatesPdoClassVariables() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertSame(cv.pdoClass, DemoEntity.class);
    assertSame(cv.clazz, DemoEntityPersistenceImpl.class);
    assertEquals(cv.pdoClassBaseName, "DemoEntity");
    assertEquals(cv.classBaseName, "DemoEntityPersistenceImpl", "inherited from the db class variables");
    assertNull(cv.superClassVariables);
    assertNull(cv.eagerJoins);
  }

  @Test
  public void testClassIdAndTableNameComeFromTheAnnotationsOfThePdoInterface() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.classId, 9001, "from @ClassId of DemoEntity");
    assertEquals(cv.tableName, "demoentity", "from @TableName of DemoEntity");
    assertEquals(cv.tableAlias, "dem");
    assertFalse(cv.isAbstract(), "a class ID is provided");
  }

  @Test
  public void testEntityWithoutClassIdIsAbstract() {
    // an abstract entity of a MULTI-table hierarchy provides no class ID
    PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> cv =
        PersistentObjectClassVariables.create(DemoSub.class, DemoSubPersistenceImpl.class, "dsb");
    assertEquals(cv.classId, 9004);
    assertFalse(cv.isAbstract());
  }

  @Test
  public void testInheritedTableNameAndAlias() {
    PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> cv = createSubVariables();
    assertNull(cv.tableName, "the derived entity provides no table of its own");
    assertNull(cv.tableAlias);
    assertEquals(cv.getTableName(), "demobase", "the table is inherited from the base entity");
    assertEquals(cv.getTableAlias(), "dbs", "the alias is inherited as well");
  }

  @Test
  public void testTopSuperClassVariables() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> entityVariables = createEntityVariables();
    assertSame(entityVariables.getTopSuperClassVariables(), entityVariables, "not inherited: itself");

    PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> subVariables = createSubVariables();
    assertSame(subVariables.getTopSuperClassVariables(), DemoBasePersistenceImpl.CLASSVARIABLES);
  }

  @Test
  public void testColumnNamesAreQualifiedWithTheTableAlias() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.getColumnName(AbstractPersistentObject.CN_ID), "dem.id");
    assertEquals(cv.getColumnName("pre_", AbstractPersistentObject.CN_ID), "dem.pre_id",
                 "the prefix of an embedded PDO is inserted between alias and column");
  }

  @Test
  public void testColumnNamesOfTheInheritedAlias() {
    assertEquals(createSubVariables().getColumnName("serial"), "dbs.serial");
  }

  @Test
  public void testColumnNameList() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.getColumnNames(List.of("id", "serial", DemoEntityPersistenceImpl.CN_NAME)),
                 "dem.id,dem.serial,dem.name");
  }

  @Test
  public void testColumnNameListWithPrefix() {
    // the prefix of an embedded PDO belongs between the table alias and the column name,
    // exactly as in the single-column getColumnName(prefix, name)
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.getColumnNames("pre_", List.of("id", "serial")), "dem.pre_id,dem.pre_serial");
  }

  @Test
  public void testColumnNameListWithPrefixMatchesTheSingleColumnName() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.getColumnNames("pre_", List.of(DemoEntityPersistenceImpl.CN_NAME)),
                 cv.getColumnName("pre_", DemoEntityPersistenceImpl.CN_NAME));
  }

  @Test
  public void testColumnNameListWithPrefixButWithoutTableAlias() {
    // no alias: nothing to qualify, the prefix simply leads the column name
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv =
        PersistentObjectClassVariables.create(DemoEntity.class, DemoEntityPersistenceImpl.class, null);
    assertNull(cv.getTableAlias());
    assertEquals(cv.getColumnNames("pre_", List.of("id", "serial")), "pre_id,pre_serial");
  }

  @Test
  public void testColumnNameListIsCached() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    String columnNames = cv.getColumnNames(List.of("id", "serial"));
    assertEquals(cv.getColumnNames(List.of("id")), columnNames,
                 "the SQL column list is built once per entity and cached");
  }

  @Test
  public void testNoClassIdConditionWithoutInheritance() {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();
    assertEquals(cv.createSqlClassIdCondition(List.of(9001), true), "",
                 "an entity without a super entity needs no class-ID condition");
    assertEquals(cv.getSqlClassIdCondition(List.of(9001)), "");
  }

  @Test
  public void testClassIdConditionForASingleClassId() {
    assertEquals(createSubVariables().createSqlClassIdCondition(List.of(9004), true), " AND dbs.classid=9004");
  }

  @Test
  public void testClassIdConditionWithoutAlias() {
    assertEquals(createSubVariables().createSqlClassIdCondition(List.of(9004), false), " AND classid=9004");
  }

  @Test
  public void testClassIdConditionForSeveralClassIds() {
    assertEquals(createSubVariables().createSqlClassIdCondition(List.of(9004, 9005), true),
                 " AND dbs.classid IN(9004,9005)");
  }

  @Test
  public void testClassIdConditionWithoutClassIds() {
    PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> cv = createSubVariables();
    assertEquals(cv.createSqlClassIdCondition(null, true), "", "no restriction without class IDs");
    assertEquals(cv.createSqlClassIdCondition(List.of(), true), "");
  }

  @Test
  public void testSqlClassIdConditionOnlyForEntitiesSharingATable() {
    // the base entity owns the table: its rows are selected without a class-ID condition
    assertEquals(DemoBasePersistenceImpl.CLASSVARIABLES.getSqlClassIdCondition(List.of(9003, 9004)), "");
    // the derived entity shares the table and must restrict the select to its own rows
    assertEquals(createSubVariables().getSqlClassIdCondition(List.of(9004)), " AND dbs.classid=9004");
  }

  @Test
  public void testEagerJoinsAreCollectedAlongTheSuperClassVariables() {
    Join<DemoBase, DemoComponent> baseJoin =
        new Join<>(JoinType.LEFT, "dbs.id", "e_1.rootid", DemoComponent.class, "e_1", (a, b) -> { });
    Join<DemoSub, DemoComponent> subJoin =
        new Join<>(JoinType.LEFT, "dbs.id", "e_2.rootid", DemoComponent.class, "e_2", (a, b) -> { });

    PersistentObjectClassVariables<DemoBase, DemoBasePersistenceImpl> baseVariables =
        PersistentObjectClassVariables.create(DemoBase.class, DemoBasePersistenceImpl.class, "dbs",
                                              null, List.of(baseJoin));
    @SuppressWarnings({ "unchecked", "rawtypes" })
    PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> subVariables =
        PersistentObjectClassVariables.create(DemoSub.class, DemoSubPersistenceImpl.class, null,
                                              (PersistentObjectClassVariables) baseVariables, List.of(subJoin));

    assertEquals(subVariables.getEagerJoins(), List.of(subJoin, baseJoin),
                 "own joins first, then the inherited ones");
    assertEquals(baseVariables.getEagerJoins(), List.of(baseJoin));
  }

  @Test
  public void testNoEagerJoins() {
    assertNull(createEntityVariables().getEagerJoins(), "null instead of an empty list");
  }

  @Test
  public void testSnapshotMethodsAreLocatedAtTheImplementationClass() throws NoSuchMethodException {
    PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> cv = createEntityVariables();

    Method revertAttributes = cv.getRevertAttributesToSnapshotMethod();
    assertSame(revertAttributes.getDeclaringClass(), DemoEntityPersistenceImpl.class);
    assertSame(cv.getCreateComponentsInSnapshotMethod().getDeclaringClass(), DemoEntityPersistenceImpl.class);
    assertSame(cv.getRevertComponentsToSnapshotMethod().getDeclaringClass(), DemoEntityPersistenceImpl.class);
    assertSame(cv.getRevertAttributesToSnapshotMethod(), revertAttributes, "determined only once");
  }

  @Test
  public void testSnapshotMethodsFallBackToTheBaseClass() throws NoSuchMethodException {
    // DemoComponentPersistenceImpl declares no component methods: the ones of the base class are used
    PersistentObjectClassVariables<DemoComponent, DemoComponentPersistenceImpl> cv =
        PersistentObjectClassVariables.create(DemoComponent.class, DemoComponentPersistenceImpl.class, "dcm");
    assertSame(cv.getRevertAttributesToSnapshotMethod().getDeclaringClass(), DemoComponentPersistenceImpl.class);
    assertSame(cv.getCreateComponentsInSnapshotMethod().getDeclaringClass(), AbstractPersistentObject.class);
    assertSame(cv.getRevertComponentsToSnapshotMethod().getDeclaringClass(), AbstractPersistentObject.class);
  }

  @Test
  public void testMethodCacheIsCreatedForThePdoClass() {
    assertEquals(createEntityVariables().methodCache.toString(), "PdoMethodCache(DemoEntity)");
  }

  @Test
  public void testToString() {
    assertTrue(createEntityVariables().toString().startsWith(DemoEntity.class.getName() + "/"),
               "the PDO class leads the description");
  }

}
