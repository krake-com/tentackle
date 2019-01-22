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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.stream.Collectors;


/**
 * Base jlink mojo.
 *
 * @author harald
 */
public abstract class AbstractJlinkMojo extends AbstractMojo {

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject mavenProject;


  /**
   * The directory holding the script templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates/jlink")
  private File templateDir;


  /**
   * Gets the template directory.
   *
   * @return the directory holding the wizard templates
   */
  public File getTemplateDir() {
    return templateDir;
  }


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    validate();
  }


  /**
   * Validates the configuration.
   *
   * @throws MojoExecutionException if project not set or misconfigured
   */
  protected void validate() throws MojoExecutionException {
    if (mavenProject == null) {
      throw new MojoExecutionException("missing project");
    }
    if (mavenProject.getBasedir() == null) {
      throw new MojoExecutionException("missing project.baseDir");
    }
    if (templateDir.exists()) {
      if (!templateDir.isDirectory()) {
        throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
      }
    }
    else {
      getLog().info("template directory created: " + templateDir.getPath());
      installTemplates();
    }
  }


  /**
   * Copies the templates to the template directory.<br>
   * Overwrites any existing templates.
   *
   * @throws MojoExecutionException if failed
   */
  protected void installTemplates() throws MojoExecutionException {
    installTemplate(Constants.PLATFORM_LINUX + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_WINDOWS + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_MACOS + Constants.TEMPLATE_EXTENSION);
    installTemplate(Constants.PLATFORM_SOLARIS + Constants.TEMPLATE_EXTENSION);
  }

  private void installTemplate(String template) throws MojoExecutionException {
    templateDir.mkdirs();
    File file = new File(templateDir, template);
    String path = "/templates/" + template;
    String text = loadResourceFileIntoString(path);
    try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
      ps.print(text);
      getLog().info("installed template " + template);
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot install template " + path, e);
    }
  }

  private String loadResourceFileIntoString(String path) throws MojoExecutionException {
    InputStream inputStream = getClass().getResourceAsStream(path);
    if (inputStream == null) {
      throw new MojoExecutionException("no such resource: " + path);
    }
    BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream));
    return buffer.lines().collect(Collectors.joining(System.getProperty("line.separator")));
  }
}
