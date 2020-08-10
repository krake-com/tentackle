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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/**
 * Initializes the templates.<br>
 * Copies the default templates to the project's template directory.
 */
@Mojo(name = "init", inheritByDefault = false, aggregator = true)
public class InitMojo extends AbstractJlinkMojo {

  private static final String[] TEMPLATES_ALL = {
      JlinkMojo.TEMPLATE_NAME,
      JlinkMojo.TEMPLATE_RUN,
      JlinkMojo.TEMPLATE_UPDATE,
      JPackageMojo.PACKAGE_IMAGE_TEMPLATE,
      JPackageMojo.PACKAGE_INSTALLER_TEMPLATE
  };

  /**
   * Controls whether all or only already installed templates should be copied.<br>
   * By default (true), all templates will be copied to the template folder.<br>
   * Otherwise (false), only those templates that already exist in the template folder
   * will be overwritten.<br>
   */
  @Parameter(defaultValue = "true")
  private boolean copyAll;

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    installTemplates(true);
  }

  @Override
  protected void installTemplates(boolean overwrite) throws MojoExecutionException {
    File templateDir = getTemplateDir();
    if (copyAll) {
      for (String name : TEMPLATES_ALL) {
        installTemplate(name, true);
        getLog().info("template '" + name + "' copied to " + templateDir);
      }
    }
    else {
      String[] names = templateDir.list();
      if (names != null) {
        for (String name : names) {    // overwrite each existing file
          File template = new File(templateDir, name);
          if (template.isFile()) {
            try {
              installTemplate(name, true);
              getLog().info("template '" + name + "' restored to its default");
            }
            catch (MojoExecutionException mx) {
              getLog().warn("no default template for '" + name + "' -> skipped");
            }
          }
        }
      }
    }
  }


  // ------------- unused abstract methods -----------------

  @Override
  protected void createImage(JlinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
  }

  @Override
  public File getImageDirectory() {
    return null;
  }

  @Override
  protected void generateFiles(JlinkResolver.Result result) throws MojoExecutionException {
  }

}
