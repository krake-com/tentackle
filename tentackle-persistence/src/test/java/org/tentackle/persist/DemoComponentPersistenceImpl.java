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
  private transient StringBuilder attributesNormText;
  private transient StringBuilder relationsNormText;
  private transient long contextUserId;

  /**
   * Creates a demo component.
   *
   * @param pdo the PDO this is a delegate for
   * @param context the domain context
   */
  public DemoComponentPersistenceImpl(DemoComponent pdo, DomainContext context) {
    super(pdo, context);
  }

  /**
   * Creates a demo component.
   *
   * @param pdo the PDO this is a delegate for
   */
  public DemoComponentPersistenceImpl(DemoComponent pdo) {
    super(pdo);
  }

  /**
   * Creates a demo component.
   */
  public DemoComponentPersistenceImpl() {
    super();
  }


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
