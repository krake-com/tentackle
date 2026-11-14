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

package org.tentackle.trip.transport;

import org.tentackle.common.Cryptor;
import org.tentackle.log.Logger;
import org.tentackle.trip.TripConstants;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * The encrypted channel of the PSK transports {@code tripe} and {@code tripce}.
 * <p>
 * The pre-shared key is the key of the application's {@link Cryptor}. Every connection gets keys of its own,
 * derived via HKDF from the pre-shared key and random salts of both peers, and every packet is encrypted and
 * authenticated with AES-GCM:
 * <ol>
 *   <li>The client sends its random 32-byte salt, followed by the TRIP connection request (magic, UUID and
 *   dictionary flag) encrypted with a key derived from that salt. The hello has a fixed size of
 *   {@link #HELLO_SIZE} bytes, and the client proves with it that it knows the pre-shared key before the
 *   server sends a single byte.</li>
 *   <li>The server verifies the hello. If it is not authentic, the server sends nothing, and the connection
 *   handler closes the connection after the {@link org.tentackle.trip.RejectionDelay}.
 *   Otherwise, the server answers with its own random 32-byte salt.</li>
 *   <li>Both sides derive the keys of both directions from the two salts. Each packet consists of a 2-byte
 *   little-endian length, which is authenticated as well, followed by the ciphertext and the tag. The nonce is
 *   a counter per direction.</li>
 * </ol>
 * Since the server contributes a salt, a recorded connection cannot be replayed. Identical messages
 * yield different ciphertexts, and a modified packet is detected.
 * <p>
 * For a transition period, the server also accepts clients of the previous version, which encrypted with the
 * plain cipher of the {@link Cryptor} (AES/ECB without authentication), unless the transport's URI
 * query parameter {@code legacy} is {@code 0}.
 */
public class EncryptedChannel {

  /** The size of a salt. */
  public static final int SALT_SIZE = 32;

  /** The size of the TRIP connection request in the hello. */
  public static final int REQUEST_SIZE = TripConstants.MAGIC.length + 16 + 1;

  /** The size of the GCM authentication tag. */
  public static final int TAG_SIZE = 16;

  /** The size of the client's hello. */
  public static final int HELLO_SIZE = SALT_SIZE + REQUEST_SIZE + TAG_SIZE;

  /** The size of the packet header. */
  public static final int HEADER_SIZE = 2;

  /** The maximum size of an encrypted packet, including the tag. */
  public static final int MAX_PACKET_SIZE = TripConstants.UNSIGNED_SHORT_MAX;

  /** The maximum number of plaintext bytes per packet. */
  public static final int MAX_PLAIN_SIZE = MAX_PACKET_SIZE - TAG_SIZE;


  private static final Logger LOGGER = Logger.get(EncryptedChannel.class);

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_STRENGTH = 256;
  private static final int NONCE_SIZE = 12;
  private static final byte[] INFO_HELLO = "tripe hello".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] INFO_C2S = "tripe c2s".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] INFO_S2C = "tripe s2c".getBytes(StandardCharsets.US_ASCII);

  /** The size of a packet of a legacy client carrying the connection request. */
  private static final int LEGACY_REQUEST_PACKET_SIZE = 32;

  private static final SecureRandom RANDOM = new SecureRandom();


  private final Cryptor cryptor;
  private final InputStream in;                 // the raw input stream
  private final OutputStream out;               // the raw output stream
  private final boolean server;                 // true if server side
  private final boolean legacyAccepted;         // true if the server accepts legacy clients
  private final Input input;
  private final Output output;

  // handshake state, guarded by this
  private byte[] clientSalt;                    // the client's salt
  private boolean helloSent;                    // client: hello sent
  private boolean established;                  // keys derived (or legacy)
  private boolean legacy;                       // server: legacy client (AES/ECB)
  private byte[] request;                       // server: the decrypted connection request, null if delivered
  private SecretKey readKey;
  private SecretKey writeKey;


  /**
   * Creates an encrypted channel.
   *
   * @param cryptor the cryptor holding the pre-shared key
   * @param in the raw input stream
   * @param out the raw output stream
   * @param server true if this is the server side of the connection
   * @param legacyAccepted true if the server accepts clients of the previous version (ignored by the client)
   */
  public EncryptedChannel(Cryptor cryptor, InputStream in, OutputStream out, boolean server, boolean legacyAccepted) {
    this.cryptor = Objects.requireNonNull(cryptor, "missing cryptor");
    this.in = Objects.requireNonNull(in, "missing input stream");
    this.out = Objects.requireNonNull(out, "missing output stream");
    this.server = server;
    this.legacyAccepted = server && legacyAccepted;
    input = new Input();
    output = new Output();
  }

  /**
   * Gets the decrypting input stream.
   *
   * @return the input stream
   */
  public InputStream getInputStream() {
    return input;
  }

  /**
   * Gets the encrypting output stream.
   *
   * @return the output stream
   */
  public OutputStream getOutputStream() {
    return output;
  }

  /**
   * Returns whether this is the server side.
   *
   * @return true if server
   */
  public boolean isServer() {
    return server;
  }

  /**
   * Returns whether the peer is a legacy client using AES/ECB.
   *
   * @return true if legacy, false if AES-GCM or the handshake is not complete yet
   */
  public synchronized boolean isLegacy() {
    return legacy;
  }


  /**
   * Client: sends the hello.
   *
   * @param connectionRequest the connection request of {@link #REQUEST_SIZE} bytes
   * @throws IOException if sending failed
   */
  private synchronized void sendHello(byte[] connectionRequest) throws IOException {
    clientSalt = randomSalt();
    SecretKey helloKey = cryptor.deriveKey(clientSalt, INFO_HELLO, KEY_STRENGTH);
    byte[] encrypted = crypt(createCipher(), Cipher.ENCRYPT_MODE, helloKey, 0, null,
                             connectionRequest, 0, connectionRequest.length);
    byte[] hello = new byte[HELLO_SIZE];
    System.arraycopy(clientSalt, 0, hello, 0, SALT_SIZE);
    System.arraycopy(encrypted, 0, hello, SALT_SIZE, encrypted.length);
    out.write(hello);
    out.flush();
    helloSent = true;
  }

  /**
   * Client: receives the server's salt and derives the keys, if not done yet.<br>
   * Invoked by whichever stream needs the keys first.
   *
   * @throws IOException if receiving failed
   */
  private synchronized void awaitServerSalt() throws IOException {
    if (!established) {
      if (!helloSent) {
        throw new IOException("hello not sent yet");
      }
      byte[] serverSalt = readFully(SALT_SIZE);
      deriveKeys(serverSalt);
    }
  }

  /**
   * Server: receives and verifies the hello, if not done yet.
   *
   * @throws IOException if receiving failed or the hello is not authentic
   */
  private synchronized void receiveHello() throws IOException {
    if (established) {
      return;
    }
    byte[] hello = readFully(HEADER_SIZE + LEGACY_REQUEST_PACKET_SIZE);
    if (legacyAccepted && receiveLegacyRequest(hello)) {
      return;
    }
    byte[] rest = readFully(HELLO_SIZE - hello.length);
    hello = Arrays.copyOf(hello, HELLO_SIZE);
    System.arraycopy(rest, 0, hello, HELLO_SIZE - rest.length, rest.length);
    clientSalt = Arrays.copyOf(hello, SALT_SIZE);
    SecretKey helloKey = cryptor.deriveKey(clientSalt, INFO_HELLO, KEY_STRENGTH);
    request = crypt(createCipher(), Cipher.DECRYPT_MODE, helloKey, 0, null, hello, SALT_SIZE, HELLO_SIZE - SALT_SIZE);
    byte[] serverSalt = randomSalt();
    out.write(serverSalt);
    out.flush();
    deriveKeys(serverSalt);
  }

  /**
   * Server: checks whether the first bytes are the connection request of a legacy client.
   *
   * @param packet the header and the encrypted packet
   * @return true if legacy client
   */
  private boolean receiveLegacyRequest(byte[] packet) {
    if ((packet[0] & 0xff) != LEGACY_REQUEST_PACKET_SIZE || packet[1] != 0) {
      return false;
    }
    byte[] decrypted;
    try {
      decrypted = cryptor.decrypt(packet, HEADER_SIZE, LEGACY_REQUEST_PACKET_SIZE);
    }
    catch (RuntimeException ex) {
      return false;   // not a legacy client
    }
    boolean current = decrypted.length == REQUEST_SIZE &&
                      decrypted[0] == TripConstants.MAGIC[0] && decrypted[1] == TripConstants.MAGIC[1];
    boolean previous = decrypted.length == REQUEST_SIZE - 1 &&     // w/o the dictionary flag
                       decrypted[0] == TripConstants.MAGIC_1[0] && decrypted[1] == TripConstants.MAGIC_1[1];
    if (!current && !previous) {
      return false;
    }
    LOGGER.warning("legacy client connected to encrypted channel (AES/ECB): client must be updated");
    request = decrypted;
    legacy = true;
    established = true;
    return true;
  }

  /**
   * Derives the keys of both directions.
   *
   * @param serverSalt the server's salt
   */
  private void deriveKeys(byte[] serverSalt) {
    byte[] salt = new byte[2 * SALT_SIZE];
    System.arraycopy(clientSalt, 0, salt, 0, SALT_SIZE);
    System.arraycopy(serverSalt, 0, salt, SALT_SIZE, SALT_SIZE);
    SecretKey c2s = cryptor.deriveKey(salt, INFO_C2S, KEY_STRENGTH);
    SecretKey s2c = cryptor.deriveKey(salt, INFO_S2C, KEY_STRENGTH);
    readKey = server ? c2s : s2c;
    writeKey = server ? s2c : c2s;
    established = true;
  }

  /**
   * Server: takes the connection request received with the hello.
   *
   * @return the request, null if already taken
   */
  private synchronized byte[] takeRequest() {
    byte[] req = request;
    request = null;
    return req;
  }

  private synchronized SecretKey getReadKey() {
    return readKey;
  }

  private synchronized SecretKey getWriteKey() {
    return writeKey;
  }


  /**
   * Reads exactly the given number of bytes from the raw input stream.
   *
   * @param length the number of bytes
   * @return the bytes
   * @throws IOException if failed or end of stream reached before
   */
  private byte[] readFully(int length) throws IOException {
    byte[] buf = in.readNBytes(length);
    if (buf.length != length) {
      throw new EOFException("end of stream reached while waiting for " + length + " bytes");
    }
    return buf;
  }

  private static byte[] randomSalt() {
    byte[] salt = new byte[SALT_SIZE];
    RANDOM.nextBytes(salt);
    return salt;
  }

  /**
   * Creates an AES-GCM cipher.<br>
   * Each stream has its own, since a cipher is not thread-safe.
   *
   * @return the cipher
   * @throws IOException if the cipher is not available
   */
  private static Cipher createCipher() throws IOException {
    try {
      return Cipher.getInstance(TRANSFORMATION);
    }
    catch (GeneralSecurityException ex) {
      throw new IOException("cannot create cipher " + TRANSFORMATION, ex);
    }
  }

  /**
   * Encrypts or decrypts with AES-GCM.
   *
   * @param cipher the cipher
   * @param mode the cipher mode
   * @param key the key
   * @param counter the nonce counter
   * @param aad the additional authenticated data, null if none
   * @param data the data
   * @param offset the offset in data
   * @param length the number of bytes
   * @return the result
   * @throws IOException if not authentic or the cipher failed
   */
  private static byte[] crypt(Cipher cipher, int mode, SecretKey key, long counter, byte[] aad,
                              byte[] data, int offset, int length) throws IOException {
    byte[] nonce = new byte[NONCE_SIZE];
    for (int i = NONCE_SIZE - 1; i >= NONCE_SIZE - 8; i--) {
      nonce[i] = (byte) counter;
      counter >>>= 8;
    }
    try {
      cipher.init(mode, key, new GCMParameterSpec(TAG_SIZE * 8, nonce));
      if (aad != null) {
        cipher.updateAAD(aad);
      }
      return cipher.doFinal(data, offset, length);
    }
    catch (GeneralSecurityException ex) {
      // don't tell more: the data was chosen by the peer
      throw new IOException(mode == Cipher.DECRYPT_MODE ? "decryption failed" : "encryption failed");
    }
  }


  /**
   * The decrypting input stream.
   */
  private class Input extends InputStream {

    private final byte[] header = new byte[HEADER_SIZE];
    private long counter;               // the nonce counter of the read direction
    private Cipher cipher;              // the cipher of the read direction
    private byte[] buf;                 // decrypted data
    private int off;                    // offset into buf
    private boolean closed;

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      int num = read(b, 0, 1);
      return num < 0 ? num : (b[0] & 0xff);
    }

    @Override
    public int read(byte[] b, int offset, int len) throws IOException {
      Objects.checkFromIndexSize(offset, len, b.length);
      if (len == 0) {
        return 0;
      }
      if (buf == null || off >= buf.length) {
        if (!nextPacket()) {
          return -1;
        }
      }
      int num = Math.min(len, buf.length - off);
      System.arraycopy(buf, off, b, offset, num);
      off += num;
      return num;
    }

    @Override
    public int available() {
      return buf == null ? 0 : buf.length - off;
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        in.close();
      }
    }

    /**
     * Reads and decrypts the next packet.
     *
     * @return false if end of stream reached
     * @throws IOException if failed or not authentic
     */
    private boolean nextPacket() throws IOException {
      if (server) {
        receiveHello();
        byte[] req = takeRequest();
        if (req != null) {
          buf = req;
          off = 0;
          return true;
        }
      }
      else {
        awaitServerSalt();
      }
      do {
        if (in.readNBytes(header, 0, HEADER_SIZE) != HEADER_SIZE) {
          return false;
        }
        int size = (header[0] & 0xff) | (header[1] & 0xff) << 8;
        byte[] packet = readFully(size);
        if (isLegacy()) {
          try {
            buf = cryptor.decrypt(packet, 0, size);
          }
          catch (RuntimeException ex) {
            throw new IOException("decryption failed");
          }
        }
        else {
          if (size < TAG_SIZE) {
            throw new IOException("invalid packet size " + size);
          }
          if (cipher == null) {
            cipher = createCipher();
          }
          buf = crypt(cipher, Cipher.DECRYPT_MODE, getReadKey(), counter++, header, packet, 0, size);
        }
        off = 0;
      }
      while (buf.length == 0);
      return true;
    }
  }


  /**
   * The encrypting output stream.
   */
  private class Output extends OutputStream {

    private final byte[] buf = new byte[MAX_PLAIN_SIZE >> 1];   // pending plaintext
    private int len;                                            // number of pending bytes in buf
    private long counter;                                       // the nonce counter of the write direction
    private Cipher cipher;                                      // the cipher of the write direction
    private boolean closed;

    @Override
    public void write(int b) throws IOException {
      write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
      Objects.checkFromIndexSize(offset, length, b.length);
      while (length > 0) {
        int capacity = (server || helloSent() ? buf.length : REQUEST_SIZE) - len;
        int num = Math.min(length, capacity);
        System.arraycopy(b, offset, buf, len, num);
        offset += num;
        length -= num;
        len += num;
        if (!server && !helloSent()) {
          if (len == REQUEST_SIZE) {
            sendHello(Arrays.copyOf(buf, REQUEST_SIZE));
            len = 0;
          }
        }
        else if (len == buf.length) {
          writePacket();
        }
      }
    }

    @Override
    public void flush() throws IOException {
      if (server || helloSent()) {    // the hello is sent once complete
        writePacket();
      }
      out.flush();
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        closed = true;
        try {
          flush();
        }
        finally {
          out.close();
        }
      }
    }

    private boolean helloSent() {
      synchronized (EncryptedChannel.this) {
        return helloSent;
      }
    }

    /**
     * Encrypts and writes the pending bytes as a packet.
     *
     * @throws IOException if failed
     */
    private void writePacket() throws IOException {
      if (len == 0) {
        return;
      }
      byte[] encrypted;
      if (server) {
        synchronized (EncryptedChannel.this) {
          if (!established) {
            throw new IOException("handshake not complete");
          }
        }
      }
      else {
        awaitServerSalt();
      }
      if (isLegacy()) {
        try {
          encrypted = cryptor.encrypt(buf, 0, len);
        }
        catch (RuntimeException ex) {
          throw new IOException("encryption failed", ex);
        }
        if (encrypted.length > MAX_PACKET_SIZE) {
          throw new IOException("encrypted packet too large (" + encrypted.length + " bytes)");
        }
      }
      else {
        byte[] header = new byte[HEADER_SIZE];
        int size = len + TAG_SIZE;
        header[0] = (byte) size;
        header[1] = (byte) (size >>> 8);
        if (cipher == null) {
          cipher = createCipher();
        }
        encrypted = crypt(cipher, Cipher.ENCRYPT_MODE, getWriteKey(), counter++, header, buf, 0, len);
      }
      byte[] packet = new byte[HEADER_SIZE + encrypted.length];
      packet[0] = (byte) encrypted.length;
      packet[1] = (byte) (encrypted.length >>> 8);
      System.arraycopy(encrypted, 0, packet, HEADER_SIZE, encrypted.length);
      out.write(packet);
      len = 0;
    }
  }

}
