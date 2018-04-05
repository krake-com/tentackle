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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;


interface ValidatorCache$Singleton {
  ValidatorCache INSTANCE = ServiceFactory.createService(ValidatorCache.class, ValidatorCache.class);
}


/**
 * Holds all fixed validators for a class.
 * <p>
 * All validators bound by an annotation are considered fixed and can be cached.
 *
 * @author harald
 */
@Service(ValidatorCache.class)    // defaults to self
public class ValidatorCache {


  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ValidatorCache getInstance() {
    return ValidatorCache$Singleton.INSTANCE;
  }


  /**
   * Key to lookup by field and/or method
   */
  private static class FieldMethodKey {

    private final Field field;
    private final Method method;

    /**
     * Creates a combined field/method key.
     *
     * @param field the field, null if none
     * @param method the method, null if none
     */
    private FieldMethodKey(Field field, Method method) {
      this.field = field;
      this.method = method;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final FieldMethodKey other = (FieldMethodKey) obj;
      if (this.field != other.field && (this.field == null || !this.field.equals(other.field))) {
        return false;
      }
      return this.method == other.method || (this.method != null && this.method.equals(other.method));
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 29 * hash + (this.field != null ? this.field.hashCode() : 0);
      hash = 29 * hash + (this.method != null ? this.method.hashCode() : 0);
      return hash;
    }

  }





  /**
   * Map of field validators by clazz
   */
  private final Map<Class<?>,List<Validator>> fieldMap;

  /**
   * Map of field validators by field and/or method
   */
  private final Map<FieldMethodKey,List<Validator>> fieldMethodMap;

  /**
   * Map of object validators
   */
  private final Map<Class<?>,List<Validator>> objectMap;



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
   * the method gets preceedence.
   * @param clazz the class to inspect
   * @return the validators for this class and scope
   */
  public List<Validator> getFieldValidators(Class<?> clazz) {
    return fieldMap.computeIfAbsent(clazz,
            k -> ValidationUtilities.getInstance().getFieldValidators(clazz, null));
  }


  /**
   * Get validators for an annotated fields and/or method.<br>
   *
   * @param field the field, null if none
   * @param method the (getter-)method, null if none
   * @return the validators
   */
  public List<Validator> getFieldValidators(Field field, Method method) {
    FieldMethodKey key = new FieldMethodKey(field, method);
    return fieldMethodMap.computeIfAbsent(key,
            k -> ValidationUtilities.getInstance().getFieldValidators(field, method, null));
  }


  /**
   * Gets all validators for annotated class.
   *
   * @param clazz the class to inspect
   * @return the list of field validators
   */
  public List<Validator> getObjectValidators(Class<?> clazz) {
    return objectMap.computeIfAbsent(clazz,
            k -> ValidationUtilities.getInstance().getObjectValidators(clazz, null));
  }

}
