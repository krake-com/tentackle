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


package org.tentackle.pdo;

import org.tentackle.common.Constants;
import org.tentackle.reflect.Interception;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * Annotation to redirect the method invocation to the buddy delegate.
 * <p>
 * If the annotated method is a domain method, the invocation will be redirected
 * to the method in the persistence delegate, and vice versa.
 * <p>
 * This is a hidden interceptor.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Interception(implementedBy=InvokeInBuddyDelegateInterceptor.class, type = Interception.Type.HIDDEN)
public @interface InvokeInBuddyDelegate {

  /**
   * The method name.<br>
   * If the name is missing, the buddy delegate's method has the same name as
   * the annotated one.
   *
   * @return the optional method name
   */
  String value() default Constants.NAME_UNKNOWN;

}

