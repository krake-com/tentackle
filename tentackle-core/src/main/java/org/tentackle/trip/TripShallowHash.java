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

package org.tentackle.trip;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the {@code hashCode} and {@code equals} methods of the annotated class do not
 * depend on the objects its instances refer to.
 * <p>
 * The hash guard of TRIP (see {@link TripStream#enterHashed()}) treats such an object as a leaf:
 * <ul>
 *   <li>it may be back-referenced within a hashed value, such as an element of a hash set or the key of a hash map,</li>
 *   <li>its contents are not part of the hashed value, i.e., they may back-reference any object received before.</li>
 * </ul>
 * Hash sets and maps within its contents are still guarded on their own.
 * <p>
 * Annotated at an {@link java.lang.reflect.InvocationHandler}, it applies to the dynamic proxies
 * the handler is responsible for, since the handler decides how they compute their hash code.
 * <p>
 * <strong>Caution:</strong> the contract is security-relevant. If {@code hashCode} or {@code equals} traverse
 * the referenced objects nevertheless, a few kilobytes of containers sharing each other make computing the hash
 * code take forever. Hence, the methods should be final, and the annotation must not be applied to
 * classes whose subclasses may override them.
 * <p>
 * This annotation is inherited by subclasses.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripShallowHash {
}
