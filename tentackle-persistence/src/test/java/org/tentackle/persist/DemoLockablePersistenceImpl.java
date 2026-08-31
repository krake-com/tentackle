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

/**
 * Persistence implementation of {@link DemoLockable}.
 * <p>
 * A root entity with the token-lock columns: the token-lock operations of
 * {@link AbstractPersistentObject} assert a root entity, which neither {@link DemoComponent}
 * (a component) nor {@link DemoEntity} (no token lock) can provide.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoLockable.class)
public class DemoLockablePersistenceImpl extends AbstractPersistentObject<DemoLockable, DemoLockablePersistenceImpl>
       implements DemoLockablePersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoLockablePersistenceImpl. */
  public static final PersistentObjectClassVariables<DemoLockable, DemoLockablePersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoLockable.class, DemoLockablePersistenceImpl.class, "dlk");

  /** database column name for 'text'. */
  public static final String CN_TEXT = "ltext";


  private String text;

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a PDO.
   *
   * @param pdo the PDO
   * @param context the domain context
   */
  public DemoLockablePersistenceImpl(DemoLockable pdo, DomainContext context) {
    super(pdo, context);
  }

  /**
   * Creates a PDO with a session only.
   *
   * @param pdo the PDO
   * @param session the session
   */
  public DemoLockablePersistenceImpl(DemoLockable pdo, Session session) {
    super(pdo, session);
  }

  /**
   * Creates a PDO without domain context or session.
   *
   * @param pdo the PDO
   */
  public DemoLockablePersistenceImpl(DemoLockable pdo) {
    super(pdo);
  }

  /**
   * Creates a PDO without domain context or session.
   */
  public DemoLockablePersistenceImpl() {
    super();
  }

  //</editor-fold>


  @Override
  public PersistentObjectClassVariables<DemoLockable, DemoLockablePersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public boolean isRootEntity() {
    return true;
  }

  @Override
  public boolean isTracked() {
    return true;
  }

  @Override
  public boolean isTokenLockProvided() {
    return true;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public void setText(String text) {
    assertMutable();
    this.text = text;
    setModified(true);
  }

  //<editor-fold defaultstate="collapsed" desc="mapping">

  @Override
  public void getFields(ResultSetWrapper rs) {
    super.getFields(rs);
    if (rs.configureSection(CLASSVARIABLES)) {
      rs.configureColumn(CN_TEXT);
      rs.configureColumn(CN_EDITEDBY);
      rs.configureColumn(CN_EDITEDSINCE);
      rs.configureColumn(CN_EDITEDEXPIRY);
      rs.configureColumn(CN_ID);
      rs.configureColumn(CN_SERIAL);
    }
    text = rs.getString();
    setEditedBy(rs.getLong());
    setEditedSince(rs.getTimestamp());
    setEditedExpiry(rs.getTimestamp());
    setId(rs.getLong());
    setSerial(rs.getLong());
  }

  @Override
  public int setFields(PreparedStatementWrapper st) {
    int ndx = super.setFields(st);
    st.setString(++ndx, text);
    st.setLong(++ndx, getEditedBy());
    st.setTimestamp(++ndx, getEditedSince());
    st.setTimestamp(++ndx, getEditedExpiry());
    st.setLong(++ndx, getId());
    st.setLong(++ndx, getSerial());
    return ndx;
  }

  @Override
  public String createInsertSql(Backend backend) {
    return Backend.SQL_INSERT_INTO + getTableName() + Backend.SQL_LEFT_PARENTHESIS +
           CN_TEXT + Backend.SQL_COMMA +
           CN_EDITEDBY + Backend.SQL_COMMA +
           CN_EDITEDSINCE + Backend.SQL_COMMA +
           CN_EDITEDEXPIRY + Backend.SQL_COMMA +
           CN_ID + Backend.SQL_COMMA +
           CN_SERIAL +
           Backend.SQL_INSERT_VALUES +
           Backend.SQL_PAR_COMMA.repeat(5) +
           Backend.SQL_PAR + Backend.SQL_RIGHT_PARENTHESIS;
  }

  @Override
  public String createUpdateSql(Backend backend) {
    return Backend.SQL_UPDATE + getTableName() + Backend.SQL_SET +
           CN_TEXT + Backend.SQL_EQUAL_PAR_COMMA +
           CN_EDITEDBY + Backend.SQL_EQUAL_PAR_COMMA +
           CN_EDITEDSINCE + Backend.SQL_EQUAL_PAR_COMMA +
           CN_EDITEDEXPIRY + Backend.SQL_EQUAL_PAR_COMMA +
           CN_SERIAL + Backend.SQL_EQUAL + CN_SERIAL + Backend.SQL_PLUS_ONE +
           Backend.SQL_WHERE + CN_ID + Backend.SQL_EQUAL_PAR +
           Backend.SQL_AND + CN_SERIAL + Backend.SQL_EQUAL_PAR;
  }

  //</editor-fold>

  /**
   * Copies the attributes back from the snapshot.
   * <p>
   * An overload, not an override: see {@link DemoEntityPersistenceImpl#revertAttributesToSnapshot}.
   *
   * @param snapshot the snapshot object
   */
  protected void revertAttributesToSnapshot(DemoLockablePersistenceImpl snapshot) {
    super.revertAttributesToSnapshot(snapshot);
    text = snapshot.text;
  }

}
