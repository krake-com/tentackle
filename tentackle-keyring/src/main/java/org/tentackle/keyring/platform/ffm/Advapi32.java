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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The binding to the Windows Credential Manager.
 * <p>
 * Uses the generic credentials of {@code advapi32.dll}, identified by their target name. The credentials
 * are persisted with {@code CRED_PERSIST_LOCAL_MACHINE}, which despite its name is per user, but does not
 * roam with the user's profile to other machines.
 * <p>
 * A credential's blob holds at most {@value #CRED_MAX_CREDENTIAL_BLOB_SIZE} bytes. Splitting larger
 * secrets is up to the caller.
 */
public final class Advapi32 {

  /**
   * The library name.
   */
  public static final String LIBRARY = "Advapi32.dll";

  /** {@code CRED_MAX_CREDENTIAL_BLOB_SIZE}: 5 * 512 bytes. */
  public static final int CRED_MAX_CREDENTIAL_BLOB_SIZE = 2560;

  /** {@code CRED_MAX_STRING_LENGTH}: the maximum length of the comment. */
  public static final int CRED_MAX_STRING_LENGTH = 256;

  /** {@code CRED_TYPE_GENERIC} */
  private static final int CRED_TYPE_GENERIC = 1;

  /** {@code CRED_PERSIST_LOCAL_MACHINE} */
  private static final int CRED_PERSIST_LOCAL_MACHINE = 2;

  /** {@code ERROR_NOT_FOUND} */
  private static final int ERROR_NOT_FOUND = 1168;

  /** {@code CREDENTIALW} from {@code wincred.h} */
  private static final StructLayout CREDENTIAL = MemoryLayout.structLayout(
      JAVA_INT.withName("Flags"),
      JAVA_INT.withName("Type"),
      ADDRESS.withName("TargetName"),
      ADDRESS.withName("Comment"),
      MemoryLayout.structLayout(JAVA_INT.withName("dwLowDateTime"),
                                JAVA_INT.withName("dwHighDateTime")).withName("LastWritten"),
      JAVA_INT.withName("CredentialBlobSize"),
      MemoryLayout.paddingLayout(4),
      ADDRESS.withName("CredentialBlob"),
      JAVA_INT.withName("Persist"),
      JAVA_INT.withName("AttributeCount"),
      ADDRESS.withName("Attributes"),
      ADDRESS.withName("TargetAlias"),
      ADDRESS.withName("UserName"));

  private static final long TYPE = offset("Type");
  private static final long TARGET_NAME = offset("TargetName");
  private static final long COMMENT = offset("Comment");
  private static final long BLOB_SIZE = offset("CredentialBlobSize");
  private static final long BLOB = offset("CredentialBlob");
  private static final long PERSIST = offset("Persist");
  private static final long USER_NAME = offset("UserName");


  private final StructLayout callState;   // the captured call state, which has GetLastError on Windows only
  private final VarHandle lastError;
  private final MethodHandle credWrite;
  private final MethodHandle credRead;
  private final MethodHandle credDelete;
  private final MethodHandle credFree;


  /**
   * Binds advapi32.
   *
   * @throws KeyringException if not running on Windows
   */
  public Advapi32() {
    SymbolLookup lookup = Native.library(LIBRARY, Arena.global());
    callState = Linker.Option.captureStateLayout();
    lastError = callState.varHandle(PathElement.groupElement("GetLastError"));
    Linker.Option captureLastError = Linker.Option.captureCallState("GetLastError");

    // BOOL CredWriteW(PCREDENTIALW Credential, DWORD Flags)
    credWrite = Native.downcall(lookup, "CredWriteW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), captureLastError);
    // BOOL CredReadW(LPCWSTR TargetName, DWORD Type, DWORD Flags, PCREDENTIALW *Credential)
    credRead = Native.downcall(lookup, "CredReadW",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), captureLastError);
    // BOOL CredDeleteW(LPCWSTR TargetName, DWORD Type, DWORD Flags)
    credDelete = Native.downcall(lookup, "CredDeleteW",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), captureLastError);
    // void CredFree(PVOID Buffer)
    credFree = Native.downcall(lookup, "CredFree", FunctionDescriptor.ofVoid(ADDRESS));
  }

  /**
   * Writes a generic credential.<br>
   * An existing credential with the same target name is replaced.
   *
   * @param targetName the target name
   * @param userName the username
   * @param comment the comment, truncated if too long
   * @param blob the blob, at most {@value #CRED_MAX_CREDENTIAL_BLOB_SIZE} bytes
   */
  public void write(String targetName, String userName, String comment, byte[] blob) {
    if (blob.length > CRED_MAX_CREDENTIAL_BLOB_SIZE) {
      throw new KeyringException("credential blob too large: " + blob.length);
    }
    if (comment.length() >= CRED_MAX_STRING_LENGTH) {
      comment = comment.substring(0, CRED_MAX_STRING_LENGTH - 1);
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment state = arena.allocate(callState);
      MemorySegment nativeBlob = Native.allocateSecret(arena, blob);
      try {
        MemorySegment credential = arena.allocate(CREDENTIAL);    // zeroed
        credential.set(JAVA_INT, TYPE, CRED_TYPE_GENERIC);
        credential.set(ADDRESS, TARGET_NAME, wide(arena, targetName));
        credential.set(ADDRESS, COMMENT, wide(arena, comment));
        credential.set(JAVA_INT, BLOB_SIZE, blob.length);
        credential.set(ADDRESS, BLOB, nativeBlob);
        credential.set(JAVA_INT, PERSIST, CRED_PERSIST_LOCAL_MACHINE);
        credential.set(ADDRESS, USER_NAME, wide(arena, userName));
        if ((int) credWrite.invokeExact(state, credential, 0) == 0) {
          throw new KeyringException("CredWriteW failed: error " + lastError(state));
        }
      }
      finally {
        nativeBlob.fill((byte) 0);
      }
    }
    catch (Throwable t) {
      throw Native.failed("CredWriteW", t);
    }
  }

  /**
   * Reads the blob of a generic credential.
   *
   * @param targetName the target name
   * @return the blob, null if not found
   */
  @SuppressWarnings("restricted")
  public byte[] read(String targetName) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment state = arena.allocate(callState);
      MemorySegment credentialRef = arena.allocate(ADDRESS);
      if ((int) credRead.invokeExact(state, wide(arena, targetName), CRED_TYPE_GENERIC, 0, credentialRef) == 0) {
        int error = lastError(state);
        if (error == ERROR_NOT_FOUND) {
          return null;
        }
        throw new KeyringException("CredReadW failed: error " + error);
      }
      MemorySegment credential = credentialRef.get(ADDRESS, 0).reinterpret(CREDENTIAL.byteSize());
      try {
        int size = credential.get(JAVA_INT, BLOB_SIZE);
        MemorySegment blob = credential.get(ADDRESS, BLOB).reinterpret(size);
        byte[] bytes = blob.toArray(JAVA_BYTE);
        blob.fill((byte) 0);
        return bytes;
      }
      finally {
        credFree.invokeExact(credential);
      }
    }
    catch (Throwable t) {
      throw Native.failed("CredReadW", t);
    }
  }

  /**
   * Deletes a generic credential.
   *
   * @param targetName the target name
   * @return true if deleted, false if not found
   */
  public boolean delete(String targetName) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment state = arena.allocate(callState);
      if ((int) credDelete.invokeExact(state, wide(arena, targetName), CRED_TYPE_GENERIC, 0) == 0) {
        int error = lastError(state);
        if (error == ERROR_NOT_FOUND) {
          return false;
        }
        throw new KeyringException("CredDeleteW failed: error " + error);
      }
      return true;
    }
    catch (Throwable t) {
      throw Native.failed("CredDeleteW", t);
    }
  }


  private static long offset(String name) {
    return CREDENTIAL.byteOffset(PathElement.groupElement(name));
  }

  private static MemorySegment wide(Arena arena, String str) {
    return arena.allocateFrom(str, StandardCharsets.UTF_16LE);   // NUL-terminated
  }

  private int lastError(MemorySegment state) {
    return (int) lastError.get(state, 0L);
  }

}
