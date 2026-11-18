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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.testng.Assert.assertEquals;
import java.io.IOException;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link FileHelper}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class FileHelperTest {

  @Test
  public void openStreamUncachedSurvivesClosedCachedJar() throws Exception {
    Path jar = Files.createTempFile("filehelper", ".jar");
    try {
      byte[] data = new byte[100_000];
      Arrays.fill(data, (byte) 'x');
      try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
        out.putNextEntry(new ZipEntry("test.txt"));
        out.write(data);
        out.closeEntry();
      }
      URL url = URI.create("jar:" + jar.toUri() + "!/test.txt").toURL();

      try (InputStream in = FileHelper.openStreamUncached(url)) {
        assertEquals(in.read(), 'x');
        // close the JVM-wide cached jar file while reading, as a disposed classloader of a concurrent build would do.
        // A stream opened via url.openStream() would fail now with "Stream closed".
        JarURLConnection cachedConnection = (JarURLConnection) url.openConnection();
        cachedConnection.getJarFile().close();
        assertEquals(in.readAllBytes().length, data.length - 1);
      }
    }
    finally {
      Files.deleteIfExists(jar);
    }
  }

  @Test
  public void unzipExtractsFilesAndDirectories() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-unzip");
    try {
      Path zip = tmp.resolve("test.zip");
      try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
        out.putNextEntry(new ZipEntry("empty/"));
        out.closeEntry();
        out.putNextEntry(new ZipEntry("sub/file.txt"));
        out.write("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.closeEntry();
      }
      Path dest = tmp.resolve("dest");
      java.util.List<java.io.File> consumed = new java.util.ArrayList<>();
      FileHelper.unzip(zip.toFile(), dest.toFile(), consumed::add);
      assertEquals(Files.readString(dest.resolve("sub/file.txt")), "hello");
      assertTrue(Files.isDirectory(dest.resolve("empty")));
      assertEquals(consumed.size(), 2);
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void unzipRestoresModificationTime() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-mtime");
    try {
      long time = 1_700_000_000_000L;   // even seconds, representable in DOS time as well
      Path zip = tmp.resolve("test.zip");
      try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
        ZipEntry entry = new ZipEntry("lib/some.jar");
        entry.setLastModifiedTime(java.nio.file.attribute.FileTime.fromMillis(time));   // adds the extended timestamp
        out.putNextEntry(entry);
        out.write('x');
        out.closeEntry();
      }
      Path dest = tmp.resolve("dest");
      FileHelper.unzip(zip.toFile(), dest.toFile(), null);
      assertEquals(Files.getLastModifiedTime(dest.resolve("lib/some.jar")).toMillis(), time);
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void unzipRejectsSiblingDirectoryWithSamePrefix() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-zipslip");
    try {
      Path zip = tmp.resolve("evil.zip");
      try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
        // resolves to <tmp>/dest2/evil.txt, which starts with the string <tmp>/dest
        out.putNextEntry(new ZipEntry("../dest2/evil.txt"));
        out.write('x');
        out.closeEntry();
      }
      Path dest = tmp.resolve("dest");
      java.util.List<java.io.File> consumed = new java.util.ArrayList<>();
      assertThrows(IOException.class, () -> FileHelper.unzip(zip.toFile(), dest.toFile(), consumed::add));
      assertFalse(Files.exists(tmp.resolve("dest2")));
      assertTrue(consumed.isEmpty());
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void unzipRejectsTooManyEntries() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-entries");
    try {
      Path zip = tmp.resolve("many.zip");
      try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
        for (int i = 0; i < 5; i++) {
          out.putNextEntry(new ZipEntry("file" + i + ".txt"));
          out.write('x');
          out.closeEntry();
        }
      }
      Path dest = tmp.resolve("dest");
      java.util.List<java.io.File> consumed = new java.util.ArrayList<>();
      assertThrows(IOException.class, () -> FileHelper.unzip(zip.toFile(), dest.toFile(), consumed::add, 1024, 3, 0));
      assertEquals(consumed.size(), 3);
      assertFalse(Files.exists(dest.resolve("file3.txt")));
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void unzipRejectsTooLargeContent() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-bomb");
    try {
      Path zip = createZeroZip(tmp, 1024 * 1024);
      Path dest = tmp.resolve("dest");
      assertThrows(IOException.class, () -> FileHelper.unzip(zip.toFile(), dest.toFile(), null, 4096, 0, 100_000));
      assertTrue(Files.size(dest.resolve("zeros.bin")) <= 100_000);
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void unzipLimitsDisabled() throws Exception {
    Path tmp = Files.createTempDirectory("filehelper-nolimits");
    try {
      Path zip = createZeroZip(tmp, 1024 * 1024);
      Path dest = tmp.resolve("dest");
      FileHelper.unzip(zip.toFile(), dest.toFile(), null, 4096, 0, 0);
      assertEquals(Files.size(dest.resolve("zeros.bin")), 1024 * 1024);
    }
    finally {
      deleteRecursively(tmp);
    }
  }

  @Test
  public void defaultMaxZipSize() {
    assertEquals(FileHelper.defaultMaxZipSize(0), FileHelper.MIN_MAX_ZIP_SIZE);
    assertEquals(FileHelper.defaultMaxZipSize(1000), FileHelper.MIN_MAX_ZIP_SIZE);
    long large = 10L * 1024 * 1024 * 1024;
    assertEquals(FileHelper.defaultMaxZipSize(large), large * FileHelper.DEFAULT_MAX_ZIP_RATIO);
    assertEquals(FileHelper.defaultMaxZipSize(Long.MAX_VALUE / 2), Long.MAX_VALUE);
  }

  @Test
  public void downloadWithinLimit() throws Exception {
    Path file = Files.createTempFile("filehelper-download", ".bin");
    try {
      Files.write(file, new byte[1000]);
      URL url = file.toUri().toURL();
      assertEquals(FileHelper.download(url).length, 1000);
      assertEquals(FileHelper.download(url, 100, 1000).length, 1000);
      assertEquals(FileHelper.download(url, 100, 0).length, 1000);
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void downloadRejectsAnnouncedContentLength() throws Exception {
    Path file = Files.createTempFile("filehelper-download", ".bin");
    try {
      Files.write(file, new byte[1000]);
      URL url = file.toUri().toURL();
      assertThrows(IOException.class, () -> FileHelper.download(url, 100, 999));
    }
    finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void downloadRejectsUnannouncedExcess() throws Exception {
    // a connection without content length: only counting the bytes read catches the excess
    java.util.concurrent.atomic.AtomicInteger bytesServed = new java.util.concurrent.atomic.AtomicInteger();
    java.net.URLStreamHandler handler = new java.net.URLStreamHandler() {
      @Override
      protected java.net.URLConnection openConnection(URL u) {
        return new java.net.URLConnection(u) {
          @Override
          public void connect() {
          }

          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public InputStream getInputStream() {
            return new InputStream() {    // endless stream of zeros
              @Override
              public int read() {
                bytesServed.incrementAndGet();
                return 0;
              }
            };
          }
        };
      }
    };
    URL url = URL.of(URI.create("test:/endless"), handler);
    assertThrows(IOException.class, () -> FileHelper.download(url, 100, 10_000));
    assertTrue(bytesServed.get() < 20_000);
  }

  private static Path createZeroZip(Path dir, int size) throws IOException {
    Path zip = dir.resolve("zeros.zip");
    try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("zeros.bin"));
      out.write(new byte[size]);
      out.closeEntry();
    }
    return zip;
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (var stream = Files.walk(dir)) {
      for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }

}
