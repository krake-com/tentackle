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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.tentackle.buildsupport.codegen.AbstractGenerator;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Generates a script.
 */
public class ScriptGenerator extends AbstractGenerator {

  private final ScriptTemplateModel model;
  private final String name;
  private final String content;

  /**
   * Creates a new generator.
   *
   * @param model the model
   * @param name the script name
   * @param content the script content
   */
  public ScriptGenerator(ScriptTemplateModel model, String name, String content) {
    this.model = model;
    this.name = name;
    this.content = content;
  }

  /**
   * Creates the script.
   *
   * @return the validation results, empty if okay, never null
   * @throws IOException if file creation failed
   * @throws TemplateException if code generation failed
   */
  public String generate() throws IOException, TemplateException {
    // configure freemarker
    Configuration cfg = createFreemarkerConfiguration();
    Template template = new Template(name, new StringReader(content), cfg);
    StringWriter writer = new StringWriter();
    template.process(model, writer);
    return writer.toString();
  }

}
