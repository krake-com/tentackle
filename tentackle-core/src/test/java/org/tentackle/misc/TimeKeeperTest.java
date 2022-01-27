/*
 * Tentackle - https://tentackle.org.
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

import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Test for Duration.
 *
 * @author harald
 */
public class TimeKeeperTest {


  private static final int LOOPCOUNT = 1000000;   // loops

  @Test
  public void testDuration() {
    TimeKeeper total = new TimeKeeper();
    for (int i=0; i < LOOPCOUNT; i++) {
      TimeKeeper dur = new TimeKeeper();
      dur.end();
    }
    total.end();
    long durNanos = total.nanos() / LOOPCOUNT;
    Reporter.log("total elapsed time: " + total.nanos()/1000000 + "ms, System.nanoTime: " + durNanos/2 + "ns/call<br/>");


    // same with milliseconds (just to measure performance difference)
    total = new TimeKeeper();
    for (int i=0; i < LOOPCOUNT; i++) {
      System.currentTimeMillis();
    }
    total.end();
    Reporter.log("System.currentTimeMillis: " + total.nanos()/LOOPCOUNT + "ns/call<br/>");
  }

}