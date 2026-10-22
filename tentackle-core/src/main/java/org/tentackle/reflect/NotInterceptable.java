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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that the framework itself invokes directly, bypassing the interceptor
 * chain entirely.
 * <p>
 * Some methods of an {@link Interceptable} are special-cased by the framework's own
 * invocation handler and never reach {@link InterceptionUtilities}/{@link InterceptableMethod}
 * (e.g. {@code PersistentDomainObject.getPersistenceDelegate()}). Applying an interception
 * annotation such as {@code @Transaction} to such a method — or to an override of it —
 * would therefore silently never fire. Since these methods are legitimately overridden by
 * applications (e.g. for debugging or simplification), they cannot simply be declared
 * {@code final} to prevent this.
 * <p>
 * Annotation processors verify at compile-time that no interception annotation is ever
 * applied to a method marked {@code @NotInterceptable}, nor to any override of it, turning
 * an otherwise silent no-op into a build error.
 *
 * @author harald
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotInterceptable {
}
