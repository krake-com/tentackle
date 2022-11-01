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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for socket configurators.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class SocketConfiguratorTest {

  private static final String CONFIG =
          "keepAlive='true', reuseAddr='true', oobInline='true', noDelay='true', rcvBuf='0x400', sndBuf='1452', linger='-1', timeout='3000', tos='0x04', perfPrefs='1,2, 3', range='100'";


  @Test
  public void parseConfiguration() {
    DefaultSocketConfigurator sc = new DefaultSocketConfigurator();
    sc.applyParameters(CONFIG);
    assertEquals(sc.getKeepAlive(), Boolean.TRUE);
    assertEquals(sc.getReuseAddress(), Boolean.TRUE);
    assertEquals(sc.getOOBInline(), Boolean.TRUE);
    assertEquals(sc.getTcpNoDelay(), Boolean.TRUE);
    assertEquals(sc.getSendBufferSize(), Integer.valueOf(1452));
    assertEquals(sc.getSoLinger(), Integer.valueOf(-1));
    assertEquals(sc.getSoTimeout(), Integer.valueOf(3000));
    assertEquals(sc.getTrafficClass(), Integer.valueOf(0x04));
    SocketPerformancePreferences prefs = sc.getPerformancePreferences();
    assertNotNull(prefs);
    assertEquals(prefs.getConnectionTime(), 1);
    assertEquals(prefs.getLatency(), 2);
    assertEquals(prefs.getBandwidth(), 3);

    DefaultServerSocketConfigurator ssc = new DefaultServerSocketConfigurator();
    ssc.applyParameters(CONFIG);
    assertEquals(sc.getReuseAddress(), Boolean.TRUE);
    assertEquals(ssc.getReceiveBufferSize(), Integer.valueOf(1024));
    assertEquals(ssc.getSoTimeout(), Integer.valueOf(3000));
    assertEquals(ssc.getPortRange(), 100);
    prefs = ssc.getPerformancePreferences();
    assertNotNull(prefs);
    assertEquals(prefs.getConnectionTime(), 1);
    assertEquals(prefs.getLatency(), 2);
    assertEquals(prefs.getBandwidth(), 3);
  }


  @Test
  public void testEqualsAndHashCode() {
    DefaultSocketConfigurator csc1 = new DefaultSocketConfigurator();
    csc1.applyParameters(CONFIG);
    DefaultSocketConfigurator csc2 = new DefaultSocketConfigurator();
    csc2.applyParameters(CONFIG);
    assertEquals(csc1, csc2);
    DefaultSocketConfigurator csc3 = new DefaultSocketConfigurator();
    assertNotEquals(csc1, csc3);
  }

}
