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
import org.tentackle.maven.TemplateModel;

import java.io.File;
import java.io.IOException;

/**
 * Generator for the jpackage tool's options files.
 */
public class JPackageOptionsGenerator extends AbstractGenerator {

  private final JPackageMojo mojo;
  private final JlinkResolver.Result result;

  /**
   * Creates the generator.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   */
  public JPackageOptionsGenerator(JPackageMojo mojo, JlinkResolver.Result result) {
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
  public void generate() throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      TemplateModel model = TemplateModelFactory.getInstance().create(mojo, result);
      File optionsFile = new File(mojo.getMavenProject().getBuild().getDirectory(), JPackageMojo.OPTIONS_IMAGE);
      GeneratedFile generatedFile = new GeneratedFile(cfg, model, mojo.getPackageImageTemplate(), optionsFile);
      generatedFile.generate();
      optionsFile = new File(mojo.getMavenProject().getBuild().getDirectory(), JPackageMojo.OPTIONS_INSTALLER);
      generatedFile = new GeneratedFile(cfg, model, mojo.getPackageInstallerTemplate(), optionsFile);
      generatedFile.generate();
    }
    catch (IOException | TemplateException e) {
      throw new MojoExecutionException("generating jpackage options failed", e);
    }
  }

}
