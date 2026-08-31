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
import org.tentackle.common.StringHelper;
import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.scope.PersistenceScope;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * Default implementation of a validation scope factory.
 *
 * @author harald
 */
@Service(ValidationScopeFactory.class)
public class DefaultValidationScopeFactory implements ValidationScopeFactory {



  private final Map<String,Class<? extends ValidationScope>> scopesByName;                // scope interfaces by name
  private final Map<Class<? extends ValidationScope>,ValidationScope> scopesByIface;      // scope implementations by interface

  // scopes essential to the framework
  private AllScope allScope;
  private ChangeableScope changeableScope;
  private InteractiveScope interactiveScope;
  private MandatoryScope mandatoryScope;
  private PersistenceScope persistenceScope;


  /**
   * Creates the factory.
   */
  public DefaultValidationScopeFactory() {
    scopesByName = new HashMap<>();
    scopesByIface = new HashMap<>();
    try {
      Map<String,String> scopeMap = ServiceFactory.getServiceFinder().createNameMap(ValidationScopeService.class.getName());
      for (Map.Entry<String,String> entry: scopeMap.entrySet()) {
        @SuppressWarnings("unchecked")
        Class<ValidationScope> scopeInterface = (Class<ValidationScope>) Class.forName(entry.getKey());
        @SuppressWarnings("unchecked")
        Class<ValidationScope> scopeImplementation = (Class<ValidationScope>) Class.forName(entry.getValue());
        if (!scopeInterface.isAssignableFrom(scopeImplementation)) {
          throw new ValidationRuntimeException("scope implementation " + scopeImplementation.getName() +
                                               " does not implement " + scopeInterface.getName());
        }
        ValidationScope scope = scopeImplementation.getDeclaredConstructor().newInstance();
        String name = scope.getName();
        if (!StringHelper.isValidJavaIdentifier(name)) {
          throw new ValidationRuntimeException("invalid name '" + name + "' in " + scopeImplementation.getName());
        }
        Class<? extends ValidationScope> oldValidationScope = scopesByName.put(name, scopeInterface);
        if (oldValidationScope != null) {
          throw new ValidationRuntimeException("scope name '" + name + "' of " + scopeImplementation.getName() +
                                               " already used by " + oldValidationScope.getName());
        }
        scopesByIface.put(scopeInterface, scope);

        if (scope instanceof AllScope) {
          allScope = (AllScope) scope;
        }
        else if (scope instanceof ChangeableScope) {
          changeableScope = (ChangeableScope) scope;
        }
        else if (scope instanceof InteractiveScope) {
          interactiveScope = (InteractiveScope) scope;
        }
        else if (scope instanceof MandatoryScope) {
          mandatoryScope = (MandatoryScope) scope;
        }
        else if (scope instanceof PersistenceScope) {
          persistenceScope = (PersistenceScope) scope;
        }
      }

      // check that all essential scopes were found
      if (allScope == null) {
        throw new ValidationRuntimeException("no AllScope configured");
      }
      if (changeableScope == null) {
        throw new ValidationRuntimeException("no ChangeableScope configured");
      }
      if (interactiveScope == null) {
        throw new ValidationRuntimeException("no InteractiveScope configured");
      }
      if (mandatoryScope == null) {
        throw new ValidationRuntimeException("no MandatoryScope configured");
      }
      if (persistenceScope == null) {
        throw new ValidationRuntimeException("no PersistenceScope configured");
      }
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
           NoSuchMethodException | InvocationTargetException | ValidationRuntimeException nfe) {
      throw new ValidationRuntimeException("supported scopes could not be determined", nfe);
    }
  }


  @Override
  public Collection<Class<? extends ValidationScope>> getValidationScopes() {
    return scopesByName.values();
  }

  @Override
  public Class<? extends ValidationScope> getValidationScope(String name) {
    return scopesByName.get(name);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends ValidationScope> T getValidationScope(Class<T> iFace) {
    return (T) scopesByIface.get(iFace);
  }

  @Override
  public AllScope getAllScope() {
    return allScope;
  }

  @Override
  public ChangeableScope getChangeableScope() {
    return changeableScope;
  }

  @Override
  public InteractiveScope getInteractiveScope() {
    return interactiveScope;
  }

  @Override
  public MandatoryScope getMandatoryScope() {
    return mandatoryScope;
  }

  @Override
  public PersistenceScope getPersistenceScope() {
    return persistenceScope;
  }

}
