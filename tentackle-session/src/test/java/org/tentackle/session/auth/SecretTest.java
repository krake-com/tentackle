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
package org.tentackle.session.auth;

import org.tentackle.common.Cryptor;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

/**
 * Tests that a secret owns its value, never shares an array with its caller,
 * and stays consistent under concurrent access.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SecretTest {

  private static final String SECRET = "secret";
  private static final int READERS = 8;
  private static final int ITERATIONS = 20000;

  @Test
  public void testSetCopiesTheCallersArray() {
    char[] password = "secret".toCharArray();
    Secret secret = new Secret(password);

    Arrays.fill(password, ' ');   // the caller wipes its own array, as it should

    Assert.assertEquals(secret.get(), "secret".toCharArray());
    Assert.assertTrue(secret.isSet());
    Assert.assertFalse(secret.isCleared());
  }


  @Test
  public void testGetReturnsACopy() {
    Secret secret = new Secret("secret".toCharArray());

    char[] password = secret.get();
    Arrays.fill(password, ' ');   // the caller wipes what it was handed

    Assert.assertEquals(secret.get(), "secret".toCharArray());
  }


  @Test
  public void testSessionInfoPasswordIsIndependentOfTheCallersArray() {
    char[] password = "secret".toCharArray();
    SessionInfo sessionInfo = new DefaultSessionInfo("hugo", password, null);
    Arrays.fill(password, ' ');
    Assert.assertEquals(sessionInfo.getPassword(), "secret".toCharArray());

    /*
     * This is what a connection manager does: pass the password on to a backend info,
     * which may wipe it once the connection is established.
     */
    char[] handedOut = sessionInfo.getPassword();
    Arrays.fill(handedOut, ' ');
    Assert.assertEquals(sessionInfo.getPassword(), "secret".toCharArray(),
                        "the session info lost its password to a caller wiping its copy");

    // only clear() clears it
    sessionInfo.clearPassword();
    Assert.assertNull(sessionInfo.getPassword());
  }


  /**
   * A reader must never observe a secret while it is being cleared or replaced.
   * <p>
   * {@link Secret#clear()} blanks the value in place, so an unsynchronized reader could copy a
   * half-wiped array, or trip over the value vanishing between the null check and its use.
   */
  @Test
  public void testConcurrentAccessNeitherTearsNorFails() throws Exception {
    Secret secret = new Secret(SECRET.toCharArray());
    Queue<String> failures = new ConcurrentLinkedQueue<>();
    CyclicBarrier barrier = new CyclicBarrier(READERS + 2);
    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < READERS; i++) {
      threads.add(new Thread(() -> run(barrier, failures, () -> {
        char[] value = secret.get();
        if (value != null && !Arrays.equals(value, SECRET.toCharArray())) {
          failures.add("torn value: '" + String.valueOf(value) + "'");
        }
      })));
    }
    threads.add(new Thread(() -> run(barrier, failures, secret::clear)));
    threads.add(new Thread(() -> run(barrier, failures, () -> secret.set(SECRET.toCharArray()))));

    for (Thread thread: threads) {
      thread.setDaemon(true);
      thread.start();
    }
    for (Thread thread: threads) {
      thread.join();
    }

    Assert.assertTrue(failures.isEmpty(), failures.size() + " failure(s), first: " + failures.peek());
  }


  /**
   * Pins the premise for the defensive copy in {@link Secret#set(char[])}: a cryptor wipes the
   * array it encrypts, so a secret must never hand it the caller's array. There is no
   * {@code @Service(Cryptor.class)} on the test classpath, hence the explicit instance.
   */
  @Test
  public void testCryptorWipesTheArrayItEncrypts() {
    Cryptor cryptor = new Cryptor("salt", "passphrase");
    char[] password = "secret".toCharArray();

    byte[] encrypted = cryptor.encrypt(password);

    Assert.assertEquals(cryptor.decryptToChars(encrypted), "secret".toCharArray());
    Assert.assertEquals(password, "      ".toCharArray(), "Cryptor no longer wipes its input");
  }


  /**
   * Runs an operation in a loop, in lockstep with the other threads of a concurrency test.
   *
   * @param barrier the start barrier
   * @param failures collects what went wrong
   * @param operation the operation to repeat
   */
  private void run(CyclicBarrier barrier, Queue<String> failures, Runnable operation) {
    try {
      barrier.await();
      for (int i = 0; i < ITERATIONS; i++) {
        operation.run();
      }
    }
    catch (RuntimeException | InterruptedException | BrokenBarrierException ex) {
      failures.add(ex.toString());
    }
  }

}
