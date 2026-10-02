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
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * Persistence implementation of {@link DemoCached}.
 * <p>
 * Never reads or writes the database: the cache tests create the PDOs in memory.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoCached.class)
public class DemoCachedPersistenceImpl extends AbstractPersistentObject<DemoCached, DemoCachedPersistenceImpl>
       implements DemoCachedPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoCachedPersistenceImpl. */
  public static final PersistentObjectClassVariables<DemoCached, DemoCachedPersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoCached.class, DemoCachedPersistenceImpl.class, "dca");

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a PDO.
   *
   * @param pdo the PDO
   * @param context the domain context
   */
  public DemoCachedPersistenceImpl(DemoCached pdo, DomainContext context) {
    super(pdo, context);
  }

  /**
   * Creates a PDO with a session only.
   *
   * @param pdo the PDO
   * @param session the session
   */
  public DemoCachedPersistenceImpl(DemoCached pdo, Session session) {
    super(pdo, session);
  }

  /**
   * Creates a PDO without domain context or session.
   *
   * @param pdo the PDO
   */
  public DemoCachedPersistenceImpl(DemoCached pdo) {
    super(pdo);
  }

  /**
   * Creates a PDO without domain context or session.
   */
  public DemoCachedPersistenceImpl() {
    super();
  }

  //</editor-fold>


  @Override
  public PersistentObjectClassVariables<DemoCached, DemoCachedPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public boolean isRootEntity() {
    return true;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Generated for entities flagged {@code tableserial} in the model.
   */
  @Override
  public boolean isTableSerialProvided() {
    return true;
  }

}
