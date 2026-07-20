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

package org.tentackle.daemon;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link Scavenger} default- and static methods.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScavengerTest {

  /**
   * A scavenging thread whose {@link Scavenger#isScavenging()} flag is controllable.
   */
  private static class ScavengerThread extends Thread implements Scavenger {

    private final boolean scavenging;

    ScavengerThread(String name, boolean scavenging) {
      super(name);
      this.scavenging = scavenging;
    }

    @Override
    public boolean isScavenging() {
      return scavenging;
    }
  }


  @Test
  public void testDefaultIsScavenging() {
    // the default implementation always reports true
    Scavenger scavenger = new Scavenger() { };
    assertTrue(scavenger.isScavenging());
  }

  @Test
  public void testScavengerThreadIsScavenging() {
    assertTrue(Scavenger.isScavenger(new ScavengerThread("scavenger", true)));
  }

  @Test
  public void testScavengerThreadNotScavenging() {
    // implements Scavenger but explicitly opted out
    assertFalse(Scavenger.isScavenger(new ScavengerThread("idle", false)));
  }

  @Test
  public void testPlainThreadIsNoScavenger() {
    assertFalse(Scavenger.isScavenger(new Thread("plain")));
  }

  @Test
  public void testSecondaryFinalizerIsScavenger() {
    // recognized purely by its thread name
    assertTrue(Scavenger.isScavenger(new Thread("Secondary finalizer")));
  }
}
