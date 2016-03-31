/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.io;

import java.util.StringTokenizer;

/**
 * The socket factory type.
 *
 * @author harald
 */
public enum SocketFactoryType {

  /**
   * System default.<br>
   * JVM's default socket factories.
   */
  SYSTEM,

  /**
   * Remote connection or session default.<br>
   */
  DEFAULT,

  /**
   * Plain.<br>
   * No ssl, no compression.
   */
  PLAIN,

  /**
   * SSL socket.<br>
   * No compression.
   */
  SSL,

  /**
   * Compressed socket.<br>
   * No SSL.
   */
  COMPRESSED,

  /**
   * Compressed SSL.
   */
  SSL_COMPRESSED;


  /**
   * Parses the socket factory type according from a string.
   * <p>
   * <tt>socketfactory=[system|default|plain|ssl|compressed]</tt>: the socket factory type:
   * <ul>
   * <li><tt>system</tt>: use system factories (this is the default)</li>
   * <li><tt>default</tt>: same factories as some provided default</li>
   * <li><tt>plain</tt>: plain sockets (see {@link ClientSocketFactory}, {@link ServerSocketFactory}</li>
   * <li><tt>ssl</tt>: use SSL (see {@link SslClientSocketFactory}, {@link SslServerSocketFactory}</li>
   * <li><tt>compressed</tt>: use compression (see
   * {@link CompressedClientSocketFactory}, {@link CompressedServerSocketFactory}</li>
   * </ul>
   * If both <tt>ssl</tt> and <tt>compressed</tt> is given, the factories used are
   * {@link CompressedSslClientSocketFactory} and {@link CompressedSslServerSocketFactory}.
   * <p>
   *
   * @param config the socket factory configuration string
   * @return the socket factory type
   */
  public static SocketFactoryType parse(String config) {

    SocketFactoryType type = SYSTEM;

    if (config != null) {
      StringTokenizer stok = new StringTokenizer(config, ", \t");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        switch(token.toLowerCase()) {

          case "default":
            type = DEFAULT;
            break;

          case "system":
            type = SYSTEM;
            break;

          case "plain":
            type = PLAIN;
            break;

          case "ssl":
            type = type == SocketFactoryType.COMPRESSED ? SocketFactoryType.SSL_COMPRESSED : SocketFactoryType.SSL;
            break;

          case "compressed":
            type = type == SocketFactoryType.SSL ? SocketFactoryType.SSL_COMPRESSED : SocketFactoryType.COMPRESSED;
            break;
        }
      }
    }

    return type;
  }

}
