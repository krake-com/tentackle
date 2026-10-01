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
 * Annotation for manually written methods that must be remoting-capable.
 * <p>
 * The result of the {@code RemoteMethodAnalyzeHandler} is picked up by the <code>RemoteMethod</code> wurblet,
 * which generates the remote delegate methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Analyze("org.tentackle.buildsupport.RemoteMethodAnalyzeHandler")
public @interface RemoteMethod {

  /**
   * Placeholder denoting the actual method name.<br>
   * This is the default for {@link #value()}.
   */
  String DEFAULT_METHODNAME = "[methodname]";


  /**
   * If methods with the same name exist in the same class, the wurblet
   * can identify it by a special tag. By default, the tag is the method's name.
   *
   * @return the tag
   */
  String value() default DEFAULT_METHODNAME;

}
