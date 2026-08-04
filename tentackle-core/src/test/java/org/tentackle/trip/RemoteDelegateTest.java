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

import org.tentackle.trip.impl.DemoLoginImpl;
import org.tentackle.trip.impl.TestRemoteObjectWithExtras;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * A remote call carries a method name and its parameter types, so a peer is not restricted to what
 * its proxy offers. The delegate must therefore dispatch to the remote interfaces only.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RemoteDelegateTest {

  private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

  private record Fixture(RemoteDelegate<TestRemoteObjectWithExtras> delegate, TestRemoteObjectWithExtras target) {

    InitialRemoteResponse call(String methodName, Class<?>[] parameterTypes, Object... parameters) {
      return delegate.invoke(new InitialRemoteCall(delegate.getRemoteId(), methodName, parameterTypes,
                                                   parameters.length == 0 ? null : parameters));
    }
  }

  private Fixture createFixture() {
    Registry registry = TripFactory.getInstance().createRegistry();
    TestRemoteObjectWithExtras target = new TestRemoteObjectWithExtras();
    return new Fixture(registry.register(target), target);
  }

  @Test
  void invokesTheMethodsOfTheRemoteInterface() {
    Fixture fixture = createFixture();
    InitialRemoteResponse response = fixture.call("getName", NO_PARAMETERS);
    assertTrue(response.wasSuccessFul(), String.valueOf(response.throwable()));
    assertEquals(response.result(), "Eiermann");
    assertTrue(response.methodIndex() >= 0);
  }

  /**
   * The methods a dynamic proxy forwards to its invocation handler stay invocable, since a client
   * may call them on its proxy.
   */
  @Test
  void invokesTheObjectMethodsForwardedByAProxy() {
    Fixture fixture = createFixture();
    assertEquals(fixture.call("toString", NO_PARAMETERS).result(), "the remote object");
    assertEquals(fixture.call("hashCode", NO_PARAMETERS).result(), fixture.target().hashCode());
    assertEquals(fixture.call("equals", new Class<?>[] { Object.class }, fixture.target()).result(), true);
  }

  /**
   * A public method of the implementation class that no remote interface declares must not be
   * reachable, even though the target does provide it.
   */
  @Test
  void refusesMethodsOutsideTheRemoteInterfaces() {
    Fixture fixture = createFixture();
    InitialRemoteResponse response = fixture.call("secret", NO_PARAMETERS);

    assertFalse(response.wasSuccessFul());
    assertNull(response.result());
    assertEquals(response.methodIndex(), -1);
    assertTrue(response.throwable() instanceof NoSuchMethodException, String.valueOf(response.throwable()));
    assertFalse(fixture.target().isSecretInvoked(), "the method must not have been invoked at all");
  }

  /**
   * The remote interfaces are cached per target class, since there may be many remote objects of
   * the same class. Each class must still get its own, and every instance the full set.
   */
  @Test
  void resolvesTheInterfacesPerClass() {
    Registry registry = TripFactory.getInstance().createRegistry();
    RemoteDelegate<TestRemoteObjectWithExtras> first = registry.register(new TestRemoteObjectWithExtras());
    RemoteDelegate<DemoLoginImpl> other = registry.register(new DemoLoginImpl("1.0.0"));
    RemoteDelegate<TestRemoteObjectWithExtras> second = registry.register(new TestRemoteObjectWithExtras());

    for (RemoteDelegate<TestRemoteObjectWithExtras> delegate : List.of(first, second)) {
      assertEquals(delegate.invoke(new InitialRemoteCall(delegate.getRemoteId(), "getName", NO_PARAMETERS, null))
                           .result(), "Eiermann");
      // the other class' method must not have leaked into the cached interfaces
      assertFalse(delegate.invoke(new InitialRemoteCall(delegate.getRemoteId(), "getVersion", NO_PARAMETERS, null))
                          .wasSuccessFul());
    }

    assertEquals(other.invoke(new InitialRemoteCall(other.getRemoteId(), "getVersion", NO_PARAMETERS, null))
                      .result(), "1.0.0");
    assertFalse(other.invoke(new InitialRemoteCall(other.getRemoteId(), "getName", NO_PARAMETERS, null))
                     .wasSuccessFul());
  }

  /**
   * The remaining {@link Object} methods are final and never forwarded by a proxy, so a call naming
   * one of them can only come from a handcrafted message.
   */
  @Test
  void refusesTheObjectMethodsAProxyNeverForwards() {
    Fixture fixture = createFixture();
    for (String methodName : new String[] { "wait", "notify", "notifyAll", "getClass" }) {
      InitialRemoteResponse response = fixture.call(methodName, NO_PARAMETERS);
      assertFalse(response.wasSuccessFul(), methodName + " must not be invocable");
      assertTrue(response.throwable() instanceof NoSuchMethodException, String.valueOf(response.throwable()));
    }
  }

}
