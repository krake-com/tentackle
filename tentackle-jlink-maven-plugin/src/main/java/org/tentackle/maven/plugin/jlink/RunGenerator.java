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

import java.io.File;
import java.io.IOException;

/**
 * Generator for the run script.
 */
public class RunGenerator extends AbstractGenerator {

  private static final String RUN_UNIXES = "run.sh";
  private static final String RUN_WINDOZES = "run.bat";


  private final AbstractJlinkMojo mojo;
  private final JlinkResolver.Result result;

  private String templateName;
  private String scriptName;

  /**
   * Creates the generator.
   *
   * @param mojo the jlink mojo
   * @param result the resolver results
   * @param addConfToClasspath true if add conf subdirectory to the classpath
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
      determineTemplateName();
      File scriptFile = new File(new File(mojo.getImageDirectory(), "bin"), scriptName);
      GeneratedFile generatedFile = new GeneratedFile(cfg, model, templateName, scriptFile);
      generatedFile.generate();
      scriptFile.setExecutable(true);
    }
    catch (IOException | TemplateException e) {
      throw new MojoExecutionException("generating run script failed", e);
    }
  }


  private void determineTemplateName() throws MojoExecutionException {
    String osName = System.getProperty("os.name");
    if (osName == null) {
      throw new MojoExecutionException("cannot determine platform");
    }
    mojo.getLog().debug("platform is " + osName);
    osName = osName.toUpperCase();
    if (osName.contains("WIN")) {
      templateName = Constants.PLATFORM_WINDOWS + Constants.TEMPLATE_EXTENSION;
      scriptName = RUN_WINDOZES;
    }
    else if (osName.contains("LIN")) {
      templateName = Constants.PLATFORM_LINUX + Constants.TEMPLATE_EXTENSION;
      scriptName = RUN_UNIXES;
    }
    else if (osName.contains("SUN") || osName.contains("SOL")) {
      templateName = Constants.PLATFORM_SOLARIS + Constants.TEMPLATE_EXTENSION;
      scriptName = RUN_UNIXES;
    }
    else if (osName.contains("MAC")) {
      templateName = Constants.PLATFORM_MACOS + Constants.TEMPLATE_EXTENSION;
      scriptName = RUN_UNIXES;
    }
    else {
      throw new MojoExecutionException("unsupported platform " + System.getProperty("os.name"));
    }
    mojo.getLog().debug("using template " + templateName + " to generate " + scriptName);
  }
}
