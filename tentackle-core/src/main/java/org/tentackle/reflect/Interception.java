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


package org.tentackle.reflect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to mark another annotation as an interception.<br>
 * The implementation can be referenced either by class, classname or servicename.
 * By classname does not need the concrete dependency, which may be necessary to
 * avoid circular dependencies in multi-module maven projects.
 * By servicename goes even further, so that the actual name of the implementation
 * is unknown.
 * <p>
 * If no arguments are given, the implementation is found by the service-name which
 * is the classname of the annotated annotation.
 * <p>
 * Annotation processors should be implemented to verify at compile-time, that the interceptors
 * apply to the proper methods and/or classes. For example, the {@code PublicInterceptorAnnotationProcessor}
 * verifies that the annotated method belongs to an interface extending {@code Interceptable} such as
 * {@code DomainObject} or {@code PersistentObject}.
 * <br>
 * The {@code HiddenInterceptorAnnotationProcessor} checks that the methods belong real implementation classes
 * that implement {@code Interceptable}.
 * <br>
 * The {@code AllInterceptorAnnotationProcessor} checks that the interceptor is either public or hidden
 * on a class implementing or extending {@code Interceptable}.
 *
 * @author harald
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Interception {

  /**
   * Defines the interceptor implementation by class.
   *
   * @return the interceptor implementing the interception.
   */
  Class<? extends Interceptor> implementedBy() default Interceptor.class;

  /**
   * Defines the interceptor implementation by its classname.<br>
   * Avoids the concrete dependency.
   *
   * @return the interceptor implementing the interception.
   */
  String implementedByName() default "";

  /**
   * Defines the interceptor implementation by a servicename.<br>
   * Avoids the concrete dependency.
   *
   * @return the interceptor implementing the interception.
   */
  String implementedByService() default "";

}
