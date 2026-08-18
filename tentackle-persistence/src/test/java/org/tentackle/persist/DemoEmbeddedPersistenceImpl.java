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
 * Persistence implementation of {@link DemoEmbedded}.
 * <p>
 * Provides no class variables: an embedded PDO uses the ones of the PDO it is embedded in,
 * which is what {@link AbstractPersistentObject#getClassVariables()} does for embedded PDOs.
 */
public class DemoEmbeddedPersistenceImpl extends AbstractPersistentObject<DemoEmbedded, DemoEmbeddedPersistenceImpl> {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an embedded PDO.
   */
  public DemoEmbeddedPersistenceImpl() {
    super();
  }

  @Override
  public boolean isEmbedded() {
    return true;
  }

}
