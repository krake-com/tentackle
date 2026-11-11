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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code quiche_conn}, one QUIC connection.
 * <p>
 * This is a thin wrapper: the return codes are turned into exceptions where they mean a failure, and left
 * alone where they carry information &mdash; {@link Quiche#ERR_DONE} in particular, which is how quiche says
 * "nothing more to do" and which every loop over this API terminates on.
 * <p>
 * <strong>A connection is not thread-safe.</strong> Quiche performs no locking of its own, and two threads
 * inside the same connection corrupt its state. Serializing the access is the caller's job; the classes in
 * {@code org.tentackle.quiche.engine} do it with one lock per connection.
 * <p>
 * The buffers are passed as {@link MemorySegment}s rather than {@code byte[]} so that the caller can reuse
 * them: there is one call per datagram in each direction, and copying through a fresh array every time
 * would dominate the cost of the transport.
 */
public class QuicheConn implements AutoCloseable {

  /**
   * The {@code QUICHE_SHUTDOWN_READ} direction of {@link #streamShutdown}.
   */
  public static final int SHUTDOWN_READ = 0;

  /**
   * The {@code QUICHE_SHUTDOWN_WRITE} direction of {@link #streamShutdown}.
   */
  public static final int SHUTDOWN_WRITE = 1;


  private static final MethodHandle CONNECT =
    Quiche.downcall("quiche_connect",
                    FunctionDescriptor.of(ValueLayouts.POINTER,
                                          ValueLayouts.POINTER,                          // server_name
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T,     // scid
                                          ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // local
                                          ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // peer
                                          ValueLayouts.POINTER));                        // config

  private static final MethodHandle ACCEPT =
    Quiche.downcall("quiche_accept",
                    FunctionDescriptor.of(ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T,     // scid
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T,     // odcid
                                          ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // local
                                          ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // peer
                                          ValueLayouts.POINTER));                        // config

  private static final MethodHandle CONN_RECV =
    Quiche.downcall("quiche_conn_recv",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T, ValueLayouts.POINTER));

  private static final MethodHandle CONN_SEND =
    Quiche.downcall("quiche_conn_send",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T, ValueLayouts.POINTER));

  private static final MethodHandle STREAM_RECV =
    Quiche.downcall("quiche_conn_stream_recv",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER, ValueLayouts.UINT64,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T,
                                          ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle STREAM_SEND =
    Quiche.downcall("quiche_conn_stream_send",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER, ValueLayouts.UINT64,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T, ValueLayouts.BOOL,
                                          ValueLayouts.POINTER));

  private static final MethodHandle STREAM_SHUTDOWN =
    Quiche.downcall("quiche_conn_stream_shutdown",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.UINT64,
                                          ValueLayouts.INT, ValueLayouts.UINT64));

  private static final MethodHandle STREAM_CAPACITY =
    Quiche.downcall("quiche_conn_stream_capacity",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER, ValueLayouts.UINT64));

  private static final MethodHandle STREAM_FINISHED =
    Quiche.downcall("quiche_conn_stream_finished",
                    FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.POINTER, ValueLayouts.UINT64));

  private static final MethodHandle STREAM_READABLE =
    Quiche.downcall("quiche_conn_stream_readable",
                    FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.POINTER, ValueLayouts.UINT64));

  private static final MethodHandle STREAM_READABLE_NEXT =
    Quiche.downcall("quiche_conn_stream_readable_next",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayouts.POINTER));

  private static final MethodHandle STREAM_WRITABLE_NEXT =
    Quiche.downcall("quiche_conn_stream_writable_next",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayouts.POINTER));

  private static final MethodHandle TIMEOUT_AS_MILLIS =
    Quiche.downcall("quiche_conn_timeout_as_millis",
                    FunctionDescriptor.of(ValueLayouts.UINT64, ValueLayouts.POINTER));

  private static final MethodHandle ON_TIMEOUT =
    Quiche.downcall("quiche_conn_on_timeout", FunctionDescriptor.ofVoid(ValueLayouts.POINTER));

  private static final MethodHandle CONN_CLOSE =
    Quiche.downcall("quiche_conn_close",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.BOOL,
                                          ValueLayouts.UINT64, ValueLayouts.POINTER, ValueLayouts.SIZE_T));

  private static final MethodHandle CONN_FREE =
    Quiche.downcall("quiche_conn_free", FunctionDescriptor.ofVoid(ValueLayouts.POINTER));

  private static final MethodHandle SEND_ACK_ELICITING =
    Quiche.downcall("quiche_conn_send_ack_eliciting",
                    FunctionDescriptor.of(ValueLayouts.SSIZE_T, ValueLayouts.POINTER));

  private static final MethodHandle PEER_STREAMS_LEFT_BIDI =
    Quiche.downcall("quiche_conn_peer_streams_left_bidi",
                    FunctionDescriptor.of(ValueLayouts.UINT64, ValueLayouts.POINTER));

  private static final MethodHandle LOCAL_ERROR =
    Quiche.downcall("quiche_conn_local_error",
                    FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER,   // bool *is_app
                                          ValueLayouts.POINTER,   // uint64_t *error_code
                                          ValueLayouts.POINTER,   // const uint8_t **reason
                                          ValueLayouts.POINTER)); // size_t *reason_len

  private static final MethodHandle SCIDS_LEFT = counter("quiche_conn_scids_left");
  private static final MethodHandle AVAILABLE_DCIDS = counter("quiche_conn_available_dcids");
  private static final MethodHandle RETIRED_SCIDS = counter("quiche_conn_retired_scids");

  private static final MethodHandle NEW_SCID =
    Quiche.downcall("quiche_conn_new_scid",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T,     // scid
                                          ValueLayouts.POINTER,                          // reset_token
                                          ValueLayouts.BOOL,                             // retire_if_needed
                                          ValueLayouts.POINTER));                        // uint64_t *scid_seq

  private static final MethodHandle RETIRE_DCID =
    Quiche.downcall("quiche_conn_retire_dcid",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.UINT64));

  private static final MethodHandle RETIRED_SCID_ITER =
    Quiche.downcall("quiche_conn_retired_scid_iter",
                    FunctionDescriptor.of(ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle CONNECTION_ID_ITER_NEXT =
    Quiche.downcall("quiche_connection_id_iter_next",
                    FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle CONNECTION_ID_ITER_FREE =
    Quiche.downcall("quiche_connection_id_iter_free", FunctionDescriptor.ofVoid(ValueLayouts.POINTER));

  private static final MethodHandle PROBE_PATH = pathSetter("quiche_conn_probe_path");
  private static final MethodHandle MIGRATE = pathSetter("quiche_conn_migrate");

  private static final MethodHandle PATH_EVENT_NEXT =
    Quiche.downcall("quiche_conn_path_event_next",
                    FunctionDescriptor.of(ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle PATH_EVENT_TYPE =
    Quiche.downcall("quiche_path_event_type",
                    FunctionDescriptor.of(ValueLayouts.UINT32, ValueLayouts.POINTER));

  private static final MethodHandle PATH_EVENT_FREE =
    Quiche.downcall("quiche_path_event_free", FunctionDescriptor.ofVoid(ValueLayouts.POINTER));

  private static final MethodHandle PATH_EVENT_NEW = pathEventAddresses("quiche_path_event_new");
  private static final MethodHandle PATH_EVENT_VALIDATED = pathEventAddresses("quiche_path_event_validated");
  private static final MethodHandle PATH_EVENT_FAILED_VALIDATION =
    pathEventAddresses("quiche_path_event_failed_validation");
  private static final MethodHandle PATH_EVENT_CLOSED = pathEventAddresses("quiche_path_event_closed");
  private static final MethodHandle PATH_EVENT_PEER_MIGRATED =
    pathEventAddresses("quiche_path_event_peer_migrated");
  private static final MethodHandle PATH_EVENT_PMTU_UPDATED =
    Quiche.downcall("quiche_path_event_pmtu_updated",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER,
                                              ValueLayouts.POINTER, ValueLayouts.POINTER,
                                              ValueLayouts.POINTER, ValueLayouts.POINTER,
                                              ValueLayouts.POINTER));

  private static final MethodHandle CONN_STATS =
    Quiche.downcall("quiche_conn_stats",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle PATH_STATS =
    Quiche.downcall("quiche_conn_path_stats",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.SIZE_T,
                                          ValueLayouts.POINTER));

  /**
   * The leading fields of {@code quiche_stats}, up to the last one read here.
   */
  private static final MemoryLayout STATS_PREFIX = MemoryLayout.structLayout(
    ValueLayouts.SIZE_T.withName("recv"),
    ValueLayouts.SIZE_T.withName("sent"),
    ValueLayouts.SIZE_T.withName("lost"),
    ValueLayouts.SIZE_T.withName("spurious_lost"),
    ValueLayouts.SIZE_T.withName("retrans"),
    ValueLayouts.UINT64.withName("sent_bytes"),
    ValueLayouts.UINT64.withName("recv_bytes"),
    ValueLayouts.UINT64.withName("acked_bytes"),
    ValueLayouts.UINT64.withName("lost_bytes"),
    ValueLayouts.UINT64.withName("stream_retrans_bytes"),
    ValueLayouts.SIZE_T.withName("dgram_recv"),
    ValueLayouts.SIZE_T.withName("dgram_sent"),
    ValueLayouts.SIZE_T.withName("paths_count"));

  /**
   * The leading fields of {@code quiche_path_stats}, up to the last one read here.
   */
  private static final MemoryLayout PATH_STATS_PREFIX = MemoryLayout.structLayout(
    SockAddr.STORAGE.withName("local_addr"),
    ValueLayouts.SOCKLEN_T.withName("local_addr_len"),
    MemoryLayout.paddingLayout(4),
    SockAddr.STORAGE.withName("peer_addr"),
    ValueLayouts.SOCKLEN_T.withName("peer_addr_len"),
    MemoryLayout.paddingLayout(4),
    ValueLayouts.SSIZE_T.withName("validation_state"),
    ValueLayouts.BOOL.withName("active"),
    MemoryLayout.paddingLayout(7),
    ValueLayouts.SIZE_T.withName("recv"),
    ValueLayouts.SIZE_T.withName("sent"),
    ValueLayouts.SIZE_T.withName("lost"),
    ValueLayouts.SIZE_T.withName("retrans"),
    ValueLayouts.SIZE_T.withName("total_pto_count"));

  private static final long ACKED_BYTES_OFFSET = STATS_PREFIX.byteOffset(PathElement.groupElement("acked_bytes"));
  private static final long PATHS_COUNT_OFFSET = STATS_PREFIX.byteOffset(PathElement.groupElement("paths_count"));
  private static final long ACTIVE_OFFSET = PATH_STATS_PREFIX.byteOffset(PathElement.groupElement("active"));
  private static final long TOTAL_PTO_COUNT_OFFSET =
    PATH_STATS_PREFIX.byteOffset(PathElement.groupElement("total_pto_count"));

  /**
   * The size of the buffers the statistics are written to.
   * <p>
   * Quiche fills in the whole structure, not just the prefix read here, and a later quiche version may
   * append fields. Too small a buffer would be a silent heap overflow, so it is sized well beyond both
   * structures (208 and 448 bytes as of quiche 0.30).
   */
  private static final long STATS_BUFFER_SIZE = 1024;

  /**
   * The length of a stateless reset token.
   */
  private static final int RESET_TOKEN_LENGTH = 16;

  private static final MethodHandle IS_ESTABLISHED = predicate("quiche_conn_is_established");
  private static final MethodHandle IS_CLOSED = predicate("quiche_conn_is_closed");
  private static final MethodHandle IS_DRAINING = predicate("quiche_conn_is_draining");
  private static final MethodHandle IS_TIMED_OUT = predicate("quiche_conn_is_timed_out");

  private static final MethodHandle TRACE_ID = accessor("quiche_conn_trace_id");
  private static final MethodHandle SOURCE_ID = accessor("quiche_conn_source_id");
  private static final MethodHandle DESTINATION_ID = accessor("quiche_conn_destination_id");
  private static final MethodHandle APPLICATION_PROTO = accessor("quiche_conn_application_proto");


  private final Arena arena;              // owns the out-parameter scratch below
  private final MemorySegment finOut;     // bool*, the "fin" out-parameter of streamRecv, and "is_app" of localError
  private final MemorySegment errorOut;   // uint64_t*, the error code out-parameter of the stream calls and localError
  private final MemorySegment pointerOut; // const uint8_t**, the out-parameter of the accessors and localError
  private final MemorySegment lengthOut;  // size_t*, the length out-parameter of the accessors and localError
  private final MemorySegment seqOut;     // uint64_t*, the sequence number out-parameter of the path and CID calls
  private final MemorySegment statsOut;   // quiche_stats* and quiche_path_stats*, see STATS_BUFFER_SIZE
  private MemorySegment conn;             // the native connection, null once closed

  /**
   * Wraps a native connection pointer.
   *
   * @param conn the pointer; must not be NULL
   */
  protected QuicheConn(MemorySegment conn) {
    this.conn = conn;
    arena = Arena.ofShared();
    finOut = arena.allocate(ValueLayouts.BOOL);
    errorOut = arena.allocate(ValueLayouts.UINT64);
    pointerOut = arena.allocate(ValueLayouts.POINTER);
    lengthOut = arena.allocate(ValueLayouts.SIZE_T);
    seqOut = arena.allocate(ValueLayouts.UINT64);
    statsOut = arena.allocate(STATS_BUFFER_SIZE, 8);
  }


  /**
   * Creates a client connection.<br>
   * Its initial packet is produced by the first {@link #send}.
   *
   * @param serverName the name verified against the server's certificate, null to skip that check
   * @param scid this side's connection ID, at most {@link Quiche#MAX_CONN_ID_LEN} bytes
   * @param local the local address
   * @param peer the server's address
   * @param config the configuration, which must outlive the connection
   * @return the connection
   * @throws QuicheException if quiche refuses to create the connection
   * @throws IllegalArgumentException if an address is unresolved
   */
  public static QuicheConn connect(String serverName, byte[] scid,
                                   InetSocketAddress local, InetSocketAddress peer, QuicheConfig config) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment name = serverName == null ? MemorySegment.NULL : arena.allocateFrom(serverName);
      MemorySegment scidSegment = arena.allocateFrom(ValueLayouts.UINT8, scid);
      MemorySegment localSegment = arena.allocate(SockAddr.STORAGE);
      MemorySegment peerSegment = arena.allocate(SockAddr.STORAGE);
      int localLen = SockAddr.encode(localSegment, local);
      int peerLen = SockAddr.encode(peerSegment, peer);
      MemorySegment result = (MemorySegment) CONNECT.invokeExact(
        name, scidSegment, (long) scid.length,
        localSegment, localLen, peerSegment, peerLen, config.getSegment());
      if (result.equals(MemorySegment.NULL)) {
        throw new QuicheException("quiche_connect to " + peer + " failed");
      }
      return new QuicheConn(result);
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_connect failed", t);
    }
  }

  /**
   * Creates a server connection for an incoming initial packet.
   *
   * @param scid the connection ID this side chooses, at most {@link Quiche#MAX_CONN_ID_LEN} bytes
   * @param odcid the original destination connection ID from the client's first packet, null if no
   *              stateless retry was performed
   * @param local the local address the packet was received on
   * @param peer the client's address
   * @param config the configuration, which must outlive the connection
   * @return the connection
   * @throws QuicheException if quiche refuses to create the connection
   * @throws IllegalArgumentException if an address is unresolved
   */
  public static QuicheConn accept(byte[] scid, byte[] odcid,
                                  InetSocketAddress local, InetSocketAddress peer, QuicheConfig config) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment scidSegment = arena.allocateFrom(ValueLayouts.UINT8, scid);
      MemorySegment odcidSegment = odcid == null ? MemorySegment.NULL
                                                 : arena.allocateFrom(ValueLayouts.UINT8, odcid);
      long odcidLength = odcid == null ? 0 : odcid.length;
      MemorySegment localSegment = arena.allocate(SockAddr.STORAGE);
      MemorySegment peerSegment = arena.allocate(SockAddr.STORAGE);
      int localLen = SockAddr.encode(localSegment, local);
      int peerLen = SockAddr.encode(peerSegment, peer);
      MemorySegment result = (MemorySegment) ACCEPT.invokeExact(
        scidSegment, (long) scid.length, odcidSegment, odcidLength,
        localSegment, localLen, peerSegment, peerLen, config.getSegment());
      if (result.equals(MemorySegment.NULL)) {
        throw new QuicheException("quiche_accept from " + peer + " failed");
      }
      return new QuicheConn(result);
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_accept failed", t);
    }
  }


  /**
   * Feeds a received datagram to the connection.
   * <p>
   * Quiche decrypts in place, so the buffer is modified.
   *
   * @param buffer the datagram
   * @param length the number of bytes in the buffer
   * @param info the addresses the datagram traveled between
   * @return the number of bytes consumed, which may be less than {@code length} for a coalesced datagram
   * @throws QuicheException if the packet was rejected
   */
  public long recv(MemorySegment buffer, long length, RecvInfo info) {
    long result;
    try {
      result = (long) CONN_RECV.invokeExact(getSegment(), buffer, length, info.getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_recv failed", t);
    }
    if (result < 0) {
      throw new QuicheException("quiche_conn_recv failed", result);
    }
    return result;
  }

  /**
   * Asks the connection for the next datagram to put on the wire.
   *
   * @param buffer the buffer to fill
   * @param length the capacity of the buffer
   * @param info filled with the addresses and the pacing time of the packet
   * @return the number of bytes written, or {@link Quiche#ERR_DONE} if there is nothing to send
   * @throws QuicheException if the connection failed
   */
  public long send(MemorySegment buffer, long length, SendInfo info) {
    long result;
    try {
      result = (long) CONN_SEND.invokeExact(getSegment(), buffer, length, info.getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_send failed", t);
    }
    if (result < 0 && result != Quiche.ERR_DONE) {
      throw new QuicheException("quiche_conn_send failed", result);
    }
    return result;
  }

  /**
   * Reads from a stream.
   *
   * @param streamId the stream
   * @param buffer the buffer to fill
   * @param length the capacity of the buffer
   * @param fin a one-element array, set to true if this read consumed the end of the stream; left
   *            untouched if nothing was read
   * @return the number of bytes read, or {@link Quiche#ERR_DONE} if there is nothing to read
   * @throws QuicheException if the stream failed, in particular when the peer reset or stopped it
   */
  public long streamRecv(long streamId, MemorySegment buffer, long length, boolean[] fin) {
    long result;
    try {
      result = (long) STREAM_RECV.invokeExact(getSegment(), streamId, buffer, length, finOut, errorOut);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_recv failed for stream " + streamId, t);
    }
    if (result < 0) {
      if (result == Quiche.ERR_DONE) {
        return result;
      }
      throw new QuicheException("quiche_conn_stream_recv failed for stream " + streamId +
                                applicationError(result), result);
    }
    fin[0] = finOut.get(ValueLayouts.BOOL, 0);
    return result;
  }

  /**
   * Writes to a stream.
   * <p>
   * Quiche accepts as much as the flow control window and the send buffer allow and reports how much that
   * was: a short write is normal and not an error.
   *
   * @param streamId the stream
   * @param buffer the data
   * @param length the number of bytes to write
   * @param fin true to close this side of the stream after the data
   * @return the number of bytes accepted, or {@link Quiche#ERR_DONE} if the stream cannot take any
   * @throws QuicheException if the stream failed, in particular when the peer reset or stopped it
   */
  public long streamSend(long streamId, MemorySegment buffer, long length, boolean fin) {
    long result;
    try {
      result = (long) STREAM_SEND.invokeExact(getSegment(), streamId, buffer, length, fin, errorOut);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_send failed for stream " + streamId, t);
    }
    if (result < 0 && result != Quiche.ERR_DONE) {
      throw new QuicheException("quiche_conn_stream_send failed for stream " + streamId +
                                applicationError(result), result);
    }
    return result;
  }

  /**
   * Shuts down one direction of a stream.
   *
   * @param streamId the stream
   * @param direction {@link #SHUTDOWN_READ} or {@link #SHUTDOWN_WRITE}
   * @param errorCode the application error code sent to the peer
   * @return true if the stream was shut down, false if it was gone already
   */
  public boolean streamShutdown(long streamId, int direction, long errorCode) {
    int result;
    try {
      result = (int) STREAM_SHUTDOWN.invokeExact(getSegment(), streamId, direction, errorCode);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_shutdown failed for stream " + streamId, t);
    }
    if (result == Quiche.ERR_DONE) {
      // the stream is already finished or was never used: nothing to do, and not a failure
      return false;
    }
    if (result < 0) {
      throw new QuicheException("quiche_conn_stream_shutdown failed for stream " + streamId, result);
    }
    return true;
  }

  /**
   * Gets the number of bytes that can be written to a stream right now.
   *
   * @param streamId the stream
   * @return the capacity in bytes, or a negative {@code ERR_} code if the stream is not writable
   */
  public long streamCapacity(long streamId) {
    try {
      return (long) STREAM_CAPACITY.invokeExact(getSegment(), streamId);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_capacity failed for stream " + streamId, t);
    }
  }

  /**
   * Tells whether all data of a stream has been read.
   *
   * @param streamId the stream
   * @return true if finished
   */
  public boolean streamFinished(long streamId) {
    try {
      return (boolean) STREAM_FINISHED.invokeExact(getSegment(), streamId);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_finished failed for stream " + streamId, t);
    }
  }

  /**
   * Tells whether a stream can be read without blocking.<br>
   * This is the case if it holds data, its end has been received, or the peer reset it.
   *
   * @param streamId the stream
   * @return true if readable
   */
  public boolean streamReadable(long streamId) {
    try {
      return (boolean) STREAM_READABLE.invokeExact(getSegment(), streamId);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_readable failed for stream " + streamId, t);
    }
  }

  /**
   * Gets the next stream with data to read, removing it from the readable set.
   *
   * @return the stream id, or -1 if no stream is readable
   */
  public long streamReadableNext() {
    try {
      return (long) STREAM_READABLE_NEXT.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_readable_next failed", t);
    }
  }

  /**
   * Gets the next stream that became writable, removing it from the writable set.
   *
   * @return the stream id, or -1 if no stream is writable
   */
  public long streamWritableNext() {
    try {
      return (long) STREAM_WRITABLE_NEXT.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stream_writable_next failed", t);
    }
  }

  /**
   * Gets the number of bidirectional streams this side may still open.
   *
   * @return the number of streams
   */
  public long peerStreamsLeftBidi() {
    try {
      return (long) PEER_STREAMS_LEFT_BIDI.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_peer_streams_left_bidi failed", t);
    }
  }

  /**
   * Gets the time until the next timeout expires.
   *
   * @return the time in milliseconds, {@link Long#MAX_VALUE} if no timer is armed
   */
  public long timeoutAsMillis() {
    long timeout;
    try {
      timeout = (long) TIMEOUT_AS_MILLIS.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_timeout_as_millis failed", t);
    }
    // quiche returns UINT64_MAX when there is nothing to time out
    return timeout == -1L ? Long.MAX_VALUE : timeout;
  }

  /**
   * Tells the connection that its timer expired.
   */
  public void onTimeout() {
    try {
      ON_TIMEOUT.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_on_timeout failed", t);
    }
  }

  /**
   * Schedules an ack-eliciting packet, which is how a connection is kept from timing out while idle.
   *
   * @return 0 if scheduled, {@link Quiche#ERR_DONE} if there was nothing to schedule it on, or another
   *         negative {@code ERR_} code
   */
  public long sendAckEliciting() {
    try {
      return (long) SEND_ACK_ELICITING.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_send_ack_eliciting failed", t);
    }
  }

  /**
   * Gets the number of further connection IDs this side may issue to the peer.
   *
   * @return the number of connection IDs
   */
  public long scidsLeft() {
    try {
      return (long) SCIDS_LEFT.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_scids_left failed", t);
    }
  }

  /**
   * Gets the number of connection IDs the peer issued that are not in use yet.
   * <p>
   * A client needs one of those to probe or migrate to a new path.
   *
   * @return the number of spare connection IDs
   */
  public long availableDcids() {
    try {
      return (long) AVAILABLE_DCIDS.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_available_dcids failed", t);
    }
  }

  /**
   * Issues a further connection ID the peer can use to reach this side, which makes quiche send a
   * NEW_CONNECTION_ID frame.
   *
   * @param scid the connection ID, at most {@link Quiche#MAX_CONN_ID_LEN} bytes
   * @param resetToken the stateless reset token that goes with it, 16 bytes
   * @return the sequence number quiche assigned
   * @throws QuicheException if the peer's limit is reached ({@link Quiche#ERR_ID_LIMIT}) or the ID is invalid
   * @throws IllegalArgumentException if the reset token is not 16 bytes
   */
  public long newScid(byte[] scid, byte[] resetToken) {
    if (resetToken.length != RESET_TOKEN_LENGTH) {
      // quiche reads exactly 16 bytes, whatever the array holds
      throw new IllegalArgumentException("a reset token must be " + RESET_TOKEN_LENGTH + " bytes");
    }
    int result;
    try (Arena confined = Arena.ofConfined()) {
      MemorySegment scidSegment = confined.allocateFrom(ValueLayouts.UINT8, scid);
      MemorySegment tokenSegment = confined.allocateFrom(ValueLayouts.UINT8, resetToken);
      result = (int) NEW_SCID.invokeExact(getSegment(), scidSegment, (long) scid.length,
                                          tokenSegment, false, seqOut);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_new_scid failed", t);
    }
    if (result < 0) {
      throw new QuicheException("quiche_conn_new_scid failed", result);
    }
    return seqOut.get(ValueLayouts.UINT64, 0);
  }

  /**
   * Retires a connection ID the peer issued, which makes quiche send a RETIRE_CONNECTION_ID frame.
   * <p>
   * A path that loses its connection ID this way and gets no spare one in exchange becomes unused, which
   * is what allows quiche to drop it once it needs room for another path.
   *
   * @param dcidSeq the sequence number of the connection ID
   * @throws QuicheException if the ID is unknown or is the last one the active path can use
   */
  public void retireDcid(long dcidSeq) {
    int result;
    try {
      result = (int) RETIRE_DCID.invokeExact(getSegment(), dcidSeq);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_retire_dcid failed", t);
    }
    if (result < 0) {
      throw new QuicheException("quiche_conn_retire_dcid failed for sequence number " + dcidSeq, result);
    }
  }

  /**
   * Takes the connection IDs of this side the peer has retired.
   * <p>
   * Each one is reported once. The caller has to stop routing packets carrying it to this connection.
   *
   * @return the retired connection IDs, empty if there are none
   */
  @SuppressWarnings("restricted")   // reinterpret: quiche reports the length in the out-parameter
  public List<byte[]> takeRetiredScids() {
    List<byte[]> retired = new ArrayList<>();
    try {
      // the cheap check first: this is called for every received datagram, and the iterator is a heap
      // allocation inside quiche
      if ((long) RETIRED_SCIDS.invokeExact(getSegment()) == 0) {
        return retired;
      }
      MemorySegment iterator = (MemorySegment) RETIRED_SCID_ITER.invokeExact(getSegment());
      if (iterator.equals(MemorySegment.NULL)) {
        return retired;
      }
      try {
        while ((boolean) CONNECTION_ID_ITER_NEXT.invokeExact(iterator, pointerOut, lengthOut)) {
          long length = lengthOut.get(ValueLayouts.SIZE_T, 0);
          MemorySegment pointer = pointerOut.get(ValueLayouts.POINTER, 0);
          retired.add(length <= 0 ? new byte[0] : pointer.reinterpret(length).toArray(ValueLayouts.UINT8));
        }
      }
      finally {
        CONNECTION_ID_ITER_FREE.invokeExact(iterator);
      }
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_retired_scid_iter failed", t);
    }
    return retired;
  }

  /**
   * Starts validating a network path, which makes quiche send a PATH_CHALLENGE on it.
   * <p>
   * A new path needs a spare connection ID on both sides. The outcome is reported as a
   * {@link PathEvent.Type#VALIDATED} or {@link PathEvent.Type#FAILED_VALIDATION} event.
   *
   * @param local the local address of the path
   * @param peer the peer address of the path
   * @return the sequence number of the peer's connection ID the path uses
   * @throws QuicheException if there are no spare connection IDs ({@link Quiche#ERR_OUT_OF_IDENTIFIERS})
   */
  public long probePath(InetSocketAddress local, InetSocketAddress peer) {
    return path(PROBE_PATH, "quiche_conn_probe_path", local, peer);
  }

  /**
   * Makes a path the active one, i.e., the one all further packets are sent on. Client side only.
   * <p>
   * Packets still in flight on the previous path are considered lost and retransmitted on the new one.
   *
   * @param local the local address of the path
   * @param peer the peer address of the path
   * @return the sequence number of the peer's connection ID the path uses
   * @throws QuicheException if there are no spare connection IDs or this is a server connection
   */
  public long migrate(InetSocketAddress local, InetSocketAddress peer) {
    return path(MIGRATE, "quiche_conn_migrate", local, peer);
  }

  /**
   * Takes the next event about the connection's network paths.
   *
   * @return the event, null if there is none
   */
  public PathEvent nextPathEvent() {
    try {
      MemorySegment event = (MemorySegment) PATH_EVENT_NEXT.invokeExact(getSegment());
      if (event.equals(MemorySegment.NULL)) {
        return null;
      }
      try {
        PathEvent.Type type = PathEvent.Type.of((int) PATH_EVENT_TYPE.invokeExact(event));
        // each accessor is only valid for its own type: quiche aborts the process on a mismatch
        MethodHandle accessor = switch (type) {
          case NEW -> PATH_EVENT_NEW;
          case VALIDATED -> PATH_EVENT_VALIDATED;
          case FAILED_VALIDATION -> PATH_EVENT_FAILED_VALIDATION;
          case CLOSED -> PATH_EVENT_CLOSED;
          case PEER_MIGRATED -> PATH_EVENT_PEER_MIGRATED;
          case REUSED_SOURCE_CONNECTION_ID, PMTU_UPDATED -> null;
        };
        if (type == PathEvent.Type.PMTU_UPDATED) {
          // same addresses as the others, plus the new PMTU, which is of no interest here
          try (Arena confined = Arena.ofConfined()) {
            MemorySegment local = confined.allocate(SockAddr.STORAGE);
            MemorySegment localLength = confined.allocate(ValueLayouts.SOCKLEN_T);
            MemorySegment peer = confined.allocate(SockAddr.STORAGE);
            MemorySegment peerLength = confined.allocate(ValueLayouts.SOCKLEN_T);
            MemorySegment pmtu = confined.allocate(ValueLayouts.SIZE_T);
            PATH_EVENT_PMTU_UPDATED.invokeExact(event, local, localLength, peer, peerLength, pmtu);
            return new PathEvent(type, SockAddr.decode(local), SockAddr.decode(peer));
          }
        }
        if (accessor == null) {
          return new PathEvent(type, null, null);
        }
        try (Arena confined = Arena.ofConfined()) {
          MemorySegment local = confined.allocate(SockAddr.STORAGE);
          MemorySegment localLength = confined.allocate(ValueLayouts.SOCKLEN_T);
          MemorySegment peer = confined.allocate(SockAddr.STORAGE);
          MemorySegment peerLength = confined.allocate(ValueLayouts.SOCKLEN_T);
          accessor.invokeExact(event, local, localLength, peer, peerLength);
          return new PathEvent(type, SockAddr.decode(local), SockAddr.decode(peer));
        }
      }
      finally {
        PATH_EVENT_FREE.invokeExact(event);
      }
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_conn_path_event_next failed", t);
    }
  }

  /**
   * Gets the number of bytes the peer has acknowledged so far, on all paths.
   *
   * @return the number of bytes
   */
  public long getAckedBytes() {
    try {
      CONN_STATS.invokeExact(getSegment(), statsOut);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_stats failed", t);
    }
    return statsOut.get(ValueLayouts.UINT64, ACKED_BYTES_OFFSET);
  }

  /**
   * Gets how often the probe timeout fired on the active path so far.
   * <p>
   * A probe timeout fires when ack-eliciting packets are outstanding and nothing acknowledges them for
   * longer than the round trip time allows. Consecutive ones without any acknowledgment in between are
   * the sign of a path that stopped delivering.
   *
   * @return the count, -1 if there is no active path
   */
  public long getActivePathPtoCount() {
    long paths;
    try {
      CONN_STATS.invokeExact(getSegment(), statsOut);
      paths = statsOut.get(ValueLayouts.SIZE_T, PATHS_COUNT_OFFSET);
      for (long index = 0; index < paths; index++) {
        int result = (int) PATH_STATS.invokeExact(getSegment(), index, statsOut);
        if (result == 0 && statsOut.get(ValueLayouts.BOOL, ACTIVE_OFFSET)) {
          return statsOut.get(ValueLayouts.SIZE_T, TOTAL_PTO_COUNT_OFFSET);
        }
      }
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_path_stats failed", t);
    }
    return -1;
  }

  /**
   * Tells whether the handshake is complete.
   *
   * @return true if established
   */
  public boolean isEstablished() {
    return predicate(IS_ESTABLISHED, "quiche_conn_is_established");
  }

  /**
   * Tells whether the connection is closed and can be freed.
   *
   * @return true if closed
   */
  public boolean isClosed() {
    return predicate(IS_CLOSED, "quiche_conn_is_closed");
  }

  /**
   * Tells whether the connection is draining, i.e., going away but not gone.
   *
   * @return true if draining
   */
  public boolean isDraining() {
    return predicate(IS_DRAINING, "quiche_conn_is_draining");
  }

  /**
   * Tells whether the connection was closed by the idle timeout.
   *
   * @return true if timed out
   */
  public boolean isTimedOut() {
    return predicate(IS_TIMED_OUT, "quiche_conn_is_timed_out");
  }

  /**
   * Gets the QUIC {@code CONNECTION_CLOSE} error this side queued or sent, if any.
   * <p>
   * Quiche reports handshake failures &mdash; including a rejected server certificate &mdash; through this call
   * rather than through the coarse, unstructured {@code ERR_TLS_FAIL} return code that
   * {@link #recv(MemorySegment, long, RecvInfo)} throws as a {@link QuicheException}: a locally detected
   * TLS alert is encoded as error code {@code 0x100 + alert-number} per
   * <a href="https://www.rfc-editor.org/rfc/rfc9001.html#section-4.8">RFC 9001 &sect;4.8</a>, which is
   * precise enough to tell a certificate failure apart from any other handshake failure.
   *
   * @return the error, or null if none was queued or sent (yet)
   */
  @SuppressWarnings("restricted")   // reinterpret: quiche reports the length in the out-parameter
  public ConnectionError getLocalError() {
    boolean present;
    try {
      present = (boolean) LOCAL_ERROR.invokeExact(getSegment(), finOut, errorOut, pointerOut, lengthOut);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_conn_local_error failed", t);
    }
    if (!present) {
      return null;
    }
    long length = lengthOut.get(ValueLayouts.SIZE_T, 0);
    String reason = "";
    if (length > 0) {
      MemorySegment pointer = pointerOut.get(ValueLayouts.POINTER, 0);
      reason = new String(pointer.reinterpret(length).toArray(ValueLayouts.UINT8), StandardCharsets.UTF_8);
    }
    return new ConnectionError(finOut.get(ValueLayouts.BOOL, 0), errorOut.get(ValueLayouts.UINT64, 0), reason);
  }

  /**
   * Gets the connection's trace id, which is what quiche's own log messages are keyed by.
   *
   * @return the trace id
   */
  public String getTraceId() {
    return new String(bytes(TRACE_ID, "quiche_conn_trace_id"), StandardCharsets.UTF_8);
  }

  /**
   * Gets this side's connection ID.
   *
   * @return the connection ID
   */
  public byte[] getSourceId() {
    return bytes(SOURCE_ID, "quiche_conn_source_id");
  }

  /**
   * Gets the peer's connection ID.
   *
   * @return the connection ID
   */
  public byte[] getDestinationId() {
    return bytes(DESTINATION_ID, "quiche_conn_destination_id");
  }

  /**
   * Gets the ALPN protocol the handshake settled on.
   *
   * @return the protocol name, empty if the handshake is not complete
   */
  public String getApplicationProtocol() {
    return new String(bytes(APPLICATION_PROTO, "quiche_conn_application_proto"), StandardCharsets.UTF_8);
  }

  /**
   * Closes the connection, which makes the next {@link #send} produce a CONNECTION_CLOSE frame.
   * <p>
   * This does not free anything: the connection must stay alive until the frame has actually gone out and
   * quiche reports {@link #isClosed()}.
   *
   * @param app true for an application error code, false for a transport one
   * @param errorCode the error code
   * @param reason the reason phrase; may be empty
   * @return true if a close was initiated, false if the connection was closing already
   * @throws QuicheException if quiche refuses to close the connection
   */
  public boolean closeConnection(boolean app, long errorCode, String reason) {
    byte[] bytes = reason.getBytes(StandardCharsets.UTF_8);
    try (Arena confined = Arena.ofConfined()) {
      // never NULL, not even for an empty reason: quiche builds a Rust slice from the pointer, and
      // slice::from_raw_parts requires a non-null one regardless of the length
      MemorySegment segment = confined.allocate(Math.max(bytes.length, 1));
      MemorySegment.copy(bytes, 0, segment, ValueLayouts.UINT8, 0, bytes.length);
      int result = (int) CONN_CLOSE.invokeExact(getSegment(), app, errorCode, segment, (long) bytes.length);
      if (result == Quiche.ERR_DONE) {
        return false;   // already closing, not a failure
      }
      if (result < 0) {
        throw new QuicheException("quiche_conn_close failed", result);
      }
      return true;
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_conn_close failed", t);
    }
  }

  /**
   * Gets the native connection pointer.
   *
   * @return the pointer
   * @throws IllegalStateException if the connection has been freed
   */
  public MemorySegment getSegment() {
    MemorySegment segment = conn;
    if (segment == null) {
      throw new IllegalStateException("the quiche connection is freed");
    }
    return segment;
  }

  /**
   * Tells whether the native connection has been freed.
   *
   * @return true if freed
   */
  public boolean isFreed() {
    return conn == null;
  }

  /**
   * Frees the native connection.
   * <p>
   * Idempotent. Every other method but {@link #isFreed()} throws afterward, which is deliberate: reaching a
   * freed connection through a stale reference would otherwise be a use-after-free in native memory. Most
   * methods report this as a {@link QuicheException} wrapping the {@link IllegalStateException} of
   * {@link #getSegment()}.
   */
  @Override
  public void close() {
    MemorySegment segment = conn;
    conn = null;
    if (segment != null) {
      try {
        CONN_FREE.invokeExact(segment);
      }
      catch (Throwable t) {
        throw new QuicheException("quiche_conn_free failed", t);
      }
      finally {
        arena.close();
      }
    }
  }


  /**
   * Appends the application error code the peer sent if the failure carries one.
   *
   * @param result the return code of the failed stream call
   * @return the message fragment, empty if there is nothing to add
   */
  private String applicationError(long result) {
    if (result == Quiche.ERR_STREAM_STOPPED || result == Quiche.ERR_STREAM_RESET) {
      return " with application error code " + Long.toUnsignedString(errorOut.get(ValueLayouts.UINT64, 0));
    }
    return "";
  }

  /**
   * Reads one of the {@code void f(const quiche_conn *, const uint8_t **out, size_t *out_len)} accessors.
   * <p>
   * The returned pointer belongs to the connection and stays valid only until it changes, so the bytes are
   * copied out immediately.
   *
   * @param handle the method handle
   * @param name the symbol name, for the error message
   * @return the bytes
   */
  @SuppressWarnings("restricted")   // reinterpret: quiche reports the length in the out-parameter
  private byte[] bytes(MethodHandle handle, String name) {
    try {
      handle.invokeExact(getSegment(), pointerOut, lengthOut);
    }
    catch (Throwable t) {
      throw new QuicheException(name + " failed", t);
    }
    long length = lengthOut.get(ValueLayouts.SIZE_T, 0);
    if (length <= 0) {
      return new byte[0];
    }
    MemorySegment pointer = pointerOut.get(ValueLayouts.POINTER, 0);
    return pointer.reinterpret(length).toArray(ValueLayouts.UINT8);
  }

  /**
   * Invokes one of the {@code bool f(const quiche_conn *)} predicates.
   *
   * @param handle the method handle
   * @param name the symbol name, for the error message
   * @return the result
   */
  private boolean predicate(MethodHandle handle, String name) {
    try {
      return (boolean) handle.invokeExact(getSegment());
    }
    catch (Throwable t) {
      throw new QuicheException(name + " failed", t);
    }
  }

  /**
   * Invokes {@code quiche_conn_probe_path} or {@code quiche_conn_migrate}.
   *
   * @param handle the method handle
   * @param name the symbol name, for the error message
   * @param local the local address of the path
   * @param peer the peer address of the path
   * @return the sequence number of the peer's connection ID the path uses
   */
  private long path(MethodHandle handle, String name, InetSocketAddress local, InetSocketAddress peer) {
    int result;
    try (Arena confined = Arena.ofConfined()) {
      MemorySegment localSegment = confined.allocate(SockAddr.STORAGE);
      MemorySegment peerSegment = confined.allocate(SockAddr.STORAGE);
      int localLen = SockAddr.encode(localSegment, local);
      int peerLen = SockAddr.encode(peerSegment, peer);
      result = (int) handle.invokeExact(getSegment(), localSegment, localLen, peerSegment, peerLen, seqOut);
    }
    catch (Throwable t) {
      throw new QuicheException(name + " failed", t);
    }
    if (result < 0) {
      throw new QuicheException(name + " failed for " + local + " -> " + peer, result);
    }
    return seqOut.get(ValueLayouts.UINT64, 0);
  }

  /**
   * Binds one of the {@code size_t f(quiche_conn *)} counters.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle counter(String name) {
    return Quiche.downcall(name, FunctionDescriptor.of(ValueLayouts.SIZE_T, ValueLayouts.POINTER));
  }

  /**
   * Binds {@code quiche_conn_probe_path} or {@code quiche_conn_migrate}, which share their signature.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle pathSetter(String name) {
    return Quiche.downcall(name, FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER,
                                                       ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // local
                                                       ValueLayouts.POINTER, ValueLayouts.SOCKLEN_T,  // peer
                                                       ValueLayouts.POINTER));                        // uint64_t *seq
  }

  /**
   * Binds one of the {@code quiche_path_event_<type>} accessors that report a path's addresses.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle pathEventAddresses(String name) {
    return Quiche.downcall(name, FunctionDescriptor.ofVoid(ValueLayouts.POINTER,
                                                           ValueLayouts.POINTER, ValueLayouts.POINTER,
                                                           ValueLayouts.POINTER, ValueLayouts.POINTER));
  }

  /**
   * Binds one of the {@code bool f(const quiche_conn *)} predicates.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle predicate(String name) {
    return Quiche.downcall(name, FunctionDescriptor.of(ValueLayouts.BOOL, ValueLayouts.POINTER));
  }

  /**
   * Binds one of the {@code void f(const quiche_conn *, const uint8_t **, size_t *)} accessors.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle accessor(String name) {
    return Quiche.downcall(name, FunctionDescriptor.ofVoid(ValueLayouts.POINTER,
                                                           ValueLayouts.POINTER, ValueLayouts.POINTER));
  }

}
