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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.maven.plugin.MojoTestSupport.RecordingLog;
import static org.tentackle.maven.plugin.MojoTestSupport.createProject;
import static org.tentackle.maven.plugin.MojoTestSupport.createTempDir;
import static org.tentackle.maven.plugin.MojoTestSupport.deleteRecursively;
import static org.tentackle.maven.plugin.MojoTestSupport.readLines;
import static org.tentackle.maven.plugin.MojoTestSupport.setField;
import static org.tentackle.maven.plugin.MojoTestSupport.writeFile;

/**
 * Tests for the {@link BeanInfoMojo}.
 * <p>
 * The mojo turns the {@code *BeanInfo.java} files of a source tree into the {@code META-INF/MANIFEST.MF}
 * sections that mark the corresponding classes as java beans. It is driven through the real
 * filesystem, since the scanning of the source tree is exactly what is under test.
 *
 * @see MojoTestSupport
 */
@SuppressWarnings("missing-explicit-ctor")
public class BeanInfoMojoTest {

  /** The manifest header written before any bean section. */
  private static final int HEADER_LINES = 5;

  private File basedir;
  private File sourceDir;
  private File manifestDir;
  private BeanInfoMojo mojo;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = createTempDir("beaninfo-mojo");
    sourceDir = new File(basedir, "src" + File.separator + "main" + File.separator + "java");
    manifestDir = new File(basedir, "target" + File.separator + "manifest");
    log = new RecordingLog();
    mojo = new BeanInfoMojo();
    mojo.setLogger(log);
    setField(mojo, "mavenProject", createProject(basedir));
    setField(mojo, "sourceDir", sourceDir);
    setField(mojo, "manifestDirectory", manifestDir.getPath());
  }

  @AfterMethod
  public void tearDown() {
    deleteRecursively(basedir);
  }


  /**
   * Each bean info gets a manifest section naming the class it describes, not the bean info itself.
   */
  @Test
  public void createsAManifestSectionPerBeanInfo() throws Exception {
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");
    writeSource("com/example/Person.java");
    writeSource("com/example/PersonBeanInfo.java");

    execute();

    List<String> lines = readLines(manifestFile());
    assertEquals(lines.get(0), "Manifest-Version: 1.0");
    assertEquals(lines.get(1), "Archiver-Version: Plexus Archiver");
    assertEquals(lines.get(2), "Created-By: Apache Maven");
    assertEquals(lines.get(3), "Built-By: " + System.getProperty("user.name"));
    assertEquals(lines.get(4), "Build-Jdk: " + Runtime.version());

    // one blank line, one name and one Java-Bean line per bean
    assertEquals(lines.size(), HEADER_LINES + 2 * 3);
    assertTrue(lines.contains("Name: " + className("com", "example", "Address")), lines.toString());
    assertTrue(lines.contains("Name: " + className("com", "example", "Person")), lines.toString());
    assertEquals(lines.stream().filter("Java-Bean: True"::equals).count(), 2);
  }

  /**
   * Bean infos of abstract classes only carry settings shared by their subclasses,
   * so they must not show up as beans of their own.
   */
  @Test
  public void skipsAbstractBeans() throws Exception {
    writeSource("com/example/AbstractContact.java");
    writeSource("com/example/AbstractContactBeanInfo.java");
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");

    execute();

    List<String> lines = readLines(manifestFile());
    assertEquals(lines.size(), HEADER_LINES + 3);
    assertTrue(lines.contains("Name: " + className("com", "example", "Address")), lines.toString());
    assertFalse(lines.stream().anyMatch(line -> line.contains("AbstractContact")), lines.toString());
    assertEquals(log.getInfoMessages().size(), 1);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "2 files processed, 0 errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A bean info without the class it describes is a leftover of a renamed or deleted class
   * and fails the build.
   */
  @Test
  public void failsOnABeanInfoWithoutItsClass() throws Exception {
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");
    writeSource("com/example/GhostBeanInfo.java");

    MojoFailureException ex = expectThrows(MojoFailureException.class, this::execute);

    assertEquals(ex.getMessage(), "1 beaninfo errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(), "no matching java-file for"),
               log.getErrorMessages().toString());
    // the manifest is written up to the error, the valid bean is still in it
    assertTrue(readLines(manifestFile()).contains("Name: " + className("com", "example", "Address")));
  }

  /**
   * An explicitly configured fileset may pick up files that don't follow the naming convention.
   * They are reported instead of being silently ignored.
   */
  @Test
  public void reportsFilesNotFollowingTheNamingConvention() throws Exception {
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");

    FileSet fileSet = new FileSet();
    fileSet.setDirectory(sourceDir.getPath());
    fileSet.addInclude("**/*.java");    // matches Address.java as well
    mojo.filesets = List.of(fileSet);

    MojoFailureException ex = expectThrows(MojoFailureException.class, this::execute);

    assertEquals(ex.getMessage(), "1 beaninfo errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(), "does not end with BeanInfo.java"),
               log.getErrorMessages().toString());
  }

  /**
   * A fileset without a directory falls back to the source directory.
   */
  @Test
  public void filesetWithoutDirectoryFallsBackToTheSourceDir() throws Exception {
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");

    FileSet fileSet = new FileSet();
    fileSet.addInclude("**/*BeanInfo.java");
    mojo.filesets = List.of(fileSet);

    execute();

    assertEquals(fileSet.getDirectory(), sourceDir.getAbsolutePath());
    assertTrue(readLines(manifestFile()).contains("Name: " + className("com", "example", "Address")));
  }

  /**
   * Modules without any sources still get a manifest, since the jar plugin is configured
   * to pick it up unconditionally.
   */
  @Test
  public void writesTheHeaderOnlyForAMissingSourceDir() throws Exception {
    execute();

    assertEquals(readLines(manifestFile()).size(), HEADER_LINES);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "missing source directory"),
               log.getInfoMessages().toString());
  }

  /**
   * Same for a source directory that exists but is empty.
   */
  @Test
  public void writesTheHeaderOnlyForAnEmptySourceDir() throws Exception {
    assertTrue(sourceDir.mkdirs());

    execute();

    assertEquals(readLines(manifestFile()).size(), HEADER_LINES);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "empty source directory"),
               log.getInfoMessages().toString());
  }

  /**
   * The manifest directory is created on demand.
   */
  @Test
  public void createsTheManifestDirectory() throws Exception {
    assertFalse(manifestDir.exists());

    execute();

    assertTrue(manifestFile().isFile());
  }

  /**
   * Without a source directory the mojo cannot even guess what to process.
   */
  @Test
  public void validateRequiresASourceDir() {
    setField(mojo, "sourceDir", null);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertEquals(ex.getMessage(), "missing tentackle.sourceDir");
  }

  /**
   * With maven's global debug flag the processed directory is logged.
   */
  @Test
  public void logsTheProcessedDirectoryInDebugMode() throws Exception {
    log.setDebugEnabled(true);
    writeSource("com/example/Address.java");
    writeSource("com/example/AddressBeanInfo.java");

    execute();

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "processing files in"),
               log.getInfoMessages().toString());
  }


  /**
   * Runs the mojo the same way {@code AbstractTentackleMojo#execute} would, but without
   * touching the JVM's logging configuration.
   */
  private void execute() throws MojoExecutionException, MojoFailureException {
    assertTrue(mojo.validate());
    mojo.executeImpl();
  }

  private File manifestFile() {
    return new File(new File(manifestDir, "META-INF"), "MANIFEST.MF");
  }

  private void writeSource(String path) {
    writeFile(sourceDir, path, "// " + path + "\n");
  }

  /**
   * The section names are built from the scanner's relative paths, hence the platform's separator.
   */
  private String className(String... pathElements) {
    return String.join(File.separator, pathElements) + ".class";
  }

}
