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


package org.tentackle.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to express that the annotated class is a service implementation.
 * <p>
 * Processed at build time by the {@code ServiceAnalyzeHandler} of the tentackle-maven-plugin's analyze goal,
 * which appends the annotated class to {@code META-INF/services/<serviced class>}.
 * The annotation can also be used as a meta-annotation, i.e., to annotate another annotation
 * (see {@link AnnotationProcessor} for an example).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.ServiceAnalyzeHandler")
public @interface Service {

  /**
   * Gets the serviced class.
   *
   * @return the serviced class
   */
  Class<?> value();

  /**
   * The name of the element that provides the value, if this annotation is used as a meta-annotation.<br>
   * If {@link #meta()} is false, the element of the annotated annotation provides the serviced class
   * (only if {@link #value()} is not given explicitly). If {@link #meta()} is true, it provides the servicing class.
   * The default is "value".
   *
   * @return the element name
   */
  String method() default "value";

  /**
   * Determines whether the servicing class is the annotated class or the value of the annotated annotation.<br>
   * Notice the difference between the serviced and servicing class!
   * <p>
   * Meta services go to META-INF/meta-services instead of META-INF/services because the
   * provider usually resides in another module.
   *
   * @return true if this is a meta annotation and the servicing class is provided by the annotated annotation
   */
  boolean meta() default false;

}

