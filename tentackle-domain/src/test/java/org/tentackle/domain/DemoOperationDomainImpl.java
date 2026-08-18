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

import org.tentackle.pdo.DomainOperationService;

import java.io.Serial;

/**
 * Domain implementation of {@link DemoOperation}.
 */
@DomainOperationService(DemoOperation.class)
public class DemoOperationDomainImpl extends AbstractDomainOperation<DemoOperation, DemoOperationDomainImpl>
       implements DemoOperationDomain {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates the domain operation.
   *
   * @param operation the operation this is a delegate for
   */
  public DemoOperationDomainImpl(DemoOperation operation) {
    super(operation);
  }

  /**
   * Creates the domain operation.
   */
  public DemoOperationDomainImpl() {
    super();
  }

}
