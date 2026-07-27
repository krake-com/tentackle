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

import org.tentackle.pdo.PersistentObjectService;
import org.tentackle.pdo.mock.MockPersistentObject;

import java.io.Serial;

/**
 * Persistence mock for {@link DemoPdo}.
 * <p>
 * {@code isRootEntity} is settable because the defaults of {@link AbstractDomainObject}
 * behave differently for root entities and components.
 */
@SuppressWarnings("exports")
@PersistentObjectService(DemoPdo.class)
public class DemoPdoPersistenceMock extends MockPersistentObject<DemoPdo, DemoPdoPersistenceMock>
       implements DemoPdoPersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  private boolean rootEntity;

  /**
   * Creates a persistence mock for a PDO.
   *
   * @param pdo the persistent domain object
   */
  public DemoPdoPersistenceMock(DemoPdo pdo) {
    super(pdo);
  }

  /**
   * Creates a persistence mock.
   */
  public DemoPdoPersistenceMock() {
    super();
  }

  /**
   * Sets whether this mock pretends to be a root entity.
   *
   * @param rootEntity true if root entity, false if component
   */
  public void setRootEntity(boolean rootEntity) {
    this.rootEntity = rootEntity;
  }

  @Override
  public boolean isRootEntity() {
    return rootEntity;
  }

}
