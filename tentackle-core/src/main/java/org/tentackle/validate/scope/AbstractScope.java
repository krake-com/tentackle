/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.validate.scope;

import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;

/**
 * Base class for validation scope implementations.
 *
 * @author harald
 */
public abstract class AbstractScope implements ValidationScope {

  // the extracted permission interface
  private Class<?> iFace;

  /**
   * Gets the scope interface of this request scope.
   *
   * @return the interface
   */
  protected Class<?> getIFace() {
    if (iFace == null) {
      iFace = getClass().getInterfaces()[0];
    }
    return iFace;
  }

  @Override
  public boolean appliesTo(Class<?>... scopes) {
    for (Class<?> scope: scopes) {
      if (!scope.isInterface()) {
        throw new ValidationRuntimeException(scope + " is not an interface");
      }
      if (scope.isAssignableFrom(AllScope.class) ||
          scope.isAssignableFrom(getIFace())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return getName() + "-scope";
  }

}
