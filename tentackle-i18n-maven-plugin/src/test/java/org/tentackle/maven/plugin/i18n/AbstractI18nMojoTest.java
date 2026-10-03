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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.project.MavenProject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import org.tentackle.common.BundleSupport;
import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.dbms.Db;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.sql.Backend;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Base class for the mojo tests of this plugin.
 * <p>
 * All four goals do the same two things: they collect the resource bundles of a project from its
 * classpath and compare them to the {@code StoredBundle}s of a database backend. Neither half is
 * stubbed here:
 * <ul>
 *   <li>the bundles are registered through {@code META-INF/services} and {@code META-INF/bundles}
 *       of a temporary project directory, exactly as the {@code analyze} goal of the
 *       tentackle-maven-plugin would write them, and</li>
 *   <li>the backend is a real in-memory H2 whose schema is built from the DDL this module's build
 *       generates into {@code META-INF/sql/H2/createttmodel.sql} (see the pom).</li>
 * </ul>
 * The mojos open their own session from the {@code url}, {@code user} and {@code password}
 * parameters, so they connect to the same in-memory database as the session the tests prepare
 * their fixtures with. Since the mojos commit, the tables are wiped before each test method
 * instead of rolling a transaction back.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public abstract class AbstractI18nMojoTest {

  /**
   * The backend URL of the tests.<br>
   * {@code DB_CLOSE_DELAY=-1} keeps the database alive while no connection is open, which matters
   * because each mojo run opens and closes a session of its own.
   */
  protected static final String URL = "jdbc:h2:mem:i18n;DB_CLOSE_DELAY=-1";

  /** The backend user of the tests. */
  protected static final String USER = "tentackle";

  /** The backend password of the tests. */
  protected static final String PASSWORD = "tentackle";

  /**
   * The user ID of the test session.<br>
   * Given explicitly because a PDO refuses to be saved by user 0.
   */
  protected static final long USER_ID = 42;

  /** The package the test bundles live in. */
  protected static final String PACKAGE = "com.example.app";

  /** The bundle the tests use unless they register another one. */
  protected static final String APP_BUNDLE = PACKAGE + ".AppBundle";


  private Session session;              // the session the fixtures are created with
  private DomainContext context;        // the domain context of that session

  private File basedir;                 // the temporary project directory
  private File resourceDir;             // the project's resource directory (src/main/resources)
  private File classesDir;              // the project's output directory (target/classes)
  private List<String> bundleNames;     // the bundles registered by an annotation
  private I18nTestSupport.RecordingLog log;   // the log of the mojo under test


  /**
   * Opens the session and creates the schema.
   */
  @BeforeClass(alwaysRun = true)
  public void openSession() {
    try {
      session = createSession();
    }
    catch (PersistenceException ex) {
      throw new SkipException("no backend found -> no tests", ex);
    }
    Db db = (Db) session;
    Backend backend = db.getBackend();
    if (!backend.isDatabaseInMemory(db.getUrl())) {
      throw new SkipException("these tests own their schema and run in-memory only");
    }
    // the generated script holds no DROP statements, so wipe whatever a previous run left over
    db.runScript("drop", "DROP ALL OBJECTS;");
    db.runScript("model", loadScript("/META-INF/sql/" + backend.getName() + "/createttmodel.sql"));
    session.makeCurrent();
    ModificationTracker.getInstance().setSession(session);
    context = Pdo.createDomainContext(session);
  }

  /**
   * Closes the session.
   */
  @AfterClass(alwaysRun = true)
  public void closeSession() {
    if (session != null) {
      session.close();
      session = null;
      context = null;
    }
  }

  /**
   * Empties the tables and creates the temporary project.
   */
  @BeforeMethod(alwaysRun = true)
  public void setUpProject() {
    ((Db) session).runScript("clean", "DELETE FROM bundlekey; DELETE FROM bundle;");
    basedir = I18nTestSupport.createTempDir("i18n-mojo");
    resourceDir = I18nTestSupport.createDir(basedir, "src/main/resources");
    classesDir = I18nTestSupport.createDir(basedir, "target/classes");
    bundleNames = new ArrayList<>();
    log = new I18nTestSupport.RecordingLog();
  }

  /**
   * Removes the temporary project.
   */
  @AfterMethod(alwaysRun = true)
  public void tearDownProject() {
    I18nTestSupport.deleteRecursively(basedir);
    basedir = null;
  }


  /**
   * Gets the temporary project's resource directory.<br>
   * This is where the {@code pull} goal writes the property files to.
   *
   * @return the directory
   */
  protected File getResourceDir() {
    return resourceDir;
  }

  /**
   * Gets the log of the mojo under test.
   *
   * @return the log
   */
  protected I18nTestSupport.RecordingLog getLog() {
    return log;
  }


  /**
   * Configures a mojo for the temporary project.
   *
   * @param mojo the mojo
   * @param <T> the mojo type
   * @return the mojo
   */
  protected <T extends AbstractI18nMojo> T configure(T mojo) {
    mojo.setLogger(log);
    MavenProject project = I18nTestSupport.createProject(basedir, resourceDir);
    project.getBuild().setOutputDirectory(classesDir.getPath());
    I18nTestSupport.setField(mojo, "mavenProject", project);
    I18nTestSupport.setField(mojo, "url", URL);
    I18nTestSupport.setField(mojo, "user", USER);
    I18nTestSupport.setField(mojo, "password", PASSWORD);
    I18nTestSupport.setField(mojo, "charset", "UTF-8");
    return mojo;
  }

  /**
   * Runs a mojo the way {@code execute()} would, minus the global logging handler.
   * <p>
   * The mojo runs with a session of its own, which it makes the current one and hands to the
   * modification tracker before closing it again. Both are restored afterwards, so that the
   * fixtures of the next test are created with an open session.
   *
   * @param mojo the mojo
   * @throws Exception if the goal failed
   */
  protected void run(AbstractI18nMojo mojo) throws Exception {
    try {
      mojo.prepareExecute();
      assertTrue(I18nTestSupport.callValidate(mojo));
      mojo.executeImpl();
      mojo.finishExecute();
    }
    finally {
      session.makeCurrent();
      ModificationTracker.getInstance().setSession(session);
    }
  }


  /**
   * Registers a bundle the way the analyze goal would and writes its property file.
   *
   * @param bundleName the fully qualified bundle name
   * @param lines the properties
   */
  protected void bundle(String bundleName, String... lines) {
    if (!bundleNames.contains(bundleName)) {
      bundleNames.add(bundleName);
    }
    I18nTestSupport.writeFile(classesDir, Constants.DEFAULT_SERVICE_PATH + BundleSupport.BUNDLE_SERVICE,
                              "org.tentackle.common.Bundle\n");
    I18nTestSupport.writeFile(classesDir, Constants.BUNDLE_SERVICE_PATH + "org.tentackle.common.Bundle",
                              String.join("\n", bundleNames) + "\n");
    properties(bundleName.replace('.', '/') + Constants.PROPS_EXTENSION, lines);
  }

  /**
   * Writes a property file to the output directory, i.e. to the classpath the mojos read.
   *
   * @param path the path relative to the output directory
   * @param lines the properties
   */
  protected void properties(String path, String... lines) {
    I18nTestSupport.writeFile(classesDir, path, String.join("\n", lines) + "\n");
  }

  /**
   * Writes a property file to the resource directory, i.e. to the sources the {@code pull} goal
   * updates.
   *
   * @param path the path relative to the resource directory
   * @param lines the properties
   * @return the file
   */
  protected File resource(String path, String... lines) {
    return I18nTestSupport.writeFile(resourceDir, path, String.join("\n", lines) + "\n");
  }

  /**
   * Creates a property file's path from a bundle name.
   *
   * @param bundleName the fully qualified bundle name
   * @param locale the locale suffix, null if the default locale
   * @return the path relative to a classpath root
   */
  protected String resourceName(String bundleName, String locale) {
    return (locale == null ? bundleName : bundleName + "_" + locale).replace('.', '/') +
           Constants.PROPS_EXTENSION;
  }


  /**
   * Stores a bundle in the backend.
   *
   * @param bundleName the fully qualified bundle name
   * @param locale the locale, null if the default one
   * @param translations the keys and values, alternating
   * @return the stored bundle
   */
  protected StoredBundle storeBundle(String bundleName, String locale, String... translations) {
    long voucher = session.begin("fixture");
    StoredBundle bundle = Pdo.create(StoredBundle.class, context);
    bundle.setName(bundleName);
    bundle.setLocale(locale);
    for (int i = 0; i < translations.length; i += 2) {
      bundle.setTranslation(translations[i], translations[i + 1]);
    }
    bundle.save();
    session.commit(voucher);
    return bundle;
  }

  /**
   * Runs an SQL script against the backend.
   *
   * @param sql the SQL statements
   */
  protected void runScript(String sql) {
    ((Db) session).runScript("test", sql);
  }

  /**
   * Loads a bundle from the backend.
   *
   * @param bundleName the fully qualified bundle name
   * @param locale the locale, null if the default one
   * @return the stored bundle, null if there is none
   */
  protected StoredBundle loadBundle(String bundleName, String locale) {
    return Pdo.create(StoredBundle.class, context).findByNameAndLocale(bundleName, locale);
  }

  /**
   * Loads all bundles from the backend.
   *
   * @return the stored bundles
   */
  protected List<StoredBundle> loadBundles() {
    return Pdo.create(StoredBundle.class, context).selectAll();
  }


  private Session createSession() {
    EncryptedProperties props = PropertiesUtilities.getInstance().create();
    props.setProperty(Constants.BACKEND_URL, URL);
    props.setProperty(Constants.BACKEND_USER, USER);
    props.setProperty(Constants.BACKEND_PASSWORD, PASSWORD);
    SessionInfo sessionInfo = Pdo.createSessionInfo(props);
    sessionInfo.setUserId(USER_ID);
    return Pdo.createSession(sessionInfo);
  }

  private String loadScript(String resource) {
    try (InputStream is = getClass().getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException("no such resource");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    catch (IOException ix) {
      throw new PersistenceException("cannot load the DDL from " + resource, ix);
    }
  }

}
