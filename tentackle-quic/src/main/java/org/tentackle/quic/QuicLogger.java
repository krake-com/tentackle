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

package org.tentackle.quic;

import tech.kwik.core.log.BaseLogger;

import org.tentackle.log.Logger;

import java.nio.ByteBuffer;

/**
 * Bridges KWIK's logging onto Tentackle's {@link Logger}.
 * <p>
 * Plain messages are logged at {@code INFO}, messages with an exception at {@code SEVERE}, and hex dumps
 * of packet data at {@code FINE}. Which categories KWIK produces at all is configured on the instance,
 * see {@link QuicTripTransport#accept()}.
 */
public class QuicLogger extends BaseLogger {

  private static final Logger LOGGER = Logger.get(QuicLogger.class);

  /**
   * Creates a logger proxy.
   */
  public QuicLogger() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected void log(String message) {
    LOGGER.info(message);
  }

  @Override
  protected void log(String message, Throwable error) {
    LOGGER.severe(message, error);
  }

  @Override
  protected void logWithHexDump(String message, byte[] data, int length) {
    if (LOGGER.isFineLoggable()) {
      LOGGER.fine("{0}: {1}", message, byteToHexBlock(data, length));
    }
  }

  @Override
  protected void logWithHexDump(String message, ByteBuffer data, int offset, int length) {
    if (LOGGER.isFineLoggable()) {
      LOGGER.fine("{0}: {1}", message, byteToHexBlock(data, offset, length));
    }
  }

}
