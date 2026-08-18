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
import org.tentackle.trip.TripInstanceCreator;
import org.tentackle.trip.TripInstanceCreatorService;

import java.util.Map;

/**
 * Creates the object from its already deserialized component values.
 */
@TripInstanceCreatorService(ValuesInstantiatable.class)
@SuppressWarnings("missing-explicit-ctor")
public class ValuesInstanceCreator implements TripInstanceCreator<ValuesInstantiatable> {

  @Override
  public ValuesInstantiatable create(Class<ValuesInstantiatable> clazz) {
    return new ValuesInstantiatable(null, 0);
  }

  @Override
  public boolean isCreationFromValuesSupported(Class<ValuesInstantiatable> clazz) {
    return true;
  }

  @Override
  public ValuesInstantiatable create(Class<ValuesInstantiatable> clazz,
                                     Map<TripComponent<ValuesInstantiatable, ?>, Object> values) {
    String name = null;
    int age = 0;
    for (Map.Entry<TripComponent<ValuesInstantiatable, ?>, Object> entry : values.entrySet()) {
      switch (entry.getKey().getName()) {
        case "name" -> name = (String) entry.getValue();
        case "age" -> age = (Integer) entry.getValue();
        default -> throw new IllegalArgumentException("unexpected component " + entry.getKey());
      }
    }
    return new ValuesInstantiatable(name, age);
  }

}
