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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to express that serializing lambdas is enabled for the annotated class.
 * <p>
 * By default, any attempt to serialize a lambda will result in a {@link TripRuntimeException}.
 * With that annotation at the so-called capturing class, serialization is enabled.
 * Static method references, however, will always be serialized and don't need this annotation.
 * <p>
 * The reason is the same as for non-static inner classes since the serialized data involves
 * a copy of the encapsulating object with all its references, which is probably not what the developer
 * expects.
 * <p>
 * Side note: in Java it's currently not possible to annotate a lambda expression directly,
 * so we have to annotate the capturing class and enable serialization for all lambdas in that class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripSerializeLambda {

}
