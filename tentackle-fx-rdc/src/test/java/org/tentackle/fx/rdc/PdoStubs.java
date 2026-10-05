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

package org.tentackle.fx.rdc;

import org.tentackle.pdo.PersistentDomainObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Lightweight PDO stubs for toolkit- and session-free unit tests.
 * <p>
 * The stubs are dynamic proxies of a PDO interface, so they need neither a PDO factory nor
 * a session. Only the identity-related methods and the methods passed as answers are
 * implemented; all others return {@code null}, {@code false} or zero.
 */
public final class PdoStubs {

  /** Test PDO type. */
  public interface Alpha extends PersistentDomainObject<Alpha> {}

  /** Another test PDO type. */
  public interface Beta extends PersistentDomainObject<Beta> {}


  /**
   * Creates a PDO stub with default behavior.
   *
   * @param type the PDO interface
   * @param <T> the PDO type
   * @return the stub
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> type) {
    return create(type, Map.of());
  }

  /**
   * Creates a PDO stub that is not allowed to be viewed.
   *
   * @param type the PDO interface
   * @param <T> the PDO type
   * @return the stub
   */
  public static <T extends PersistentDomainObject<T>> T createNotViewable(Class<T> type) {
    return create(type, Map.of("isViewAllowed", false));
  }

  /**
   * Creates a PDO stub.
   *
   * @param type the PDO interface
   * @param answers the return values by method name
   * @param <T> the PDO type
   * @return the stub
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> type, Map<String, Object> answers) {
    InvocationHandler handler = (proxy, method, args) -> {
      String name = method.getName();
      if (answers.containsKey(name)) {
        return answers.get(name);
      }
      return switch (name) {
        case "getEffectiveClass" -> type;
        case "getEffectiveSuperClasses" -> List.of();
        case "getClassBaseName" -> type.getSimpleName();
        case "isViewAllowed" -> true;
        case "toString" -> type.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> defaultValue(method.getReturnType());
      };
    };
    return type.cast(Proxy.newProxyInstance(PdoStubs.class.getClassLoader(), new Class<?>[] { type }, handler));
  }

  /**
   * Gets the default value for a return type.
   *
   * @param type the return type
   * @return null, false or zero
   */
  public static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0.0;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == char.class) {
      return (char) 0;
    }
    return null;
  }


  private PdoStubs() {}

}
