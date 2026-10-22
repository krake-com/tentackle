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

import org.tentackle.buildsupport.codegen.AbstractGenerator;
import org.tentackle.buildsupport.codegen.GeneratedFile;
import org.tentackle.buildsupport.codegen.TemplateModel;
import org.tentackle.common.StringHelper;

import java.io.File;
import java.io.IOException;

/**
 * Generator for the jpackage tool's options and image's update script.
 */
public class JPackageGenerator extends AbstractGenerator {

  private final JPackageMojo mojo;
  private final JLinkResolver.Result result;

  /**
   * Creates the generator.
   *
   * @param mojo the jpackage mojo
   * @param result the resolver results
   */
  public JPackageGenerator(JPackageMojo mojo, JLinkResolver.Result result) {
    this.mojo = mojo;
    this.result = result;
    setTemplateDirectory(mojo.getTemplateDir());
  }

  /**
   * Generates the option files.<br>
   * Besides the variables of the {@link TemplateModelFactory}, the model provides <code>runtimeDir</code>.
   *
   * @throws MojoExecutionException if I/O or template error
   */
  public void generateOptions() throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      TemplateModel model = TemplateModelFactory.getInstance().create(mojo, result);
      model.putValue("runtimeDir", mojo.getImagePathPrefix());
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

  /**
   * Generates the update script into the <code>bin</code> directory of the application image.<br>
   * The model provides the variables of the {@link TemplateModelFactory} only.
   *
   * @param appImageDir the application image directory
   * @throws MojoExecutionException if I/O or template error
   */
  public void generateUpdateScript(File appImageDir) throws MojoExecutionException {
    try {
      Configuration cfg = createFreemarkerConfiguration();
      TemplateModel model = TemplateModelFactory.getInstance().create(mojo, result);
      if (mojo.getPackageUpdateTemplate() != null) {
        File binDir = new File(appImageDir, "bin");
        binDir.mkdirs();
        File scriptFile = new File(binDir, StringHelper.getPlatform().contains("win") ? "update.cmd" : "update.sh");
        GeneratedFile generatedFile = new GeneratedFile(cfg, model, mojo.getPackageUpdateTemplate(), scriptFile);
        generatedFile.generate();
        scriptFile.setExecutable(true);
      }
    }
    catch (IOException | TemplateException e) {
      throw new MojoExecutionException("generating update script failed", e);
    }
  }

}
