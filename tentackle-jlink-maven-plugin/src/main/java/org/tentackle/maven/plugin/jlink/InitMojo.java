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
 * Copies the default templates of the <code>jlink</code> and <code>jpackage</code> goals to the project's
 * template directory, overwriting existing ones. Needs no jlink, jdeps or jpackage tool.
 */
@Mojo(name = "init", aggregator = true, threadSafe = true)
public class InitMojo extends AbstractJLinkMojo {

  private static final String[] TEMPLATES_ALL = {
      JLinkMojo.NAME_TEMPLATE,
      JLinkMojo.RUN_TEMPLATE,
      JLinkMojo.UPDATE_TEMPLATE,
      JPackageMojo.PACKAGE_IMAGE_TEMPLATE,
      JPackageMojo.PACKAGE_INSTALLER_TEMPLATE,
      JPackageMojo.PACKAGE_UPDATE_TEMPLATE
  };

  /**
   * Controls whether all or only already installed templates should be copied.<br>
   * By default, all templates are copied to the project's template folder.<br>
   * Otherwise, only those templates that already exist in the template folder
   * will be overwritten.
   */
  @Parameter(defaultValue = "true")
  private boolean copyAll;


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    installTemplates(true);
  }

  @Override
  protected boolean isBuildingImage() {
    return false;     // no tools needed, and the templates are installed by executeImpl only
  }


  @Override
  protected void installTemplates(boolean overwrite) throws MojoExecutionException {
    File templateDir = getTemplateDir();
    if (copyAll) {
      for (String name : TEMPLATES_ALL) {
        installTemplate(name, name, true);
        getLogger().info("template '{}' copied to {}", name, templateDir);
      }
    }
    else {
      String[] names = templateDir.list();
      if (names != null) {
        for (String name : names) {    // overwrite each existing file
          File template = new File(templateDir, name);
          if (template.isFile()) {
            try {
              installTemplate(name, name, true);
              getLogger().info("template '{}' restored to its default", name);
            }
            catch (MojoExecutionException mx) {
              getLogger().warn("no default template for '{}' -> skipped", name);
            }
          }
        }
      }
    }
  }


  // ------------- unused abstract methods -----------------

  @Override
  protected void validateTemplates() {
    // nothing to validate: the goal installs the templates under the names the plugin ships them
  }

  @Override
  protected void createJLinkImage(JLinkResolver.Result result) throws MojoExecutionException, MojoFailureException {
  }

  @Override
  public File getImageDirectory() {
    return null;
  }

  @Override
  protected void generateFiles(JLinkResolver.Result result) throws MojoExecutionException {
  }

}
