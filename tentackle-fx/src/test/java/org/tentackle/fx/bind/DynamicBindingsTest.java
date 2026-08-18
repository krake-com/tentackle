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

import org.tentackle.bind.Binding;

import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the bookkeeping of the dynamic mandatory- and changeable bindings.<p>
 * No controller and no controls are involved, so that the tests run without a JavaFX toolkit.
 * The bindings are stand-ins that are only ever compared by identity.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DynamicBindingsTest {

  /**
   * Creates a binding stand-in.
   *
   * @return a binding whose methods all return null
   */
  private Binding createBinding() {
    return (Binding) Proxy.newProxyInstance(Binding.class.getClassLoader(),
                                            new Class<?>[] { Binding.class },
                                            (proxy, method, args) -> {
      // the bindings are identified by identity, so Object's methods must not return null
      return switch (method.getName()) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> "binding@" + System.identityHashCode(proxy);
        default -> null;
      };
    });
  }


  /**
   * Unbinding must forget the dynamic bindings as well, else they pile up with
   * every rebind and are evaluated although their components are no longer bound.
   */
  @Test
  public void testUnbindClearsDynamicBindings() {
    DefaultFxComponentBinder binder = new DefaultFxComponentBinder(null);
    binder.addMandatoryBinding(createBinding());
    binder.addChangeableBinding(createBinding());
    assertEquals(binder.getMandatoryBindings().size(), 1);
    assertEquals(binder.getChangeableBindings().size(), 1);

    binder.unbind();
    assertTrue(binder.getMandatoryBindings().isEmpty());
    assertTrue(binder.getChangeableBindings().isEmpty());

    // a rebind must not resurrect them
    binder.addMandatoryBinding(createBinding());
    assertEquals(binder.getMandatoryBindings().size(), 1);
  }


  /**
   * Removing a single binding drops it from the dynamic bindings, but leaves the others alone.
   */
  @Test
  public void testRemoveDynamicBinding() {
    DefaultFxComponentBinder binder = new DefaultFxComponentBinder(null);
    Binding removed = createBinding();
    Binding kept = createBinding();
    binder.addMandatoryBinding(removed);
    binder.addMandatoryBinding(kept);
    binder.addChangeableBinding(removed);

    binder.removeDynamicBinding(removed);
    assertEquals(binder.getMandatoryBindings(), java.util.List.of(kept));
    assertTrue(binder.getChangeableBindings().isEmpty());
  }

}
