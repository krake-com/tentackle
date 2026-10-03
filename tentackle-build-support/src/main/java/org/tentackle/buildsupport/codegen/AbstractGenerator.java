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
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.IOException;

/**
 * Base class for FreeMarker-based code generators.
 * <p>
 * Holds the template directory and creates a FreeMarker {@link Configuration} with the settings shared by all
 * Tentackle generators. Used by the build-time plugins (e.g. the SQL, wizard and jlink Maven plugins).
 */
public abstract class AbstractGenerator {

  private File templateDirectory;

  /**
   * Gets the FreeMarker template directory.
   *
   * @return the template directory
   */
  public File getTemplateDirectory() {
    return templateDirectory;
  }

  /**
   * Sets the FreeMarker template directory.
   *
   * @param templateDirectory the template directory, null if templates are loaded otherwise
   */
  public void setTemplateDirectory(File templateDirectory) {
    this.templateDirectory = templateDirectory;
  }

  /**
   * Creates the FreeMarker configuration.
   * <p>
   * The configuration loads the templates from the template directory (if set), reads them as UTF-8
   * and rethrows template exceptions instead of logging them, wrapping unchecked exceptions.
   *
   * @return the config
   * @throws IOException if there's some trouble with the template directory
   */
  public Configuration createFreemarkerConfiguration() throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_35);
    if (templateDirectory != null) {
      cfg.setDirectoryForTemplateLoading(templateDirectory);
    }
    cfg.setDefaultEncoding("UTF-8");
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(false);
    cfg.setWrapUncheckedExceptions(true);
    return cfg;
  }

}
