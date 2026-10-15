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

import org.testng.annotations.Test;

import org.tentackle.common.ObjectSerializer;
import org.tentackle.common.Timestamp;
import org.tentackle.trip.impl.PackagePrivateConstructorClass;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TestSubClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class CopyTest {

  @Test
  void testCopy() {
    TestClass testObject = new TestClass();
    testObject.name = "test";
    testObject.age = 42;
    testObject.child = new TestSubClass(testObject, 100.0, new Timestamp());
    TestClass copy = ObjectSerializer.getInstance().copy(testObject);   // uses TRIP under the hood
    assertEquals(copy, testObject);
    assertNotSame(copy, testObject);
    assertNotSame(copy.child, testObject.child);
    assertSame(((TestSubClass) copy.child).parent, copy);

    // same as self-contained stream
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectSerializer.getInstance().serialize(testObject, bos);
    Object selfContCopy = ObjectSerializer.getInstance().deserialize(new ByteArrayInputStream(bos.toByteArray()));
    assertTrue(selfContCopy instanceof TestClass);
    copy = (TestClass) selfContCopy;
    assertEquals(copy, testObject);
    assertNotSame(copy, testObject);
    assertNotSame(copy.child, testObject.child);
    assertSame(((TestSubClass) copy.child).parent, copy);
  }

  @Test
  void testCopyWithPackagePrivateConstructor() {
    // the no-args constructor is not accessible from the trip types: it must be made accessible,
    // and since the constructor is cached, this must work for every object, not just the first one
    List<PackagePrivateConstructorClass> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add(new PackagePrivateConstructorClass("name" + i, i));
    }
    List<PackagePrivateConstructorClass> copy = ObjectSerializer.getInstance().copy(list);
    assertEquals(copy, list);
    for (int i = 0; i < list.size(); i++) {
      assertNotSame(copy.get(i), list.get(i));
    }
    // again, with the constructor already resolved
    assertEquals(ObjectSerializer.getInstance().copy(list), list);
  }

  @Test
  void testArrayCopy() {
    String[] testArray = new String[]{"test1", "test2"};
    String[] copy = ObjectSerializer.getInstance().copy(testArray);   // uses TRIP under the hood
    assertEquals(copy, testArray);
    assertNotSame(copy, testArray);
  }

}
