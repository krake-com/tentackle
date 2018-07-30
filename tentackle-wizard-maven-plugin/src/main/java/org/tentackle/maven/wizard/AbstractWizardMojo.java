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

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.swing.ListModel;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
    if (mavenProject.getParent() != null) {
      throw new MojoExecutionException("mojo must be invoked on parent module");
    }
  }


  /**
   * Creates a map of package names to package infos.
   *
   * @return the packages
   * @throws MojoExecutionException if split package detected
   */
  protected Map<String, PackageInfo> createPackageMap() throws MojoExecutionException {
    Map<String, PackageInfo> map = new HashMap<>();
    for (MavenProject project : mavenProject.getCollectedProjects()) {
      for (String path: project.getCompileSourceRoots()) {
        for (PackageInfo info : getPackages(project, path)) {
          PackageInfo splitPkg = map.put(info.getName(), info);
          if (splitPkg != null) {
            throw new MojoExecutionException("split package detected: " + info.getName() + " in " + project.getName() +
                                           " and " + info.getProject().getName());
          }
        }
      }
    }
    return map;
  }

  private List<PackageInfo> getPackages(MavenProject project, String compileSourceRoot) {
    List<PackageInfo> packages = new ArrayList<>();
    checkDir(project, new File(compileSourceRoot), packages, "");
    return packages;
  }

  private void checkDir(MavenProject project, File dir, List<PackageInfo> packages, String packageName) {
    if (dir.isDirectory()) {
      boolean containsJavaFiles = false;
      for (File file: dir.listFiles()) {
        if (file.isDirectory()) {
          checkDir(project, file, packages, packageName.isEmpty() ? file.getName() : packageName + "." + file.getName());
        }
        else if (file.getName().endsWith(".java")) {
          containsJavaFiles = true;
        }
      }
      if (containsJavaFiles && !packageName.isEmpty()) {
        packages.add(new PackageInfo(packageName, project, dir));
      }
    }
  }

}
