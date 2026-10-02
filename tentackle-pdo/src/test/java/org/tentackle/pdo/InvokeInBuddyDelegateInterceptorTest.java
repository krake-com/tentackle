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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import java.io.Serial;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link InvokeInBuddyDelegateInterceptor}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class InvokeInBuddyDelegateInterceptorTest {

  /**
   * The domain side.
   */
  public interface Greeter {

    @InvokeInBuddyDelegate
    String greet(String name);
  }

  /**
   * Base class of the buddy, declaring the method.
   */
  public static class BaseBuddy implements PersistenceDelegate<Object> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public DomainDelegate<Object> getDomainDelegate() {
      return null;
    }

    @Override
    public Object me() {
      return this;
    }

    @SuppressWarnings("unused")
    public String greet(String name) {
      return "hello " + name;
    }
  }

  /**
   * The buddy's concrete class, inheriting the method.
   */
  public static class Buddy extends BaseBuddy {

    @Serial
    private static final long serialVersionUID = 1L;

  }

  /**
   * The buddy's method may be inherited from a superclass, as for generated delegates extending an
   * application-specific base class.
   *
   * @throws Throwable if failed
   */
  @Test
  public void testInheritedBuddyMethodIsFound() throws Throwable {
    Buddy buddy = new Buddy();
    Object domainDelegate = Proxy.newProxyInstance(
        DomainDelegate.class.getClassLoader(), new Class<?>[] { DomainDelegate.class },
        (proxy, m, args) -> switch (m.getName()) {
          case "getPersistenceDelegate" -> buddy;
          case "hashCode"   -> System.identityHashCode(proxy);
          case "equals"     -> proxy == args[0];
          case "toString"   -> "domain-stub";
          default -> throw new UnsupportedOperationException(m.getName());
        });

    Method method = Greeter.class.getMethod("greet", String.class);
    InvokeInBuddyDelegateInterceptor interceptor = new InvokeInBuddyDelegateInterceptor();
    interceptor.setAnnotation(method.getAnnotation(InvokeInBuddyDelegate.class));
    Object[] args = { "world" };
    assertEquals(interceptor.proceed(domainDelegate, method, args, domainDelegate, method, args), "hello world");
  }

}
