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

package org.tentackle.maven;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link PackageInfo#isContainingFiles()}, which decides whether a package is a real
 * one or just a leftover directory - and thus whether two occurrences of the same package name
 * are a split package (illegal under JPMS) or something to be tolerated.
 */
public class PackageInfoTest {

  private Path baseDir;

  @BeforeMethod
  public void setUp() throws IOException {
    baseDir = Files.createTempDirectory("packageinfo");
  }

  @AfterMethod
  public void tearDown() throws IOException {
    try (Stream<Path> paths = Files.walk(baseDir)) {
      paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
  }


  @Test
  public void detectsAPackageWithSources() throws Exception {
    assertTrue(packageInfo(dir("com/example", "Foo.java", "package-info.java")).isContainingFiles());
  }

  @Test
  public void detectsAnEmptyPackage() throws Exception {
    assertFalse(packageInfo(dir("com/example")).isContainingFiles());
  }

  /**
   * A directory holding nothing but sub-packages is not a package of its own.
   */
  @Test
  public void doesNotCountSourcesOfSubPackages() throws Exception {
    File dir = dir("com/example");
    dir("com/example/sub", "Foo.java");

    assertFalse(packageInfo(dir).isContainingFiles());
  }

  /**
   * Hidden files don't make a package, exactly as when the package was collected - otherwise a
   * leftover directory carrying a {@code .gitignore} would look like a split package.
   */
  @Test
  public void ignoresHiddenFiles() throws Exception {
    assertFalse(packageInfo(dir("com/example", ".gitignore")).isContainingFiles());
  }

  @Test
  public void copesWithAMissingDirectory() {
    assertFalse(new PackageInfo("com.example", null, new File(baseDir.toFile(), "nope")).isContainingFiles());
  }


  private PackageInfo packageInfo(File dir) {
    return new PackageInfo("com.example", null, dir);
  }

  /**
   * Creates a package directory holding the given files.
   */
  private File dir(String packagePath, String... fileNames) throws IOException {
    Path dir = Files.createDirectories(baseDir.resolve(packagePath));
    for (String fileName : fileNames) {
      Files.writeString(dir.resolve(fileName), "// " + fileName);
    }
    return dir.toFile();
  }

}
