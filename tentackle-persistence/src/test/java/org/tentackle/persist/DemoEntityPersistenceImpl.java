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

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentObjectService;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence implementation of {@link DemoEntity}.
 * <p>
 * A hand-written equivalent of what the {@code ClassVariables} wurblet generates for a root entity
 * mapping to its own table.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoEntity.class)
public class DemoEntityPersistenceImpl extends AbstractPersistentObject<DemoEntity, DemoEntityPersistenceImpl>
       implements DemoEntityPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoEntityPersistenceImpl. */
  public static final PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoEntity.class, DemoEntityPersistenceImpl.class, "dem");

  /** database column name for 'name'. */
  public static final String CN_NAME = "name";


  private String name;
  private String namePersisted;
  private ArrayList<DemoComponent> componentList;
  private transient ArrayList<DemoComponent> componentListSnapshot;

  /**
   * Creates a demo entity.
   *
   * @param pdo the PDO this is a delegate for
   * @param context the domain context
   */
  public DemoEntityPersistenceImpl(DemoEntity pdo, DomainContext context) {
    super(pdo, context);
  }

  /**
   * Creates a demo entity.
   *
   * @param pdo the PDO this is a delegate for
   */
  public DemoEntityPersistenceImpl(DemoEntity pdo) {
    super(pdo);
  }

  /**
   * Creates a demo entity.
   */
  public DemoEntityPersistenceImpl() {
    super();
  }


  @Override
  public PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This is the root entity of {@link DemoComponent}.
   */
  @Override
  public boolean isRootEntity() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Tracked, i.e., all setters check for modification. Generated for entities flagged {@code tracked}
   * in the model. {@link DemoComponentPersistenceImpl} is untracked to cover the other case.
   */
  @Override
  public boolean isTracked() {
    return true;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String name) {
    assertMutable();
    this.name = name;
    setModified(true);
  }

  /**
   * Gets the last persisted name.
   *
   * @return the persisted name
   */
  public String getNamePersisted() {
    return namePersisted;
  }

  /**
   * Sets the last persisted name.
   *
   * @param namePersisted the persisted name
   */
  public void setNamePersisted(String namePersisted) {
    this.namePersisted = namePersisted;
  }

  /**
   * Gets the components of this aggregate.
   *
   * @return the components, null if not loaded
   */
  public List<DemoComponent> getComponentList() {
    return componentList;
  }

  /**
   * Sets the components of this aggregate.
   *
   * @param componentList the components, null if not loaded
   */
  public void setComponentList(List<DemoComponent> componentList) {
    this.componentList = componentList == null ? null : new ArrayList<>(componentList);
  }


  /**
   * Copies the attributes back from the snapshot.
   * <p>
   * Notice that this is an overload, not an override: the snapshot methods are located by
   * {@link PersistentObjectClassVariables#getRevertAttributesToSnapshotMethod()} through the
   * declaring class, which is exactly how the generated code declares them.
   *
   * @param snapshot the snapshot object
   */
  protected void revertAttributesToSnapshot(DemoEntityPersistenceImpl snapshot) {
    super.revertAttributesToSnapshot(snapshot);
    name = snapshot.name;
    namePersisted = snapshot.namePersisted;
  }

  /**
   * Updates the components in the snapshot object.
   * <p>
   * The generated code uses {@code TrackedList} here, a plain copy is enough for the tests.
   *
   * @param snapshot the snapshot object
   */
  protected void createComponentsInSnapshot(DemoEntityPersistenceImpl snapshot) {
    super.createComponentsInSnapshot(snapshot);
    snapshot.componentListSnapshot = componentList == null ? null : new ArrayList<>(componentList);
  }

  /**
   * Reverts all components of this object to a given snapshot.
   *
   * @param snapshot the snapshot object
   */
  protected void revertComponentsToSnapshot(DemoEntityPersistenceImpl snapshot) {
    super.revertComponentsToSnapshot(snapshot);
    componentList = snapshot.componentListSnapshot == null ? null : new ArrayList<>(snapshot.componentListSnapshot);
  }

}
