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
import org.tentackle.pdo.PersistentOperationService;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * Persistence implementation of {@link DemoOperation}.
 * <p>
 * A hand-written equivalent of what the wurblets generate for an operation.
 */
@SuppressWarnings("exports")
@PersistentOperationService(DemoOperation.class)
public class DemoOperationPersistenceImpl extends AbstractPersistentOperation<DemoOperation, DemoOperationPersistenceImpl>
       implements DemoOperationPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoOperationPersistenceImpl. */
  public static final PersistentOperationClassVariables<DemoOperation, DemoOperationPersistenceImpl> CLASSVARIABLES =
      PersistentOperationClassVariables.create(DemoOperation.class, DemoOperationPersistenceImpl.class);

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param context the domain context
   */
  public DemoOperationPersistenceImpl(DemoOperation operation, DomainContext context) {
    super(operation, context);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param session the session
   */
  public DemoOperationPersistenceImpl(DemoOperation operation, Session session) {
    super(operation, session);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   */
  public DemoOperationPersistenceImpl(DemoOperation operation) {
    super(operation);
  }

  /**
   * Creates a persistent object.
   */
  public DemoOperationPersistenceImpl() {
    super();
  }

  //</editor-fold>

  @Override
  public PersistentOperationClassVariables<DemoOperation, DemoOperationPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public String execute() {
    return "executed in " + getDomainContext();
  }

}
