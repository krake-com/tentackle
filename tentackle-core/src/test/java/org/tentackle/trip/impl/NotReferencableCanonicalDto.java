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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;
import org.tentackle.trip.TripReferencable;

import java.util.Objects;

/**
 * A non-referencable class created by its canonical constructor.<br>
 * Without a reference, the first component directly follows the object's header.
 */
@TripReferencable(TripReferencable.NEVER)
public class NotReferencableCanonicalDto {

  private final String name;
  private final int age;

  @CanonicalConstructor
  public NotReferencableCanonicalDto(@CanonicalName("name") String name, @CanonicalName("age") int age) {
    this.name = name;
    this.age = age;
  }

  public String getName() {
    return name;
  }

  public int getAge() {
    return age;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    NotReferencableCanonicalDto that = (NotReferencableCanonicalDto) o;
    return age == that.age && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hashCode(name) + age;
  }

  @Override
  public String toString() {
    return "NotReferencableCanonicalDto[" + name + ", " + age + "]";
  }

}
