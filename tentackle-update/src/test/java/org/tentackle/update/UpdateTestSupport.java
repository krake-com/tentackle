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

package org.tentackle.update;

import org.tentackle.common.StringHelper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Common helpers for the update tests.
 */
public final class UpdateTestSupport {

  /** The length of a SHA-256 hash rendered as a hex string. */
  public static final int SHA256_HEX_LENGTH = 64;

  /**
   * Computes the SHA-256 checksum exactly the way {@code ClientUpdateUtilities.downloadZip}
   * expects it: a zero-padded, lowercase hex string.
   *
   * @param content the content to hash
   * @return the checksum
   */
  public static String sha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return StringHelper.fillLeft(new BigInteger(1, digest.digest(content)).toString(16), SHA256_HEX_LENGTH, '0');
    }
    catch (NoSuchAlgorithmException ex) {
      throw new AssertionError("SHA-256 must be available", ex);
    }
  }

  /**
   * Determines a currently free TCP port on the loopback interface.<br>
   * A TRIP server transport binds to the port named in its URI, so it cannot be left to the OS.
   *
   * @return the port number
   * @throws IOException if no port could be allocated
   */
  public static int freeTcpPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }


  private UpdateTestSupport() {
  }

}
