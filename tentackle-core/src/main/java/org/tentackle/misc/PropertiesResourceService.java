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

package org.tentackle.misc;

import org.tentackle.common.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for {@link PropertiesResource}s.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service(PropertiesResource.class)
public @interface PropertiesResourceService {

  /**
   * The execution priority.<br>
   * {@link PropertiesResource}s with lower priorities are executed first,
   * so that higher priority resources override the lower ones.<br>
   * A value of 0 (default) assigns a priority in steps of 100 according
   * to the discovery order of the {@link org.tentackle.common.ServiceFactory}.
   * For example, if 3 were found, the first gets the priority 300,
   * the next 200 and the last 100. Resources with the same priority are
   * executed in the order of their class names.
   *
   * @return the priority, default is 0
   */
  int priority() default 0;

  /**
   * Stops the execution of higher priority loaders if this loader found a resource.<br>
   * Notice that, despite its name, loading stops if the resource <em>was found</em>.
   *
   * @return true to stop loading, false is default
   */
  boolean stopIfNotFound() default false;

}
