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
 * Annotation to override a service on the module path from the classpath.
 * <p>
 * By default, the {@link ModuleSorter} organizes resources for the <em>same</em> service name
 * according to the module dependency hierarchy first, followed by the automatic modules and classpath.
 * This is the desired behavior in most cases, since it allows modules to override or implement services
 * that are declared or already implemented by other modules or by traditional (non-modular) artifacts.<br>
 * This would mean, however, that a non-modularized dependency could never override a service from a real module.
 * Think of Maven artifacts that in turn depend on some 3rd party dependency that isn't modularized yet.<br>
 * In such cases, this annotation causes the classpath to take precedence over the module path
 * for the service given by {@link #value()}.
 * <p>
 * The annotation is a meta-service: the analyze goal of the tentackle-maven-plugin appends the serviced class to
 * {@code META-INF/meta-services/org.tentackle.common.ClasspathFirst}, which is read by the {@link ModuleSorter}.
 * <p>
 * For an example, see {@code PoiTableUtilities} in {@code tentackle-fx-rdc-poi}.
 *
 * @author harald
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service(value = ClasspathFirst.class, meta = true)
public @interface ClasspathFirst {

  /**
   * Gets the serviced class to be overridden from the classpath.
   *
   * @return the serviced class
   */
  Class<?> value();

}
