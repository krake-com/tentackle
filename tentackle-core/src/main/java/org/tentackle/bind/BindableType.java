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

package org.tentackle.bind;

import org.tentackle.common.Constants;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables automatic binding for all bindable members of an annotated class.
 * <p>
 * Useful for application-specific types that are not already handled
 * by the framework (such as PDOs, for example). Good candidates
 * are simple DTOs, where default views are ok.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BindableType {

  /**
   * set <code>resources=BindableType.RESOURCES_NONE</code> to disable resources.
   */
  String RESOURCES_NONE = Constants.NAME_NONE;

  /**
   * Ignore or include inherited bindable members.
   *
   * @return true if declared only (default), else include inherited as well
   */
  boolean declaredOnly() default true;

  /**
   * Gets the resources bundle.
   *
   * @return the bundle url, empty string if <code>&lt;annotated-classname&gt;.properties</code>, no resources if {@link #RESOURCES_NONE}
   */
  String resources() default "";

}
