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

package org.tentackle.fx.bind;

import org.testng.annotations.Test;

import org.tentackle.bind.BindingMember;
import org.tentackle.bind.DefaultBindableElement;
import org.tentackle.bind.DefaultBindingMember;
import org.tentackle.fx.FxComponent;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that components bound to read-only members (such as final fields) are never changeable.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReadOnlyBindingTest {

  /** the bound model. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class Model {
    public final String code = "code";
    public String text;
  }

  /**
   * Creates a component stand-in that records its changeability.
   *
   * @param changeable the recorded changeability
   * @return the component
   */
  private FxComponent createComponent(AtomicBoolean changeable) {
    return (FxComponent) Proxy.newProxyInstance(FxComponent.class.getClassLoader(),
                                                new Class<?>[] { FxComponent.class },
                                                (proxy, method, args) -> switch (method.getName()) {
      case "setChangeable" -> {
        changeable.set((Boolean) args[0]);
        yield null;
      }
      case "isChangeable" -> changeable.get();
      case "equals" -> proxy == args[0];
      case "hashCode" -> System.identityHashCode(proxy);
      case "toString" -> "component@" + System.identityHashCode(proxy);
      default -> method.getReturnType() == Boolean.TYPE ? Boolean.FALSE : null;
    });
  }

  private DefaultFxComponentBinding bind(String fieldName, AtomicBoolean changeable) throws NoSuchFieldException {
    DefaultBindableElement element = new DefaultBindableElement(fieldName);
    element.setField(Model.class.getField(fieldName));
    BindingMember member = new DefaultBindingMember(Model.class, null, fieldName, fieldName, element);
    return new DefaultFxComponentBinding(new DefaultFxComponentBinder(null), null, member,
                                         createComponent(changeable), null);
  }

  @Test
  public void testFinalFieldIsNotChangeable() throws NoSuchFieldException {
    AtomicBoolean changeable = new AtomicBoolean(true);
    DefaultFxComponentBinding binding = bind("code", changeable);
    assertTrue(binding.isModelReadOnly());
    assertFalse(changeable.get());
    // changeable validators or application code must not make it changeable again
    binding.setChangeable(true);
    assertFalse(changeable.get());
    assertFalse(binding.isChangeable());
  }

  @Test
  public void testMutableFieldIsChangeable() throws NoSuchFieldException {
    AtomicBoolean changeable = new AtomicBoolean(true);
    DefaultFxComponentBinding binding = bind("text", changeable);
    assertFalse(binding.isModelReadOnly());
    assertTrue(changeable.get());
    binding.setChangeable(false);
    assertFalse(changeable.get());
    binding.setChangeable(true);
    assertTrue(changeable.get());
  }
}
