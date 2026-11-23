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

package org.tentackle.trip;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link RejectionLog}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RejectionLogTest {

  @AfterMethod
  public void restore() {
    RejectionLog.setMaxMessages(RejectionLog.DEFAULT_MAX_MESSAGES);
    RejectionLog.setInterval(RejectionLog.DEFAULT_INTERVAL);
    RejectionLog.setClock(null);
  }

  @Test
  public void throttlesWithinInterval() {
    AtomicLong now = new AtomicLong(1000);
    RejectionLog.setClock(now::get);
    RejectionLog.setMaxMessages(3);
    RejectionLog.setInterval(1000);

    assertTrue(RejectionLog.isLoggable());
    assertTrue(RejectionLog.isLoggable());
    assertTrue(RejectionLog.isLoggable());
    assertFalse(RejectionLog.isLoggable());
    assertFalse(RejectionLog.isLoggable());

    now.addAndGet(999);
    assertFalse(RejectionLog.isLoggable());

    now.addAndGet(1);   // next interval: logs the summary and starts over
    assertTrue(RejectionLog.isLoggable());
    assertTrue(RejectionLog.isLoggable());
    assertTrue(RejectionLog.isLoggable());
    assertFalse(RejectionLog.isLoggable());
  }

  @Test
  public void unlimited() {
    RejectionLog.setClock(() -> 1000);
    RejectionLog.setMaxMessages(0);
    for (int i = 0; i < 1000; i++) {
      assertTrue(RejectionLog.isLoggable());
    }
  }

}
