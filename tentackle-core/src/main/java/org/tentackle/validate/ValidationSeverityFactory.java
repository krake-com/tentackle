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

import java.util.Collection;


interface ValidationSeverityFactoryHolder {
  ValidationSeverityFactory INSTANCE = ServiceFactory.createService(
    ValidationSeverityFactory.class, DefaultValidationSeverityFactory.class);
}


/**
 * Factory for validation severity singletons.
 * <p>
 * The factory provides the predefined severities necessary for the framework
 * but creates singletons for any other application-specific severities as well.
 * Severity implementations are annotated with &#64;{@link ValidationSeverityService}.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 *
 * @author harald
 */
public interface ValidationSeverityFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ValidationSeverityFactory getInstance() {
    return ValidationSeverityFactoryHolder.INSTANCE;
  }


  /**
   * Gets all configured validation severities.
   *
   * @return the validation severities
   */
  Collection<ValidationSeverity> getValidationSeverities();


  /**
   * Gets a validation severity implementation singleton by its interface.
   *
   * @param <T> the interface type
   * @param iFace the interface
   * @return the validation severity, null if no such validation severity configured
   */
  <T extends ValidationSeverity> T getValidationSeverity(Class<T> iFace);

}
