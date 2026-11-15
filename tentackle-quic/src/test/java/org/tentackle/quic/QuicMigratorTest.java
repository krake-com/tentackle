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

import tech.kwik.core.ConnectionStalledEvent;
import tech.kwik.core.QuicClientConnection;

import org.testng.Reporter;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests when {@link QuicMigrator} decides to migrate, without any network.
 */
@SuppressWarnings("missing-explicit-ctor")
public class QuicMigratorTest {

  /**
   * A fake connection whose {@code migrate} returns a configurable result.
   */
  private static class FakeConnection {

    private final AtomicInteger attempts = new AtomicInteger();
    private final Semaphore done = new Semaphore(0);
    private volatile boolean succeed;
    private volatile int port = 1000;

    private QuicClientConnection proxy() {
      return (QuicClientConnection) Proxy.newProxyInstance(
        QuicClientConnection.class.getClassLoader(), new Class<?>[] { QuicClientConnection.class },
        (_, method, _) -> switch (method.getName()) {
          case "migrate" -> {
            attempts.incrementAndGet();
            if (succeed) {
              port++;
            }
            done.release();
            yield succeed;
          }
          case "getLocalAddress" -> new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
          case "toString" -> "fake";
          default -> null;
        });
    }

    private void awaitAttempt() throws InterruptedException {
      assertTrue(done.tryAcquire(5, TimeUnit.SECONDS), "no migration attempt");
      Thread.sleep(50);    // let the migrator finish its bookkeeping
    }
  }


  private static ConnectionStalledEvent stalled(QuicClientConnection connection, int count) {
    return new ConnectionStalledEvent(connection, count, Duration.ofMillis(100L * count));
  }


  @Test(timeOut = 30000)
  public void migratesAtThreshold() throws Exception {
    Reporter.log("migratesAtThreshold", true);
    FakeConnection fake = new FakeConnection();
    fake.succeed = true;
    QuicClientConnection connection = fake.proxy();
    QuicMigrator migrator = new QuicMigrator(connection, 2, Duration.ofSeconds(1), "test");

    migrator.stalled(stalled(connection, 1));
    Thread.sleep(100);
    assertEquals(fake.attempts.get(), 0, "migrated below the threshold");

    migrator.stalled(stalled(connection, 2));
    fake.awaitAttempt();
    assertEquals(migrator.getMigrationCount(), 1);

    // the same series continues: not another migration right away
    migrator.stalled(stalled(connection, 3));
    Thread.sleep(100);
    assertEquals(fake.attempts.get(), 1, "migrated again without waiting for another threshold");

    // a new series after an acknowledgment
    migrator.stalled(stalled(connection, 1));
    migrator.stalled(stalled(connection, 2));
    fake.awaitAttempt();
    assertEquals(migrator.getMigrationCount(), 2);
  }

  @Test(timeOut = 30000)
  public void retriesAfterAnotherThreshold() throws Exception {
    Reporter.log("retriesAfterAnotherThreshold", true);
    FakeConnection fake = new FakeConnection();
    QuicClientConnection connection = fake.proxy();
    QuicMigrator migrator = new QuicMigrator(connection, 2, Duration.ofSeconds(1), "test");

    migrator.stalled(stalled(connection, 1));
    migrator.stalled(stalled(connection, 2));
    fake.awaitAttempt();
    assertEquals(migrator.getMigrationCount(), 0);

    migrator.stalled(stalled(connection, 3));
    Thread.sleep(100);
    assertEquals(fake.attempts.get(), 1, "retried too early");

    fake.succeed = true;
    migrator.stalled(stalled(connection, 4));
    fake.awaitAttempt();
    assertEquals(fake.attempts.get(), 2);
    assertEquals(migrator.getMigrationCount(), 1);
  }

  @Test(timeOut = 30000)
  public void ignoresEventsOnceDisconnected() throws Exception {
    Reporter.log("ignoresEventsOnceDisconnected", true);
    FakeConnection fake = new FakeConnection();
    QuicClientConnection connection = fake.proxy();
    QuicMigrator migrator = new QuicMigrator(connection, 1, Duration.ofSeconds(1), "test");
    migrator.disconnected(null);
    migrator.stalled(stalled(connection, 5));
    Thread.sleep(100);
    assertEquals(fake.attempts.get(), 0);
    assertFalse(migrator.getMigrationCount() > 0);
  }

}
