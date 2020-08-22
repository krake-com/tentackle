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

import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractGenerator;
import org.tentackle.maven.GeneratedFile;
import org.tentackle.maven.GeneratedString;
import org.tentackle.maven.TemplateModel;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Generator for the jlink image's run and update scripts.
 */
public class JLinkGenerator extends AbstractGenerator {

  private final JLinkMojo mojo;
  private final JLinkResolver.Result result;

  /**
   * Creates the generator.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   */
  public JLinkGenerator(JLinkMojo mojo, JLinkResolver.Result result) {
    this.mojo = mojo;
    this.result = result;
    setTemplateDirectory(mojo.getTemplateDir());
    setLogger(mojo.getLog());
  }

  /**
   * Generates the scripts.
   *
   * @throws MojoExecutionException if I/O or template error
   */
  public void generateScripts() throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      TemplateModel model = TemplateModelFactory.getInstance().create(mojo, result);
      String output = new GeneratedString(cfg, model, mojo.getNameTemplate()).generate();
      StringTokenizer stok = new StringTokenizer(output);
      if (stok.hasMoreTokens()) {
        // generate run script
        String fileName = stok.nextToken();
        File scriptFile = new File(new File(mojo.getImageDirectory(), "bin"), fileName);
        GeneratedFile generatedFile = new GeneratedFile(cfg, model, mojo.getRunTemplate(), scriptFile);
        generatedFile.generate();
        scriptFile.setExecutable(true);

        // generate update script
        model.putValue("runScript", fileName);    // if update script also restarts the application
        if (mojo.getUpdateTemplate() != null) {
          scriptFile = new File(new File(mojo.getImageDirectory(), "bin"),
                                StringHelper.getPlatform().contains("win") ? "update.cmd" : "update.sh");
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
