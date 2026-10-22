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

package org.tentackle.fx.rdc.component.delegate;

import org.testng.annotations.Test;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.bind.FxComponentBinder;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.rdc.PdoStubs;
import org.tentackle.fx.rdc.PdoStubs.Alpha;
import org.tentackle.pdo.DomainContext;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the item-loading decisions of {@link RdcBoxDelegateImpl}.
 * <p>
 * The component, its binding and the domain context are dynamic-proxy stubs, so neither
 * a JavaFX toolkit nor a session is needed. The paths that actually load the PDOs need
 * a PDO factory and are not covered here.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RdcBoxDelegateImplTest {

  /**
   * Stubs a component, optionally bound to a binder providing a domain context.
   */
  private static class ComponentStub {

    private final List<String> invocations = new ArrayList<>();
    private final boolean bound;
    private final DomainContext context;

    ComponentStub(boolean bound, DomainContext context) {
      this.bound = bound;
      this.context = context;
    }

    FxComponent create() {
      return stub(FxComponent.class, (name, _) -> switch (name) {
        case "getBinding" -> bound ? binding() : null;
        default -> null;
      });
    }

    private FxComponentBinding binding() {
      return stub(FxComponentBinding.class, (name, _) -> "getBinder".equals(name) ? binder() : null);
    }

    private FxComponentBinder binder() {
      return stub(FxComponentBinder.class,
                  (name, args) -> "getBindingProperty".equals(name) && args[0] == DomainContext.class ? context : null);
    }

    private <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> answer) {
      return type.cast(Proxy.newProxyInstance(
          type.getClassLoader(), new Class<?>[] { type },
          (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
              case "hashCode" -> { return System.identityHashCode(proxy); }
              case "equals" -> { return proxy == args[0]; }
              case "toString" -> { return type.getSimpleName() + "Stub"; }
              default -> {
                invocations.add(type.getSimpleName() + "." + name);
                Object result = answer.apply(name, args);
                return result != null ? result : PdoStubs.defaultValue(method.getReturnType());
              }
            }
          }));
    }
  }


  private int itemRequests;
  private int addonRequests;

  @SuppressWarnings("rawtypes")
  private RdcBoxDelegateImpl createDelegate(ComponentStub component) {
    itemRequests = 0;
    addonRequests = 0;
    return new RdcBoxDelegateImpl(component.create(),
                                  () -> { itemRequests++; return null; },
                                  () -> { addonRequests++; return null; });
  }

  private static DomainContext contextStub() {
    return (DomainContext) Proxy.newProxyInstance(
        DomainContext.class.getClassLoader(), new Class<?>[] { DomainContext.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "DomainContextStub";
          default -> PdoStubs.defaultValue(method.getReturnType());
        });
  }


  @Test
  public void testNonPdoTypeIsIgnored() {
    ComponentStub component = new ComponentStub(true, contextStub());
    RdcBoxDelegateImpl delegate = createDelegate(component);
    delegate.prepareSetType(String.class);
    delegate.prepareSetViewValue();
    assertTrue(component.invocations.isEmpty(), "nothing to load for non-PDO types: " + component.invocations);
    assertEquals(itemRequests, 0);
    assertEquals(addonRequests, 0);
  }

  @Test
  public void testNothingLoadedBeforeTypeIsSet() {
    ComponentStub component = new ComponentStub(true, contextStub());
    RdcBoxDelegateImpl delegate = createDelegate(component);
    delegate.prepareSetViewValue();
    assertTrue(component.invocations.isEmpty(), "nothing to load without a type: " + component.invocations);
  }

  @Test
  public void testPdoTypeWithoutBindingLoadsNothing() {
    ComponentStub component = new ComponentStub(false, null);
    RdcBoxDelegateImpl delegate = createDelegate(component);
    delegate.prepareSetType(Alpha.class);
    delegate.prepareSetViewValue();
    assertEquals(component.invocations, List.of("FxComponent.getBinding"),
                 "the domain context must be looked up for a PDO type");
    assertEquals(itemRequests, 0, "no items loaded without a domain context");
    assertEquals(addonRequests, 0);
  }

  @Test
  public void testPdoTypeWithoutDomainContextLoadsNothing() {
    ComponentStub component = new ComponentStub(true, null);
    RdcBoxDelegateImpl delegate = createDelegate(component);
    delegate.prepareSetType(Alpha.class);
    delegate.prepareSetViewValue();
    assertTrue(component.invocations.contains("FxComponentBinder.getBindingProperty"), component.invocations.toString());
    assertEquals(itemRequests, 0, "no items loaded without a domain context");
    assertEquals(addonRequests, 0);
  }

  @Test
  public void testGetDomainContext() {
    assertNull(createDelegate(new ComponentStub(false, null)).getDomainContext(), "no binding -> no context");
    DomainContext context = contextStub();
    assertSame(createDelegate(new ComponentStub(true, context)).getDomainContext(), context);
  }

}
