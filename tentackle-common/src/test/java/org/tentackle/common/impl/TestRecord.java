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

package org.tentackle.common.impl;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public record TestRecord(String name, int age, Boolean male, TestRecord child, TestClass person,
                         Object someObject) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    TestRecord that = (TestRecord) o;
    if (someObject != null) {
      if (someObject.getClass().isArray()) {
        if (!Arrays.equals((Object[]) someObject, (Object[]) that.someObject)) return false;
      }
      else if (!someObject.equals(that.someObject)) return false;
    }
    return age == that.age && Objects.equals(name, that.name) && Objects.equals(male, that.male) &&
           Objects.equals(child, that.child) && Objects.equals(person, that.person);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(name);
    result = 31 * result + age;
    result = 31 * result + Objects.hashCode(male);
    result = 31 * result + Objects.hashCode(child);
    result = 31 * result + Objects.hashCode(person);
    result = 31 * result + Objects.hashCode(someObject);
    return result;
  }
}
