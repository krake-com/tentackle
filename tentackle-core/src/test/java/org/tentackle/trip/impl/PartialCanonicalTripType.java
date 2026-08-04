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

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.types.ObjectTripType;

import java.util.Set;

/**
 * Writes a {@link CanonicalDto} without some of its components.
 * <p>
 * Simulates a sender whose version of the class does not provide them, or one that omits them
 * because they are at their default value. Not registered as a service: it is used as the writer
 * only, while the reader remains the regular type of {@link CanonicalDto}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "unchecked", "rawtypes" })
public class PartialCanonicalTripType extends ObjectTripType<CanonicalDto> {

  private final Set<String> skipped;

  /**
   * Creates the type.
   *
   * @param skipped the names of the components not to write
   */
  public PartialCanonicalTripType(String... skipped) {
    super(CanonicalDto.class);
    this.skipped = Set.of(skipped);
  }

  @Override
  protected void writeComponents(CanonicalDto value, TripStream stream) {
    int lastOrdinal = -1;
    for (TripComponent<CanonicalDto, ?> component : getComponents()) {
      if (skipped.contains(component.getName())) {
        continue;   // as if the sender did not provide this component at all
      }
      lastOrdinal = stream.writeNextComponentOrdinal(lastOrdinal, component);
      Object componentValue = component.getValue(value);
      if (componentValue == null) {
        sendNull(stream);
      }
      else {
        TripType componentType = component.getType();
        if (!componentType.isFinal()) {
          componentType = stream.getType(componentValue.getClass());
        }
        componentType.write(componentValue, stream);
      }
    }
    sendEnd(stream);
  }

}
