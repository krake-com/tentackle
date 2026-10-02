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

package org.tentackle.security;

import org.tentackle.reflect.Interception;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to secure a method.
 * <p>
 * Methods of the PDO- or Operation-interfaces annotated
 * with {@code @Secured} will be checked by the {@link SecurityManager}.
 * <p>
 * If the permissions are not granted, a {@link SecurityException} is thrown.
 * If the annotated object is not a {@link org.tentackle.pdo.DomainContextProvider},
 * a {@link org.tentackle.session.PersistenceException} is thrown.
 *
 * @author harald
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=SecuredInterceptor.class, type = Interception.Type.PUBLIC)
public @interface Secured {

  /**
   * The permissions that must be accepted within the object's domain context.
   *
   * @return the permissions
   */
  Class<? extends Permission>[] value();

  /**
   * Enforces explicit acceptance.
   *
   * @see SecurityResult#isDefault()
   * @return true if permissions must be explicitly accepted, default is false, i.e., none must be denied
   */
  boolean explicit() default false;

  /**
   * Determines whether multiple permissions should be ORed.
   * <p>
   * If true, at least one permission must be accepted.<br>
   * If false (default), all permissions must be accepted.
   *
   * @return true if ored, false if logically ANDed (default)
   */
  boolean ored() default false;

}
