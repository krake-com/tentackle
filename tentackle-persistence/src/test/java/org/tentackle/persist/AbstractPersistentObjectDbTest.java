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

import org.testng.annotations.Test;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.NotFoundException;
import org.tentackle.session.PersistenceException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Database-backed tests for the read and write path of {@link AbstractPersistentObject}.
 * <p>
 * The statements themselves are asserted as strings by {@link AbstractPersistentObjectSqlTest};
 * what is verified here is that they actually run, that the values survive the round trip and
 * that the optimistic locking rejects a stale serial.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPersistentObjectDbTest extends AbstractPersistenceDbTest {

  /**
   * Creates a fresh entity in the shared domain context.
   *
   * @param name the name
   * @return the persistence delegate
   */
  private DemoEntityPersistenceImpl entity(String name) {
    DemoEntityPersistenceImpl po = delegate(Pdo.create(DemoEntity.class, getDomainContext()));
    po.setName(name);
    return po;
  }

  private DemoEntityPersistenceImpl delegate(DemoEntity pdo) {
    return (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
  }

  /**
   * Creates an empty entity to select with.
   *
   * @return the persistence delegate
   */
  private DemoEntityPersistenceImpl finder() {
    return delegate(Pdo.create(DemoEntity.class, getDomainContext()));
  }


  @Test
  public void testInsertAndSelect() {
    DemoEntityPersistenceImpl po = entity("alpha");
    assertTrue(po.isNew());
    po.save();
    assertFalse(po.isNew());
    assertNotEquals(po.getId(), 0L);
    assertEquals(po.getSerial(), 1L);

    DemoEntity loaded = finder().select(po.getId());
    assertEquals(delegate(loaded).getName(), "alpha");
    assertEquals(delegate(loaded).getSerial(), 1L);
    assertFalse(delegate(loaded).attributesModified(), "a freshly loaded object is unmodified");
  }

  @Test
  public void testSelectOfAnUnknownIdReturnsNull() {
    assertNull(finder().select(4711L));
  }

  @Test
  public void testUpdateIncrementsTheSerial() {
    DemoEntityPersistenceImpl po = entity("beta");
    po.save();
    po.setName("beta2");
    po.save();
    assertEquals(po.getSerial(), 2L);
    assertEquals(delegate(finder().select(po.getId())).getName(), "beta2");
  }

  @Test
  public void testDeleteRemovesTheRow() {
    DemoEntityPersistenceImpl po = entity("gamma");
    po.save();
    long id = po.getId();
    po.delete();
    assertNull(finder().select(id));
  }

  @Test
  public void testReloadPicksUpTheDatabaseValues() {
    DemoEntityPersistenceImpl po = entity("delta");
    po.save();

    // change the row behind the back of po
    DemoEntityPersistenceImpl other = delegate(finder().select(po.getId()));
    other.setName("delta2");
    other.save();

    assertEquals(po.getName(), "delta");
    DemoEntity reloaded = po.reload();
    assertEquals(delegate(reloaded).getName(), "delta2");
    assertEquals(delegate(reloaded).getSerial(), 2L);
  }

  @Test
  public void testSelectAll() {
    entity("one").save();
    entity("two").save();
    List<DemoEntity> all = finder().selectAll();
    assertEquals(all.size(), 2);
    assertEquals(all.stream().map(pdo -> delegate(pdo).getName()).sorted().toList(), List.of("one", "two"));
  }

  @Test
  public void testSelectAny() {
    DemoEntityPersistenceImpl first = entity("first");
    first.save();
    DemoEntityPersistenceImpl second = entity("second");
    second.save();
    entity("third").save();

    List<DemoEntity> some = finder().selectAny(List.of(first.getId(), second.getId()));
    assertEquals(some.size(), 2);
    assertEquals(some.stream().map(pdo -> delegate(pdo).getName()).sorted().toList(), List.of("first", "second"));
  }

  @Test
  public void testSelectLatestIsLimitedAndOrderedByIdDescending() {
    DemoEntityPersistenceImpl first = entity("l1");
    first.save();
    entity("l2").save();
    entity("l3").save();

    List<DemoEntity> latest = finder().selectLatest(first.getId(), 2);
    assertEquals(latest.size(), 2);
    assertEquals(latest.stream().map(pdo -> delegate(pdo).getName()).toList(), List.of("l3", "l2"));
  }

  @Test
  public void testSelectByTemplateLoadsTheSameId() {
    DemoEntityPersistenceImpl template = entity("template");
    template.save();
    DemoEntity found = finder().selectByTemplate(template);
    assertEquals(delegate(found).getId(), template.getId());
    assertEquals(delegate(found).getName(), "template");
  }

  @Test
  public void testUpdateWithAStaleSerialIsRejected() {
    DemoEntityPersistenceImpl po = entity("stale");
    po.save();

    DemoEntityPersistenceImpl other = delegate(finder().select(po.getId()));
    other.setName("won");
    other.save();

    po.setName("lost");
    NotFoundException nfx = expectThrows(NotFoundException.class, po::save);
    assertTrue(nfx.isTemporary(), "an optimistic locking failure may disappear when the tx is retried");
    assertTrue(nfx.getMessage().contains("persisted serial=2"), nfx.getMessage());
  }

  @Test
  public void testDeleteWithAStaleSerialIsRejected() {
    DemoEntityPersistenceImpl po = entity("stale2");
    po.save();

    DemoEntityPersistenceImpl other = delegate(finder().select(po.getId()));
    other.setName("won");
    other.save();

    expectThrows(PersistenceException.class, po::delete);
  }

  @Test
  public void testDeleteOfAnAlreadyDeletedObjectIsRejected() {
    DemoEntityPersistenceImpl po = entity("twice");
    po.save();
    DemoEntityPersistenceImpl other = delegate(finder().select(po.getId()));
    po.delete();
    NotFoundException nfx = expectThrows(NotFoundException.class, other::delete);
    assertTrue(nfx.getMessage().contains("doesn't exist in table demoentity"), nfx.getMessage());
  }


  // --- components: root columns and the context condition -------------------------------------

  /**
   * Creates a component in the root context of the given root entity.
   *
   * @param root the saved root entity
   * @param text the component's text
   * @return the persistence delegate of the component
   */
  private DemoComponentPersistenceImpl component(DemoEntityPersistenceImpl root, String text) {
    DomainContext rootContext = getDomainContext().getRootContext(root.getPdo());
    DemoComponent pdo = Pdo.create(DemoComponent.class, rootContext);
    DemoComponentPersistenceImpl po = (DemoComponentPersistenceImpl) pdo.getPersistenceDelegate();
    po.setText(text);
    return po;
  }

  /**
   * Creates an empty component to select with, in the root context of the given root entity.
   *
   * @param root the saved root entity
   * @return the persistence delegate
   */
  private DemoComponentPersistenceImpl componentFinder(DemoEntityPersistenceImpl root) {
    return (DemoComponentPersistenceImpl) Pdo.create(DemoComponent.class,
                                                     getDomainContext().getRootContext(root.getPdo()))
                                              .getPersistenceDelegate();
  }

  @Test
  public void testComponentGetsItsRootColumnsFromTheDomainContext() {
    DemoEntityPersistenceImpl root = entity("root");
    root.save();

    DemoComponentPersistenceImpl comp = component(root, "c1");
    comp.saveObject();

    assertEquals(comp.getRootId(), root.getId());
    assertEquals(comp.getRootClassId(), 9001);
    assertTrue(root.isRootEntityOf(comp.getPdo()));

    // and the columns are persisted, not just set in memory
    DemoComponentPersistenceImpl loaded =
        (DemoComponentPersistenceImpl) componentFinder(root).select(comp.getId()).getPersistenceDelegate();
    assertEquals(loaded.getRootId(), root.getId());
    assertEquals(loaded.getRootClassId(), 9001);
  }

  @Test
  public void testComponentsAreSelectedWithinTheirRootContextOnly() {
    DemoEntityPersistenceImpl rootA = entity("rootA");
    rootA.save();
    DemoEntityPersistenceImpl rootB = entity("rootB");
    rootB.save();

    component(rootA, "b").saveObject();
    component(rootA, "a").saveObject();
    component(rootB, "z").saveObject();

    List<DemoComponent> componentsOfA = componentFinder(rootA).selectAll();
    // the context condition restricts to rootA, orderBy() sorts by ctext
    assertEquals(componentsOfA.stream()
                              .map(pdo -> ((DemoComponentPersistenceImpl) pdo.getPersistenceDelegate()).getText())
                              .toList(),
                 List.of("a", "b"));
  }

  @Test
  public void testSelectByNormText() {
    DemoEntityPersistenceImpl root = entity("nt");
    root.save();

    DemoComponentPersistenceImpl hit = component(root, "hit");
    hit.setAttributesNormText(new StringBuilder("Mueller"));
    hit.updateNormText();
    hit.saveObject();

    DemoComponentPersistenceImpl miss = component(root, "miss");
    miss.setAttributesNormText(new StringBuilder("Schmidt"));
    miss.updateNormText();
    miss.saveObject();

    DemoComponentPersistenceImpl finder = componentFinder(root);

    List<DemoComponent> found = finder.selectByNormText("Mueller");
    assertEquals(found.size(), 1);
    assertEquals(((DemoComponentPersistenceImpl) found.get(0).getPersistenceDelegate()).getText(), "hit");

    // "!" inverts the search
    List<DemoComponent> inverted = finder.selectByNormText("!Mueller");
    assertEquals(inverted.size(), 1);
    assertEquals(((DemoComponentPersistenceImpl) inverted.get(0).getPersistenceDelegate()).getText(), "miss");

    // an empty search matches all
    assertEquals(finder.selectByNormText("").size(), 2);
  }

}
