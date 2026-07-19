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

package org.tentackle.bind;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link BindingEvent}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BindingEventTest {

  /** creates a stub {@link Binding} usable as an event source. */
  private static Binding stubBinding() {
    return (Binding) Proxy.newProxyInstance(
        BindingEventTest.class.getClassLoader(),
        new Class<?>[]{ Binding.class },
        (_, method, _) -> {
          if ("toString".equals(method.getName())) {
            return "stubBinding";
          }
          return null;
        });
  }


  @Test
  public void testAccessors() {
    Binding source = stubBinding();
    Object parent = new Object();
    Object value = "the-value";

    BindingEvent event = new BindingEvent(source, BindingEvent.Type.TO_VIEW_VIA_GETTER, parent, value);

    assertSame(event.getSource(), source);
    assertEquals(event.getType(), BindingEvent.Type.TO_VIEW_VIA_GETTER);
    assertSame(event.getParent(), parent);
    assertSame(event.getValue(), value);
  }

  @Test
  public void testToStringContainsTypeAndValue() {
    BindingEvent event = new BindingEvent(stubBinding(), BindingEvent.Type.TO_MODEL_VIA_SETTER, null, "abc");
    String s = event.toString();
    assertTrue(s.contains("TO_MODEL_VIA_SETTER"));
    assertTrue(s.contains("abc"));
  }

  @Test
  public void testAllTypesCovered() {
    // guards against accidental enum removal/rename
    assertEquals(BindingEvent.Type.values().length, 5);
    assertEquals(BindingEvent.Type.valueOf("TO_VIEW_VIA_GETTER"), BindingEvent.Type.TO_VIEW_VIA_GETTER);
    assertEquals(BindingEvent.Type.valueOf("TO_VIEW_VIA_FIELD"), BindingEvent.Type.TO_VIEW_VIA_FIELD);
    assertEquals(BindingEvent.Type.valueOf("TO_MODEL_VIA_SETTER"), BindingEvent.Type.TO_MODEL_VIA_SETTER);
    assertEquals(BindingEvent.Type.valueOf("TO_MODEL_VIA_FROM_METHOD"), BindingEvent.Type.TO_MODEL_VIA_FROM_METHOD);
    assertEquals(BindingEvent.Type.valueOf("TO_MODEL_VIA_FIELD"), BindingEvent.Type.TO_MODEL_VIA_FIELD);
  }
}
