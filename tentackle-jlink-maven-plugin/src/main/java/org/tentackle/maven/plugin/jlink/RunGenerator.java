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
package org.tentackle.maven.plugin.jlink;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.maven.AbstractGenerator;
import org.tentackle.maven.GeneratedFile;
import org.tentackle.maven.GeneratedString;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Generator for the run script.
 */
public class RunGenerator extends AbstractGenerator {

  private final AbstractJlinkMojo mojo;
  private final JlinkResolver.Result result;

  /**
   * Creates the generator.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   */
  public RunGenerator(AbstractJlinkMojo mojo, JlinkResolver.Result result) {
    this.mojo = mojo;
    this.result = result;
    setTemplateDirectory(mojo.getTemplateDir());
  }

  /**
   * Generates the script.
   *
   * @throws MojoExecutionException if I/O or template error
   */
  public void generate() throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      RunTemplateModel model = new RunTemplateModel(mojo, result);
      String output = new GeneratedString(cfg, model, mojo.getNameTemplate()).generate();
      StringTokenizer stok = new StringTokenizer(output);
      if (stok.hasMoreTokens()) {
        // generate run script
        String fileName = stok.nextToken();
        File scriptFile = new File(new File(mojo.getApplicationDirectory(), "bin"), fileName);
        GeneratedFile generatedFile = new GeneratedFile(cfg, model, mojo.getRunTemplate(), scriptFile);
        generatedFile.generate();
        scriptFile.setExecutable(true);

        // generate update script with the same extension as the run script
        model.putValue("runScript", fileName);    // if update script also restarts the application
        String fileExtension = "";
        int ndx = fileName.lastIndexOf('.');
        if (ndx >= 0) {
          fileExtension = fileName.substring(ndx);
        }
        fileName = mojo.getUpdateTemplate();
        if (fileName != null) {
          ndx = fileName.lastIndexOf('.');
          if (ndx >= 0) {
            fileName = fileName.substring(0, ndx);
          }
          scriptFile = new File(new File(mojo.getApplicationDirectory(), "bin"), fileName + fileExtension);
          generatedFile = new GeneratedFile(cfg, model, mojo.getUpdateTemplate(), scriptFile);
          generatedFile.generate();
          scriptFile.setExecutable(true);
        }
      }
      else {
        throw new MojoExecutionException("no valid name for runner script");
      }
    }
    catch (IOException | TemplateException e) {
      throw new MojoExecutionException("generating run script failed", e);
    }
  }

}
