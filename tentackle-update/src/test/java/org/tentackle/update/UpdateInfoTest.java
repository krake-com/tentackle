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

package org.tentackle.update;

import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;

import java.net.URI;
import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link UpdateInfo}, the record the server answers an update request with.
 */
@SuppressWarnings("missing-explicit-ctor")
public class UpdateInfoTest {

  private static final String URL_STR = "https://downloads.example.org/myapp/myapp-2.1.0-linux-amd64.zip";

  @Test
  public void keepsEveryFieldTheServerDecided() {
    UpdateInfo info = new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.sh");
    assertEquals(info.version(), "2.1.0");
    assertEquals(info.urlStr(), URL_STR);
    assertEquals(info.checksum(), "cafebabe");
    assertEquals(info.size(), 4711L);
    assertEquals(info.updateExecutor(), "update.sh");
  }

  /**
   * The URL is carried as a string because {@link URL} is not a value type worth serializing;
   * the convenience constructor must store its external form verbatim.
   */
  @Test
  public void storesTheExternalFormOfAUrl() throws Exception {
    URL url = new URI(URL_STR).toURL();
    UpdateInfo info = new UpdateInfo("2.1.0", url, "cafebabe", 4711L, "update.sh");
    assertEquals(info.urlStr(), URL_STR);
    assertEquals(info.getUrl(), url);
    assertEquals(info, new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.sh"));
  }

  @Test
  public void rebuildsTheUrlFromItsStringRepresentation() {
    assertEquals(new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.sh").getUrl().toExternalForm(), URL_STR);
  }

  /**
   * A syntactically broken URL only blows up when the client tries to use it - the record itself
   * accepts any string, since it also has to survive deserialization of whatever the server sent.
   */
  @Test
  public void reportsASyntacticallyInvalidUrl() {
    UpdateInfo info = new UpdateInfo("2.1.0", "not a url", "cafebabe", 1L, "update.sh");
    expectThrows(TentackleRuntimeException.class, info::getUrl);
  }

  @Test
  public void reportsAnUnknownUrlProtocol() {
    UpdateInfo info = new UpdateInfo("2.1.0", "nosuchprotocol://host/app.zip", "cafebabe", 1L, "update.sh");
    expectThrows(TentackleRuntimeException.class, info::getUrl);
  }

  /**
   * The client terminates and restarts right after this record arrives, so the transports are
   * torn down gracefully to keep socket resets out of the server's log.
   */
  @Test
  public void requestsAllTransportsToBeClosed() {
    assertTrue(new UpdateInfo("2.1.0", URL_STR, "cafebabe", 1L, "update.sh").isCloseAllTransportsRequested());
  }

  @Test
  public void comparesByValue() {
    UpdateInfo info = new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.sh");
    assertEquals(info.hashCode(), new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.sh").hashCode());
    assertNotEquals(info, new UpdateInfo("2.1.1", URL_STR, "cafebabe", 4711L, "update.sh"));
    assertNotEquals(info, new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4712L, "update.sh"));
    assertNotEquals(info, new UpdateInfo("2.1.0", URL_STR, "cafebabe", 4711L, "update.cmd"));
  }

}
