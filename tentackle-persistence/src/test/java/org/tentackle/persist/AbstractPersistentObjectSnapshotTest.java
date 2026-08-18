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

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the snapshots of {@link AbstractPersistentObject}.
 * <p>
 * Snapshots are taken and reverted entirely in memory: the PDO is cloned, the clone is marked as a
 * snapshot and registered at the original. Reverting copies the attributes back through the snapshot
 * methods located by the class variables. No session is involved.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPersistentObjectSnapshotTest {

  /**
   * Creates an entity PDO with a name.
   *
   * @param name the name
   * @return the PDO
   */
  private DemoEntity createEntity(String name) {
    DemoEntity pdo = Pdo.create(DemoEntity.class);
    ((DemoEntityPersistenceImpl) pdo.getPersistenceDelegate()).setName(name);
    return pdo;
  }

  /**
   * Gets the persistence delegate.
   *
   * @param pdo the PDO
   * @return the delegate
   */
  private DemoEntityPersistenceImpl delegate(DemoEntity pdo) {
    return (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
  }


  @Test
  public void testSnapshotIsACloneMarkedAsSnapshot() {
    DemoEntity pdo = createEntity("original");
    DemoEntity snapshot = pdo.createSnapshot();

    assertNotSame(snapshot, pdo);
    assertTrue(delegate(snapshot).isSnapshot());
    assertFalse(delegate(pdo).isSnapshot(), "the original is no snapshot");
    assertFalse(delegate(snapshot).isCopy(), "a snapshot is not a copy");
    assertEquals(delegate(snapshot).getName(), "original", "the attributes are cloned");
  }

  @Test
  public void testSnapshotIsImmutable() {
    DemoEntity snapshot = createEntity("original").createSnapshot();
    assertTrue(expectThrows(PersistenceException.class, () -> delegate(snapshot).setName("nope"))
                   .getMessage().contains("object is a snapshot"));
  }

  @Test
  public void testSnapshotsAreRegisteredAtTheOriginal() {
    DemoEntity pdo = createEntity("original");
    assertTrue(delegate(pdo).getSnapshots().isEmpty());

    DemoEntity first = pdo.createSnapshot();
    DemoEntity second = pdo.createSnapshot();
    assertEquals(delegate(pdo).getSnapshots(), List.of(first, second), "in the order they were taken");
    assertTrue(delegate(first).getSnapshots().isEmpty(), "a snapshot has no snapshots of its own");
  }

  @Test
  public void testTheSameSnapshotCannotBeAddedTwice() {
    DemoEntity pdo = createEntity("original");
    DemoEntity snapshot = pdo.createSnapshot();
    assertTrue(expectThrows(PersistenceException.class, () -> delegate(pdo).addSnapshot(snapshot))
                   .getMessage().contains("snapshot already added"));
  }

  @Test
  public void testRevertRestoresTheAttributes() {
    DemoEntity pdo = createEntity("original");
    DemoEntity snapshot = pdo.createSnapshot();

    delegate(pdo).setName("changed");
    delegate(pdo).setNamePersisted("persisted");
    pdo.revertToSnapshot(snapshot);

    assertEquals(delegate(pdo).getName(), "original", "reverted through the generated snapshot method");
    assertEquals(delegate(pdo).getNamePersisted(), null);
  }

  @Test
  public void testRevertToNullDoesNothing() {
    DemoEntity pdo = createEntity("original");
    delegate(pdo).setName("changed");
    pdo.revertToSnapshot(null);
    assertEquals(delegate(pdo).getName(), "changed");
  }

  @Test
  public void testRevertRestoresTheSnapshotRegistryOfTheSnapshotsPointInTime() {
    DemoEntity pdo = createEntity("original");
    DemoEntity snapshot = pdo.createSnapshot();
    assertEquals(delegate(pdo).getSnapshots().size(), 1);

    pdo.revertToSnapshot(snapshot);
    assertTrue(delegate(pdo).getSnapshots().isEmpty(),
               "the object is restored as it was when the snapshot was taken, snapshot registry included");
  }

  @Test
  public void testRevertToAForeignSnapshot() {
    DemoEntity pdo = createEntity("original");
    DemoEntity foreignSnapshot = createEntity("other").createSnapshot();
    assertTrue(expectThrows(PersistenceException.class, () -> pdo.revertToSnapshot(foreignSnapshot))
                   .getMessage().contains("not my snapshot"));
  }

  @Test
  public void testRevertToSomethingThatIsNoSnapshot() {
    DemoEntity pdo = createEntity("original");
    DemoEntity other = createEntity("other");
    assertTrue(expectThrows(PersistenceException.class, () -> pdo.revertToSnapshot(other))
                   .getMessage().contains("not a snapshot"));
  }

  @Test
  public void testDiscardSnapshot() {
    DemoEntity pdo = createEntity("original");
    DemoEntity first = pdo.createSnapshot();
    DemoEntity second = pdo.createSnapshot();

    pdo.discardSnapshot(first);
    assertEquals(delegate(pdo).getSnapshots(), List.of(second));

    assertTrue(expectThrows(PersistenceException.class, () -> pdo.discardSnapshot(first))
                   .getMessage().contains("not my snapshot"), "already discarded");
  }

  @Test
  public void testDiscardAllSnapshots() {
    DemoEntity pdo = createEntity("original");
    pdo.createSnapshot();
    pdo.createSnapshot();

    delegate(pdo).discardSnapshots();
    assertTrue(delegate(pdo).getSnapshots().isEmpty());
  }

  @Test
  public void testMarkingAsCopyDropsTheSnapshots() {
    DemoEntity pdo = createEntity("original");
    DemoEntity snapshot = pdo.createSnapshot();

    delegate(pdo).setCopy(true);
    assertTrue(delegate(pdo).isCopy());
    assertTrue(delegate(pdo).getSnapshots().isEmpty(), "a copy inherits no snapshots");

    // a copy may be reverted to any snapshot, that's how copy() works
    delegate(pdo).setName("changed");
    pdo.revertToSnapshot(snapshot);
    assertEquals(delegate(pdo).getName(), "original");
  }

  @Test
  public void testComponentsAreSnapshottedAndReverted() {
    DemoEntity pdo = createEntity("root");
    DemoComponent component = Pdo.create(DemoComponent.class);
    List<DemoComponent> components = new ArrayList<>();
    components.add(component);
    delegate(pdo).setComponentList(components);

    DemoEntity snapshot = pdo.createSnapshot();

    delegate(pdo).setComponentList(new ArrayList<>());
    assertTrue(delegate(pdo).getComponentList().isEmpty());

    pdo.revertToSnapshot(snapshot);
    assertEquals(delegate(pdo).getComponentList(), List.of(component),
                 "the components are reverted through the generated component-snapshot methods");
    assertNotSame(delegate(pdo).getComponentList(), components, "but not the very same list instance");
  }

  @Test
  public void testSnapshotOfAComponent() {
    DemoComponent pdo = Pdo.create(DemoComponent.class);
    DemoComponentPersistenceImpl po = (DemoComponentPersistenceImpl) pdo.getPersistenceDelegate();
    po.setText("original");

    DemoComponent snapshot = pdo.createSnapshot();
    po.setText("changed");
    pdo.revertToSnapshot(snapshot);

    assertEquals(po.getText(), "original");
  }

  @Test
  public void testSnapshotKeepsTheDomainContext() {
    DemoEntity pdo = createEntity("original");
    delegate(pdo).setDomainContext(Pdo.createDomainContext(null));
    DemoEntity snapshot = pdo.createSnapshot();
    assertSame(delegate(snapshot).getDomainContext(), delegate(pdo).getDomainContext());
  }

}
