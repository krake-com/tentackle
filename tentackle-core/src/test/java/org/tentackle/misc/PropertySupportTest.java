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

package org.tentackle.misc;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link PropertySupport} and {@link PropertyEvent}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PropertySupportTest {

  private final Object source = new Object();

  @Test
  public void testPropertyEventAccessors() {
    PropertyEvent event = new PropertyEvent(source, "name", "old", "new");
    assertSame(event.getSource(), source);
    assertEquals(event.getPropertyName(), "name");
    assertEquals(event.getOldValue(), "old");
    assertEquals(event.getNewValue(), "new");
  }

  @Test
  public void testPropertyEventNullSourceRejected() {
    assertThrows(IllegalArgumentException.class, () -> new PropertyEvent(null, "name", "old", "new"));
  }

  @Test
  public void testPropertyEventNullNameRejected() {
    assertThrows(NullPointerException.class, () -> new PropertyEvent(source, null, "old", "new"));
  }

  @Test
  public void testSpecificListenerReceivesMatchingProperty() {
    PropertySupport support = new PropertySupport(source);
    List<PropertyEvent> received = new ArrayList<>();
    support.addPropertyListener("age", received::add);

    support.firePropertyChanged("age", 10, 20);

    assertEquals(received.size(), 1);
    PropertyEvent event = received.getFirst();
    assertSame(event.getSource(), source);
    assertEquals(event.getPropertyName(), "age");
    assertEquals(event.getOldValue(), 10);
    assertEquals(event.getNewValue(), 20);
  }

  @Test
  public void testSpecificListenerIgnoresOtherProperty() {
    PropertySupport support = new PropertySupport(source);
    List<PropertyEvent> received = new ArrayList<>();
    support.addPropertyListener("age", received::add);

    support.firePropertyChanged("name", "a", "b");

    assertTrue(received.isEmpty());
  }

  @Test
  public void testAllPropertiesListenerReceivesEverything() {
    PropertySupport support = new PropertySupport(source);
    List<String> names = new ArrayList<>();
    support.addPropertyListener(ev -> names.add(ev.getPropertyName()));

    support.firePropertyChanged("age", 1, 2);
    support.firePropertyChanged("name", "a", "b");

    assertEquals(names, List.of("age", "name"));
  }

  @Test
  public void testSpecificAndAllListenersBothFireOnce() {
    PropertySupport support = new PropertySupport(source);
    List<String> log = new ArrayList<>();
    support.addPropertyListener("age", _ -> log.add("specific"));
    support.addPropertyListener(_ -> log.add("all"));

    support.firePropertyChanged("age", 1, 2);

    // both listeners fire, each exactly once
    assertEquals(log.size(), 2);
    assertTrue(log.contains("specific"));
    assertTrue(log.contains("all"));
  }

  @Test
  public void testRemoveSpecificListener() {
    PropertySupport support = new PropertySupport(source);
    List<PropertyEvent> received = new ArrayList<>();
    PropertyListener listener = received::add;
    support.addPropertyListener("age", listener);
    support.removePropertyListener("age", listener);

    support.firePropertyChanged("age", 1, 2);
    assertTrue(received.isEmpty());
  }

  @Test
  public void testRemoveAllPropertiesListener() {
    PropertySupport support = new PropertySupport(source);
    List<PropertyEvent> received = new ArrayList<>();
    PropertyListener listener = received::add;
    support.addPropertyListener(listener);
    support.removePropertyListener(listener);

    support.firePropertyChanged("age", 1, 2);
    assertTrue(received.isEmpty());
  }

  @Test
  public void testRemoveUnknownListenerIsNoOp() {
    PropertySupport support = new PropertySupport(source);
    // must not throw
    support.removePropertyListener("age", _ -> { });
    support.removePropertyListener(_ -> { });
  }

  @Test
  public void testFireEmptyPropertyNameRejected() {
    PropertySupport support = new PropertySupport(source);
    assertThrows(IllegalArgumentException.class, () -> support.firePropertyChanged("", 1, 2));
  }

  @Test
  public void testFireNullPropertyNameRejected() {
    PropertySupport support = new PropertySupport(source);
    assertThrows(NullPointerException.class, () -> support.firePropertyChanged(null, 1, 2));
  }

  @Test
  public void testListenerVetoPropagates() {
    PropertySupport support = new PropertySupport(source);
    support.addPropertyListener("age", _ -> {
      throw new IllegalStateException("veto");
    });
    assertThrows(IllegalStateException.class, () -> support.firePropertyChanged("age", 1, 2));
  }

  @Test
  public void testNullPropertyNameOnAddIsTreatedAsAllProperties() {
    PropertySupport support = new PropertySupport(source);
    List<PropertyEvent> received = new ArrayList<>();
    // a null property name registers an all-properties listener
    support.addPropertyListener(null, received::add);
    support.firePropertyChanged("whatever", 1, 2);
    assertEquals(received.size(), 1);
  }

  @Test
  public void testMultipleListenersOnSameProperty() {
    PropertySupport support = new PropertySupport(source);
    List<String> log = new ArrayList<>();
    support.addPropertyListener("age", _ -> log.add("one"));
    support.addPropertyListener("age", _ -> log.add("two"));
    support.firePropertyChanged("age", 1, 2);
    assertEquals(log, List.of("one", "two"));
  }
}
