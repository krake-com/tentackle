/*
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.maven.wizard;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Base wizard mojo.
 *
 * @author harald
 */
public abstract class AbstractWizardMojo extends AbstractMojo {

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}",
             readonly = true,
             required = true)
  private MavenProject mavenProject;


  /**
   * The profiles.
   */
  @Parameter(required = true)
  private List<Profile> profiles;


  /**
   * The directory holding the templates.
   */
  @Parameter(defaultValue = "${project}/templates")
  private File templateDir;


  /**
   * Gets the profiles.
   *
   * @param clazz the concrete profile class
   * @return the list of profiles
   */
  @SuppressWarnings("unchecked")
  public <T extends Profile> List<T> getProfiles(Class<T> clazz) {
    List<T> list = new ArrayList<>();
    for (Profile profile: profiles) {
      if (clazz.isAssignableFrom(profile.getClass())) {
        list.add((T) profile);
      }
    }
    return list;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    validate();

    // todo: remove me, just a test
    for (Profile profile: profiles) {
      getLog().info(profile.toString());
    }
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
  }

}
