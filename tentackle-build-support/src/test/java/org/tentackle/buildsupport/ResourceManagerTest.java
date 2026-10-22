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

package org.tentackle.buildsupport;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link ResourceManager}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ResourceManagerTest {

  private Path tempDir;

  @AfterMethod
  public void cleanup() throws IOException {
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
      }
      tempDir = null;
    }
  }

  @Test
  public void writesAndReadsBackAResource() throws IOException {
    tempDir = Files.createTempDirectory("resource-manager-test");
    ResourceManager manager = new ResourceManager(tempDir.toFile());

    try (PrintWriter writer = manager.getWriter("some.txt")) {
      writer.print("hello");
    }
    manager.close();

    assertTrue(manager.exists("some.txt"));

    try (BufferedReader reader = new BufferedReader(manager.getReader("some.txt"))) {
      assertEquals(reader.readLine(), "hello");
    }
    manager.close();
  }

  @Test
  public void createsNestedDirectoriesForSlashSeparatedNames() throws IOException {
    tempDir = Files.createTempDirectory("resource-manager-test-nested");
    ResourceManager manager = new ResourceManager(tempDir.toFile());

    try (PrintWriter writer = manager.getWriter("a/b/c.txt")) {
      writer.print("nested");
    }
    manager.close();

    File expected = new File(new File(new File(tempDir.toFile(), "a"), "b"), "c.txt");
    assertTrue(expected.exists());
    assertEquals(Files.readString(expected.toPath()), "nested");
  }

  @Test
  public void exposesLocationUnchanged() {
    File location = new File("some/location");
    ResourceManager manager = new ResourceManager(location);
    assertSame(manager.getLocation(), location);
  }

  @Test
  public void nonExistentResourceReportsAsAbsent() throws IOException {
    tempDir = Files.createTempDirectory("resource-manager-test-missing");
    ResourceManager manager = new ResourceManager(tempDir.toFile());
    assertFalse(manager.exists("nope.txt"));
  }

  @Test
  public void tracksResourceNamesOfResourcesTouched() throws IOException {
    tempDir = Files.createTempDirectory("resource-manager-test-names");
    ResourceManager manager = new ResourceManager(tempDir.toFile());

    manager.exists("first.txt");
    manager.getWriter("second.txt").close();

    Set<String> names = manager.getResourceNames();
    assertEquals(names.size(), 2);
    assertTrue(names.contains("first.txt"));
    assertTrue(names.contains("second.txt"));
  }

  @Test
  public void reusesTheSameResourceForRepeatedNames() throws IOException {
    tempDir = Files.createTempDirectory("resource-manager-test-reuse");
    ResourceManager manager = new ResourceManager(tempDir.toFile());

    PrintWriter first = manager.getWriter("same.txt");
    PrintWriter second = manager.getWriter("same.txt");

    assertSame(first, second);
    manager.close();
  }

}
