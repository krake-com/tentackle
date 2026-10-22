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
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * A {@code quiche_config}, the settings a QUIC connection is created with.
 * <p>
 * A configuration is immutable from quiche's point of view once a connection has been created from it, and
 * one configuration can back any number of connections. It must outlive all of them, so {@link #close()}
 * must not be called before the last of them has been freed.
 * <p>
 * The setters mirror the C API one to one and return {@code this} so they can be chained. A setter that
 * fails throws a {@link QuicheException}, which wraps an {@link IllegalStateException} once the
 * configuration has been closed. The strings handed to the setters are kept until the configuration is
 * closed.
 */
public class QuicheConfig implements AutoCloseable {

  private static final MethodHandle CONFIG_NEW =
    Quiche.downcall("quiche_config_new", FunctionDescriptor.of(ValueLayouts.POINTER, ValueLayouts.UINT32));

  private static final MethodHandle CONFIG_FREE =
    Quiche.downcall("quiche_config_free", FunctionDescriptor.ofVoid(ValueLayouts.POINTER));

  private static final MethodHandle LOAD_CERT_CHAIN =
    Quiche.downcall("quiche_config_load_cert_chain_from_pem_file",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle LOAD_PRIV_KEY =
    Quiche.downcall("quiche_config_load_priv_key_from_pem_file",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle LOAD_VERIFY_LOCATIONS =
    Quiche.downcall("quiche_config_load_verify_locations_from_file",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle VERIFY_PEER =
    Quiche.downcall("quiche_config_verify_peer",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.BOOL));

  private static final MethodHandle SET_APPLICATION_PROTOS =
    Quiche.downcall("quiche_config_set_application_protos",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER,
                                          ValueLayouts.POINTER, ValueLayouts.SIZE_T));

  private static final MethodHandle SET_MAX_IDLE_TIMEOUT =
    setter("quiche_config_set_max_idle_timeout");

  private static final MethodHandle SET_INITIAL_MAX_DATA =
    setter("quiche_config_set_initial_max_data");

  private static final MethodHandle SET_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL =
    setter("quiche_config_set_initial_max_stream_data_bidi_local");

  private static final MethodHandle SET_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE =
    setter("quiche_config_set_initial_max_stream_data_bidi_remote");

  private static final MethodHandle SET_INITIAL_MAX_STREAMS_BIDI =
    setter("quiche_config_set_initial_max_streams_bidi");

  private static final MethodHandle SET_INITIAL_MAX_STREAMS_UNI =
    setter("quiche_config_set_initial_max_streams_uni");

  private static final MethodHandle SET_MAX_CONNECTION_WINDOW =
    setter("quiche_config_set_max_connection_window");

  private static final MethodHandle SET_MAX_STREAM_WINDOW =
    setter("quiche_config_set_max_stream_window");

  private static final MethodHandle SET_MAX_RECV_UDP_PAYLOAD_SIZE =
    Quiche.downcall("quiche_config_set_max_recv_udp_payload_size",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.SIZE_T));

  private static final MethodHandle SET_MAX_SEND_UDP_PAYLOAD_SIZE =
    Quiche.downcall("quiche_config_set_max_send_udp_payload_size",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.SIZE_T));

  private static final MethodHandle SET_DISABLE_ACTIVE_MIGRATION =
    Quiche.downcall("quiche_config_set_disable_active_migration",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.BOOL));

  private static final MethodHandle SET_ACTIVE_CONNECTION_ID_LIMIT =
    setter("quiche_config_set_active_connection_id_limit");

  private static final MethodHandle ENABLE_PACING =
    Quiche.downcall("quiche_config_enable_pacing",
                    FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.BOOL));

  private static final MethodHandle SET_CC_ALGORITHM_NAME =
    Quiche.downcall("quiche_config_set_cc_algorithm_name",
                    FunctionDescriptor.of(ValueLayouts.INT, ValueLayouts.POINTER, ValueLayouts.POINTER));

  private static final MethodHandle GREASE =
    Quiche.downcall("quiche_config_grease", FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.BOOL));


  private final Arena arena;              // for the strings handed to the setters
  private MemorySegment config;           // the native config, null once closed

  /**
   * Creates a configuration for {@link Quiche#PROTOCOL_VERSION}.
   */
  public QuicheConfig() {
    this(Quiche.PROTOCOL_VERSION);
  }

  /**
   * Creates a configuration.
   *
   * @param version the QUIC version
   * @throws QuicheException if quiche does not support the version
   */
  public QuicheConfig(int version) {
    arena = Arena.ofShared();
    try {
      MemorySegment segment = (MemorySegment) CONFIG_NEW.invokeExact(version);
      if (segment.equals(MemorySegment.NULL)) {
        throw new QuicheException("quiche_config_new failed for version 0x" + Integer.toHexString(version));
      }
      config = segment;
    }
    catch (Throwable t) {
      arena.close();
      throw t instanceof RuntimeException e ? e : new QuicheException("quiche_config_new failed", t);
    }
  }


  /**
   * Gets the native configuration pointer.
   *
   * @return the pointer
   * @throws IllegalStateException if the configuration has been closed
   */
  public MemorySegment getSegment() {
    MemorySegment segment = config;
    if (segment == null) {
      throw new IllegalStateException("the quiche configuration is closed");
    }
    return segment;
  }

  /**
   * Loads the certificate chain presented to the peer, in PEM format.
   *
   * @param path the PEM file, read right away
   * @return this configuration
   * @throws QuicheException if the file cannot be read or holds no valid certificate chain
   */
  public QuicheConfig loadCertChain(Path path) {
    invoke(LOAD_CERT_CHAIN, "quiche_config_load_cert_chain_from_pem_file", path);
    return this;
  }

  /**
   * Loads the private key belonging to the certificate chain, in PEM format.
   *
   * @param path the PEM file, read right away
   * @return this configuration
   * @throws QuicheException if the file cannot be read or holds no valid private key
   */
  public QuicheConfig loadPrivateKey(Path path) {
    invoke(LOAD_PRIV_KEY, "quiche_config_load_priv_key_from_pem_file", path);
    return this;
  }

  /**
   * Loads the CA certificates the peer's certificate is verified against, in PEM format.
   * <p>
   * Without this, verification relies on the default trust store BoringSSL is initialized with, which does
   * not contain the certificate of a self-signed Tentackle server, nor the JVM's CAs.
   *
   * @param path the PEM file, read right away
   * @return this configuration
   * @throws QuicheException if the file cannot be read or holds no valid certificate
   */
  public QuicheConfig loadVerifyLocations(Path path) {
    invoke(LOAD_VERIFY_LOCATIONS, "quiche_config_load_verify_locations_from_file", path);
    return this;
  }

  /**
   * Sets whether the peer's certificate is verified.
   * <p>
   * The default is to verify on the client and not to verify on the server, i.e., no client certificates.
   *
   * @param verify true to verify
   * @return this configuration
   */
  public QuicheConfig verifyPeer(boolean verify) {
    try {
      VERIFY_PEER.invokeExact(getSegment(), verify);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_verify_peer failed", t);
    }
    return this;
  }

  /**
   * Sets the ALPN protocols offered during the TLS handshake.
   * <p>
   * The wire format is a sequence of length-prefixed names, so {@code "tripj"} becomes
   * {@code "\x05tripj"}. A connection whose peer offers none of them is refused by the handshake, which
   * is what keeps a Tentackle service from answering an unrelated QUIC client.
   *
   * @param protocols the protocol names, in order of preference
   * @return this configuration
   * @throws IllegalArgumentException if a name is empty or longer than 255 bytes
   * @throws QuicheException if quiche refuses the list
   */
  public QuicheConfig setApplicationProtocols(String... protocols) {
    int length = 0;
    for (String protocol : protocols) {
      int nameLength = protocol.getBytes(StandardCharsets.UTF_8).length;
      if (nameLength == 0 || nameLength > 255) {
        throw new IllegalArgumentException("ALPN protocol name '" + protocol + "' must be 1..255 bytes long");
      }
      length += nameLength + 1;
    }
    byte[] wire = new byte[length];
    int offset = 0;
    for (String protocol : protocols) {
      byte[] name = protocol.getBytes(StandardCharsets.UTF_8);
      wire[offset++] = (byte) name.length;
      System.arraycopy(name, 0, wire, offset, name.length);
      offset += name.length;
    }
    MemorySegment segment = arena.allocateFrom(ValueLayouts.UINT8, wire);
    try {
      int result = (int) SET_APPLICATION_PROTOS.invokeExact(getSegment(), segment, (long) wire.length);
      if (result < 0) {
        throw new QuicheException("quiche_config_set_application_protos failed", result);
      }
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e
                                            : new QuicheException("quiche_config_set_application_protos failed", t);
    }
    return this;
  }

  /**
   * Sets the maximum idle timeout in milliseconds.<br>
   * A connection is discarded silently by both peers once it stays idle for longer than the minimum of the
   * timeouts both of them advertise, see RFC-9000, 10.1. Zero disables the timeout.
   *
   * @param millis the timeout in milliseconds
   * @return this configuration
   */
  public QuicheConfig setMaxIdleTimeout(long millis) {
    set(SET_MAX_IDLE_TIMEOUT, "quiche_config_set_max_idle_timeout", millis);
    return this;
  }

  /**
   * Sets the connection-level flow control window in bytes.
   *
   * @param bytes the window
   * @return this configuration
   */
  public QuicheConfig setInitialMaxData(long bytes) {
    set(SET_INITIAL_MAX_DATA, "quiche_config_set_initial_max_data", bytes);
    return this;
  }

  /**
   * Sets the flow control window for bidirectional streams this peer opens.
   *
   * @param bytes the window
   * @return this configuration
   */
  public QuicheConfig setInitialMaxStreamDataBidiLocal(long bytes) {
    set(SET_INITIAL_MAX_STREAM_DATA_BIDI_LOCAL, "quiche_config_set_initial_max_stream_data_bidi_local", bytes);
    return this;
  }

  /**
   * Sets the flow control window for bidirectional streams the peer opens.
   *
   * @param bytes the window
   * @return this configuration
   */
  public QuicheConfig setInitialMaxStreamDataBidiRemote(long bytes) {
    set(SET_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE, "quiche_config_set_initial_max_stream_data_bidi_remote", bytes);
    return this;
  }

  /**
   * Sets the number of concurrent bidirectional streams the peer may open.
   *
   * @param count the number of streams
   * @return this configuration
   */
  public QuicheConfig setInitialMaxStreamsBidi(long count) {
    set(SET_INITIAL_MAX_STREAMS_BIDI, "quiche_config_set_initial_max_streams_bidi", count);
    return this;
  }

  /**
   * Sets the number of concurrent unidirectional streams the peer may open.
   * <p>
   * TRIP uses bidirectional streams only, so this is zero for a Tentackle transport.
   *
   * @param count the number of streams
   * @return this configuration
   */
  public QuicheConfig setInitialMaxStreamsUni(long count) {
    set(SET_INITIAL_MAX_STREAMS_UNI, "quiche_config_set_initial_max_streams_uni", count);
    return this;
  }

  /**
   * Sets the upper bound the connection-level flow control window may be auto-tuned to.
   *
   * @param bytes the window
   * @return this configuration
   */
  public QuicheConfig setMaxConnectionWindow(long bytes) {
    set(SET_MAX_CONNECTION_WINDOW, "quiche_config_set_max_connection_window", bytes);
    return this;
  }

  /**
   * Sets the upper bound a stream's flow control window may be auto-tuned to.
   *
   * @param bytes the window
   * @return this configuration
   */
  public QuicheConfig setMaxStreamWindow(long bytes) {
    set(SET_MAX_STREAM_WINDOW, "quiche_config_set_max_stream_window", bytes);
    return this;
  }

  /**
   * Sets the largest UDP payload this peer is willing to receive.
   *
   * @param bytes the payload size
   * @return this configuration
   */
  public QuicheConfig setMaxRecvUdpPayloadSize(long bytes) {
    try {
      SET_MAX_RECV_UDP_PAYLOAD_SIZE.invokeExact(getSegment(), bytes);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_set_max_recv_udp_payload_size failed", t);
    }
    return this;
  }

  /**
   * Sets the largest UDP payload this peer sends.
   *
   * @param bytes the payload size
   * @return this configuration
   */
  public QuicheConfig setMaxSendUdpPayloadSize(long bytes) {
    try {
      SET_MAX_SEND_UDP_PAYLOAD_SIZE.invokeExact(getSegment(), bytes);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_set_max_send_udp_payload_size failed", t);
    }
    return this;
  }

  /**
   * Sets whether the peer is forbidden to migrate the connection to another address.
   *
   * @param disable true to disable active migration
   * @return this configuration
   */
  public QuicheConfig setDisableActiveMigration(boolean disable) {
    try {
      SET_DISABLE_ACTIVE_MIGRATION.invokeExact(getSegment(), disable);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_set_disable_active_migration failed", t);
    }
    return this;
  }

  /**
   * Sets how many connection IDs this side is willing to hold for the peer at the same time.
   * <p>
   * Every network path needs a connection ID of its own in each direction, so this bounds how many paths
   * a connection can use &mdash; quiche keeps at most this many paths per connection &mdash; and thereby how
   * often a client can migrate. Values below 2, the protocol's default, are ignored by quiche.
   *
   * @param limit the number of connection IDs
   * @return this configuration
   */
  public QuicheConfig setActiveConnectionIdLimit(long limit) {
    set(SET_ACTIVE_CONNECTION_ID_LIMIT, "quiche_config_set_active_connection_id_limit", limit);
    return this;
  }

  /**
   * Sets whether outgoing packets are paced.
   *
   * @param enable true to pace
   * @return this configuration
   */
  public QuicheConfig enablePacing(boolean enable) {
    try {
      ENABLE_PACING.invokeExact(getSegment(), enable);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_enable_pacing failed", t);
    }
    return this;
  }

  /**
   * Sets whether GREASE values are sent, which keeps peers from ossifying on unused protocol values.
   *
   * @param enable true to send GREASE
   * @return this configuration
   */
  public QuicheConfig grease(boolean enable) {
    try {
      GREASE.invokeExact(getSegment(), enable);
    }
    catch (Throwable t) {
      throw new QuicheException("quiche_config_grease failed", t);
    }
    return this;
  }

  /**
   * Sets the congestion control algorithm by name, for example {@code cubic}, {@code reno} or {@code bbr2}.
   *
   * @param name the algorithm name
   * @return this configuration
   * @throws QuicheException if the algorithm is unknown
   */
  public QuicheConfig setCongestionControlAlgorithm(String name) {
    MemorySegment segment = arena.allocateFrom(name);
    try {
      int result = (int) SET_CC_ALGORITHM_NAME.invokeExact(getSegment(), segment);
      if (result < 0) {
        throw new QuicheException("unknown congestion control algorithm '" + name + "'", result);
      }
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e
                                            : new QuicheException("quiche_config_set_cc_algorithm_name failed", t);
    }
    return this;
  }

  /**
   * Frees the native configuration.
   * <p>
   * Must not happen while a connection created from this configuration is still alive.
   */
  @Override
  public void close() {
    MemorySegment segment = config;
    config = null;
    if (segment != null) {
      try {
        CONFIG_FREE.invokeExact(segment);
      }
      catch (Throwable t) {
        throw new QuicheException("quiche_config_free failed", t);
      }
      finally {
        arena.close();
      }
    }
  }


  /**
   * Binds one of the many {@code void set(quiche_config *, uint64_t)} setters.
   *
   * @param name the symbol name
   * @return the method handle
   */
  private static MethodHandle setter(String name) {
    return Quiche.downcall(name, FunctionDescriptor.ofVoid(ValueLayouts.POINTER, ValueLayouts.UINT64));
  }

  /**
   * Invokes one of the {@code uint64_t} setters.
   *
   * @param handle the method handle
   * @param name the symbol name, for the error message
   * @param value the value
   */
  private void set(MethodHandle handle, String name, long value) {
    try {
      handle.invokeExact(getSegment(), value);
    }
    catch (Throwable t) {
      throw new QuicheException(name + " failed", t);
    }
  }

  /**
   * Invokes one of the {@code int load(quiche_config *, const char *path)} functions.
   * <p>
   * These report a failure &mdash; an unreadable file, a certificate BoringSSL does not accept &mdash; as a
   * negative return code without saying anything about what went wrong, so the path is put into the message.
   *
   * @param handle the method handle
   * @param name the symbol name, for the error message
   * @param path the file
   */
  private void invoke(MethodHandle handle, String name, Path path) {
    MemorySegment segment = arena.allocateFrom(path.toString());
    try {
      int result = (int) handle.invokeExact(getSegment(), segment);
      if (result < 0) {
        throw new QuicheException(name + " failed for '" + path + "'", result);
      }
    }
    catch (Throwable t) {
      throw t instanceof RuntimeException e ? e : new QuicheException(name + " failed for '" + path + "'", t);
    }
  }

}
