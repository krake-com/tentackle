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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


interface ValidatorCacheHolder {
  ValidatorCache INSTANCE = ServiceFactory.createService(ValidatorCache.class, ValidatorCache.class);
}


/**
 * Holds all fixed validators cached for all classes.
 * <p>
 * All validators bound by an annotation are considered fixed and can be cached.
 * <p>
 * Replaceable via &#64;{@link Service}.
 *
 * @author harald
 */
@Service(ValidatorCache.class)    // defaults to self
public class ValidatorCache {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static ValidatorCache getInstance() {
    return ValidatorCacheHolder.INSTANCE;
  }


  /**
   * Key to lookup by field and/or method
   *
   * @param field  the field, null if none
   * @param method the method, null if none
   */
  private record FieldMethodKey(Field field, Method method) {}

  /**
   * Map of field validators by clazz
   */
  private final Map<Class<?>, List<Validator>> fieldMap;

  /**
   * Map of field validators by field and/or method
   */
  private final Map<FieldMethodKey, List<Validator>> fieldMethodMap;

  /**
   * Map of object validators
   */
  private final Map<Class<?>, List<Validator>> objectMap;


  /**
   * Creates a validator cache.
   */
  public ValidatorCache() {
    fieldMap        = new ConcurrentHashMap<>();
    fieldMethodMap  = new ConcurrentHashMap<>();
    objectMap       = new ConcurrentHashMap<>();
  }

  /**
   * Invalidates the cache.
   */
  public void invalidate() {
    fieldMap.clear();
    fieldMethodMap.clear();
    objectMap.clear();
  }

  /**
   * Gets all validators for all annotated fields or methods.<br>
   * If an attribute is annotated both at the field and the (getter-)method level,
   * the validators of both are applied.
   *
   * @param clazz the class to inspect
   * @return the validators for this class
   * @see ValidationUtilities#getFieldValidators(Class, ValidationScope)
   */
  public List<Validator> getFieldValidators(Class<?> clazz) {
    return fieldMap.computeIfAbsent(clazz,
            c -> ValidationUtilities.getInstance().getFieldValidators(c, null));
  }

  /**
   * Gets the validators for an annotated field and/or method.
   *
   * @param field the field, null if none
   * @param method the (getter-)method, null if none
   * @return the validators
   */
  public List<Validator> getFieldValidators(Field field, Method method) {
    FieldMethodKey key = new FieldMethodKey(field, method);
    return fieldMethodMap.computeIfAbsent(key,
            k -> ValidationUtilities.getInstance().getFieldValidators(k.field, k.method, null));
  }

  /**
   * Gets all validators for an annotated class.
   *
   * @param clazz the class to inspect
   * @return the list of object validators
   */
  public List<Validator> getObjectValidators(Class<?> clazz) {
    return objectMap.computeIfAbsent(clazz,
            c -> ValidationUtilities.getInstance().getObjectValidators(c, null));
  }

}
