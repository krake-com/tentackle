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

/**
 * Persistence implementation of {@link DemoBase}.
 * <p>
 * The topmost entity of the single-table inheritance hierarchy: it provides the table and the alias.
 */
public class DemoBasePersistenceImpl extends AbstractPersistentObject<DemoBase, DemoBasePersistenceImpl> {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Variables common to all instances of DemoBasePersistenceImpl. */
  public static final PersistentObjectClassVariables<DemoBase, DemoBasePersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoBase.class, DemoBasePersistenceImpl.class, "dbs");

  /**
   * Creates a demo base entity.
   */
  public DemoBasePersistenceImpl() {
    super();
  }

  @Override
  public PersistentObjectClassVariables<DemoBase, DemoBasePersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

}
