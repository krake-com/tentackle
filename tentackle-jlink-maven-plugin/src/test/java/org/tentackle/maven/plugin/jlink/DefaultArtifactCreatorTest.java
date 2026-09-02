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

package org.tentackle.maven.plugin.jlink;

import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingLog;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingProjectHelper;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.RecordingProjectHelper.Attachment;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJLinkMojo;
import org.tentackle.maven.plugin.jlink.JLinkTestSupport.TestJPackageMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the deployable artifacts the plugin creates and attaches to the project.
 * <p>
 * The classifier and the file names are what downstream projects and the auto-updater resolve
 * later on, so they are part of the plugin's contract.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DefaultArtifactCreatorTest {

  private File tempDir;
  private File targetDir;
  private RecordingProjectHelper projectHelper;
  private DefaultArtifactCreator creator;


  @BeforeMethod
  public void setUp() {
    tempDir = JLinkTestSupport.createTempDir("jlinkartifact");
    targetDir = JLinkTestSupport.createDir(tempDir, "target");
    projectHelper = new RecordingProjectHelper();
    creator = new DefaultArtifactCreator();
  }

  @AfterMethod
  public void tearDown() {
    JLinkTestSupport.deleteRecursively(tempDir);
  }


  /**
   * The jlink goal attaches the zipped image under the platform classifier.
   */
  @Test
  public void theJlinkImageIsZippedAndAttached() throws Exception {
    TestJLinkMojo mojo = jlinkMojo();
    File imageDir = JLinkTestSupport.createDir(targetDir, "jlink");
    JLinkTestSupport.writeFile(imageDir, "bin/run.sh", "#!/bin/sh\n");
    JLinkTestSupport.writeFile(imageDir, "conf/backend.properties", "url=jdbc:h2:mem:test");

    creator.createAndAttachArtifact(mojo);

    assertEquals(projectHelper.getAttachments().size(), 1);
    Attachment attachment = projectHelper.getAttachments().getFirst();
    assertEquals(attachment.type(), AbstractJLinkMojo.ZIP_EXTENSION);
    assertEquals(attachment.classifier(), mojo.getClassifier());
    assertEquals(attachment.file(), new File(targetDir, "app-1.2.3-SNAPSHOT-jlink.zip"));
    assertEquals(entries(attachment.file()), List.of("bin/run.sh", "conf/backend.properties"));
  }

  /**
   * The jpackage goal's update artifact is marked by an extra {@code -pkg} classifier, because the
   * jpackage image differs from the jlink image.
   */
  @Test
  public void theUpdateArchiveIsAttachedWithThePackageClassifier() throws Exception {
    TestJPackageMojo mojo = jpackageMojo();
    File appImageDir = JLinkTestSupport.createDir(targetDir, "jpackage");
    JLinkTestSupport.writeFile(appImageDir, "bin/update.sh", "#!/bin/sh\n");

    creator.createAndAttachArtifact(mojo, appImageDir);

    Attachment attachment = projectHelper.getAttachments().getFirst();
    assertEquals(attachment.type(), AbstractJLinkMojo.ZIP_EXTENSION);
    assertEquals(attachment.classifier(), mojo.getClassifier() + "-pkg");
    assertEquals(attachment.file(), new File(targetDir, "app-1.2.3-SNAPSHOT-jpackage.zip"));
  }

  /**
   * Every installer the jpackage tool has created is attached under its own extension.
   */
  @Test
  public void everyInstallerIsAttachedUnderItsExtension() throws Exception {
    TestJPackageMojo mojo = jpackageMojo();
    long startTime = System.currentTimeMillis();
    JLinkTestSupport.writeFile(targetDir, "app-1.2.3.deb", "deb");
    JLinkTestSupport.writeFile(targetDir, "app-1.2.3.dmg", "dmg");
    JLinkTestSupport.writeFile(targetDir, "app-1.2.3.EXE", "exe");
    touch(targetDir, startTime + 5000, "app-1.2.3.deb", "app-1.2.3.dmg", "app-1.2.3.EXE");

    creator.attachInstallers(mojo, startTime);

    List<String> attached = new ArrayList<>();
    for (Attachment attachment : projectHelper.getAttachments()) {
      assertEquals(attachment.classifier(), mojo.getClassifier());
      attached.add(attachment.type() + ":" + attachment.file().getName());
    }
    attached.sort(null);

    // the extension is matched case-insensitively - the file keeps its name, but the artifact type
    // is the lowercase one the artifact handlers are registered for
    assertEquals(attached, List.of("deb:app-1.2.3.deb", "dmg:app-1.2.3.dmg", "exe:app-1.2.3.EXE"));
  }

  /**
   * Only the files created by this very run are attached: the target directory may still hold the
   * installers of a previous build.
   */
  @Test
  public void staleInstallersAreNotAttached() throws Exception {
    TestJPackageMojo mojo = jpackageMojo();
    long startTime = System.currentTimeMillis();
    JLinkTestSupport.writeFile(targetDir, "stale-1.0.deb", "deb");
    touch(targetDir, startTime - 60_000, "stale-1.0.deb");
    JLinkTestSupport.writeFile(targetDir, "fresh-1.0.deb", "deb");
    touch(targetDir, startTime + 5000, "fresh-1.0.deb");

    creator.attachInstallers(mojo, startTime);

    assertEquals(projectHelper.getAttachments().size(), 1);
    assertEquals(projectHelper.getAttachments().getFirst().file().getName(), "fresh-1.0.deb");
  }

  /**
   * Everything else in the target directory is left alone.
   */
  @Test
  public void nonInstallerFilesAreIgnored() throws Exception {
    TestJPackageMojo mojo = jpackageMojo();
    long startTime = System.currentTimeMillis();
    JLinkTestSupport.writeFile(targetDir, "app-1.2.3.jar", "jar");
    JLinkTestSupport.writeFile(targetDir, "jpackage-image.options", "");
    touch(targetDir, startTime + 5000, "app-1.2.3.jar", "jpackage-image.options");

    creator.attachInstallers(mojo, startTime);

    assertTrue(projectHelper.getAttachments().isEmpty(), "got " + projectHelper.getAttachments());
  }

  /**
   * The application image may be post-processed, but the default creator leaves it as it is.
   */
  @Test
  public void theDefaultCreatorDoesNotTouchTheApplicationImage() throws Exception {
    TestJPackageMojo mojo = jpackageMojo();
    File appImageDir = JLinkTestSupport.createDir(targetDir, "jpackage");
    JLinkTestSupport.writeFile(appImageDir, "bin/app", "");

    creator.processApplicationImage(mojo, appImageDir);

    assertEquals(JLinkTestSupport.fileNames(appImageDir), List.of("bin"));
    assertTrue(projectHelper.getAttachments().isEmpty());
  }

  /**
   * The creator is a service that an application specific plugin dependency may replace.
   */
  @Test
  public void theDefaultCreatorIsTheSingleton() {
    assertSame(ArtifactCreator.getInstance().getClass(), DefaultArtifactCreator.class);
    assertSame(ArtifactCreator.getInstance(), ArtifactCreator.getInstance());
  }


  // ---------------- fixture ----------------

  private TestJLinkMojo jlinkMojo() {
    TestJLinkMojo mojo = new TestJLinkMojo();
    prepare(mojo);
    JLinkTestSupport.setField(mojo, "imageDirectory", new File(targetDir, "jlink"));
    return mojo;
  }

  private TestJPackageMojo jpackageMojo() {
    TestJPackageMojo mojo = new TestJPackageMojo();
    prepare(mojo);
    JLinkTestSupport.setField(mojo, "imageDirectory", new File(targetDir, "jlink"));
    JLinkTestSupport.setField(mojo, "packageDirectory", new File(targetDir, "jpackage"));
    return mojo;
  }

  private void prepare(AbstractJLinkMojo mojo) {
    mojo.setLog(new RecordingLog());
    JLinkTestSupport.setField(mojo, "mavenProject", JLinkTestSupport.createProject(tempDir));
    JLinkTestSupport.setField(mojo, "mainClass", "com.example.Main");
    JLinkTestSupport.setField(mojo, "finalName", "app-1.2.3-SNAPSHOT");
    JLinkTestSupport.setField(mojo, "zipDirectory", targetDir);
    JLinkTestSupport.setField(mojo, "projectHelper", projectHelper);
    JLinkTestSupport.setField(mojo, "zipArchiver", new ZipArchiver());
  }

  /**
   * Sets the modification time of files.
   *
   * @param dir the directory
   * @param time the modification time
   * @param names the file names
   */
  private void touch(File dir, long time, String... names) {
    for (String name : names) {
      assertTrue(new File(dir, name).setLastModified(time), "cannot set the modification time of " + name);
    }
  }

  /**
   * Gets the sorted entry names of a zip file.
   *
   * @param zip the zip file
   * @return the names
   * @throws Exception if the file could not be read
   */
  private List<String> entries(File zip) throws Exception {
    try (ZipFile zipFile = new ZipFile(zip)) {
      List<String> names = new ArrayList<>(zipFile.stream().filter(entry -> !entry.isDirectory())
                                                  .map(entry -> entry.getName()).toList());
      names.sort(null);
      return names;
    }
  }

}
