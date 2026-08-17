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

package org.tentackle.quiche.ffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * The binding to the native Cloudflare quiche library.
 * <p>
 * Only the functions Tentackle actually uses are bound. The wrappers stay as close to the C API as the
 * FFM API allows &mdash; interpreting return codes, managing lifetimes, and enforcing the threading rules
 * is the job of the classes above this one.
 * <p>
 * Quiche reports failures as negative return codes rather than by any out-of-band mechanism, see the
 * {@code ERR_} constants. A return value of {@code >= 0} is always a success, and for the functions
 * filling a buffer it is the number of bytes written.
 */
public final class Quiche {

  /**
   * The QUIC version this library implements, i.e., version 1 (RFC-9000).
   */
  public static final int PROTOCOL_VERSION = 0x0000_0001;

  /**
   * The maximum length of a connection ID in bytes.
   */
  public static final int MAX_CONN_ID_LEN = 20;

  /**
   * The minimum length of a client initial packet, see RFC-9000, 14.1.
   */
  public static final int MIN_CLIENT_INITIAL_LEN = 1200;

  /**
   * The largest address validation token {@link #headerInfo} accepts.
   * <p>
   * RFC-9000 puts no limit on the token, but one that does not fit into an initial packet is useless,
   * and the tokens this transport issues are far smaller.
   */
  public static final int MAX_TOKEN_LEN = 256;

  /**
   * There is no more work to do.
   * <p>
   * This is not an error, but the normal way quiche signals "nothing (more) to read/write". Every
   * loop over {@code recv}, {@code send} or {@code stream_recv} terminates on it.
   */
  public static final int ERR_DONE = -1;

  /**
   * The provided buffer is too short.
   */
  public static final int ERR_BUFFER_TOO_SHORT = -2;

  /**
   * The provided packet cannot be parsed because its version is unknown.
   */
  public static final int ERR_UNKNOWN_VERSION = -3;

  /**
   * The provided packet cannot be parsed because it contains an invalid frame.
   */
  public static final int ERR_INVALID_FRAME = -4;

  /**
   * The provided packet cannot be parsed.
   */
  public static final int ERR_INVALID_PACKET = -5;

  /**
   * The operation cannot be completed because the connection is in an invalid state.
   */
  public static final int ERR_INVALID_STATE = -6;

  /**
   * The operation cannot be completed because the stream is in an invalid state.
   */
  public static final int ERR_INVALID_STREAM_STATE = -7;

  /**
   * The peer's transport parameters cannot be parsed.
   */
  public static final int ERR_INVALID_TRANSPORT_PARAM = -8;

  /**
   * A cryptographic operation failed.
   */
  public static final int ERR_CRYPTO_FAIL = -9;

  /**
   * The TLS handshake failed.
   */
  public static final int ERR_TLS_FAIL = -10;

  /**
   * The peer violated the local flow control limits.
   */
  public static final int ERR_FLOW_CONTROL = -11;

  /**
   * The peer violated the local stream limits.
   */
  public static final int ERR_STREAM_LIMIT = -12;

  /**
   * The specified stream was stopped by the peer.
   */
  public static final int ERR_STREAM_STOPPED = -15;

  /**
   * The specified stream was reset by the peer.
   */
  public static final int ERR_STREAM_RESET = -16;

  /**
   * The received data exceeds the stream's final size.
   */
  public static final int ERR_FINAL_SIZE = -13;

  /**
   * The operation cannot be completed because the congestion window is full.
   */
  public static final int ERR_CONGESTION_CONTROL = -14;

  /**
   * Too many identifiers were provided.
   */
  public static final int ERR_ID_LIMIT = -17;

  /**
   * Not enough available identifiers.
   */
  public static final int ERR_OUT_OF_IDENTIFIERS = -18;

  /**
   * The key update attempt failed.
   */
  public static final int ERR_KEY_UPDATE = -19;

  /**
   * The peer sent more data in CRYPTO frames than fits into the buffer.
   */
  public static final int ERR_CRYPTO_BUFFER_EXCEEDED = -20;


  private static final Linker LINKER = Linker.nativeLinker();

  private static final MethodHandle QUICHE_VERSION =
    downcall("quiche_version", FunctionDescriptor.of(ValueLayouts.POINTER));

  private static final MethodHandle QUICHE_VERSION_IS_SUPPORTED =
    downcall("quiche_version_is_supported", FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.UINT32));

  private static final MethodHandle QUICHE_ENABLE_DEBUG_LOGGING =
    downcall("quiche_enable_debug_logging",
             FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.POINTER));

  /**
   * The descriptor of quiche's log callback, {@code void (*cb)(const char *line, void *argp)}.
   */
  private static final FunctionDescriptor LOG_CALLBACK =
    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.POINTER);

  private static final MethodHandle QUICHE_NEGOTIATE_VERSION =
    downcall("quiche_negotiate_version",
             FunctionDescriptor.of(ValueLayouts.SSIZE_T,
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // scid
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // dcid
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T));  // out

  private static final MethodHandle QUICHE_RETRY =
    downcall("quiche_retry",
             FunctionDescriptor.of(ValueLayouts.SSIZE_T,
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // scid
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // dcid
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // new_scid
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // token
                                   ValueLayouts.UINT32,                          // version
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T));  // out

  private static final MethodHandle QUICHE_HEADER_INFO =
    downcall("quiche_header_info",
             FunctionDescriptor.of(ValueLayouts.INT,
                                   ValueLayouts.POINTER, ValueLayouts.SIZE_T,    // buf
                                   ValueLayouts.SIZE_T,                          // dcil
                                   ValueLayouts.POINTER,                         // version
                                   ValueLayouts.POINTER,                         // type
                                   ValueLayouts.POINTER, ValueLayouts.POINTER,   // scid
                                   ValueLayouts.POINTER, ValueLayouts.POINTER,   // dcid
                                   ValueLayouts.POINTER, ValueLayouts.POINTER)); // token


  /**
   * Gets the version of the native quiche library.
   *
   * @return the version string, for example {@code 0.29.3}
   */
  @SuppressWarnings("restricted")   // reinterpret: the C string's length is only known from its NUL
  public static String version() {
    try {
      MemorySegment version = (MemorySegment) QUICHE_VERSION.invokeExact();
      return version.reinterpret(Long.MAX_VALUE).getString(0);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_version failed", t);
    }
  }

  /**
   * Tells whether the given QUIC version is supported by the native library.
   *
   * @param version the QUIC version
   * @return true if supported
   */
  public static boolean versionIsSupported(int version) {
    try {
      return (boolean) QUICHE_VERSION_IS_SUPPORTED.invokeExact(version);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_version_is_supported failed", t);
    }
  }

  /**
   * Installs a callback receiving the native library's debug log.
   * <p>
   * Quiche accepts this exactly once per process and refuses every further attempt, so the callback stays
   * installed for the lifetime of the JVM. Its upcall stub is therefore allocated from
   * {@link Arena#global()} &mdash; the native library holds the pointer forever, and freeing it would
   * leave quiche calling into unmapped memory.
   *
   * @param consumer receives one log line at a time, on whatever thread is inside quiche
   * @return true if the callback was installed, false if there already was one
   */
  @SuppressWarnings("restricted")   // upcallStub: the native library calls back into the JVM
  public static boolean enableDebugLogging(Consumer<String> consumer) {
    MethodHandle handle;
    try {
      handle = MethodHandles.lookup()
                            .findStatic(Quiche.class, "logCallback",
                                        MethodType.methodType(void.class, Consumer.class,
                                                              MemorySegment.class, MemorySegment.class))
                            .bindTo(consumer);
    }
    catch (ReflectiveOperationException e) {
      throw new QuicheException("cannot bind the quiche log callback", e);
    }
    MemorySegment stub = LINKER.upcallStub(handle, LOG_CALLBACK, Arena.global());
    try {
      return (int) QUICHE_ENABLE_DEBUG_LOGGING.invokeExact(stub, MemorySegment.NULL) == 0;
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_enable_debug_logging failed", t);
    }
  }

  /**
   * The target of the log upcall stub.
   * <p>
   * Nothing may be thrown from here: an exception unwinding into the native caller terminates the JVM.
   *
   * @param consumer the consumer bound into the stub
   * @param line the log line, a NUL-terminated C string of unknown length
   * @param argp the opaque argument passed to {@code quiche_enable_debug_logging}, always NULL here
   */
  @SuppressWarnings("restricted")   // reinterpret: see version()
  private static void logCallback(Consumer<String> consumer, MemorySegment line, MemorySegment argp) {
    try {
      consumer.accept(line.reinterpret(Long.MAX_VALUE).getString(0));
    }
    catch (Throwable t) {    // NOSONAR: nothing may escape into the native caller
      // there is nowhere left to report this to
    }
  }

  /**
   * Writes a version negotiation packet.
   * <p>
   * A server sends this when a client offers a QUIC version it does not implement, see RFC-9000, 6.
   *
   * @param scid the connection ID the client used as its source
   * @param dcid the connection ID the client used as its destination
   * @param out the buffer to write the packet to
   * @param length the capacity of the buffer
   * @return the number of bytes written
   */
  public static long negotiateVersion(byte[] scid, byte[] dcid, MemorySegment out, long length) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment scidSegment = arena.allocateFrom(ValueLayouts.UINT8, scid);
      MemorySegment dcidSegment = arena.allocateFrom(ValueLayouts.UINT8, dcid);
      long result = (long) QUICHE_NEGOTIATE_VERSION.invokeExact(
        scidSegment, (long) scid.length, dcidSegment, (long) dcid.length, out, length);
      if (result < 0) {
        throw new QuicheException("quiche_negotiate_version failed", result);
      }
      return result;
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_negotiate_version failed", t);
    }
  }

  /**
   * Writes a retry packet.
   * <p>
   * A server sends this to validate that the client can actually receive at the address it claims, before
   * committing any state to the connection, see RFC-9000, 8.1.2. The token travels back with the client's
   * next initial packet, where it proves both the address and that the server issued it.
   *
   * @param scid the connection ID the client used as its source
   * @param dcid the connection ID the client used as its destination
   * @param newScid the connection ID the server wants the client to use from now on
   * @param token the address validation token
   * @param version the QUIC version of the client's packet
   * @param out the buffer to write the packet to
   * @param length the capacity of the buffer
   * @return the number of bytes written
   */
  public static long retry(byte[] scid, byte[] dcid, byte[] newScid, byte[] token,
                           int version, MemorySegment out, long length) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment scidSegment = arena.allocateFrom(ValueLayouts.UINT8, scid);
      MemorySegment dcidSegment = arena.allocateFrom(ValueLayouts.UINT8, dcid);
      MemorySegment newScidSegment = arena.allocateFrom(ValueLayouts.UINT8, newScid);
      MemorySegment tokenSegment = arena.allocateFrom(ValueLayouts.UINT8, token);
      long result = (long) QUICHE_RETRY.invokeExact(
        scidSegment, (long) scid.length, dcidSegment, (long) dcid.length,
        newScidSegment, (long) newScid.length, tokenSegment, (long) token.length,
        version, out, length);
      if (result < 0) {
        throw new QuicheException("quiche_retry failed", result);
      }
      return result;
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_retry failed", t);
    }
  }

  /**
   * Parses the header of an incoming packet, far enough to decide what to do with it.
   * <p>
   * This is what a server needs before it has a connection to feed the packet to: the connection IDs say
   * which connection it belongs to, or that a new one has to be created.
   *
   * @param buffer the packet
   * @param length the number of bytes in the buffer
   * @param dcidLength the length of the connection IDs this server issues
   * @return the parsed header
   * @throws QuicheException if the packet cannot be parsed
   */
  public static PacketHeader headerInfo(MemorySegment buffer, long length, int dcidLength) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment version = arena.allocate(ValueLayouts.UINT32);
      MemorySegment type = arena.allocate(ValueLayouts.UINT8);
      MemorySegment scid = arena.allocate(ValueLayouts.UINT8, MAX_CONN_ID_LEN);
      MemorySegment scidLength = arena.allocate(ValueLayouts.SIZE_T);
      MemorySegment dcid = arena.allocate(ValueLayouts.UINT8, MAX_CONN_ID_LEN);
      MemorySegment dcidLengthOut = arena.allocate(ValueLayouts.SIZE_T);
      MemorySegment token = arena.allocate(ValueLayouts.UINT8, MAX_TOKEN_LEN);
      MemorySegment tokenLength = arena.allocate(ValueLayouts.SIZE_T);

      // the out-parameters are in/out: they carry the capacity of the buffers on the way in
      scidLength.set(ValueLayouts.SIZE_T, 0, MAX_CONN_ID_LEN);
      dcidLengthOut.set(ValueLayouts.SIZE_T, 0, MAX_CONN_ID_LEN);
      tokenLength.set(ValueLayouts.SIZE_T, 0, MAX_TOKEN_LEN);

      int result = (int) QUICHE_HEADER_INFO.invokeExact(
        buffer, length, (long) dcidLength,
        version, type, scid, scidLength, dcid, dcidLengthOut, token, tokenLength);
      if (result < 0) {
        throw new QuicheException("quiche_header_info failed", result);
      }
      return new PacketHeader(
        version.get(ValueLayouts.UINT32, 0),
        Byte.toUnsignedInt(type.get(ValueLayouts.UINT8, 0)),
        toArray(scid, scidLength),
        toArray(dcid, dcidLengthOut),
        toArray(token, tokenLength));
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_header_info failed", t);
    }
  }

  /**
   * Copies an out-parameter buffer into an array of the length the callee reported.
   *
   * @param buffer the buffer
   * @param lengthOut the segment holding the length
   * @return the bytes
   */
  private static byte[] toArray(MemorySegment buffer, MemorySegment lengthOut) {
    long length = lengthOut.get(ValueLayouts.SIZE_T, 0);
    return length <= 0 ? new byte[0] : buffer.asSlice(0, length).toArray(ValueLayouts.UINT8);
  }

  /**
   * Gets a human-readable name for one of the {@code ERR_} codes.
   *
   * @param code the return code of a quiche function
   * @return the name, or the plain number if the code is not one of the known ones
   */
  public static String errorName(long code) {
    return switch ((int) code) {
      case ERR_DONE -> "DONE";
      case ERR_BUFFER_TOO_SHORT -> "BUFFER_TOO_SHORT";
      case ERR_UNKNOWN_VERSION -> "UNKNOWN_VERSION";
      case ERR_INVALID_FRAME -> "INVALID_FRAME";
      case ERR_INVALID_PACKET -> "INVALID_PACKET";
      case ERR_INVALID_STATE -> "INVALID_STATE";
      case ERR_INVALID_STREAM_STATE -> "INVALID_STREAM_STATE";
      case ERR_INVALID_TRANSPORT_PARAM -> "INVALID_TRANSPORT_PARAM";
      case ERR_CRYPTO_FAIL -> "CRYPTO_FAIL";
      case ERR_TLS_FAIL -> "TLS_FAIL";
      case ERR_FLOW_CONTROL -> "FLOW_CONTROL";
      case ERR_STREAM_LIMIT -> "STREAM_LIMIT";
      case ERR_STREAM_STOPPED -> "STREAM_STOPPED";
      case ERR_STREAM_RESET -> "STREAM_RESET";
      case ERR_FINAL_SIZE -> "FINAL_SIZE";
      case ERR_CONGESTION_CONTROL -> "CONGESTION_CONTROL";
      case ERR_ID_LIMIT -> "ID_LIMIT";
      case ERR_OUT_OF_IDENTIFIERS -> "OUT_OF_IDENTIFIERS";
      case ERR_KEY_UPDATE -> "KEY_UPDATE";
      case ERR_CRYPTO_BUFFER_EXCEEDED -> "CRYPTO_BUFFER_EXCEEDED";
      default -> String.valueOf(code);
    };
  }

  /**
   * Binds a function of the native library.
   *
   * @param name the symbol name
   * @param descriptor the function descriptor
   * @return the method handle
   * @throws QuicheException if the library does not export the symbol
   */
  @SuppressWarnings("restricted")   // downcallHandle: the descriptor is trusted to match quiche.h
  static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
    MemorySegment symbol = NativeLibrary.getLookup().find(name)
        .orElseThrow(() -> new QuicheException(
          "the native quiche library does not export '" + name + "' (version " + safeVersion() + ")"));
    return LINKER.downcallHandle(symbol, descriptor);
  }

  /**
   * Gets the library version for an error message.
   * <p>
   * Used while binding, where {@link #version()} may itself be the binding that failed.
   *
   * @return the version, or a placeholder if it cannot be determined
   */
  private static String safeVersion() {
    try {
      return version();
    }
    catch (RuntimeException e) {
      return "unknown";
    }
  }

  private Quiche() {
  }

}
