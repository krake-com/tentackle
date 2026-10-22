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

import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.TripType;

import java.util.Objects;

/**
 * A component of a dictionary entry.
 */
public final class DefaultDictionaryEntryComponent implements DictionaryEntryComponent {

  private final String componentName;
  private final TripType<?> type;

  /**
   * Creates a dictionary entry component.
   *
   * @param componentName the name of the component
   * @param type          the compile-time type of the component
   * @throws NullPointerException if componentName or type is null
   */
  public DefaultDictionaryEntryComponent(String componentName, TripType<?> type) {
    this.componentName = Objects.requireNonNull(componentName);
    this.type = Objects.requireNonNull(type);
  }

  @Override
  public String toString() {
    return type + "#" + componentName;
  }

  @Override
  public String getComponentName() {
    return componentName;
  }

  @Override
  public TripType<?> getType() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (DefaultDictionaryEntryComponent) obj;
    return Objects.equals(this.componentName, that.componentName) &&
           Objects.equals(this.type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentName, type);
  }


}
