/*
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



package org.tentackle.validate;

/**
 * The default validation scope.
 * <p>
 * Annotations for objects implementing {@link ScopeConfigurator} will get the scopes
 * defined by {@link ScopeConfigurator#getDefaultScopes()}.
 * <p>
 * Annotations of all other objects will get the {@link org.tentackle.validate.scope.AllScope}.
 * <p>
 * There is no implementation of the DefaultScope.
 * It's just used to map to the effective scopes.
 *
 * @author harald
 */
public interface DefaultScope extends ValidationScope {

}
