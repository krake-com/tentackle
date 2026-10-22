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

import org.testng.annotations.Test;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripFilter;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link TripFilter}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TripFilterTest {

  /**
   * Object whose components are filtered.
   */
  public static class Filtered {
    public String name;
    public List<String> list;
    public int count;
    public Nested nested;
    public Nested other;
  }

  /**
   * Nested object.
   */
  public static class Nested {
    public String text;
  }


  private static Filtered createObject() {
    Filtered object = new Filtered();
    object.name = "blah";
    object.list = new ArrayList<>(List.of("a", "b"));
    object.count = 42;
    object.nested = new Nested();
    object.nested.text = "nested";
    object.other = object.nested;
    return object;
  }

  private static TripFilter skip(String... names) {
    Set<String> skipped = Set.of(names);
    return (parent, component) -> !(parent instanceof Filtered) || !skipped.contains(component.getName());
  }

  @SuppressWarnings("unchecked")
  private static <T> T copy(T object, TripFilter filter) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.setFilter(filter);
    TripType<T> type = TripFactory.getInstance().getType((Class<T>) object.getClass());
    type.write(object, stream);
    transceiver.echo();
    stream = transceiver.getStream();   // switched!
    OpCode opCode = stream.getDeserializer().deserializeOpCode();
    return type.read(stream, opCode);
  }


  @Test
  public void testNoFilter() {
    Filtered object = createObject();
    Filtered copy = copy(object, null);
    assertNotSame(copy, object);
    assertEquals(copy.name, "blah");
    assertEquals(copy.list, List.of("a", "b"));
    assertEquals(copy.count, 42);
    assertEquals(copy.nested.text, "nested");
    assertSame(copy.other, copy.nested);
  }

  @Test
  public void testSkipComponents() {
    Filtered copy = copy(createObject(), skip("list", "count"));
    assertEquals(copy.name, "blah");
    assertNull(copy.list);
    assertEquals(copy.count, 0);
    assertEquals(copy.nested.text, "nested");
    assertSame(copy.other, copy.nested);
  }

  @Test
  public void testSkipFirstComponent() {
    // the components following a gap must still be assigned to the right fields
    Filtered copy = copy(createObject(), skip("name"));
    assertNull(copy.name);
    assertEquals(copy.list, List.of("a", "b"));
    assertEquals(copy.count, 42);
    assertEquals(copy.nested.text, "nested");
  }

  @Test
  public void testSharedReference() {
    // the object referenced by the skipped component still arrives via the kept one
    Filtered copy = copy(createObject(), skip("nested"));
    assertNull(copy.nested);
    assertNotNull(copy.other);
    assertEquals(copy.other.text, "nested");
  }

}
