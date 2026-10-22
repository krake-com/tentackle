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
 * Annotation to mark an annotation to be processed during the analyze phase,
 * i.e., by the analyze goal of the tentackle-maven-plugin.
 * <p>
 * These are usually annotations like @{@link RemoteMethod} that gather information
 * for subsequent wurblet invocations, or services like @{@link Service}
 * to be configured for the {@link ServiceFinder}.
 * The handler processing the annotation is given by {@link #value()}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Analyze {

  /**
   * The classname of the handler.<br>
   * Notice: we don't provide the class directly to avoid unnecessary runtime dependencies.
   *
   * @return the handler's classname
   */
  String value();

}
