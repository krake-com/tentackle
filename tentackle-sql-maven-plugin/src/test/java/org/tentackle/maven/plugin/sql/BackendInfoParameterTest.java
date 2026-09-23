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

package org.tentackle.maven.plugin.sql;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.sql.SqlTestSupport.RecordingLog;
import org.tentackle.sql.BackendInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@link BackendInfoParameter#createBackendInfo} and
 * {@link BackendInfoParameter#loadMigrationHints}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class BackendInfoParameterTest {

  private static final String URL = "jdbc:h2:mem:backendinfoparameter;DB_CLOSE_DELAY=-1";

  private static Connection connection;

  private RecordingLog log;
  private CreateSqlMojo mojo;


  @BeforeClass(alwaysRun = true)
  public void openDatabase() throws SQLException {
    connection = DriverManager.getConnection(URL);
  }

  @AfterClass(alwaysRun = true)
  public void closeDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }

  @BeforeMethod
  public void setUp() {
    log = new RecordingLog();
    mojo = new CreateSqlMojo();
    mojo.setLogger(log);
  }

  @AfterMethod
  public void tearDown() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DROP ALL OBJECTS");
    }
  }


  @Test
  public void createsBackendInfoFromPlainCredentials() throws Exception {
    BackendInfoParameter param = new BackendInfoParameter();
    param.url = URL;
    param.user = "sa";
    param.password = "secret";
    param.schemaNames = "one, two";

    BackendInfo info = param.createBackendInfo(mojo);

    assertEquals(info.getUrl(), URL);
    assertEquals(info.getUser(), "sa");
    assertEquals(new String(info.getPassword()), "secret");
    assertEquals(info.getSchemas(), new String[] { "one", "two" });
  }

  @Test
  public void resolvesCredentialsFromSettingsServerId() throws Exception {
    Settings settings = new Settings();
    Server server = new Server();
    server.setId("myserver");
    server.setUsername("fromsettings");
    server.setPassword("fromsettings-pw");
    settings.addServer(server);

    SqlTestSupport.setField(mojo, "settings", settings);
    SqlTestSupport.setField(mojo, "settingsDecrypter", passThroughDecrypter());

    BackendInfoParameter param = new BackendInfoParameter();
    param.url = URL;
    param.serverId = "myserver";

    BackendInfo info = param.createBackendInfo(mojo);

    assertEquals(info.getUser(), "fromsettings");
    assertEquals(new String(info.getPassword()), "fromsettings-pw");
  }

  @Test
  public void warnsWhenServerIdReplacesPomCredentials() throws Exception {
    Settings settings = new Settings();
    Server server = new Server();
    server.setId("myserver");
    server.setUsername("fromsettings");
    server.setPassword("fromsettings-pw");
    settings.addServer(server);

    SqlTestSupport.setField(mojo, "settings", settings);
    SqlTestSupport.setField(mojo, "settingsDecrypter", passThroughDecrypter());

    BackendInfoParameter param = new BackendInfoParameter();
    param.url = URL;
    param.user = "frompom";
    param.password = "frompom-pw";
    param.serverId = "myserver";

    param.createBackendInfo(mojo);

    assertTrue(RecordingLog.contains(log.getWarnMessages(),
                                     "replacing user/password from pom.xml by settings.xml for server id 'myserver'"),
               log.getWarnMessages().toString());
  }

  @Test
  public void throwsWhenServerIdIsUnknown() {
    SqlTestSupport.setField(mojo, "settings", new Settings());

    BackendInfoParameter param = new BackendInfoParameter();
    param.url = URL;
    param.serverId = "nosuchserver";

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> param.createBackendInfo(mojo));
    assertTrue(ex.getMessage().contains("no such server id in settings.xml: nosuchserver"), ex.getMessage());
  }


  @Test
  public void loadMigrationHintsUsesAFixedVersionAsIs() throws Exception {
    BackendInfoParameter param = new BackendInfoParameter();
    param.minVersion = "1.0.0";

    BackendInfo backendInfo = new BackendInfo(URL, null, null, null);
    MigrationHints hints = param.loadMigrationHints(backendInfo, mojo, false, null);

    assertTrue(hints.getHintFileNames().isEmpty());
    assertTrue(RecordingLog.contains(log.getDebugMessages(), "applying filter '1.0.0'"), log.getDebugMessages().toString());
  }

  @Test
  public void loadMigrationHintsResolvesTheVersionFromASelectQuery() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE dbversion (version VARCHAR(20))");
      stmt.execute("INSERT INTO dbversion VALUES ('2.7.1')");
    }

    BackendInfoParameter param = new BackendInfoParameter();
    param.minVersion = "SELECT version FROM dbversion";

    BackendInfo backendInfo = new BackendInfo(URL, null, null, null);
    param.loadMigrationHints(backendInfo, mojo, false, null);

    assertEquals(param.minVersion, "2.7.1");
  }

  @Test
  public void loadMigrationHintsFailsWhenTheSelectQueryReturnsNoRows() {
    BackendInfoParameter param = new BackendInfoParameter();
    param.minVersion = "SELECT 1 WHERE 1 = 0";

    BackendInfo backendInfo = new BackendInfo(URL, null, null, null);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
        () -> param.loadMigrationHints(backendInfo, mojo, false, null));
    assertTrue(ex.getMessage().contains("returned no results"), ex.getMessage());
  }

  @Test
  public void loadMigrationHintsResolvesTheVersionFromADatabaseVersionLoader() throws Exception {
    BackendInfoParameter param = new BackendInfoParameter();
    param.minVersion = FixedDatabaseVersionLoader.class.getName();

    BackendInfo backendInfo = new BackendInfo(URL, null, null, null);
    param.loadMigrationHints(backendInfo, mojo, false, null);

    assertEquals(param.minVersion, FixedDatabaseVersionLoader.VERSION);
  }

  @Test
  public void loadMigrationHintsRejectsAClassThatIsNotADatabaseVersionLoader() {
    BackendInfoParameter param = new BackendInfoParameter();
    param.minVersion = "java.lang.String";

    BackendInfo backendInfo = new BackendInfo(URL, null, null, null);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
        () -> param.loadMigrationHints(backendInfo, mojo, false, null));
    assertTrue(ex.getMessage().contains("is not a DatabaseVersionLoader"), ex.getMessage());
  }


  private static SettingsDecrypter passThroughDecrypter() {
    return (SettingsDecryptionRequest request) -> new SettingsDecryptionResult() {
      @Override
      public Server getServer() {
        return request.getServers().isEmpty() ? null : request.getServers().get(0);
      }

      @Override
      public List<Server> getServers() {
        return request.getServers();
      }

      @Override
      public org.apache.maven.settings.Proxy getProxy() {
        return null;
      }

      @Override
      public List<org.apache.maven.settings.Proxy> getProxies() {
        return List.of();
      }

      @Override
      public List<SettingsProblem> getProblems() {
        return List.of();
      }
    };
  }

}
