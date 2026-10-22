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
 * Annotation to express that the annotated class corresponds to a resource bundle.
 * <p>
 * The bundle's base name is the name of the annotated class, i.e., the {@code .properties} files
 * reside in the same package as the class (usually in {@code src/main/resources}).
 * The analyze goal of the tentackle-maven-plugin registers the bundle in
 * {@code META-INF/bundles/org.tentackle.common.Bundle}, so that the {@link BundleFactory}
 * can load it from the module it belongs to (see {@link BundleSupport}).
 *
 * @author harald
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.BundleAnalyzeHandler")
public @interface Bundle {

  /**
   * The optional key name.<br>
   * If given, the bundle of the annotated class can also be loaded via this name.
   * The registration then reads {@code <value> = <annotated class>}.
   * Empty string to register the bundle under the name of the annotated class only.
   *
   * @return the key name, empty if none
   */
  String value() default "";

}
