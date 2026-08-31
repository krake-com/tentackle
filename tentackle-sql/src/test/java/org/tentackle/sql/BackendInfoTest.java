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

package org.tentackle.sql;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.sql.backends.H2;
import org.tentackle.sql.backends.MySql;
import org.tentackle.sql.backends.Postgres;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@link BackendInfo}.
 * <p>
 * The URL is the single string an application configures a database connection with, and everything
 * else — the backend implementation, whether the connection is local or remote, and whether the info
 * can connect at all — is derived from it. The parsing is therefore worth pinning down, especially the
 * explicit backend type behind the vertical bar, which is the escape hatch whenever the URL alone is
 * ambiguous.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class BackendInfoTest {

  private static final String H2_URL = "jdbc:h2:mem:backendinfotest";


  // ---- construction from a backend -----------------------------------------

  @Test
  public void testFromBackend() {
    Backend backend = new H2();
    BackendInfo info = new BackendInfo(backend);
    assertSame(info.getBackend(), backend);
    assertFalse(info.isRemote());
    assertFalse(info.isConnectable(), "an info without url cannot connect");
    assertNull(info.getUrl());
    assertNull(info.getUser());
    assertNull(info.getPassword());
    assertNull(info.getSchemas());
    assertNull(info.getJndiDataSource());
    assertEquals(info.toString(), "H2");
  }

  @Test
  public void testFromBackendName() {
    BackendInfo info = new BackendInfo("postgresql");    // matched case-insensitively
    assertEquals(info.getBackend().getName(), new Postgres().getName());
    assertFalse(info.isConnectable());
  }

  @Test
  public void testFromUnknownBackendName() {
    expectThrows(BackendException.class, () -> new BackendInfo("nosuchbackend"));
  }


  // ---- construction from a url ---------------------------------------------

  @Test
  public void testJdbcUrl() {
    BackendInfo info = new BackendInfo(H2_URL, "sa", new char[0], null);
    assertEquals(info.getBackend().getName(), new H2().getName());
    assertEquals(info.getUrl(), H2_URL);
    assertEquals(info.getUser(), "sa");
    assertFalse(info.isRemote());
    assertTrue(info.isConnectable());
    assertEquals(info.toString(), "H2@" + H2_URL);
  }

  /**
   * The backend type after the vertical bar overrides the URL-based detection and is cut off,
   * so that the remaining URL is the one handed to the driver.
   */
  @Test
  public void testJdbcUrlWithExplicitBackendType() {
    BackendInfo info = new BackendInfo(H2_URL + "|MySQL", null, null, null);
    assertEquals(info.getBackend().getName(), new MySql().getName());
    assertEquals(info.getUrl(), H2_URL, "the backend type must be cut off the url");
  }

  @Test
  public void testJdbcUrlWithUnknownBackend() {
    expectThrows(BackendException.class, () -> new BackendInfo("jdbc:nosuchdb://localhost/x", null, null, null));
  }

  /**
   * Anything that is neither a JDBC- nor a JNDI-URL is a remote (TRIP) connection,
   * for which no backend is known on this side.
   */
  @Test
  public void testRemoteUrl() {
    BackendInfo info = new BackendInfo("trip://localhost:24321/TestServer", "user", null, null);
    assertTrue(info.isRemote());
    assertNull(info.getBackend());
    assertTrue(info.isConnectable());
    assertEquals(info.toString(), "remote@trip://localhost:24321/TestServer");
  }

  @Test
  public void testMissingUrl() {
    assertEquals(expectThrows(BackendException.class,
                              () -> new BackendInfo(null, null, null, null)).getMessage(), "url missing");
    assertEquals(expectThrows(BackendException.class,
                              () -> new BackendInfo("", null, null, null)).getMessage(), "url missing");
  }

  @Test
  public void testJndiLookupFailure() {
    // no initial context factory is configured in the tests -> the lookup must fail with a BackendException
    expectThrows(BackendException.class,
                 () -> new BackendInfo(Constants.BACKEND_JNDI_URL_INTRO + "comp/env/jdbc/test", null, null, null));
  }


  // ---- construction from properties ----------------------------------------

  @Test
  public void testFromProperties() {
    EncryptedProperties props = new EncryptedProperties();
    props.setProperty(Constants.BACKEND_URL, H2_URL);
    props.setProperty(Constants.BACKEND_USER, "sa");
    props.setProperty(Constants.BACKEND_PASSWORD, "secret");
    props.setProperty(Constants.BACKEND_SCHEMAS, "public, other");
    props.setProperty(Constants.BACKEND_TIMEOUT, "15");
    props.setProperty(Constants.BACKEND_KEEP_ALIVE, "true");

    BackendInfo info = new BackendInfo(props);
    assertEquals(info.getUrl(), H2_URL);
    assertEquals(info.getUser(), "sa");
    assertEquals(info.getPassword(), "secret".toCharArray());
    assertEquals(info.getSchemas(), new String[] { "public", "other" });
    assertEquals(info.getBackendTimeout(), 15);
    assertTrue(info.isBackendKeepAliveEnabled());
  }

  @Test
  public void testFromPropertiesDefaults() {
    EncryptedProperties props = new EncryptedProperties();
    props.setProperty(Constants.BACKEND_URL, H2_URL);

    BackendInfo info = new BackendInfo(props);
    assertNull(info.getUser());
    assertNull(info.getPassword());
    assertNull(info.getSchemas());
    assertEquals(info.getBackendTimeout(), 0);
    assertFalse(info.isBackendKeepAliveEnabled());
  }

  @Test
  public void testFromPropertiesWithUnloadableDriver() {
    EncryptedProperties props = new EncryptedProperties();
    props.setProperty(Constants.BACKEND_URL, H2_URL);
    props.setProperty(Constants.BACKEND_DRIVER, "no.such.Driver:file:/no/such.jar");
    expectThrows(BackendException.class, () -> new BackendInfo(props));
  }


  // ---- derived infos -------------------------------------------------------

  @Test
  public void testCopyWithOtherCredentials() {
    BackendInfo original = new BackendInfo(H2_URL, "sa", "secret".toCharArray(), new String[] { "public" });
    BackendInfo copy = new BackendInfo(original, "other", "otherpwd".toCharArray());

    assertSame(copy.getBackend(), original.getBackend());
    assertEquals(copy.getUrl(), original.getUrl());
    assertSame(copy.getSchemas(), original.getSchemas());
    assertEquals(copy.getUser(), "other");
    assertEquals(copy.getPassword(), "otherpwd".toCharArray());
    assertEquals(original.getUser(), "sa", "the original must be left untouched");
  }

  @Test
  public void testClearPassword() {
    BackendInfo info = new BackendInfo(H2_URL, "sa", "secret".toCharArray(), null);
    info.clearPassword();
    assertEquals(info.getPassword(), "      ".toCharArray(), "the password must be blanked in memory");

    // must not fail if there is no password at all
    new BackendInfo(H2_URL, "sa", null, null).clearPassword();
  }

  @Test
  public void testTimeoutAndKeepAliveSetters() {
    BackendInfo info = new BackendInfo(new H2());
    info.setBackendTimeout(30);
    info.setBackendKeepAliveEnabled(true);
    assertEquals(info.getBackendTimeout(), 30);
    assertTrue(info.isBackendKeepAliveEnabled());
  }


  // ---- static parsers ------------------------------------------------------

  @Test
  public void testParseSchemas() {
    assertNull(BackendInfo.parseSchemas(null));
    assertEquals(BackendInfo.parseSchemas("public"), new String[] { "public" });
    assertEquals(BackendInfo.parseSchemas(" a , b ,c "), new String[] { "a", "b", "c" });
    assertNull(BackendInfo.parseSchemas(""), "an empty option yields no schemas");
  }

  @Test
  public void testParsePassword() {
    assertNull(BackendInfo.parsePassword(null));
    assertEquals(BackendInfo.parsePassword(""), new char[0]);
    assertEquals(BackendInfo.parsePassword("secret"), "secret".toCharArray());
  }


  // ---- connecting ----------------------------------------------------------

  /**
   * The connection must always come back in autocommit mode, whatever the driver's default is.
   *
   * @throws SQLException if the test failed
   */
  @Test
  public void testConnect() throws SQLException {
    BackendInfo info = new BackendInfo(H2_URL, "sa", new char[0], null);
    try (Connection connection = info.connect()) {
      assertTrue(connection.getAutoCommit());
    }
  }

}
