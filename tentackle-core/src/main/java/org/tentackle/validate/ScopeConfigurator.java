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
 * A configurator for default scopes.
 * <p>
 * Classes implementing this interface specify the default scopes that
 * should be applied to all annotations that don't specify a scope (i.e. have the {@link DefaultScope}).
 * <p>
 * Annotations of objects not implementing {@link ScopeConfigurator} keep the {@link DefaultScope},
 * which is applied only if validating with the {@link org.tentackle.validate.scope.AllScope}.
 *
 * @author harald
 */
public interface ScopeConfigurator {

  /**
   * Gets the validation scopes to be used as the default for annotations
   * unless something different is defined.
   *
   * @return the default scopes
   */
  Class<? extends ValidationScope>[] getDefaultScopes();

}
