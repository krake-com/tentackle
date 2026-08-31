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
 * Persistence implementation of {@link DemoComponent}.
 * <p>
 * Switches on the optional features of {@link AbstractPersistentObject} (root columns, normtext and
 * token lock) and provides the seams the tests need: the normtext builders and the context's user ID,
 * which would otherwise require a session.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoComponent.class)
public class DemoComponentPersistenceImpl extends AbstractPersistentObject<DemoComponent, DemoComponentPersistenceImpl>
       implements DemoComponentPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoComponentPersistenceImpl. */
  public static final PersistentObjectClassVariables<DemoComponent, DemoComponentPersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoComponent.class, DemoComponentPersistenceImpl.class, "dcm");

  /** database column name for 'text'. */
  public static final String CN_TEXT = "ctext";


  private String text;
  /*
   * No initializer, on purpose: determineContextId() runs from the AbstractPersistentObject
   * constructor, i.e. before the field initializers of this class, so an initializer here would
   * silently overwrite the value that was just determined. The generated code has none either.
   */
  private long contextId;
  private transient StringBuilder attributesNormText;
  private transient StringBuilder relationsNormText;
  private transient long contextUserId;

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a PDO.
   *
   * @param pdo the PDO
   * @param context the domain context
   */
  public DemoComponentPersistenceImpl (DemoComponent pdo, DomainContext context)    {
    super(pdo, context);
  }

  /**
   * Creates a PDO with a session only.
   *
   * @param pdo the PDO
   * @param session the session
   */
  public DemoComponentPersistenceImpl(DemoComponent pdo, Session session) {
    super(pdo, session);
  }

  /**
   * Creates a pdo without domain context or session.
   *
   * @param pdo the number space PDO
   */
  public DemoComponentPersistenceImpl(DemoComponent pdo) {
    super(pdo);
  }

  /**
   * Creates a PDO without domain context or session.
   */
  public DemoComponentPersistenceImpl() {
    super();
  }

  //</editor-fold>


  @Override
  public PersistentObjectClassVariables<DemoComponent, DemoComponentPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public boolean isRootIdProvided() {
    return true;
  }

  @Override
  public boolean isRootClassIdProvided() {
    return true;
  }

  @Override
  public boolean isNormTextProvided() {
    return true;
  }

  @Override
  public boolean isTokenLockProvided() {
    return true;
  }

  @Override
  public String orderBy() {
    return getColumnName(CN_TEXT);
  }

  @Override
  public String getSqlContextCondition() {
    return Backend.SQL_AND + getColumnName(CN_ROOTID) + "=?";
  }

  /**
   * {@inheritDoc}
   * <p>
   * The parameter of {@link #getSqlContextCondition()}: the ID of the root entity the component
   * belongs to. Determined once when the domain context is set, exactly like the generated code
   * of a context-dependent entity does.
   */
  @Override
  public void determineContextId() {
    DomainContext context = getDomainContext();
    contextId = context == null ? -1 : context.getRootId();
  }

  @Override
  public long getContextId() {
    return contextId;
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

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation of the generated code returns the normalized attributes.
   * The tests set the value directly.
   */
  @Override
  public StringBuilder createAttributesNormText() {
    return attributesNormText;
  }

  /**
   * Sets the text returned by {@link #createAttributesNormText()}.
   *
   * @param attributesNormText the normtext of the attributes, null if none
   */
  public void setAttributesNormText(StringBuilder attributesNormText) {
    this.attributesNormText = attributesNormText;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation of the generated code returns the normalized relations.
   * The tests set the value directly.
   */
  @Override
  public StringBuilder createRelationsNormText() {
    return relationsNormText;
  }

  /**
   * Sets the text returned by {@link #createRelationsNormText()}.
   *
   * @param relationsNormText the normtext of the relations, null if none
   */
  public void setRelationsNormText(StringBuilder relationsNormText) {
    this.relationsNormText = relationsNormText;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to avoid the session the domain context would need.
   */
  @Override
  public long getContextUserId() {
    return contextUserId;
  }

  /**
   * Sets the user ID returned by {@link #getContextUserId()}.
   *
   * @param contextUserId the user ID
   */
  public void setContextUserId(long contextUserId) {
    this.contextUserId = contextUserId;
  }

  //<editor-fold defaultstate="collapsed" desc="mapping">

  /**
   * {@inheritDoc}
   * <p>
   * A hand-written equivalent of what the {@code Fields} wurblet generates for an entity with root
   * columns, a normtext and the token lock. All of those live in {@link AbstractPersistentObject},
   * so the generated code only has to read and write them.
   *
   * @param rs the result set
   */
  @Override
  public void getFields(ResultSetWrapper rs) {
    super.getFields(rs);
    if (rs.configureSection(CLASSVARIABLES)) {
      rs.configureColumn(CN_TEXT);
      rs.configureColumn(CN_ROOTID);
      rs.configureColumn(CN_ROOTCLASSID);
      rs.configureColumn(CN_NORMTEXT);
      rs.configureColumn(CN_EDITEDBY);
      rs.configureColumn(CN_EDITEDSINCE);
      rs.configureColumn(CN_EDITEDEXPIRY);
      rs.configureColumn(CN_ID);
      rs.configureColumn(CN_SERIAL);
    }
    text = rs.getString();
    setRootId(rs.getLong());
    setRootClassId(rs.getInt());
    setNormText(rs.getString());
    setEditedBy(rs.getLong());
    setEditedSince(rs.getTimestamp());
    setEditedExpiry(rs.getTimestamp());
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
    st.setString(++ndx, text);
    st.setLong(++ndx, getRootId());
    st.setInt(++ndx, getRootClassId());
    st.setString(++ndx, getNormText());
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
           CN_ROOTID + Backend.SQL_COMMA +
           CN_ROOTCLASSID + Backend.SQL_COMMA +
           CN_NORMTEXT + Backend.SQL_COMMA +
           CN_EDITEDBY + Backend.SQL_COMMA +
           CN_EDITEDSINCE + Backend.SQL_COMMA +
           CN_EDITEDEXPIRY + Backend.SQL_COMMA +
           CN_ID + Backend.SQL_COMMA +
           CN_SERIAL +
           Backend.SQL_INSERT_VALUES +
           Backend.SQL_PAR_COMMA.repeat(8) +
           Backend.SQL_PAR + Backend.SQL_RIGHT_PARENTHESIS;
  }

  @Override
  public String createUpdateSql(Backend backend) {
    return Backend.SQL_UPDATE + getTableName() + Backend.SQL_SET +
           CN_TEXT + Backend.SQL_EQUAL_PAR_COMMA +
           CN_ROOTID + Backend.SQL_EQUAL_PAR_COMMA +
           CN_ROOTCLASSID + Backend.SQL_EQUAL_PAR_COMMA +
           CN_NORMTEXT + Backend.SQL_EQUAL_PAR_COMMA +
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
  protected void revertAttributesToSnapshot(DemoComponentPersistenceImpl snapshot) {
    super.revertAttributesToSnapshot(snapshot);
    text = snapshot.text;
  }

}
