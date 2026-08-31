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

import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentObjectService;
import org.tentackle.session.Session;
import org.tentackle.sql.Backend;

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

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a PDO.
   *
   * @param pdo the PDO
   * @param context the domain context
   */
  public DemoEntityPersistenceImpl(DemoEntity pdo, DomainContext context)    {
    super(pdo, context);
  }

  /**
   * Creates a PDO with a session only.
   *
   * @param pdo the PDO
   * @param session the session
   */
  public DemoEntityPersistenceImpl(DemoEntity pdo, Session session) {
    super(pdo, session);
  }

  /**
   * Creates a pdo without domain context or session.
   *
   * @param pdo the number space PDO
   */
  public DemoEntityPersistenceImpl(DemoEntity pdo) {
    super(pdo);
  }

  /**
   * Creates a PDO without domain context or session.
   */
  public DemoEntityPersistenceImpl() {
    super();
  }

  //</editor-fold>


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


  //<editor-fold defaultstate="collapsed" desc="mapping">

  /**
   * {@inheritDoc}
   * <p>
   * A hand-written equivalent of what the {@code Fields} wurblet generates. The columns are looked
   * up by name once per section, hence the order only has to match the sequence of the getters
   * below, not the order of the columns in the table.
   *
   * @param rs the result set
   */
  @Override
  public void getFields(ResultSetWrapper rs) {
    super.getFields(rs);
    if (rs.configureSection(CLASSVARIABLES)) {
      rs.configureColumn(CN_NAME);
      rs.configureColumn(CN_ID);
      rs.configureColumn(CN_SERIAL);
    }
    name = rs.getString();
    setId(rs.getLong());
    setSerial(rs.getLong());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The parameter order must match {@link #createInsertSql(Backend)} and
   * {@link #createUpdateSql(Backend)}.
   *
   * @param st the statement
   * @return the number of fields set
   */
  @Override
  public int setFields(PreparedStatementWrapper st) {
    int ndx = super.setFields(st);
    st.setString(++ndx, name);
    st.setLong(++ndx, getId());
    st.setLong(++ndx, getSerial());
    return ndx;
  }

  @Override
  public String createInsertSql(Backend backend) {
    return Backend.SQL_INSERT_INTO + getTableName() + Backend.SQL_LEFT_PARENTHESIS +
           CN_NAME + Backend.SQL_COMMA +
           CN_ID + Backend.SQL_COMMA +
           CN_SERIAL +
           Backend.SQL_INSERT_VALUES +
           Backend.SQL_PAR_COMMA.repeat(2) +
           Backend.SQL_PAR + Backend.SQL_RIGHT_PARENTHESIS;
  }

  @Override
  public String createUpdateSql(Backend backend) {
    return Backend.SQL_UPDATE + getTableName() + Backend.SQL_SET +
           CN_NAME + Backend.SQL_EQUAL_PAR_COMMA +
           CN_SERIAL + Backend.SQL_EQUAL + CN_SERIAL + Backend.SQL_PLUS_ONE +
           Backend.SQL_WHERE + CN_ID + Backend.SQL_EQUAL_PAR +
           Backend.SQL_AND + CN_SERIAL + Backend.SQL_EQUAL_PAR;
  }

  //</editor-fold>


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
