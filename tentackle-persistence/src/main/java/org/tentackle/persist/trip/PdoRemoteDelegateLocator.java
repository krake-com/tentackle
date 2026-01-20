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

package org.tentackle.persist.trip;

import org.tentackle.common.Service;
import org.tentackle.dbms.trip.DbRemoteDelegateLocator;
import org.tentackle.dbms.trip.RemoteDbDelegateLocator;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PdoUtilities;

/**
 * The default implementation for a {@link RemoteDbDelegateLocator}.
 *
 * @author harald
 */
@Service(RemoteDbDelegateLocator.class)
public class PdoRemoteDelegateLocator extends DbRemoteDelegateLocator {

  /**
   * Creates the remote delegate locator.
   */
  public PdoRemoteDelegateLocator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected Class<?> findEffectiveClass(Class<?> clazz) {
    Class<?> servicedClass = PdoUtilities.getInstance().determineServicedClass(clazz);
    return servicedClass != null ? servicedClass : super.findEffectiveClass(clazz);
  }

  @Override
  protected boolean isOperation(Class<?> clazz) {
    return Operation.class.isAssignableFrom(clazz) || super.isOperation(clazz);
  }

}
