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

package org.tentackle.maven.plugin;

import org.eclipse.aether.artifact.Artifact;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.Settings;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.tentackle.maven.plugin.MojoTestSupport.RecordingLog;
import static org.tentackle.maven.plugin.MojoTestSupport.createArtifact;
import static org.tentackle.maven.plugin.MojoTestSupport.createProject;
import static org.tentackle.maven.plugin.MojoTestSupport.createTempDir;
import static org.tentackle.maven.plugin.MojoTestSupport.deleteRecursively;
import static org.tentackle.maven.plugin.MojoTestSupport.readString;
import static org.tentackle.maven.plugin.MojoTestSupport.setField;

/**
 * Tests for {@link AbstractVersionInfoMojo} and {@link VersionInfoMojo}.
 * <p>
 * Collecting the artifacts needs a resolved reactor and is therefore left to the integration
 * build. What is covered here is everything the two {@code versions} goals do with the artifacts
 * once they have them: turning them into property names, folding artifacts of the same groupId
 * into a shared version property, and rendering the result as XML or a properties file.
 * <p>
 * {@code generateVersionProperties} is exercised through a minimal concrete subclass, because
 * both real subclasses differ only in how they collect their artifacts.
 *
 * @see MojoTestSupport
 */
@SuppressWarnings("missing-explicit-ctor")
public class VersionInfoMojoTest {

  private File basedir;
  private TestVersionInfoMojo mojo;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = createTempDir("version-info-mojo");
    log = new RecordingLog();
    mojo = new TestVersionInfoMojo();
    mojo.setLogger(log);
    setField(mojo, "mavenProject", createProject(basedir));
  }

  @AfterMethod
  public void tearDown() {
    deleteRecursively(basedir);
  }


  /**
   * The property names are the camelized groupId and artifactId, so that they can be
   * pasted into the properties section of a pom.
   */
  @Test
  public void createsACamelCasePropertyPerArtifact() throws Exception {
    generate(createArtifact("org.tentackle", "tentackle-maven-plugin", "25.0"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgTentackle.tentackleMavenPlugin>25.0</version.orgTentackle.tentackleMavenPlugin>""");
  }

  /**
   * Underscores separate name parts just like dashes and dots do.
   */
  @Test
  public void camelizesAllSeparators() throws Exception {
    generate(createArtifact("com.acme_corp", "some_cool-lib.core", "1.0"));

    assertTrue(loggedXml().contains("<version.comAcmeCorp.someCoolLibCore>1.0</version.comAcmeCorp.someCoolLibCore>"),
               loggedXml());
  }

  /**
   * The properties are sorted by name, no matter in which order the artifacts were collected.
   */
  @Test
  public void sortsThePropertiesByName() throws Exception {
    generate(createArtifact("org.tentackle", "tentackle-pdo", "25.0"),
             createArtifact("org.slf4j", "slf4j-api", "2.0.18"),
             createArtifact("org.tentackle", "tentackle-core", "25.0"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgSlf4j.slf4jApi>2.0.18</version.orgSlf4j.slf4jApi>
                 <version.orgTentackle.tentackleCore>25.0</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentacklePdo>25.0</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * The XML is written to the output file as well, if configured.
   */
  @Test
  public void writesTheXmlToTheOutputFile() throws Exception {
    File outputFile = new File(basedir, "versions.xml");
    setField(mojo, "outputFile", outputFile);

    generate(createArtifact("org.slf4j", "slf4j-api", "2.0.18"));

    assertEquals(readString(outputFile),
                 """
                 <!-- test -->
                 <version.orgSlf4j.slf4jApi>2.0.18</version.orgSlf4j.slf4jApi>""");
  }

  /**
   * An output file ending in {@code .properties} switches the format.
   */
  @Test
  public void writesAPropertiesFile() throws Exception {
    File outputFile = new File(basedir, "versions.properties");
    setField(mojo, "outputFile", outputFile);

    generate(createArtifact("org.slf4j", "slf4j-api", "2.0.18"),
             createArtifact("org.tentackle", "tentackle-core", "25.0"));

    Properties properties = loadProperties(outputFile);
    assertEquals(properties.size(), 2);
    assertEquals(properties.getProperty("version.orgSlf4j.slf4jApi"), "2.0.18");
    assertEquals(properties.getProperty("version.orgTentackle.tentackleCore"), "25.0");
  }

  /**
   * Properties files hold plain values: a property referencing another one would have to be
   * resolved by whoever reads the file.
   */
  @Test
  public void propertiesFilesDontUseGroupVersions() throws Exception {
    File outputFile = new File(basedir, "versions.properties");
    setField(mojo, "outputFile", outputFile);
    setField(mojo, "groupVersions", 2);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"));

    Properties properties = loadProperties(outputFile);
    assertEquals(properties.size(), 2);
    assertEquals(properties.getProperty("version.orgTentackle.tentackleCore"), "25.0");
    assertNull(properties.getProperty("version.orgTentackle"));

    // ... while the logged XML does use them
    assertTrue(loggedXml().contains("<version.orgTentackle>25.0</version.orgTentackle>"), loggedXml());
  }

  /**
   * Artifacts of the same groupId sharing a version are folded into a group property,
   * as soon as there are at least {@code groupVersions} of them.
   */
  @Test
  public void foldsArtifactsOfTheSameGroupIntoAGroupProperty() throws Exception {
    setField(mojo, "groupVersions", 2);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"),
             createArtifact("org.slf4j", "slf4j-api", "2.0.18"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgSlf4j.slf4jApi>2.0.18</version.orgSlf4j.slf4jApi>
                 <version.orgTentackle>25.0</version.orgTentackle>
                 <version.orgTentackle.tentackleCore>${version.orgTentackle}</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentacklePdo>${version.orgTentackle}</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * Groups below the threshold are left alone.
   */
  @Test
  public void ignoresGroupsBelowTheThreshold() throws Exception {
    setField(mojo, "groupVersions", 3);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgTentackle.tentackleCore>25.0</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentacklePdo>25.0</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * Only the most popular version of a groupId becomes the group version. Artifacts pinned to
   * another version keep their own property.
   */
  @Test
  public void keepsTheVersionOfOutdatedArtifacts() throws Exception {
    setField(mojo, "groupVersions", 2);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"),
             createArtifact("org.tentackle", "tentackle-legacy", "24.1"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgTentackle>25.0</version.orgTentackle>
                 <version.orgTentackle.tentackleCore>${version.orgTentackle}</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentackleLegacy>24.1</version.orgTentackle.tentackleLegacy>
                 <version.orgTentackle.tentacklePdo>${version.orgTentackle}</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * A negative {@code groupVersions} bounds the number of artifacts allowed to differ instead of
   * bounding the number of artifacts that have to agree: with {@code -1} a single outlier is
   * still accepted.
   */
  @Test
  public void negativeGroupVersionsBoundTheNumberOfOutliers() throws Exception {
    setField(mojo, "groupVersions", -1);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"),
             createArtifact("org.tentackle", "tentackle-legacy", "24.1"));

    assertTrue(loggedXml().contains("<version.orgTentackle>25.0</version.orgTentackle>"), loggedXml());
  }

  /**
   * With one more outlier the group is dropped.
   */
  @Test
  public void negativeGroupVersionsDropTooDiverseGroups() throws Exception {
    setField(mojo, "groupVersions", -1);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "25.0"),
             createArtifact("org.tentackle", "tentackle-legacy", "24.1"),
             createArtifact("org.tentackle", "tentackle-ancient", "23.4"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgTentackle.tentackleAncient>23.4</version.orgTentackle.tentackleAncient>
                 <version.orgTentackle.tentackleCore>25.0</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentackleLegacy>24.1</version.orgTentackle.tentackleLegacy>
                 <version.orgTentackle.tentacklePdo>25.0</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * A groupId whose artifacts all use different versions has nothing to fold.
   */
  @Test
  public void doesNotFoldSingleArtifactGroups() throws Exception {
    setField(mojo, "groupVersions", -1);

    generate(createArtifact("org.tentackle", "tentackle-core", "25.0"),
             createArtifact("org.tentackle", "tentackle-pdo", "24.0"));

    assertEquals(loggedXml(),
                 """
                 <!-- test -->
                 <version.orgTentackle.tentackleCore>25.0</version.orgTentackle.tentackleCore>
                 <version.orgTentackle.tentacklePdo>24.0</version.orgTentackle.tentacklePdo>""");
  }

  /**
   * A project without 3rd-party dependencies is worth a warning: most likely the reactor
   * has not been built yet.
   */
  @Test
  public void warnsIfThereAreNoArtifacts() throws Exception {
    mojo.generateVersionProperties("test", Set.of());

    assertTrue(RecordingLog.contains(log.getWarnMessages(), "no artifacts found"), log.getWarnMessages().toString());
    assertTrue(log.getInfoMessages().isEmpty(), log.getInfoMessages().toString());
  }

  /**
   * The scope is matched against the artifact scopes, which maven keeps in lower case.
   */
  @Test
  public void validateNormalizesTheScope() throws Exception {
    VersionInfoMojo versionInfoMojo = new VersionInfoMojo();
    versionInfoMojo.setLogger(log);
    setField(versionInfoMojo, "mavenProject", createProject(basedir));
    setField(versionInfoMojo, "scope", "Compile");

    assertTrue(versionInfoMojo.validate());
    assertEquals(MojoTestSupport.<String>getField(versionInfoMojo, "scope"), "compile");
  }

  /**
   * A blank scope means "all scopes", same as no scope at all.
   */
  @Test
  public void validateTreatsABlankScopeAsNoScope() throws Exception {
    VersionInfoMojo versionInfoMojo = new VersionInfoMojo();
    versionInfoMojo.setLogger(log);
    setField(versionInfoMojo, "mavenProject", createProject(basedir));
    setField(versionInfoMojo, "scope", "  ");

    assertTrue(versionInfoMojo.validate());
    assertNull(MojoTestSupport.<String>getField(versionInfoMojo, "scope"));
  }


  private void generate(Artifact... artifacts) throws Exception {
    // a linked set to prove that the output does not depend on the iteration order
    mojo.generateVersionProperties("test", new LinkedHashSet<>(Set.of(artifacts)));
  }

  /**
   * Gets the XML the mojo logged as its result.
   *
   * @return the XML without the leading newline
   */
  private String loggedXml() {
    assertEquals(log.getInfoMessages().size(), 1, log.getInfoMessages().toString());
    String message = log.getInfoMessages().get(0);
    assertTrue(message.startsWith("\n"), message);
    return message.substring(1);
  }

  private Properties loadProperties(File file) throws Exception {
    Properties properties = new Properties();
    try (FileReader reader = new FileReader(file, Settings.getEncodingCharset())) {
      properties.load(reader);
    }
    return properties;
  }


  /** The artifacts are passed in directly, so nothing is left to execute. */
  private static class TestVersionInfoMojo extends AbstractVersionInfoMojo {

    @Override
    public void executeImpl() {
    }
  }

}
