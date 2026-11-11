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

import org.tentackle.misc.Identifiable;
import org.tentackle.reflect.EffectiveClassProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;

/**
 * Guards the name-based dispatch of the special methods in {@link PdoInvocationHandler} and
 * {@link OperationInvocationHandler}.<br>
 * The handlers identify those methods by declaring class and name only. This test fails if one of them
 * is renamed or gets overloaded, which the compiler cannot detect.
 */
@SuppressWarnings("missing-explicit-ctor")
public class InvocationHandlerDispatchTest {

  @Test
  public void specialMethodsAreUnique() {
    assertUnique(Comparable.class, "compareTo", 1);
    assertUnique(PersistentDomainObject.class, "getDomainDelegate", 0);
    assertUnique(PersistentDomainObject.class, "getPersistenceDelegate", 0);
    assertUnique(Operation.class, "getDomainDelegate", 0);
    assertUnique(Operation.class, "getPersistenceDelegate", 0);
    assertUnique(EffectiveClassProvider.class, "getEffectiveClass", 0);
    assertUnique(EffectiveClassProvider.class, "getEffectiveSuperClasses", 0);
    assertUnique(Identifiable.class, "toGenericString", 0);
    assertUnique(PdoProvider.class, "getPdo", 0);
    assertUnique(OperationProvider.class, "getOperation", 0);
  }

  @Test
  public void comparableHasOnlyCompareTo() {
    long count = Arrays.stream(Comparable.class.getDeclaredMethods())
                       .filter(m -> !Modifier.isStatic(m.getModifiers()))
                       .count();
    assertEquals(count, 1);
  }

  private static void assertUnique(Class<?> clazz, String name, int parameterCount) {
    Method[] methods = Arrays.stream(clazz.getDeclaredMethods())
                             // static methods are never dispatched by a proxy, and bridges for covariant
                             // return types delegate to the real method and dispatch alike
                             .filter(m -> !Modifier.isStatic(m.getModifiers()) && !m.isBridge() && m.getName().equals(name))
                             .toArray(Method[]::new);
    assertEquals(methods.length, 1, clazz.getName() + "." + name + " must exist exactly once");
    assertEquals(methods[0].getParameterCount(), parameterCount, clazz.getName() + "." + name);
  }

}
