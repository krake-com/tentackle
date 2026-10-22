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
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link GeneratedString}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class GeneratedStringTest {

  @Test
  public void generatesStringFromTemplateAndModel() throws IOException, TemplateException {
    Configuration cfg = configurationWithTemplate("greeting.ftl", "Hello, ${name}!");

    TemplateModel model = new TemplateModel();
    model.putValue("name", "World");

    String result = new GeneratedString(cfg, model, "greeting.ftl").generate();

    assertEquals(result, "Hello, World!");
  }

  @Test
  public void reusesTheSameConfigurationForDifferentTemplates() throws IOException, TemplateException {
    Configuration cfg = configurationWithTemplate("a.ftl", "A: ${value}");
    addTemplate(cfg, "b.ftl", "B: ${value}");

    Map<String, Object> model = Map.of("value", "x");

    assertEquals(new GeneratedString(cfg, model, "a.ftl").generate(), "A: x");
    assertEquals(new GeneratedString(cfg, model, "b.ftl").generate(), "B: x");
  }

  @Test
  public void keepsNonAsciiCharacters() throws IOException, TemplateException {
    Configuration cfg = configurationWithTemplate("umlauts.ftl", "Grüße, ${name}!");

    TemplateModel model = new TemplateModel();
    model.putValue("name", "Jürgen €");

    assertEquals(new GeneratedString(cfg, model, "umlauts.ftl").generate(), "Grüße, Jürgen €!");
  }

  private static Configuration configurationWithTemplate(String name, String source) throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
    cfg.setDefaultEncoding("UTF-8");
    addTemplate(cfg, name, source);
    return cfg;
  }

  private static void addTemplate(Configuration cfg, String name, String source) {
    StringTemplateLoader loader = cfg.getTemplateLoader() instanceof StringTemplateLoader
        ? (StringTemplateLoader) cfg.getTemplateLoader()
        : new StringTemplateLoader();
    loader.putTemplate(name, source);
    cfg.setTemplateLoader(loader);
  }

}
