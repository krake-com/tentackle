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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests for {@link AbstractGenerator}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractGeneratorTest {

  private static class TestGenerator extends AbstractGenerator {
  }

  @Test
  public void templateDirectoryDefaultsToNull() {
    assertNull(new TestGenerator().getTemplateDirectory());
  }

  @Test
  public void getTemplateDirectoryReturnsWhatWasSet() {
    TestGenerator generator = new TestGenerator();
    File dir = new File(".");
    generator.setTemplateDirectory(dir);
    assertSame(generator.getTemplateDirectory(), dir);
  }

  @Test
  public void createsConfigurationWithExpectedDefaults() throws IOException {
    Configuration cfg = new TestGenerator().createFreemarkerConfiguration();

    assertEquals(cfg.getDefaultEncoding(), "UTF-8");
    assertEquals(cfg.getTemplateExceptionHandler(), TemplateExceptionHandler.RETHROW_HANDLER);
  }

  @Test
  public void configurationLoadsTemplatesFromTheConfiguredDirectory() throws Exception {
    Path dir = Files.createTempDirectory("abstract-generator-test");
    try {
      Files.writeString(dir.resolve("test.ftl"), "value=${value}");

      TestGenerator generator = new TestGenerator();
      generator.setTemplateDirectory(dir.toFile());
      Configuration cfg = generator.createFreemarkerConfiguration();

      Template template = cfg.getTemplate("test.ftl");
      StringWriter writer = new StringWriter();
      template.process(Map.of("value", "42"), writer);

      assertEquals(writer.toString(), "value=42");
    }
    finally {
      Files.deleteIfExists(dir.resolve("test.ftl"));
      Files.deleteIfExists(dir);
    }
  }

  @Test(expectedExceptions = TemplateException.class)
  public void rethrowsTemplateExceptions() throws Exception {
    Path dir = Files.createTempDirectory("abstract-generator-test-error");
    try {
      Files.writeString(dir.resolve("broken.ftl"), "${undefinedVariable}");

      TestGenerator generator = new TestGenerator();
      generator.setTemplateDirectory(dir.toFile());
      Configuration cfg = generator.createFreemarkerConfiguration();

      cfg.getTemplate("broken.ftl").process(Map.of(), new StringWriter());
    }
    finally {
      Files.deleteIfExists(dir.resolve("broken.ftl"));
      Files.deleteIfExists(dir);
    }
  }

}
