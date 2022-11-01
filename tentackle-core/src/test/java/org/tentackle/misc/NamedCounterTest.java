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

package org.tentackle.misc;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

@SuppressWarnings("missing-explicit-ctor")
public class NamedCounterTest {

  private static final int THREAD_NUM = 500;
  private static final int NAMES_NUM = 1000;
  private static final int ITERATIONS = 100000;


  private final NamedCounter counter = new NamedCounter();

  private final String[] names = new String[NAMES_NUM];
  private final CountDownLatch latch = new CountDownLatch(THREAD_NUM);

  private class CounterTest extends Thread {

    @Override
    public void run() {
      try {
        // count names randomly
        Random random = new Random();
        for (int i = 0; i < ITERATIONS; i++) {
          counter.next(names[random.nextInt(NAMES_NUM)]);
        }
      }
      finally {
        latch.countDown();
      }
    }
  }

  @Test
  public void testCounters() {

    // create array of random names and tests
    Random r = new Random();
    for (int i=0; i < NAMES_NUM; i++) {
      names[i] = Long.toString(r.nextLong());
    }

    // run the tests in parallel
    for (int i = 0; i < THREAD_NUM; i++) {
      new CounterTest().start();
    }

    try {
      latch.await();
      Collection<String> counterNames = counter.names();
      Reporter.log(counterNames.size() + " counters<br>");
      long total = 0;
      long min = Long.MAX_VALUE;
      long max = 0;
      for (String counterName: counterNames) {
        long value = counter.get(counterName);
        max = Math.max(max, value);
        min = Math.min(min, value);
        total += value;
      }
      Reporter.log("max is " + max + ", min is " + min + "<br>");
      Assert.assertEquals(total, THREAD_NUM * ITERATIONS);
    }
    catch (InterruptedException ix) {
      Assert.fail("test was interrupted");
    }
  }

}
