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
 * Annotation to express that the annotated class
 * must be serialized via the old-school Java Object Serialization (JOS).
 * <p>
 * This is only the last resort if the class cannot be serialized via TRIP,
 * and it is not possible to create an application-specific {@link TripType},
 * define a {@link org.tentackle.reflect.CanonicalConstructor} or create
 * a dedicated {@link org.tentackle.trip.TripComponentProvider} or provide
 * a {@link TripInstanceCreator}.
 * <p>
 * If the class cannot be annotated (e.g., because it is 3rd party),
 * there is still the option to replace the {@link TripFactory} and override
 * {@code DefaultTripFactory#createType(Class)} and/or replace
 * {@link org.tentackle.trip.builder.ObjectTypeBuilder}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TripViaJOS {

}
