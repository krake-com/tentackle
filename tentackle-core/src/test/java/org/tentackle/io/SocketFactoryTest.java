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

import static org.testng.Assert.*;

/**
 *
 * @author harald
 */
public class SocketFactoryTest {

  private static final String CONFIG =
          "keepAlive='true', reuseAddr='true', oobInline='true', noDelay='true', rcvBuf='0x400', sndBuf='1452', linger='-1', timeout='3000', tos='0x04', perfPrefs='1,2, 3', range='100'";


  @Test
  public void testEqualsAndHashCode() {
    ClientSocketFactory csf1 = new ClientSocketFactory();
    ClientSocketFactory csf2 = new ClientSocketFactory();
    assertEquals(csf1, csf2);
    DefaultSocketConfigurator csc1 = new DefaultSocketConfigurator();
    csf1.setSocketConfigurator(csc1);
    assertEquals(csf1, csf2);
    assertEquals(csf1.hashCode(), csf2.hashCode());
    csc1.applyParameters(CONFIG);
    assertNotEquals(csf1, csf2);
    assertNotEquals(csf1.hashCode(), csf2.hashCode());

    ServerSocketFactory ssf1 = new ServerSocketFactory();
    ServerSocketFactory ssf2 = new ServerSocketFactory();
    assertEquals(ssf1, ssf2);
    DefaultServerSocketConfigurator ssc1 = new DefaultServerSocketConfigurator();
    ssf1.setSocketConfigurator(ssc1);
    ssf2 = new ServerSocketFactory();
    assertEquals(ssf1, ssf2);
    assertEquals(ssf1.hashCode(), ssf2.hashCode());
    ssc1.applyParameters(CONFIG);
    assertNotEquals(ssf1, ssf2);
    assertNotEquals(ssf1.hashCode(), ssf2.hashCode());
  }
}
