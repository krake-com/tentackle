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

import org.tentackle.pdo.DomainObjectService;

import java.io.Serial;

/**
 * Domain implementation of {@link DemoGlass}.
 */
@DomainObjectService(DemoGlass.class)
public class DemoGlassDomainImpl extends AbstractDomainObject<DemoGlass, DemoGlassDomainImpl>
       implements DemoGlassDomain {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates the domain object.
   *
   * @param pdo the PDO this is a delegate for
   */
  public DemoGlassDomainImpl(DemoGlass pdo) {
    super(pdo);
  }

  /**
   * Creates the domain object.
   */
  public DemoGlassDomainImpl() {
    super();
  }

}
