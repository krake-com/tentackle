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

package org.tentackle.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.PersistenceException;

/**
 * Interceptor to perform security checks.
 *
 * @author harald
 */
public class SecuredInterceptor extends AbstractInterceptor {

  private Class<? extends Permission>[] permissions;    // the permissions from the annotation
  private boolean explicit;                             // whether to check for explicit acceptance
  private boolean ored;                                 // true if or multiple permissions

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    Secured anno = (Secured) annotation;
    permissions = anno.value();
    explicit = anno.explicit();
    ored = anno.ored();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    if (orgProxy instanceof DomainContextProvider) {
      DomainContext context = ((DomainContextProvider) orgProxy).getDomainContext();
      int classId = 0;
      long id = 0;
      Class<?> effectiveClass;    // != null if Operation, null if Identifiable PDO
      if (orgProxy instanceof PersistentObject) {
        // pdo
        PersistentObject<?> po = (PersistentObject) orgProxy;
        classId = po.getClassId();
        id = po.getId();
        effectiveClass = null;
      }
      else {
        // operation
        effectiveClass = EffectiveClassProvider.getEffectiveClass(orgProxy);
      }

      // check permissions
      List<SecurityException> failedSx = ored ? new ArrayList<>() : null;   // failed permissions (ored mode only)
      boolean success = false;                                              // true if at least one accepted (ored mode only)

      for (Class<? extends Permission> clazz: permissions) {
        Permission permission = SecurityFactory.getInstance().getPermission(clazz);
        SecurityResult result = effectiveClass != null ?
                SecurityFactory.getInstance().getSecurityManager().evaluate(context, permission, effectiveClass) :
                SecurityFactory.getInstance().getSecurityManager().evaluate(context, permission, classId, id);
        if (!result.isAccepted()) {
          SecurityException sx = new SecurityException(
                  context.getSession(), result.explain("permission " + permission + " not accepted"));
          if (ored) {
            failedSx.add(sx);
          }
          else {
            throw sx;
          }
        }
        else if (explicit && result.isDefault()) {
          SecurityException sx = new SecurityException(
                  context.getSession(), result.explain("permission " + permission + " not explicitly accepted"));
          if (ored) {
            failedSx.add(sx);
          }
          else {
            throw sx;
          }
        }
        else {
          success = true;
        }
      }

      if (!success) {
        // all permissions failed: collect messages and throw exception
        StringBuilder buf = new StringBuilder();
        if (ored) {
          for (SecurityException sx : failedSx) {
            if (buf.length() > 0) {
              buf.append('\n');
            }
            buf.append(sx.getMessage());
          }
        }
        else {
          buf.append("no permissions in @Secured annotation");
        }
        throw new SecurityException(context.getSession(), buf.toString());
      }

      // accepted -> go
      return method.invoke(proxy, args);
    }
    else  {
      throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) +
                                     " is not a DomainContextProvider");
    }
  }
}
