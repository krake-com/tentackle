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

package org.tentackle.quiche.ffm;

import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests loading the native quiche library and the plain functions that need nothing else.
 * <p>
 * This is the smoke test for the whole FFM layer: if it passes, the binary was found in the
 * {@code jetty-quiche-native} artifact, extracted, loaded, and its symbols are reachable.
 */
public class NativeLibraryTest {

  @Test(timeOut = 30000)
  public void testPlatformIsSupported() {
    Reporter.log("platform is one of the directories in the quiche binary artifact", true);
    String platform = NativeLibrary.getPlatform();
    assertTrue(platform.matches("(darwin|linux|win32)-(x86-64|aarch64)"), "unexpected platform " + platform);
  }

  @Test(timeOut = 30000)
  public void testLibraryNameMatchesThePlatform() {
    Reporter.log("the library file name follows the platform's convention", true);
    String name = NativeLibrary.getLibraryName();
    String platform = NativeLibrary.getPlatform();
    if (platform.startsWith("darwin")) {
      assertEquals(name, "libquiche.dylib");
    }
    else if (platform.startsWith("win32")) {
      assertEquals(name, "quiche.dll");
    }
    else {
      assertEquals(name, "libquiche.so");
    }
  }

  @Test(timeOut = 30000)
  public void testLibraryIsLoaded() {
    Reporter.log("the native library is loaded and exports its symbols", true);
    assertNotNull(NativeLibrary.getLookup().find("quiche_version").orElse(null));
  }

  @Test(timeOut = 30000)
  public void testVersion() {
    Reporter.log("quiche_version returns a version string", true);
    String version = Quiche.version();
    Reporter.log("native quiche version is " + version, true);
    assertNotNull(version);
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected version string '" + version + "'");
  }

  @Test(timeOut = 30000)
  public void testVersionIsSupported() {
    Reporter.log("the library speaks QUIC version 1 (RFC-9000), and not the old drafts", true);
    assertTrue(Quiche.versionIsSupported(Quiche.PROTOCOL_VERSION));
    assertFalse(Quiche.versionIsSupported(0xff00_001d));   // draft-29
    assertFalse(Quiche.versionIsSupported(0xdead_beef));
  }

  @Test(timeOut = 30000)
  public void testErrorName() {
    Reporter.log("error codes are translated to their names", true);
    assertEquals(Quiche.errorName(Quiche.ERR_DONE), "DONE");
    assertEquals(Quiche.errorName(Quiche.ERR_TLS_FAIL), "TLS_FAIL");
    assertEquals(Quiche.errorName(Quiche.ERR_STREAM_STOPPED), "STREAM_STOPPED");
    assertEquals(Quiche.errorName(-4711), "-4711");
  }

  @Test(timeOut = 30000)
  public void testUnknownSymbolIsReported() {
    Reporter.log("binding a symbol the library does not export fails with a clear message", true);
    try {
      Quiche.downcall("quiche_no_such_function",
                      java.lang.foreign.FunctionDescriptor.of(ValueLayouts.INT));
      org.testng.Assert.fail("binding an unknown symbol must fail");
    }
    catch (QuicheException e) {
      assertTrue(e.getMessage().contains("quiche_no_such_function"), e.getMessage());
    }
  }

}
