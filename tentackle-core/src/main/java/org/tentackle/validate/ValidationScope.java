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


package org.tentackle.validate;

/**
 * A validation scope.
 * <p>
 * Validators are configured with the scopes they apply to. The validation is run with an effective scope,
 * and only the validators whose configured scopes match the effective scope are applied.
 * Scopes are singletons implementing an interface extending {@code ValidationScope}.
 *
 * @author harald
 */
public interface ValidationScope {

  /**
   * Checks if this scope falls within at least one of the given scopes.
   *
   * @param scopes the scopes to check
   * @return true if applies
   */
  boolean appliesTo(Class<?>... scopes);

  /**
   * Gets the name of this scope.
   *
   * @return the name
   */
  String getName();

}
