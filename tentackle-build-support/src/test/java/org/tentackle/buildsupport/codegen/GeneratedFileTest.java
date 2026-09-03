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

package org.tentackle.buildsupport.codegen;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link GeneratedFile}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class GeneratedFileTest {

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
  public void generatesFileFromTemplateAndModel() throws IOException, TemplateException {
    tempDir = Files.createTempDirectory("generated-file-test");

    Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
    cfg.setDefaultEncoding("UTF-8");
    StringTemplateLoader loader = new StringTemplateLoader();
    loader.putTemplate("class.ftl", "public class ${className} {}");
    cfg.setTemplateLoader(loader);

    Map<String, Object> model = Map.of("className", "Foo");
    File outputFile = new File(tempDir.toFile(), "Foo.java");

    new GeneratedFile(cfg, model, "class.ftl", outputFile).generate();

    assertTrue(outputFile.exists());
    assertEquals(Files.readString(outputFile.toPath()), "public class Foo {}");
  }

}
