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

import org.tentackle.trip.TripViaJOS;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@TripViaJOS
public class TestJOSClass implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public String name;
  @SuppressWarnings("serial")
  public Object someObject;

  public TestJOSClass() {
    name = "test";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    TestJOSClass that = (TestJOSClass) o;
    return Objects.equals(name, that.name) && Objects.equals(someObject, that.someObject);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(name);
    result = 31 * result + Objects.hashCode(someObject);
    return result;
  }
}
