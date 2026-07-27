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
import org.tentackle.pdo.PersistentObjectService;
import org.tentackle.pdo.mock.MockPersistentObject;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * Persistence mock for {@link DemoGlass}.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoGlass.class)
public class DemoGlassPersistenceMock extends MockPersistentObject<DemoGlass, DemoGlassPersistenceMock>
       implements DemoGlassPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  //<editor-fold defaultstate="collapsed" desc="constructors">

  /**
   * Creates a PDO.
   *
   * @param pdo the PDO
   * @param context the domain context
   */
  public DemoGlassPersistenceMock (DemoGlass pdo, DomainContext context)    {
    super(pdo, context);
  }

  /**
   * Creates a PDO with a session only.
   *
   * @param pdo the PDO
   * @param session the session
   */
  public DemoGlassPersistenceMock(DemoGlass pdo, Session session) {
    super(pdo, session);
  }

  /**
   * Creates a pdo without domain context or session.
   *
   * @param pdo the number space PDO
   */
  public DemoGlassPersistenceMock(DemoGlass pdo) {
    super(pdo);
  }

  /**
   * Creates a PDO without domain context or session.
   */
  public DemoGlassPersistenceMock() {
    super();
  }

  //</editor-fold>

}
