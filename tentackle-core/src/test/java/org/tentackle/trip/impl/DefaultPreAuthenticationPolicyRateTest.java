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

package org.tentackle.trip.impl;

import org.testng.annotations.Test;

import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.TripPolicyViolationException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

/**
 * Tests the connection rate limit of the {@link DefaultPreAuthenticationPolicy}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultPreAuthenticationPolicyRateTest {

  /**
   * A policy with a clock under the test's control.
   */
  private static class ClockedPolicy extends DefaultPreAuthenticationPolicy {

    private long now = 1_000_000_000L;

    ClockedPolicy(Mode mode) {
      super(mode);
      setFirstMessageTimeout(0);    // no deadlines: the admissions are never used
      setMaxConnectionsPerHost(0);
      setMaxConnections(0);
    }

    void advanceMillis(long millis) {
      now += millis * 1_000_000L;
    }

    @Override
    protected long nanoTime() {
      return now;
    }
  }


  private PreAuthenticationPolicy.Admission admit(DefaultPreAuthenticationPolicy policy, String host, AtomicInteger closed) {
    return policy.admit(host, closed::incrementAndGet, () -> false);
  }

  @Test
  public void burstIsAdmittedThenRefused() {
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setConnectionRate(10);
    policy.setConnectionBurst(5);
    AtomicInteger closed = new AtomicInteger();
    for (int i = 0; i < 5; i++) {
      assertNotNull(admit(policy, "10.0.0.1", closed), "connection " + i);
    }
    assertNull(admit(policy, "10.0.0.1", closed));
    assertEquals(policy.getAdmittedConnections(), 5);
    assertEquals(closed.get(), 0, "refusing must not evict anybody");

    // other hosts have buckets of their own
    assertNotNull(admit(policy, "10.0.0.2", closed));
  }

  @Test
  public void bucketRefills() {
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setConnectionRate(10);
    policy.setConnectionBurst(2);
    AtomicInteger closed = new AtomicInteger();
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNull(admit(policy, "10.0.0.1", closed));

    policy.advanceMillis(100);    // one token at 10/s
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNull(admit(policy, "10.0.0.1", closed));

    policy.advanceMillis(10_000);    // never more than the burst
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNull(admit(policy, "10.0.0.1", closed));
  }

  @Test
  public void ipv6NetworkSharesOneBucket() {
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setConnectionRate(1);
    policy.setConnectionBurst(1);
    AtomicInteger closed = new AtomicInteger();
    assertNotNull(admit(policy, "[2001:db8:0:0:0:0:0:1]", closed));
    assertNull(admit(policy, "[2001:db8:0:0:0:0:0:2]", closed), "same /64");
    assertNotNull(admit(policy, "[2001:db8:0:1:0:0:0:1]", closed), "other /64");
  }

  @Test
  public void unlimited() {
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    policy.setConnectionRate(0);
    AtomicInteger closed = new AtomicInteger();
    for (int i = 0; i < 1000; i++) {
      assertNotNull(admit(policy, "10.0.0.1", closed));
    }
  }

  @Test
  public void auditRefusesNothing() {
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.AUDIT);
    policy.setConnectionRate(1);
    policy.setConnectionBurst(1);
    AtomicInteger closed = new AtomicInteger();
    assertNotNull(admit(policy, "10.0.0.1", closed));
    assertNotNull(admit(policy, "10.0.0.1", closed));
  }

  @Test
  public void enforcingViolationThrows() {
    // sanity check of the mode the refusal depends on
    ClockedPolicy policy = new ClockedPolicy(PreAuthenticationPolicy.Mode.ENFORCE);
    expectThrows(TripPolicyViolationException.class, () -> policy.violation("test"));
  }

}
