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

package org.tentackle.fx;

import org.tentackle.log.Logger;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Provides access to BundleMonkey services.
 */
public class BundleMonkeyHelper {

  /**
   * Unix domain socket in user's home directory.
   */
  public static final String SOCKET_NAME = ".bmonkey.sock";

  /**
   * Resource bundle name prefix in hierarchy dump.
   */
  public static final String BUNDLENAME_PREFIX = "${";

  /**
   * Resource bundle name postfix in hierarchy dump.
   */
  public static final String BUNDLENAME_POSTFIX = "}$";

  public static final String LOCALE_PREFIX = "(LOCALE=";

  public static final String LOCALE_POSTFIX = ")";

  private static final Logger LOGGER = Logger.get(BundleMonkeyHelper.class);


  public static Path createSocketPath() {
    return Path.of(System.getProperty("user.home")).resolve(SOCKET_NAME);
  }

  public static String createNavigationString(String bundleName) {
    return BUNDLENAME_PREFIX + bundleName + BUNDLENAME_POSTFIX;
  }

  public static void navigateTo(String hierarchyDump) {
    try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      channel.connect(UnixDomainSocketAddress.of(createSocketPath()));
      byte[] bytes = hierarchyDump.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buf = ByteBuffer.allocate(bytes.length);
      buf.clear();
      buf.put(bytes);
      buf.flip();
      while (buf.hasRemaining()) {
        channel.write(buf);
      }
      LOGGER.fine("component hierarchy dump sent to BundleMonkey");
    }
    catch (IOException iox) {
      LOGGER.fine("BundleMonkey not listening");
    }
  }


  private BundleMonkeyHelper() {}

}
