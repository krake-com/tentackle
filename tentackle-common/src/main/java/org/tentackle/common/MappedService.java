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
 * Annotation to mark an annotation to be processed by the {@code MappedServiceAnalyzeHandler}.<br>
 * For each class annotated with the marked annotation, a line {@code <annotated class> = <value>}
 * is appended to {@code META-INF/mapped-services/<service class>} (see {@link Constants#MAPPED_SERVICE_PATH}).
 * The mappings are read via {@link ServiceFinder#createNameMap(String)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Analyze("org.tentackle.buildsupport.MappedServiceAnalyzeHandler")
public @interface MappedService {

  /**
   * The service class.
   *
   * @return the service class
   */
  Class<?> value();

  /**
   * The name of the element of the marked annotation that provides the mapped value.<br>
   * The default is "value".
   *
   * @return the element name
   */
  String method() default "value";

}
