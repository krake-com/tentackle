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

import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionProvider;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Implementation of the {@link PostCommit} interception.
 */
public final class PostCommitInterceptor extends AbstractInterceptor {

  /**
   * Creates a {@link PostCommit} interceptor.
   */
  public PostCommitInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) {

    if (orgMethod.getReturnType() != Void.TYPE) {
      throw new PersistenceException("method '" + orgMethod + "' does not return void, but " + orgMethod.getReturnType().getName());
    }

    if (orgProxy instanceof SessionProvider sessionProvider) {
      sessionProvider.getSession().postCommit(s -> {
        try {
          method.invoke(proxy, args);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          throw new PersistenceException(s, "post-commit failed", e);
        }
      });
      return null;
    }
    else {
      throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) + " is not a SessionProvider");
    }
  }

}
