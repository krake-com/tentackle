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

import org.tentackle.common.Analyze;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to mark another annotation as an interception.
 * <p>
 * There are 4 different ways to assign the implementation:
 * <ol>
 *   <li>by class ({@code implementedBy=...}): direct reference to the implementing class.</li>
 *   <li>by class name ({@code implementedByName=...}): the name of the implementing class.
 *   Avoids the dependency to the class, but may be prone to typos.</li>
 *   <li>by service name ({@code implementedByService=...}):
 *   the implementing class must be annotated with &#64;{@link org.tentackle.common.ServiceName} and
 *   both names must match. Again, this may be prone to typos, but avoids dependency.</li>
 *   <li>by service class: if none of the above is given, the implementation must be annotated with
 *   &#64;{@link org.tentackle.common.Service} referencing the interception annotation.</li>
 * </ol>
 * The options are tried in the order above.
 * <p>
 * Interceptors declared in an interface (public) are invoked before those declared in the implementation (hidden).
 * Within each group, the order follows the order of the annotations.
 *
 * <p>
 * The optional {@code type} parameter defines the visibility of the interception:
 * <ul>
 *   <li>ALL: no restrictions. This is the default.</li>
 *   <li>PUBLIC: interface only, i.e. part of the model.</li>
 *   <li>HIDDEN: classes only, i.e. not visible in the model interfaces.</li>
 * </ul>
 *
 * <p>
 * Annotation processors verify at compile-time, that the interceptors
 * are applied to the proper methods and/or classes according to the {@code type} parameter:
 * <ul>
 * <li>{@code AllInterceptorAnnotationProcessor}:
 * verifies that the annotated method belongs to an {@code Interceptable} such as
 * {@code DomainObject}, {@code PersistentObject} or some of their implementations.</li>
 * <li>{@code PublicInterceptorAnnotationProcessor}: same as above, but only for declared methods in interfaces.</li>
 * <li>{@code HiddenInterceptorAnnotationProcessor}: same as above, but only for implementing methods.</li>
 * </ul>
 *
 * @author harald
 */
@Documented
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Analyze("org.tentackle.buildsupport.InterceptionAnalyzeHandler")
public @interface Interception {

  /** the interceptor type. */
  enum Type {

    /** public interceptors only. */
    PUBLIC,

    /** hidden interceptors only. */
    HIDDEN,

    /** public or hidden. */
    ALL
  }

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
   * Defines the interceptor implementation by a service name.<br>
   * Avoids the concrete dependency.
   *
   * @return the interceptor implementing the interception.
   */
  String implementedByService() default "";

  /**
   * Defines the interceptor type.
   *
   * @return PUBLIC, HIDDEN or ALL (default)
   */
  Type type() default Type.ALL;

}
