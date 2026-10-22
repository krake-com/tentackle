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

package org.tentackle.keyring.platform.ffm;

import org.tentackle.keyring.KeyringException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The binding to libsecret, the client library of the freedesktop.org Secret Service.
 * <p>
 * The Secret Service is a D-Bus API implemented by GNOME Keyring, KWallet (since 5.97) and KeePassXC,
 * among others. Items are found by attributes rather than by a key. All items stored here belong to the
 * schema {@value #SCHEMA_NAME} and carry the attributes {@value #SERVICE} and {@value #ACCOUNT}.
 * <p>
 * Only the synchronous "simple password" API is bound. Its functions take the attributes as
 * NULL-terminated variable arguments; since the attributes are fixed, so are the descriptors.
 */
public final class LibSecret {

  /**
   * The name of the libsecret library.
   */
  public static final String LIBRARY = "libsecret-1.so.0";

  /**
   * The name of the glib library.
   */
  public static final String GLIB_LIBRARY = "libglib-2.0.so.0";

  /**
   * The schema name.
   */
  public static final String SCHEMA_NAME = "org.tentackle.keyring";

  /**
   * The attribute holding the service.
   */
  public static final String SERVICE = "service";

  /**
   * The attribute holding the account.
   */
  public static final String ACCOUNT = "account";


  /** {@code SECRET_SCHEMA_NONE}: the schema name is matched as well. */
  private static final int SECRET_SCHEMA_NONE = 0;

  /** {@code SECRET_SCHEMA_ATTRIBUTE_STRING} */
  private static final int SECRET_SCHEMA_ATTRIBUTE_STRING = 0;

  /** {@code SecretSchemaAttribute} */
  private static final StructLayout SCHEMA_ATTRIBUTE = MemoryLayout.structLayout(
      ADDRESS.withName("name"),
      JAVA_INT.withName("type"),
      MemoryLayout.paddingLayout(4));

  /** {@code SecretSchema} from {@code secret-schema.h} */
  private static final StructLayout SCHEMA = MemoryLayout.structLayout(
      ADDRESS.withName("name"),
      JAVA_INT.withName("flags"),
      MemoryLayout.paddingLayout(4),
      MemoryLayout.sequenceLayout(32, SCHEMA_ATTRIBUTE).withName("attributes"),
      JAVA_INT.withName("reserved"),
      MemoryLayout.paddingLayout(4),
      MemoryLayout.sequenceLayout(7, ADDRESS).withName("reserved1to7"));

  /** {@code GError} from {@code gerror.h} */
  private static final StructLayout GERROR = MemoryLayout.structLayout(
      JAVA_INT.withName("domain"),
      JAVA_INT.withName("code"),
      ADDRESS.withName("message"));


  private final Arena arena;                  // holds the libraries and the schema
  private final MemorySegment schema;         // the SecretSchema
  private final MemorySegment serviceAttr;    // the attribute names as C strings
  private final MemorySegment accountAttr;

  private final MethodHandle storeSync;
  private final MethodHandle lookupSync;
  private final MethodHandle clearSync;
  private final MethodHandle passwordFree;
  private final MethodHandle errorFree;


  /**
   * Binds libsecret.
   *
   * @throws KeyringException if libsecret is not installed
   */
  public LibSecret() {
    arena = Arena.ofAuto();
    SymbolLookup lookup = Native.library(LIBRARY, arena).or(Native.library(GLIB_LIBRARY, arena));

    // gboolean secret_password_store_sync(const SecretSchema *schema, const gchar *collection, const gchar *label,
    //                                     const gchar *password, GCancellable *cancellable, GError **error, ...)
    storeSync = Native.downcall(lookup, "secret_password_store_sync",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                              ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
        Linker.Option.firstVariadicArg(6));

    // gchar *secret_password_lookup_sync(const SecretSchema *schema, GCancellable *cancellable, GError **error, ...)
    lookupSync = Native.downcall(lookup, "secret_password_lookup_sync",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
        Linker.Option.firstVariadicArg(3));

    // gboolean secret_password_clear_sync(const SecretSchema *schema, GCancellable *cancellable, GError **error, ...)
    clearSync = Native.downcall(lookup, "secret_password_clear_sync",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
        Linker.Option.firstVariadicArg(3));

    // void secret_password_free(gchar *password): wipes and frees
    passwordFree = Native.downcall(lookup, "secret_password_free", FunctionDescriptor.ofVoid(ADDRESS));

    // void g_error_free(GError *error)
    errorFree = Native.downcall(lookup, "g_error_free", FunctionDescriptor.ofVoid(ADDRESS));

    serviceAttr = arena.allocateFrom(SERVICE);
    accountAttr = arena.allocateFrom(ACCOUNT);
    schema = arena.allocate(SCHEMA);    // zeroed: the attribute list is terminated by a NULL name
    schema.set(ADDRESS, SCHEMA.byteOffset(PathElement.groupElement("name")), arena.allocateFrom(SCHEMA_NAME));
    schema.set(JAVA_INT, SCHEMA.byteOffset(PathElement.groupElement("flags")), SECRET_SCHEMA_NONE);
    setAttribute(0, serviceAttr);
    setAttribute(1, accountAttr);
  }

  /**
   * Stores a password in the default collection.<br>
   * An existing password with the same attributes is replaced.
   *
   * @param service the service
   * @param account the account
   * @param label the label
   * @param password the password as UTF-8
   */
  public void store(String service, String account, String label, byte[] password) {
    try (Arena local = Arena.ofConfined()) {
      MemorySegment error = local.allocate(ADDRESS);
      MemorySegment nativePassword = Native.allocateSecret(local, password);
      try {
        int stored = (int) storeSync.invokeExact(schema, MemorySegment.NULL, local.allocateFrom(label), nativePassword,
                                                 MemorySegment.NULL, error,
                                                 serviceAttr, local.allocateFrom(service),
                                                 accountAttr, local.allocateFrom(account),
                                                 MemorySegment.NULL);
        checkError("secret_password_store_sync", error);
        if (stored == 0) {
          throw new KeyringException("secret_password_store_sync failed");
        }
      }
      finally {
        nativePassword.fill((byte) 0);
      }
    }
    catch (Throwable t) {
      throw Native.failed("secret_password_store_sync", t);
    }
  }

  /**
   * Looks up a password.
   *
   * @param service the service
   * @param account the account
   * @return the password as UTF-8, null if not found
   */
  public byte[] lookup(String service, String account) {
    try (Arena local = Arena.ofConfined()) {
      MemorySegment error = local.allocate(ADDRESS);
      MemorySegment password = (MemorySegment) lookupSync.invokeExact(schema, MemorySegment.NULL, error,
                                                                      serviceAttr, local.allocateFrom(service),
                                                                      accountAttr, local.allocateFrom(account),
                                                                      MemorySegment.NULL);
      checkError("secret_password_lookup_sync", error);
      if (password.equals(MemorySegment.NULL)) {
        return null;
      }
      try {
        return Native.copyCString(password);
      }
      finally {
        passwordFree.invokeExact(password);
      }
    }
    catch (Throwable t) {
      throw Native.failed("secret_password_lookup_sync", t);
    }
  }

  /**
   * Removes a password.
   *
   * @param service the service
   * @param account the account
   * @return true if removed, false if not found
   */
  public boolean clear(String service, String account) {
    try (Arena local = Arena.ofConfined()) {
      MemorySegment error = local.allocate(ADDRESS);
      int cleared = (int) clearSync.invokeExact(schema, MemorySegment.NULL, error,
                                                serviceAttr, local.allocateFrom(service),
                                                accountAttr, local.allocateFrom(account),
                                                MemorySegment.NULL);
      checkError("secret_password_clear_sync", error);
      return cleared != 0;
    }
    catch (Throwable t) {
      throw Native.failed("secret_password_clear_sync", t);
    }
  }


  /**
   * Sets an attribute of the schema.
   *
   * @param index the attribute's index
   * @param name the attribute's name as a C string
   */
  private void setAttribute(int index, MemorySegment name) {
    long offset = SCHEMA.byteOffset(PathElement.groupElement("attributes"), PathElement.sequenceElement(index));
    schema.set(ADDRESS, offset + SCHEMA_ATTRIBUTE.byteOffset(PathElement.groupElement("name")), name);
    schema.set(JAVA_INT, offset + SCHEMA_ATTRIBUTE.byteOffset(PathElement.groupElement("type")),
               SECRET_SCHEMA_ATTRIBUTE_STRING);
  }

  /**
   * Throws an exception if a function reported an error.
   *
   * @param function the function's name
   * @param errorRef the {@code GError **} passed to the function
   * @throws Throwable if the error could not be freed
   */
  @SuppressWarnings("restricted")
  private void checkError(String function, MemorySegment errorRef) throws Throwable {
    MemorySegment error = errorRef.get(ADDRESS, 0);
    if (!error.equals(MemorySegment.NULL)) {
      String message;
      try {
        MemorySegment gerror = error.reinterpret(GERROR.byteSize());
        MemorySegment text = gerror.get(ADDRESS, GERROR.byteOffset(PathElement.groupElement("message")));
        message = text.equals(MemorySegment.NULL) ? "unknown error" : text.reinterpret(Long.MAX_VALUE).getString(0);
        message += " (code " + gerror.get(ValueLayout.JAVA_INT, GERROR.byteOffset(PathElement.groupElement("code"))) + ")";
      }
      finally {
        errorFree.invokeExact(error);
      }
      throw new KeyringException(function + " failed: " + message);
    }
  }

}
