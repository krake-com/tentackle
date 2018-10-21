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

package org.tentackle.buildsupport;

import java.io.IOException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 *
 * @author harald
 */
public class RemoteMethodInfoTest {

  public RemoteMethodInfoTest() {
  }

  @Test
  public void testCleanTypeString() {
    try {
      RemoteMethodInfo info = new RemoteMethodInfo("method");
      assertEquals(info.cleanTypeString("doedel java.util.Map<java.lang.String, org.tentackle.Session>"),
                                        "doedel Map<String, Session>");
      assertEquals(info.cleanTypeString("java.util.Map"),
                                        "Map");
      assertEquals(info.cleanTypeString("doedel java."),
                                        "doedel java.");
      assertEquals(info.cleanTypeString("doedel java. a"),
                                        "doedel java. a");
      assertEquals(info.cleanTypeString("a"),
                                        "a");
      assertEquals(info.cleanTypeString(""),
                                        "");
    }
    catch (IOException ex) {
      fail("creation of RemoteMethodInfo failed", ex);
    }
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
  }

  @BeforeMethod
  public void setUpMethod() throws Exception {
  }

  @AfterMethod
  public void tearDownMethod() throws Exception {
  }
}
