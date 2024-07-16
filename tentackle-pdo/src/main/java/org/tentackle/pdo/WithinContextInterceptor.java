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
 * Implementation for the {@link WithinContext} interception.
 *
 * @author harald
 */
public final class WithinContextInterceptor extends AbstractInterceptor {

  private String[] names;    // the context names

  /**
   * Creates a {@link WithinContext} interceptor.
   */
  public WithinContextInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    names = ((WithinContext) annotation).value();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args, Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    if (orgProxy instanceof DomainContextProvider dcp) {
      DomainContext context = dcp.getDomainContext();
      if (context == null) {
        return false;   // context not set yet (unit test, whatever)
      }
      for (String name : names) {
        if (!context.isWithinContext(name)) {
          throw createException(orgProxy, "context '" + name + "' is not within " + context.toDiagnosticString());
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
