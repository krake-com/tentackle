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

package org.tentackle.domain;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentOperationService;
import org.tentackle.pdo.mock.MockPersistentOperation;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * Persistence mock for {@link DemoOperation}.
 */
@SuppressWarnings("exports")
@PersistentOperationService(DemoOperation.class)
public class DemoOperationPersistenceMock extends MockPersistentOperation<DemoOperation>
       implements DemoOperationPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param context the domain context
   */
  public DemoOperationPersistenceMock(DemoOperation operation, DomainContext context) {
    super(operation, context);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   * @param session the session
   */
  public DemoOperationPersistenceMock(DemoOperation operation, Session session) {
    super(operation, session);
  }

  /**
   * Creates a persistent object for an operation.
   *
   * @param operation the operation object
   */
  public DemoOperationPersistenceMock(DemoOperation operation) {
    super(operation);
  }

  /**
   * Creates a persistent object.
   */
  public DemoOperationPersistenceMock() {
    super();
  }

  //</editor-fold>

}
