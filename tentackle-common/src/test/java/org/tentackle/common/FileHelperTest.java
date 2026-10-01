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

}
