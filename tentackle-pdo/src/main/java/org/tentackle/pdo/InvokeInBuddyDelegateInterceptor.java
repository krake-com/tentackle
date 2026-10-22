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

package org.tentackle.pdo;

import org.tentackle.common.Constants;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.session.PersistenceException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Implementation for the {@link InvokeInBuddyDelegate} interception.
 *
 * @author harald
 */
public final class InvokeInBuddyDelegateInterceptor extends AbstractInterceptor {

  private String methodName;   // the method name from the annotation


  /**
   * Creates a {@link InvokeInBuddyDelegate} interceptor.
   */
  public InvokeInBuddyDelegateInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    methodName = ((InvokeInBuddyDelegate) annotation).value();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Object proceed(Object delegate, Method method, Object[] args,
                        Object orgDelegate, Method orgMethod, Object[] orgArgs) throws Throwable {

    if (method != orgMethod) {
      throw new PdoRuntimeException("@InvokeInBuddyDelegate must be the last interceptor for " + orgMethod);
    }
    Object buddyDelegate;
    if (delegate instanceof DomainDelegate) {
      buddyDelegate = ((DomainDelegate) delegate).getPersistenceDelegate();
    }
    else if (delegate instanceof PersistenceDelegate) {
      buddyDelegate = ((PersistenceDelegate) delegate).getDomainDelegate();
    }
    else  {
      throw new PersistenceException(delegate.getClass() + " is neither a domain- nor a persistence-delegate");
    }

    String effectiveMethodName = methodName.equals(Constants.NAME_UNKNOWN) ? method.getName() : methodName;
    Method buddyMethod = findMethod(buddyDelegate.getClass(), effectiveMethodName, method.getParameterTypes());
    try {
      return buddyMethod.invoke(buddyDelegate, args);
    }
    catch (IllegalAccessException ex) {
      buddyMethod.setAccessible(true);
      return buddyMethod.invoke(buddyDelegate, args);
    }
  }

  /**
   * Finds a method declared by the given class or one of its superclasses.<br>
   * Non-public methods are found as well.
   *
   * @param clazz the class
   * @param name the method name
   * @param parameterTypes the parameter types
   * @return the method
   * @throws NoSuchMethodException if no such method
   */
  private static Method findMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
    for (Class<?> cls = clazz; cls != null; cls = cls.getSuperclass()) {
      try {
        return cls.getDeclaredMethod(name, parameterTypes);
      }
      catch (NoSuchMethodException nx) {
        // try superclass
      }
    }
    throw new NoSuchMethodException(clazz.getName() + "." + name + Arrays.toString(parameterTypes));
  }

}
