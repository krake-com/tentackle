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

package org.tentackle.maven.plugin.wizard;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests loading and saving a script fragment.
 */
public class ScriptFragmentFileTest {

  private static final String FRAGMENT = "^import org.tentackle.misc.*\nprintln 'grüß gott'\n";

  private Path directory;
  private Preferences preferences;

  @BeforeMethod
  public void setUp() throws IOException {
    directory = Files.createTempDirectory("script-fragment-test");
    preferences = Preferences.userNodeForPackage(ScriptFragmentFileTest.class).node("test");
  }

  @AfterMethod
  public void tearDown() throws IOException, BackingStoreException {
    preferences.removeNode();
    try (var files = Files.walk(directory)) {
      files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
  }

  @Test
  public void testSaveAndLoad() throws IOException {
    File file = new File(directory.toFile(), "fragment.groovy");
    ScriptFragmentFile fragmentFile = new ScriptFragmentFile(preferences, directory.toFile());
    assertNull(fragmentFile.getFile());

    fragmentFile.save(file, FRAGMENT);
    assertEquals(fragmentFile.getFile(), file);
    assertEquals(Files.readString(file.toPath(), StandardCharsets.UTF_8), FRAGMENT);

    // a fresh instance picks up the file used last
    ScriptFragmentFile nextSession = new ScriptFragmentFile(preferences, directory.toFile());
    File lastFile = nextSession.getLastFile();
    assertEquals(lastFile, file.getAbsoluteFile());
    assertEquals(nextSession.load(lastFile), FRAGMENT);
    assertEquals(nextSession.getFile(), lastFile);
  }

  @Test
  public void testVanishedLastFile() throws IOException {
    File file = new File(directory.toFile(), "fragment.groovy");
    ScriptFragmentFile fragmentFile = new ScriptFragmentFile(preferences, directory.toFile());
    fragmentFile.save(file, FRAGMENT);
    Files.delete(file.toPath());
    assertNull(new ScriptFragmentFile(preferences, directory.toFile()).getLastFile());
  }

  @Test
  public void testDefaultDirectory() throws IOException {
    ScriptFragmentFile fragmentFile = new ScriptFragmentFile(preferences, directory.toFile());
    assertEquals(fragmentFile.getDefaultDirectory(), directory.toFile());

    // once a file is known, its directory wins
    Path subDirectory = Files.createDirectory(directory.resolve("scripts"));
    fragmentFile.save(new File(subDirectory.toFile(), "fragment.groovy"), FRAGMENT);
    assertEquals(fragmentFile.getDefaultDirectory(), subDirectory.toFile());
  }

  @Test
  public void testNoDefaultDirectory() {
    assertNull(new ScriptFragmentFile(preferences, null).getDefaultDirectory());
  }

}
