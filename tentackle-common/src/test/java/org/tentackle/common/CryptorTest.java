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

package org.tentackle.common;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests for en- and decryption.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CryptorTest {
  private static final String MSG = "Congratulations! You killed the dragon with your bare hands!";

  /** number of threads hammering the shared cryptor. */
  private static final int THREADS = 8;

  /** number of round trips per thread. */
  private static final int ROUNDTRIPS = 500;

  @Test
  public void doCrypt() {
    Cryptor cryptor = Cryptor.getInstanceSafely();
    String base64 = cryptor.encrypt64(MSG);
    Assert.assertEquals(cryptor.decrypt64(base64), MSG);

    char[] msg = MSG.toCharArray();
    String arr64 = cryptor.encrypt64(msg);
    cryptor = new Cryptor("cage", "wave rod");
    Assert.assertEquals(cryptor.decrypt64(arr64), MSG);

    Assert.assertEquals(cryptor.decrypt64ToChars(arr64), MSG.toCharArray());
  }

  /**
   * Asserts that a cryptor shared by concurrent threads works.
   * <p>
   * Cryptors are singletons in real applications, used by all threads of a server, for example.
   * Since the underlying {@link javax.crypto.Cipher} is stateful and not threadsafe, sharing one
   * instance made concurrent en-/decryptions corrupt each other, either throwing or -- way worse --
   * silently returning wrong data.
   */
  @Test
  public void doConcurrentCrypt() throws InterruptedException {
    Cryptor cryptor = new Cryptor("cage", "wave rod");
    CountDownLatch startSignal = new CountDownLatch(1);
    ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int i = 0; i < THREADS; i++) {
        pool.execute(() -> {
          try {
            startSignal.await();    // maximize the overlap
            for (int n = 0; n < ROUNDTRIPS; n++) {
              String decrypted = cryptor.decrypt64(cryptor.encrypt64(MSG));
              if (!MSG.equals(decrypted)) {
                failures.add("wrong plaintext: " + decrypted);
              }
            }
          }
          catch (InterruptedException ix) {
            Thread.currentThread().interrupt();
            failures.add("interrupted");
          }
          catch (RuntimeException rx) {
            failures.add(rx.toString());
          }
        });
      }

      startSignal.countDown();
      pool.shutdown();
      Assert.assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "concurrent crypt timed out");
    }
    finally {
      pool.shutdownNow();
    }

    Assert.assertEquals(failures.size(), 0, "concurrent crypt failed " + failures.size() + " of " +
                        (THREADS * ROUNDTRIPS) + " times, first: " + failures.peek());
  }
}
