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
package org.tentackle.maven.plugin.sql;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.buildsupport.codegen.AbstractGenerator;
import org.tentackle.buildsupport.codegen.GeneratedFile;
import org.tentackle.sql.BackendInfo;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Generator for the run script.
 */
public class MigrateGenerator extends AbstractGenerator {

  private final MigrateSqlMojo mojo;
  private final File templateFile;
  private final BackendInfo backendInfo;
  private final Map<String, Boolean> migratedEntitiesMap;

  /**
   * Creates the generator.
   *
   * @param mojo the migrate mojo
   * @param templateFile the sql template file
   * @param migratedEntitiesMap set of migrated tables
   */
  public MigrateGenerator(MigrateSqlMojo mojo, File templateFile, BackendInfo backendInfo, Map<String, Boolean> migratedEntitiesMap) {
    this.mojo = mojo;
    this.templateFile = templateFile;
    this.backendInfo = backendInfo;
    this.migratedEntitiesMap = migratedEntitiesMap;
    setTemplateDirectory(templateFile.getParentFile());
  }

  /**
   * Generates the SQL file.
   *
   * @param fileName the filename to generate
   * @throws MojoExecutionException if I/O or template error
   */
  public void generate(String fileName) throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      MigrateTemplateModel model = new MigrateTemplateModel(mojo, backendInfo, migratedEntitiesMap);
      File sqlFile = new File(getTemplateDirectory(), fileName);
      GeneratedFile generatedFile = new GeneratedFile(cfg, model, templateFile.getName(), sqlFile);
      generatedFile.generate();
      mojo.getLogger().debug("processed as template {} into {}", templateFile, sqlFile);
      templateFile.delete();
    }
    catch (IOException | TemplateException e) {
      throw new MojoExecutionException("generating migrate script from template failed", e);
    }
  }

}
