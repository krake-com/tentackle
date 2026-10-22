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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The binding to the keychain services of macOS.
 * <p>
 * Uses the {@code SecItem} API of {@code Security.framework} with items of class {@code kSecClassGenericPassword},
 * identified by {@code kSecAttrService} and {@code kSecAttrAccount}. The items go to the user's default
 * keychain, the login keychain. The data protection keychain would need a signed application with a
 * keychain access group entitlement, which cannot be assumed.
 * <p>
 * The {@code SecItem} functions take CoreFoundation dictionaries, so part of CoreFoundation is bound as well.
 * All CoreFoundation objects created are released before a method returns.
 */
public final class MacSecurity {

  /**
   * The CoreFoundation framework.
   */
  public static final String CORE_FOUNDATION = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation";

  /**
   * The Security framework.
   */
  public static final String SECURITY = "/System/Library/Frameworks/Security.framework/Security";

  /** {@code errSecSuccess} */
  public static final int ERR_SEC_SUCCESS = 0;

  /** {@code errSecItemNotFound} */
  public static final int ERR_SEC_ITEM_NOT_FOUND = -25300;

  /** {@code errSecDuplicateItem} */
  public static final int ERR_SEC_DUPLICATE_ITEM = -25299;

  /** {@code kCFStringEncodingUTF8} */
  private static final int CF_STRING_ENCODING_UTF8 = 0x0800_0100;

  /** {@code CFIndex} is a {@code signed long}. */
  private static final ValueLayout.OfLong CF_INDEX = JAVA_LONG;

  /** {@code OSStatus} is a {@code SInt32}. */
  private static final ValueLayout.OfInt OS_STATUS = JAVA_INT;


  private final MethodHandle cfStringCreateWithBytes;
  private final MethodHandle cfStringGetCString;
  private final MethodHandle cfDataCreate;
  private final MethodHandle cfDataGetLength;
  private final MethodHandle cfDataGetBytePtr;
  private final MethodHandle cfDictionaryCreate;
  private final MethodHandle cfRelease;
  private final MethodHandle secItemAdd;
  private final MethodHandle secItemCopyMatching;
  private final MethodHandle secItemUpdate;
  private final MethodHandle secItemDelete;
  private final MethodHandle secCopyErrorMessageString;

  private final MemorySegment keyCallBacks;       // &kCFTypeDictionaryKeyCallBacks
  private final MemorySegment valueCallBacks;     // &kCFTypeDictionaryValueCallBacks
  private final MemorySegment cfBooleanTrue;
  private final MemorySegment secClass;
  private final MemorySegment secClassGenericPassword;
  private final MemorySegment secAttrService;
  private final MemorySegment secAttrAccount;
  private final MemorySegment secAttrLabel;
  private final MemorySegment secValueData;
  private final MemorySegment secReturnData;
  private final MemorySegment secMatchLimit;
  private final MemorySegment secMatchLimitOne;


  /**
   * Binds the frameworks.
   *
   * @throws KeyringException if not running on macOS
   */
  public MacSecurity() {
    Arena arena = Arena.global();     // the frameworks are part of every process anyway
    SymbolLookup cf = Native.library(CORE_FOUNDATION, arena);
    SymbolLookup sec = Native.library(SECURITY, arena);

    // CFStringRef CFStringCreateWithBytes(CFAllocatorRef alloc, const UInt8 *bytes, CFIndex numBytes,
    //                                     CFStringEncoding encoding, Boolean isExternalRepresentation)
    cfStringCreateWithBytes = Native.downcall(cf, "CFStringCreateWithBytes",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, CF_INDEX, JAVA_INT, JAVA_BYTE));
    // Boolean CFStringGetCString(CFStringRef theString, char *buffer, CFIndex bufferSize, CFStringEncoding encoding)
    cfStringGetCString = Native.downcall(cf, "CFStringGetCString",
        FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, CF_INDEX, JAVA_INT));
    // CFDataRef CFDataCreate(CFAllocatorRef allocator, const UInt8 *bytes, CFIndex length)
    cfDataCreate = Native.downcall(cf, "CFDataCreate", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, CF_INDEX));
    // CFIndex CFDataGetLength(CFDataRef theData)
    cfDataGetLength = Native.downcall(cf, "CFDataGetLength", FunctionDescriptor.of(CF_INDEX, ADDRESS));
    // const UInt8 *CFDataGetBytePtr(CFDataRef theData)
    cfDataGetBytePtr = Native.downcall(cf, "CFDataGetBytePtr", FunctionDescriptor.of(ADDRESS, ADDRESS));
    // CFDictionaryRef CFDictionaryCreate(CFAllocatorRef allocator, const void **keys, const void **values,
    //    CFIndex numValues, const CFDictionaryKeyCallBacks *keyCallBacks, const CFDictionaryValueCallBacks *valueCallBacks)
    cfDictionaryCreate = Native.downcall(cf, "CFDictionaryCreate",
        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, CF_INDEX, ADDRESS, ADDRESS));
    // void CFRelease(CFTypeRef cf)
    cfRelease = Native.downcall(cf, "CFRelease", FunctionDescriptor.ofVoid(ADDRESS));

    // OSStatus SecItemAdd(CFDictionaryRef attributes, CFTypeRef *result)
    secItemAdd = Native.downcall(sec, "SecItemAdd", FunctionDescriptor.of(OS_STATUS, ADDRESS, ADDRESS));
    // OSStatus SecItemCopyMatching(CFDictionaryRef query, CFTypeRef *result)
    secItemCopyMatching = Native.downcall(sec, "SecItemCopyMatching", FunctionDescriptor.of(OS_STATUS, ADDRESS, ADDRESS));
    // OSStatus SecItemUpdate(CFDictionaryRef query, CFDictionaryRef attributesToUpdate)
    secItemUpdate = Native.downcall(sec, "SecItemUpdate", FunctionDescriptor.of(OS_STATUS, ADDRESS, ADDRESS));
    // OSStatus SecItemDelete(CFDictionaryRef query)
    secItemDelete = Native.downcall(sec, "SecItemDelete", FunctionDescriptor.of(OS_STATUS, ADDRESS));
    // CFStringRef SecCopyErrorMessageString(OSStatus status, void *reserved)
    secCopyErrorMessageString = Native.downcall(sec, "SecCopyErrorMessageString",
        FunctionDescriptor.of(ADDRESS, OS_STATUS, ADDRESS));

    keyCallBacks = Native.symbol(cf, "kCFTypeDictionaryKeyCallBacks");
    valueCallBacks = Native.symbol(cf, "kCFTypeDictionaryValueCallBacks");
    cfBooleanTrue = constant(cf, "kCFBooleanTrue");
    secClass = constant(sec, "kSecClass");
    secClassGenericPassword = constant(sec, "kSecClassGenericPassword");
    secAttrService = constant(sec, "kSecAttrService");
    secAttrAccount = constant(sec, "kSecAttrAccount");
    secAttrLabel = constant(sec, "kSecAttrLabel");
    secValueData = constant(sec, "kSecValueData");
    secReturnData = constant(sec, "kSecReturnData");
    secMatchLimit = constant(sec, "kSecMatchLimit");
    secMatchLimitOne = constant(sec, "kSecMatchLimitOne");
  }

  /**
   * Adds a generic password.
   *
   * @param service the service
   * @param account the account
   * @param label the label
   * @param password the password
   * @return {@link #ERR_SEC_SUCCESS} or {@link #ERR_SEC_DUPLICATE_ITEM}
   * @throws KeyringException for all other errors
   */
  public int add(String service, String account, String label, byte[] password) {
    try (CFScope scope = new CFScope()) {
      MemorySegment attributes = scope.dictionary(
          secClass, secClassGenericPassword,
          secAttrService, scope.string(service),
          secAttrAccount, scope.string(account),
          secAttrLabel, scope.string(label),
          secValueData, scope.data(password));
      int status = (int) secItemAdd.invokeExact(attributes, MemorySegment.NULL);
      if (status != ERR_SEC_DUPLICATE_ITEM) {
        check("SecItemAdd", status);
      }
      return status;
    }
    catch (Throwable t) {
      throw Native.failed("SecItemAdd", t);
    }
  }

  /**
   * Updates the data and label of an existing generic password.
   *
   * @param service the service
   * @param account the account
   * @param label the label
   * @param password the password
   * @return {@link #ERR_SEC_SUCCESS} or {@link #ERR_SEC_ITEM_NOT_FOUND}
   * @throws KeyringException for all other errors
   */
  public int update(String service, String account, String label, byte[] password) {
    try (CFScope scope = new CFScope()) {
      MemorySegment query = scope.dictionary(
          secClass, secClassGenericPassword,
          secAttrService, scope.string(service),
          secAttrAccount, scope.string(account));
      MemorySegment attributes = scope.dictionary(
          secAttrLabel, scope.string(label),
          secValueData, scope.data(password));
      int status = (int) secItemUpdate.invokeExact(query, attributes);
      if (status != ERR_SEC_ITEM_NOT_FOUND) {
        check("SecItemUpdate", status);
      }
      return status;
    }
    catch (Throwable t) {
      throw Native.failed("SecItemUpdate", t);
    }
  }

  /**
   * Copies the data of a generic password.
   *
   * @param service the service
   * @param account the account
   * @return the password, null if not found
   * @throws KeyringException if the keychain could not be accessed or the user denied access
   */
  @SuppressWarnings("restricted")
  public byte[] copy(String service, String account) {
    try (CFScope scope = new CFScope()) {
      MemorySegment query = scope.dictionary(
          secClass, secClassGenericPassword,
          secAttrService, scope.string(service),
          secAttrAccount, scope.string(account),
          secReturnData, cfBooleanTrue,
          secMatchLimit, secMatchLimitOne);
      MemorySegment resultRef = scope.arena.allocate(ADDRESS);
      int status = (int) secItemCopyMatching.invokeExact(query, resultRef);
      if (status == ERR_SEC_ITEM_NOT_FOUND) {
        return null;
      }
      check("SecItemCopyMatching", status);
      MemorySegment data = scope.track(resultRef.get(ADDRESS, 0));
      long length = (long) cfDataGetLength.invokeExact(data);
      MemorySegment bytes = (MemorySegment) cfDataGetBytePtr.invokeExact(data);
      return bytes.reinterpret(length).toArray(JAVA_BYTE);
    }
    catch (Throwable t) {
      throw Native.failed("SecItemCopyMatching", t);
    }
  }

  /**
   * Deletes a generic password.
   *
   * @param service the service
   * @param account the account
   * @return true if deleted, false if not found
   * @throws KeyringException for all other errors
   */
  public boolean delete(String service, String account) {
    try (CFScope scope = new CFScope()) {
      MemorySegment query = scope.dictionary(
          secClass, secClassGenericPassword,
          secAttrService, scope.string(service),
          secAttrAccount, scope.string(account));
      int status = (int) secItemDelete.invokeExact(query);
      if (status == ERR_SEC_ITEM_NOT_FOUND) {
        return false;
      }
      check("SecItemDelete", status);
      return true;
    }
    catch (Throwable t) {
      throw Native.failed("SecItemDelete", t);
    }
  }


  /**
   * Reads a global {@code CFTypeRef} constant.
   *
   * @param lookup the framework
   * @param name the constant's name
   * @return the constant's value
   */
  @SuppressWarnings("restricted")
  private static MemorySegment constant(SymbolLookup lookup, String name) {
    return Native.symbol(lookup, name).reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
  }

  /**
   * Throws an exception unless the status is {@link #ERR_SEC_SUCCESS}.
   *
   * @param function the function's name
   * @param status the status
   * @throws Throwable if the error message could not be determined
   */
  private void check(String function, int status) throws Throwable {
    if (status != ERR_SEC_SUCCESS) {
      throw new KeyringException(function + " failed: " + errorMessage(status) + " (OSStatus " + status + ")");
    }
  }

  /**
   * Gets the error message of a status.
   *
   * @param status the status
   * @return the message
   * @throws Throwable if a downcall failed
   */
  private String errorMessage(int status) throws Throwable {
    MemorySegment message = (MemorySegment) secCopyErrorMessageString.invokeExact(status, MemorySegment.NULL);
    if (message.equals(MemorySegment.NULL)) {
      return "unknown error";
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(1024);
      byte ok = (byte) cfStringGetCString.invokeExact(message, buffer, buffer.byteSize(), CF_STRING_ENCODING_UTF8);
      return ok != 0 ? buffer.getString(0) : "unknown error";
    }
    finally {
      cfRelease.invokeExact(message);
    }
  }


  /**
   * Tracks the native memory and the CoreFoundation objects of a single call.
   */
  private final class CFScope implements AutoCloseable {

    private final Arena arena = Arena.ofConfined();
    private final List<MemorySegment> objects = new ArrayList<>();
    private final List<MemorySegment> secrets = new ArrayList<>();

    /**
     * Tracks a CoreFoundation object to be released.
     *
     * @param object the object
     * @return the object
     */
    MemorySegment track(MemorySegment object) {
      if (object.equals(MemorySegment.NULL)) {
        throw new KeyringException("CoreFoundation object creation failed");
      }
      objects.add(object);
      return object;
    }

    /**
     * Creates a {@code CFString}.
     *
     * @param value the Java string
     * @return the CFString
     * @throws Throwable if the downcall failed
     */
    MemorySegment string(String value) throws Throwable {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      MemorySegment segment = arena.allocateFrom(JAVA_BYTE, bytes);
      return track((MemorySegment) cfStringCreateWithBytes.invokeExact(
          MemorySegment.NULL, segment, (long) bytes.length, CF_STRING_ENCODING_UTF8, (byte) 0));
    }

    /**
     * Creates a {@code CFData}.
     * <p>
     * CFData copies the bytes, and the copy cannot be wiped, as CFData is immutable.
     * The native memory passed to it is wiped, though.
     *
     * @param bytes the bytes
     * @return the CFData
     * @throws Throwable if the downcall failed
     */
    MemorySegment data(byte[] bytes) throws Throwable {
      MemorySegment segment = Native.allocateSecret(arena, bytes);
      secrets.add(segment);
      return track((MemorySegment) cfDataCreate.invokeExact(MemorySegment.NULL, segment, (long) bytes.length));
    }

    /**
     * Creates a {@code CFDictionary}.
     *
     * @param keysAndValues the alternating keys and values
     * @return the CFDictionary
     * @throws Throwable if the downcall failed
     */
    MemorySegment dictionary(MemorySegment... keysAndValues) throws Throwable {
      int count = keysAndValues.length / 2;
      MemorySegment keys = arena.allocate(ADDRESS, count);
      MemorySegment values = arena.allocate(ADDRESS, count);
      for (int i = 0; i < count; i++) {
        keys.setAtIndex(ADDRESS, i, keysAndValues[2 * i]);
        values.setAtIndex(ADDRESS, i, keysAndValues[2 * i + 1]);
      }
      return track((MemorySegment) cfDictionaryCreate.invokeExact(
          MemorySegment.NULL, keys, values, (long) count, keyCallBacks, valueCallBacks));
    }

    @Override
    public void close() {
      try {
        // release in reverse order: dictionaries retain their contents
        for (int i = objects.size() - 1; i >= 0; i--) {
          cfRelease.invokeExact(objects.get(i));
        }
      }
      catch (Throwable t) {
        throw Native.failed("CFRelease", t);
      }
      finally {
        for (MemorySegment secret : secrets) {
          secret.fill((byte) 0);
        }
        arena.close();
      }
    }
  }

}
