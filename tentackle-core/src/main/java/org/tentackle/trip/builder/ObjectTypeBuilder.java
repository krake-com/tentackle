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

package org.tentackle.trip.builder;

import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeBuilder;
import org.tentackle.trip.TripViaJOS;
import org.tentackle.trip.types.JOSTripType;
import org.tentackle.trip.types.LambdaTripType;
import org.tentackle.trip.types.ObjectTripType;

/**
 * Fallback builder for all other object types.<br>
 * Creates a {@link JOSTripType} for classes annotated with &#64;{@link TripViaJOS}, a {@link LambdaTripType}
 * for lambdas, and an {@link ObjectTripType} otherwise.
 *
 * @param <T> the object type
 */
// no @TripTypeBuilderService(Object.class) since this is the fallback builder!
// With the annotation, no interfaces like Collection would be mapped, since
// there is a mapper for the topmost superclass: Object
public class ObjectTypeBuilder<T> implements TripTypeBuilder<T> {

  /**
   * Creates the type builder.
   */
  public ObjectTypeBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings("unchecked")
  public TripType<T> build(Class<T> clazz) {
    if (isSerializableViaJOS(clazz)) {
      return (TripType<T>) new JOSTripType();
    }
    if (isLambda(clazz)) {
      return (TripType<T>) new LambdaTripType();
    }
    return new ObjectTripType<>(clazz);
  }

  /**
   * Returns whether the given class must be serialized via JOS.
   *
   * @param clazz the class to check
   * @return true if the class must be serialized via JOS
   */
  protected boolean isSerializableViaJOS(Class<?> clazz) {
    return clazz.isAnnotationPresent(TripViaJOS.class);
  }

  /**
   * Returns whether the given class is the class of a lambda.
   *
   * @param clazz the class to check
   * @return true if lambda
   */
  protected boolean isLambda(Class<?> clazz) {
    return clazz.isSynthetic() && clazz.getName().indexOf('/') >= 0;
  }

}
