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

/**
 * Creates a {@link TripType} for a given concrete class.<br>
 * Type builders are used to create the concrete {@link TripType}s for all data types
 * without a predefined or application-specific type.<br>
 * Notice: <code>TripTypeBuilders</code> are singletons and must not maintain any state
 * to provide thread-safety!
 * <p>
 * Implementations must be annotated with {@link TripTypeBuilderService}.
 *
 * @param <T> the data type
 */
public interface TripTypeBuilder<T> {

  /**
   * Builds a trip type for a concrete class.
   *
   * @param clazz the class of the data type
   * @return the trip type
   */
  TripType<T> build(Class<T> clazz);

}
