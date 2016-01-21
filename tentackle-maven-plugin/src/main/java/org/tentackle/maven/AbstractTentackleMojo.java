/**
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


package org.tentackle.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.wurbelizer.misc.Verbosity;


/**
 * A generic wurbelizer mojo.
 *
 * @author harald
 */
public abstract class AbstractTentackleMojo extends AbstractMojo {

  /**
   * The Maven Project.
   *
   * @parameter default-value="${project}"
   * @required
   * @readonly
   */
  protected MavenProject mavenProject;

  /**
   * The list of file sets.<br>
   * If set the source directory is ignored.
   *
   * @parameter
   */
  protected List<FileSet> filesets;

  /**
   * The verbosity.<br>
   * One of "default", "info" or "debug".
   * Debus is also turned on (if not set explicitly) by Maven's global debug flag (see command line switch <code>-X</code>).
   *
   * @parameter default-value="${tentackle.verbosity}"
   */
  protected String verbosity;


  /**
   * mapped verbosity level
   */
  protected Verbosity verbosityLevel;

  /**
   * List of resource dirs.
   */
  protected List<String> resourceDirs;



  /**
   * sets the verbosity
   */
  protected void determineVerbosity() {
    if (verbosity != null) {
      try {
        verbosityLevel = Verbosity.valueOf(verbosity.toUpperCase());
      }
      catch (Exception ex) {
        verbosityLevel = Verbosity.DEFAULT;
      }
    }
    else  {
      // use maven's global setting
      verbosityLevel = getLog().isDebugEnabled() ? Verbosity.DEBUG : Verbosity.DEFAULT;
    }
  }


  /**
   * Gets the path relative to the basedir.<br>
   * Parent dirs of the basedir will also be tried.
   *
   * @param path the absolute path
   * @return the shortened path
   */
  protected String getPathRelativeToBasedir(String path) {
    String basePath = mavenProject.getBasedir().getAbsolutePath();
    while (basePath != null && basePath.length() > 0) {
      if (path.startsWith(basePath)) {
        int len = basePath.lastIndexOf(File.separatorChar);
        if (len > 0) {
          len++;    // include the last dir of the basepath
        }
        else  {
          len = basePath.length() + 1;
        }
        return path.substring(len);
      }
      // continue with the parent dir
      int ndx = basePath.lastIndexOf(File.separatorChar);
      if (ndx > 0) {
        basePath = basePath.substring(0, ndx);
      }
      else  {
        break;
      }
    }
    return path;
  }


  /**
   * Finds all resource directories.
   */
  protected void findResourceDirs() {
    resourceDirs = new ArrayList<>();
    for (Object resource : mavenProject.getBuild().getResources()) {
      if (resource instanceof Resource) {
        String name = ((Resource) resource).getDirectory();
        if (name != null) {
          getLog().debug("found resource directory " + name);
          resourceDirs.add(name);
        }
      }
    }
  }


  /**
   * Checks if given directory resides in resources.
   * 
   * @param dirName the directory name
   * @return the resource dir, null if not a resource
   */
  protected String getResourceDirName(String dirName) {
    if (resourceDirs != null) {
      for (String resourceDir: resourceDirs) {
        if (dirName.startsWith(resourceDir)) {
          return resourceDir;
        }
      }
    }
    return null;
  }


  /**
   * Gets the canonical path of diven directory.
   *
   * @param dir the directory
   * @return the path
   * @throws MojoExecutionException if failed
   */
  protected String getCanonicalPath(File dir) throws MojoExecutionException {
    try {
      return dir.getCanonicalPath();
    }
    catch (IOException ex) {
      throw new MojoExecutionException("cannot determine canonical path of " + dir, ex);
    }
  }


  /**
   * Checks the configuration.<br>
   *
   * @throws MojoExecutionException if validation failed
   */
  protected void validate() throws MojoExecutionException {
    determineVerbosity();
    if (mavenProject == null) {
      throw new MojoExecutionException("missing project");
    }
    if (mavenProject.getBasedir() == null) {
      throw new MojoExecutionException("missing project.baseDir");
    }
  }

}
