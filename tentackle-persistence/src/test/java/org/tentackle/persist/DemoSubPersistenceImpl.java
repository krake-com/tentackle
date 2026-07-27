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

import java.io.Serial;
import java.util.List;

/**
 * Persistence implementation of {@link DemoSub}.
 * <p>
 * Neither table nor alias of its own: both are inherited from {@link DemoBasePersistenceImpl}
 * through the class variables. Because the rows of both entities live in the same table, the
 * selects must be restricted to the valid class IDs.
 */
public class DemoSubPersistenceImpl extends AbstractPersistentObject<DemoSub, DemoSubPersistenceImpl> {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoSubPersistenceImpl. */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static final PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoSub.class, DemoSubPersistenceImpl.class, null,
                                            (PersistentObjectClassVariables) DemoBasePersistenceImpl.CLASSVARIABLES,
                                            null);

  /**
   * Creates a demo sub entity.
   */
  public DemoSubPersistenceImpl() {
    super();
  }

  @Override
  public PersistentObjectClassVariables<DemoSub, DemoSubPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public List<Integer> getValidClassIds() {
    return List.of(9004);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Generated for entities flagged {@code tableserial} in the model. The table serial itself lives
   * in the table of the base entity.
   */
  @Override
  public boolean isTableSerialProvided() {
    return true;
  }

}
