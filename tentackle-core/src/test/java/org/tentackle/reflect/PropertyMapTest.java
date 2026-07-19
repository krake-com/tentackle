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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import org.tentackle.misc.ImmutableException;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link PropertyMap} and {@link PropertyMapping}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class PropertyMapTest {

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class Person {
    private String name;
    private int age;
    private boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getId() { return "ID-42"; }    // read-only property (no setter)
  }


  // --------------------------- PropertyMap ---------------------------------

  @Test
  public void testGetAndPut() {
    Person p = new Person();
    p.setName("Alice");
    PropertyMap<Person> map = new PropertyMap<>(p);

    assertSame(map.getBean(), p);
    assertSame(map.getBeanType(), Person.class);

    assertEquals(map.get("name"), "Alice");
    map.put("name", "Bob");
    assertEquals(p.getName(), "Bob");
    assertEquals(map.get("name"), "Bob");
  }

  @Test
  public void testPutConvertsValue() {
    Person p = new Person();
    PropertyMap<Person> map = new PropertyMap<>(p);
    // the string is converted to the int property type
    map.put("age", "42");
    assertEquals(p.getAge(), 42);
    assertEquals(map.get("age"), 42);
  }

  @Test
  public void testBooleanProperty() {
    Person p = new Person();
    PropertyMap<Person> map = new PropertyMap<>(p);
    map.put("active", true);
    assertTrue(p.isActive());
    assertEquals(map.get("active"), Boolean.TRUE);
  }

  @Test
  public void testContainsKey() {
    PropertyMap<Person> map = new PropertyMap<>(new Person());
    assertTrue(map.containsKey("name"));
    assertTrue(map.containsKey("id"));
    assertFalse(map.containsKey("doesNotExist"));
  }

  @Test
  public void testGetUnknownProperty() {
    PropertyMap<Person> map = new PropertyMap<>(new Person());
    assertNull(map.get("doesNotExist"));
    assertNull(map.getMapping("doesNotExist"));
  }

  @Test
  public void testPutUnknownPropertyThrows() {
    PropertyMap<Person> map = new PropertyMap<>(new Person());
    assertThrows(IllegalArgumentException.class, () -> map.put("doesNotExist", "x"));
  }

  @Test
  public void testReadOnlyPropertyCannotBeSet() {
    PropertyMap<Person> map = new PropertyMap<>(new Person());
    assertTrue(map.getMapping("id").isReadOnly());
    assertThrows(ImmutableException.class, () -> map.put("id", "other"));
  }

  @Test
  public void testRemoveThrows() {
    PropertyMap<Person> map = new PropertyMap<>(new Person());
    assertThrows(ImmutableException.class, () -> map.remove("name"));
  }

  @Test
  public void testEntrySet() {
    Person p = new Person();
    p.setName("Alice");
    p.setAge(30);
    PropertyMap<Person> map = new PropertyMap<>(p);
    assertTrue(map.keySet().contains("name"));
    assertTrue(map.keySet().contains("age"));
    assertTrue(map.keySet().contains("active"));
    assertTrue(map.keySet().contains("id"));
    assertEquals(map.get("name"), "Alice");
    assertEquals(map.get("age"), 30);
    // entry set entries are immutable
    var entry = map.entrySet().iterator().next();
    assertThrows(ImmutableException.class, () -> entry.setValue("x"));
  }

  @Test
  public void testEqualsAndHashCode() {
    Person p = new Person();
    PropertyMap<Person> map1 = new PropertyMap<>(p);
    PropertyMap<Person> map2 = new PropertyMap<>(p);
    // call equals() directly: TestNG's assertEquals(Map, Map) compares by content and would bypass it.
    // equality requires equal contents AND the identical bean instance.
    assertTrue(map1.equals(map2));
    assertEquals(map1.hashCode(), map2.hashCode());
    assertFalse(map1.equals(new PropertyMap<>(new Person())));
  }

  @Test
  public void testExplicitBeanType() {
    Person p = new Person();
    PropertyMap<Person> map = new PropertyMap<>(p, Person.class);
    assertSame(map.getBeanType(), Person.class);
    assertEquals(map.get("id"), "ID-42");
  }


  // --------------------------- PropertyMapping -----------------------------

  private static Method method(String name, Class<?>... args) throws NoSuchMethodException {
    return Person.class.getMethod(name, args);
  }

  @Test
  public void testMappingReadWrite() throws Exception {
    PropertyMapping<Person> mapping = new PropertyMapping<>(
        "name", method("getName"), method("setName", String.class));
    assertEquals(mapping.getName(), "name");
    assertEquals(mapping.getGetter(), method("getName"));
    assertEquals(mapping.getSetter(), method("setName", String.class));
    assertFalse(mapping.isReadOnly());
    assertSame(mapping.getValueType(), String.class);
    assertEquals(mapping.toString(), "name");

    Person p = new Person();
    mapping.set(p, "Carol");
    assertEquals(mapping.get(p), "Carol");
  }

  @Test
  public void testMappingReadOnly() throws Exception {
    PropertyMapping<Person> mapping = new PropertyMapping<>("id", method("getId"), null);
    assertTrue(mapping.isReadOnly());
    assertSame(mapping.getValueType(), String.class);
    assertEquals(mapping.get(new Person()), "ID-42");
    assertThrows(ImmutableException.class, () -> mapping.set(new Person(), "x"));
  }

  @Test
  public void testMappingValueConversion() throws Exception {
    PropertyMapping<Person> mapping = new PropertyMapping<>(
        "age", method("getAge"), method("setAge", int.class));
    assertSame(mapping.getValueType(), int.class);
    Person p = new Person();
    mapping.set(p, "7");    // converted to int
    assertEquals(p.getAge(), 7);
    assertEquals(mapping.get(p), 7);
  }

  @Test
  public void testMappingEqualsAndHashCode() throws Exception {
    PropertyMapping<Person> a = new PropertyMapping<>("name", method("getName"), method("setName", String.class));
    PropertyMapping<Person> b = new PropertyMapping<>("name", method("getName"), null);
    PropertyMapping<Person> c = new PropertyMapping<>("age", method("getAge"), null);
    // equality is based on the property name only
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
  }
}
