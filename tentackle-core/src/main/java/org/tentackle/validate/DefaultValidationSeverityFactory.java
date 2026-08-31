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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.validate.severity.DefaultSeverity;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of a validation severity factory.
 *
 * @author harald
 */
@Service(ValidationSeverityFactory.class)
public class DefaultValidationSeverityFactory implements ValidationSeverityFactory {

  private final Map<Class<? extends ValidationSeverity>, ValidationSeverity> severitiesByIface;      // implementations by interface
  private DefaultSeverity defaultSeverity;                                                           // to speed up and assert

  /**
   * Creates the factory.
   */
  public DefaultValidationSeverityFactory() {
    severitiesByIface = new HashMap<>();
    try {
      Map<String,String> severityMap = ServiceFactory.getServiceFinder().createNameMap(ValidationSeverityService.class.getName());
      for (Map.Entry<String,String> entry: severityMap.entrySet()) {
        @SuppressWarnings("unchecked")
        Class<ValidationSeverity> severityInterface = (Class<ValidationSeverity>) Class.forName(entry.getKey());
        @SuppressWarnings("unchecked")
        Class<ValidationSeverity> severityImplementation = (Class<ValidationSeverity>) Class.forName(entry.getValue());
        if (!severityInterface.isAssignableFrom(severityImplementation)) {
          throw new ValidationRuntimeException("severity implementation " + severityImplementation.getName() +
                                               " does not implement " + severityInterface.getName());
        }
        ValidationSeverity severity = severityImplementation.getDeclaredConstructor().newInstance();
        severitiesByIface.put(severityInterface, severity);
        if (severity instanceof DefaultSeverity) {
          defaultSeverity = (DefaultSeverity) severity;
        }
      }

      // check that all essential severities were found
      if (defaultSeverity == null) {
        throw new ValidationRuntimeException("no DefaultSeverity configured");
      }
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException | ValidationRuntimeException nfe) {
      throw new ValidationRuntimeException("supported severities could not be determined", nfe);
    }
  }


  @Override
  public Collection<ValidationSeverity> getValidationSeverities() {
    return severitiesByIface.values();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends ValidationSeverity> T getValidationSeverity(Class<T> iFace) {
    if (iFace == DefaultSeverity.class) {
      return (T) defaultSeverity;   // 99% case...
    }
    return (T) severitiesByIface.get(iFace);
  }

}
