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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link SocketChannelDispatcher} using a Unix domain socket.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SocketChannelDispatcherTest {

  private static final long TIMEOUT_MS = 5000;

  @Test
  public void testUnsupportedSocketAddressThrows() {
    // an address that is neither a UnixDomainSocketAddress nor an InetSocketAddress
    SocketAddress bogus = new SocketAddress() { };
    assertThrows(TentackleRuntimeException.class,
                 () -> new SocketChannelDispatcher("bogus", bogus, () -> channel -> { }));
  }

  @Test
  public void testDispatchesIncomingConnectionAndShutsDown() throws IOException, InterruptedException {
    Path dir = Files.createTempDirectory("scd");
    Path socketFile = dir.resolve("s");     // kept short: Unix socket paths are length-limited
    UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketFile);

    CountDownLatch handled = new CountDownLatch(1);
    AtomicInteger received = new AtomicInteger(-1);

    SocketChannelDispatcher dispatcher = new SocketChannelDispatcher("test-dispatcher", address, () -> channel -> {
      try {
        ByteBuffer buf = ByteBuffer.allocate(1);
        while (buf.hasRemaining() && channel.read(buf) >= 0) {
          // read the single byte
        }
        buf.flip();
        received.set(buf.get() & 0xFF);
        channel.close();
      }
      catch (IOException iox) {
        received.set(-2);
      }
      finally {
        handled.countDown();
      }
    });

    try {
      assertFalse(dispatcher.isShutdown());

      try (SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX)) {
        client.connect(address);
        client.write(ByteBuffer.wrap(new byte[] {0x42}));
        assertTrue(handled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "handler was not invoked in time");
      }

      assertEquals(received.get(), 0x42);
    }
    finally {
      dispatcher.shutdown();
      assertTrue(dispatcher.isShutdown());
      dispatcher.shutdown();    // idempotent
      Files.deleteIfExists(socketFile);
      Files.deleteIfExists(dir);
    }
  }
}
