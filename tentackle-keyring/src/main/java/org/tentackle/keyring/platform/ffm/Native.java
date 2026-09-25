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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Helpers shared by the bindings.
 */
final class Native {

  /**
   * The native linker.
   */
  static final Linker LINKER = Linker.nativeLinker();

  /**
   * Loads a library.
   * <p>
   * The library is looked up by its name, not by a path, and thus the way the OS does it. On macOS this
   * is the only way to reach the system frameworks, which live in the dyld shared cache and not in the
   * file system.
   *
   * @param name the library name, such as {@code "libsecret-1.so.0"}
   * @param arena the arena controlling the library's lifetime
   * @return the lookup
   * @throws KeyringException if the library cannot be loaded
   */
  @SuppressWarnings("restricted")
  static SymbolLookup library(String name, Arena arena) {
    try {
      return SymbolLookup.libraryLookup(name, arena);
    }
    catch (IllegalArgumentException | IllegalCallerException ex) {
      throw new KeyringException("cannot load " + name, ex);
    }
  }

  /**
   * Creates a downcall handle.
   *
   * @param lookup the library lookup
   * @param name the function's name
   * @param descriptor the function descriptor
   * @param options the linker options
   * @return the method handle
   * @throws KeyringException if the library does not export the function
   */
  @SuppressWarnings("restricted")   // the descriptors are trusted to match the C headers
  static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor, Linker.Option... options) {
    MemorySegment symbol = lookup.find(name).orElseThrow(
        () -> new KeyringException("native library does not export '" + name + "'"));
    return LINKER.downcallHandle(symbol, descriptor, options);
  }

  /**
   * Gets a symbol's address.
   *
   * @param lookup the library lookup
   * @param name the symbol's name
   * @return the address
   * @throws KeyringException if the library does not export the symbol
   */
  static MemorySegment symbol(SymbolLookup lookup, String name) {
    return lookup.find(name).orElseThrow(
        () -> new KeyringException("native library does not export '" + name + "'"));
  }

  /**
   * Allocates a secret in native memory.
   * <p>
   * The segment is one byte larger than the secret and NUL-terminated, so it can also be passed as a C string.
   * It must be wiped by the caller after use.
   *
   * @param arena the arena
   * @param secret the secret
   * @return the native secret
   */
  static MemorySegment allocateSecret(Arena arena, byte[] secret) {
    MemorySegment segment = arena.allocate(secret.length + 1L);   // zeroed, hence NUL-terminated
    MemorySegment.copy(secret, 0, segment, ValueLayout.JAVA_BYTE, 0, secret.length);
    return segment;
  }

  /**
   * Copies a C string to a byte array.
   *
   * @param string the C string, NUL-terminated
   * @return the bytes without the terminator
   */
  @SuppressWarnings("restricted")
  static byte[] copyCString(MemorySegment string) {
    MemorySegment unbounded = string.reinterpret(Long.MAX_VALUE);
    long length = 0;
    while (unbounded.get(ValueLayout.JAVA_BYTE, length) != 0) {
      length++;
    }
    return unbounded.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
  }

  /**
   * Turns a throwable caught around a downcall into a keyring exception.
   *
   * @param function the function invoked
   * @param t the throwable
   * @return the exception to throw
   */
  static KeyringException failed(String function, Throwable t) {
    if (t instanceof KeyringException kex) {
      return kex;
    }
    if (t instanceof Error error) {
      throw error;
    }
    return new KeyringException(function + " failed", t);
  }


  private Native() {
  }

}
