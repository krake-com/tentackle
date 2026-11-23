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

import tech.kwik.core.ConnectionListener;
import tech.kwik.core.ConnectionStalledEvent;
import tech.kwik.core.ConnectionTerminatedEvent;
import tech.kwik.core.QuicClientConnection;

import org.tentackle.log.Logger;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Migrates a client's QUIC connection to a new source port when its path stalls.
 * <p>
 * KWIK reports every probe timeout (PTO) of an established client connection as a
 * {@link ConnectionStalledEvent}, along with the number of consecutive PTOs without any acknowledgment in
 * between. Once that number reaches the threshold, {@link QuicClientConnection#migrate(Duration)} opens a new
 * UDP socket, validates the path from it and switches over only if the server answered; otherwise the
 * connection stays where it is. Since the PTO follows the measured round trip time and backs off
 * exponentially, a threshold of 2 amounts to roughly three times the first PTO, on a slow path as well as
 * on a fast one.
 * <p>
 * The migration blocks until the path is validated or the timeout elapsed, so it runs on a virtual thread
 * of its own: KWIK delivers all events of a connection on a single callback thread, which must not be held
 * up. Only one migration runs at a time. Another one is started only once another {@code threshold} PTOs
 * have passed without an acknowledgment, which retries a migration that failed.
 * <p>
 * KWIK retires the connection ID of the abandoned path, and the peer issues a new one in return, so the
 * number of migrations per connection is not limited. However, KWIK's client holds only a single spare
 * connection ID of the server ({@code active_connection_id_limit} 2, not configurable), and its replacement
 * arrives only after the old path's ID was retired, about 100 ms plus a round trip after the migration. A
 * new path that stalls before that cannot be left anymore: the attempts fail until the idle timeout ends the
 * connection and the pool re-establishes it.
 */
class QuicMigrator implements ConnectionListener {

  private static final Logger LOGGER = Logger.get(QuicMigrator.class);

  private final QuicClientConnection connection;
  private final int threshold;
  private final Duration timeout;
  private final String name;
  private final AtomicBoolean migrating;
  private final AtomicInteger migrationCount;
  private int baseline;                 // the PTO count of the last attempt in this series, only accessed by the callback thread
  private int lastCount;                // the PTO count of the last event, only accessed by the callback thread
  private volatile int attemptCount;    // the PTO count of a finished attempt not taken as baseline yet, -1 if none
  private volatile boolean closed;

  /**
   * Creates a migrator.
   *
   * @param connection the client connection to migrate
   * @param threshold the number of consecutive probe timeouts to migrate after, &gt; 0
   * @param timeout how long to wait for the new path to be validated
   * @param name the name for logging and the migration thread, usually the URI without its query
   */
  QuicMigrator(QuicClientConnection connection, int threshold, Duration timeout, String name) {
    if (threshold <= 0) {
      throw new IllegalArgumentException("threshold must be > 0");
    }
    this.connection = connection;
    this.threshold = threshold;
    this.timeout = timeout;
    this.name = name;
    migrating = new AtomicBoolean();
    migrationCount = new AtomicInteger();
    attemptCount = -1;
  }

  /**
   * Gets how often the connection migrated to a new source port.
   *
   * @return the number of migrations
   */
  int getMigrationCount() {
    return migrationCount.get();
  }

  @Override
  public void stalled(ConnectionStalledEvent event) {
    int count = event.probeTimeoutCount();
    if (count <= lastCount) {
      baseline = 0;               // an acknowledgment came in: a new series of probe timeouts
    }
    lastCount = count;
    int attempted = attemptCount;
    if (attempted >= 0) {
      attemptCount = -1;
      if (attempted <= count) {
        // Still the same series: wait for another threshold probe timeouts. After a failure, that is when
        // the next attempt is due. After a success, the packets in flight on the old path are still to be
        // declared lost, which may well take another probe timeout, but is no reason to move on again.
        baseline = attempted;
      }
    }
    if (closed || count - baseline < threshold || !migrating.compareAndSet(false, true)) {
      return;
    }
    LOGGER.info("{0}: {1} probe timeouts without acknowledgment for {2} ms -> migrating",
                name, count, event.stalledDuration().toMillis());
    Thread.ofVirtual().name("QUIC migrator " + name).start(() -> migrate(count));
  }

  @Override
  public void disconnected(ConnectionTerminatedEvent event) {
    closed = true;
  }


  /**
   * Migrates the connection.<br>
   * Runs on a thread of its own.
   *
   * @param count the probe timeout count the migration was triggered at
   */
  private void migrate(int count) {
    boolean migrated = false;
    try {
      InetSocketAddress oldAddress = connection.getLocalAddress();
      migrated = connection.migrate(timeout);
      if (migrated) {
        migrationCount.incrementAndGet();
        LOGGER.info("{0}: migrated from local port {1} to {2}", name,
                    oldAddress == null ? "?" : Integer.toString(oldAddress.getPort()),
                    Integer.toString(connection.getLocalAddress().getPort()));
      }
      else if (!closed) {
        LOGGER.info("{0}: migration failed, staying on the current path", name);
      }
    }
    catch (SocketException | RuntimeException e) {
      if (!closed) {
        LOGGER.warning(name + ": migration failed", e);
      }
    }
    finally {
      attemptCount = count;
      migrating.set(false);
    }
  }

}
