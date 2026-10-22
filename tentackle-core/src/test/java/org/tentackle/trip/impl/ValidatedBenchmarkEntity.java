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

package org.tentackle.trip.impl;

import org.tentackle.trip.TripSerializable;

/**
 * A {@link BenchmarkEntity} with {@link TripSerializable} hooks.
 * <p>
 * Mirrors {@code AbstractDbObject}, whose validate method runs for every persistent object read,
 * so that the cost of invoking the hooks per object becomes visible.
 */
@TripSerializable(prepare = "prepareTripSerialization", validate = "validateTripSerialization")
public class ValidatedBenchmarkEntity extends BenchmarkEntity {

  private transient int prepared;
  private transient int validated;

  public ValidatedBenchmarkEntity() {
  }

  /**
   * Creates an entity filled with values derived from the given index.
   *
   * @param index the index within the generated batch
   * @param parent the parent entity, null if none
   */
  public ValidatedBenchmarkEntity(int index, BenchmarkEntity parent) {
    super(index, parent);
  }

  public void prepareTripSerialization() {
    prepared++;
  }

  public void validateTripSerialization() {
    validated++;
  }

  public int getPrepared() {
    return prepared;
  }

  public int getValidated() {
    return validated;
  }

}
