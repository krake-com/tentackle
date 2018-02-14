/**
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


package org.tentackle.pdo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.tentackle.common.Constants;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.session.PersistenceException;

/**
 * Implementation for the invoke-in-buddy-delegate interception.
 *
 * @author harald
 */
public class InvokeInBuddyDelegateInterceptor extends AbstractInterceptor {


  private String methodName;   // the method name from the annotation


  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    methodName = ((InvokeInBuddyDelegate) annotation).value();
  }

  @Override
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
    Method buddyMethod = buddyDelegate.getClass().getDeclaredMethod(effectiveMethodName, method.getParameterTypes());
    try {
      return buddyMethod.invoke(buddyDelegate, args);
    }
    catch (IllegalAccessException ex) {
      buddyMethod.setAccessible(true);
      return buddyMethod.invoke(buddyDelegate, args);
    }
  }

}
