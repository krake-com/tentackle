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

import org.tentackle.common.ServiceFactory;
import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.scope.PersistenceScope;

import java.util.Collection;


interface ValidationScopeFactoryHolder {
  ValidationScopeFactory INSTANCE = ServiceFactory.createService(
    ValidationScopeFactory.class, DefaultValidationScopeFactory.class);
}


/**
 * Factory for validation scope singletons.
 * <p>
 * The factory provides methods for the predefined scopes necessary for the framework
 * but creates singletons for any other application-specific scopes as well.
 * Scope implementations are annotated with &#64;{@link ValidationScopeService}.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 *
 * @author harald
 */
public interface ValidationScopeFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ValidationScopeFactory getInstance() {
    return ValidationScopeFactoryHolder.INSTANCE;
  }



  /**
   * Gets the AllScope singleton.
   *
   * @return the all scope
   */
  AllScope getAllScope();

  /**
   * Gets the ChangeableScope singleton.
   *
   * @return the validation scope
   */
  ChangeableScope getChangeableScope();

  /**
   * Gets the InteractiveScope singleton.
   *
   * @return the validation scope
   */
  InteractiveScope getInteractiveScope();

  /**
   * Gets the MandatoryScope singleton.
   *
   * @return the validation scope
   */
  MandatoryScope getMandatoryScope();

   /**
   * Gets the PersistenceScope singleton.
   *
   * @return the validation scope
   */
  PersistenceScope getPersistenceScope();

  /**
   * Gets all configured validation scopes.
   *
   * @return the validation scopes
   */
  Collection<Class<? extends ValidationScope>> getValidationScopes();

  /**
   * Gets a validation scope interface by its name.
   *
   * @param name the unique name
   * @return the validation scope, null if no such validation scope configured
   */
  Class<? extends ValidationScope> getValidationScope(String name);

  /**
   * Gets a validation scope implementation singleton by its interface.
   *
   * @param <T> the interface type
   * @param iFace the interface
   * @return the validation scope, null if no such validation scope configured
   */
  <T extends ValidationScope> T getValidationScope(Class<T> iFace);

}
