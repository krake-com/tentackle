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

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link DefaultBindingMember} (delegation to the {@link BindableElement}).
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultBindingMemberTest {

  @SuppressWarnings("missing-explicit-ctor")
  public static class Model {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  private static Method method(String name, Class<?>... args) {
    try {
      return Model.class.getMethod(name, args);
    }
    catch (NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }


  private DefaultBindingMember newMember() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method("getName"));
    element.setSetter(method("setName", String.class));
    return new DefaultBindingMember(Model.class, null, "name", "root.name", element);
  }


  @Test
  public void testIdentityAccessors() {
    DefaultBindableElement element = new DefaultBindableElement("Name");
    element.setGetter(method("getName"));
    BindingMember parent = new DefaultBindingMember(Model.class, null, "root", "root", element);
    DefaultBindingMember member = new DefaultBindingMember(Model.class, parent, "name", "root.name", element);

    assertEquals(member.getParentClass(), Model.class);
    assertSame(member.getParentMember(), parent);
    assertEquals(member.getMemberName(), "name");
    assertEquals(member.getMemberPath(), "root.name");
    assertEquals(member.toString(), "root.name");
  }

  @Test
  public void testDelegatesReflectionAccessors() {
    DefaultBindingMember member = newMember();
    assertEquals(member.getGetter(), method("getName"));
    assertEquals(member.getSetter(), method("setName", String.class));
    assertNull(member.getField());
    assertNull(member.getFrom());
    assertEquals(member.getType(), String.class);
    assertEquals(member.getGenericType(), String.class);
    assertFalse(member.isReadOnly());
  }

  @Test
  public void testDelegatesGetAndSet() {
    DefaultBindingMember member = newMember();
    Model model = new Model();
    member.setObject(model, "value");
    assertEquals(model.getName(), "value");
    assertEquals(member.getObject(model), "value");
  }
}
