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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.pdo.PersistentDomainObject;

import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/**
 * Creates PDO stubs for tests without a session.<br>
 * Only the methods needed by the data node classes are implemented.
 */
public final class PdoStub {

  /** Stub for the {@code Article} entity of the test model. */
  public interface Article extends PersistentDomainObject<Article> {}

  /** Stub for the {@code Customer} entity of the test model. */
  public interface Customer extends PersistentDomainObject<Customer> {}

  /** Stub for the {@code Contact} entity of the test model. */
  public interface Contact extends PersistentDomainObject<Contact> {}

  /** Stub for the {@code InvoiceLine} entity of the test model. */
  public interface InvoiceLine extends PersistentDomainObject<InvoiceLine> {}

  /** A unique domain key without a data type. */
  public record Code(String value) {
    @Override
    public String toString() {
      return value;
    }
  }


  /**
   * Creates a PDO stub.
   *
   * @param clazz the PDO interface
   * @param id the object ID
   * @param serial the serial
   * @param rootEntity true if root entity
   * @param udk the unique domain key, null if none
   * @param reloaded supplies the reloaded PDO, null if reload not supported
   * @param <T> the PDO type
   * @return the stub
   */
  @SuppressWarnings("unchecked")
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, long id, long serial, boolean rootEntity,
                                                               Object udk, Supplier<T> reloaded) {
    return (T) Proxy.newProxyInstance(PdoStub.class.getClassLoader(), new Class<?>[] { clazz }, (proxy, method, args) ->
      switch (method.getName()) {
        case "getEffectiveClass" -> clazz;
        case "getId" -> id;
        case "getSerial" -> serial;
        case "isRootEntity" -> rootEntity;
        case "isEmbedded" -> false;
        case "isUniqueDomainKeyProvided" -> udk != null;
        case "getUniqueDomainKey" -> udk;
        case "getUniqueDomainKeyType" -> udk == null ? null : udk.getClass();
        case "reload" -> reloaded == null ? null : reloaded.get();
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        case "toString" -> clazz.getSimpleName() + "[" + id + "/" + serial + "]";
        default -> throw new UnsupportedOperationException(method.getName());
      });
  }

  /**
   * Creates a PDO stub without reload.
   *
   * @param clazz the PDO interface
   * @param id the object ID
   * @param udk the unique domain key, null if none
   * @param <T> the PDO type
   * @return the stub
   */
  public static <T extends PersistentDomainObject<T>> T create(Class<T> clazz, long id, Object udk) {
    return create(clazz, id, 1, true, udk, null);
  }


  private PdoStub() {}

}
