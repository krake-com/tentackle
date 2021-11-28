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

import org.tentackle.misc.Identifiable;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implementation for the {@link NotWithinContext} interception.
 *
 * @author harald
 */
public class NotWithinContextInterceptor extends AbstractInterceptor {

  private String[] names;    // the context names

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    names = ((NotWithinContext) annotation).value();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args, Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    if (orgProxy instanceof DomainContextProvider dcp) {
      DomainContext context = dcp.getDomainContext();
      if (context != null) {
        for (String name : names) {
          if (context.isWithinContext(name)) {
            throw createException(orgProxy, "context '" + name + "' is within " + context.toDiagnosticString());
          }
        }
      }
      return method.invoke(proxy, args);
    }
    else {
      throw createException(orgProxy, EffectiveClassProvider.getEffectiveClass(orgProxy) + " is not a DomainContextProvider");
    }
  }

  private DomainException createException(Object proxy, String message) {
    return proxy instanceof Identifiable identifiable ? new DomainException(identifiable, message) : new DomainException(message);
  }
}
