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

import org.tentackle.common.BMoney;

import java.util.Objects;

public sealed class TestClass permits TestSubClass {

  public String name;
  public int age;
  public Boolean male = true;
  public TestClass child;
  public transient String transientField;
  public BMoney money;
  public TestEnum color;
  public TestRecord person;
  public Object someObject;

  public TestClass() {
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    TestClass testClass = (TestClass) o;
    return age == testClass.age && Objects.equals(name, testClass.name) && Objects.equals(male, testClass.male) && Objects.equals(child, testClass.child) && Objects.equals(money, testClass.money) && color == testClass.color && Objects.equals(person, testClass.person) && Objects.equals(someObject, testClass.someObject);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(name);
    result = 31 * result + age;
    result = 31 * result + Objects.hashCode(male);
    result = 31 * result + Objects.hashCode(child);
    result = 31 * result + Objects.hashCode(money);
    result = 31 * result + Objects.hashCode(color);
    result = 31 * result + Objects.hashCode(person);
    result = 31 * result + Objects.hashCode(someObject);
    return result;
  }
}
