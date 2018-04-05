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


package org.tentackle.maven;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.wurbelizer.misc.Constants;

/**
 * A mojo to find all BeanInfo-files and create a manifest from.
 *
 * @author harald
 */
@Mojo(name = "beaninfo",
      defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class BeanInfoMojo extends AbstractTentackleMojo {

  private static final String MANIFEST_FILENAME = "MANIFEST.MF";    // generated filename
  private static final String BEANINFO_TRAILER  = "BeanInfo.java";  // trailing filename part (Java convention)


  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java sources of the current project.
   * If this is not desired, filesets must be used.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}",
             property = "tentackle.sourceDir",
             required = true)
  private File sourceDir;


  /**
   * Directory to write manifest to.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-resources/manifest",
             property = "tentackle.manifestDirectory",
             required = true)
  private String manifestDirectory;


  /**
   * Total number of errors.
   */
  private int totalErrors;



  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    // validate configuration
    validate();

    totalErrors = 0;

    File manifestDir = new File(manifestDirectory, "META-INF");
    manifestDir.mkdirs();

    File manifestFile = new File(manifestDir, MANIFEST_FILENAME);

    try {
      try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(manifestFile)))) {
        ps.println("Manifest-Version: 1.0");
        ps.println("Archiver-Version: Plexus Archiver");
        ps.println("Created-By: Apache Maven");
        ps.println("Built-By: " + System.getProperty("user.name"));
        ps.println("Build-Jdk: " + System.getProperty("java.version"));

        // process files
        if (filesets != null && !filesets.isEmpty()) {
          // explicit filesets given instead of source dir
          for (FileSet fileSet : filesets) {
            processFileSet(fileSet, ps);
          }
        }
        else {
          // all from source dir with default java-extension
          String[] files = sourceDir.isDirectory() ? sourceDir.list() : null;
          if (files != null && files.length > 0) {
            final FileSet fs = new FileSet();
            fs.setDirectory(sourceDir.getPath());
            fs.addInclude("**/*BeanInfo" + Constants.JAVA_SOURCE_EXTENSION);
            processFileSet(fs, ps);
          }
          else {
            getLog().warn("no or empty sourceDir " + sourceDir.getAbsolutePath());
          }
        }
      }
    }
    catch (IOException iox) {
      throw new MojoExecutionException("failed to create manifest-file " + manifestFile.getAbsolutePath(), iox);
    }

    if (totalErrors > 0) {
      throw new MojoFailureException(totalErrors + " wurbel errors");
    }
  }



  /**
   * Process all files in a fileset.
   *
   * @param fileSet the set of files
   * @throws MojoExecutionException
   */
  private void processFileSet(FileSet fileSet, PrintStream ps) throws MojoExecutionException {

    if (fileSet.getDirectory() == null) {
      // directory missing: use sourceDir as default
      fileSet.setDirectory(sourceDir.getAbsolutePath());
    }

    File dir = new File(fileSet.getDirectory());

    String dirName = getCanonicalPath(dir);

    if (verbosityLevel.isDebug()) {
      getLog().info("processing files in " + dirName);
    }

    int errorCount = 0;

    String[] fileNames = new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet);

    if (fileNames.length > 0) {
      for (String fileName : fileNames) {
        int ndx = fileName.lastIndexOf(BEANINFO_TRAILER); // convention
        if (ndx < 0) {
          getLog().error("file " + fileName + " does not end with " + BEANINFO_TRAILER);
          errorCount++;
          continue;
        }
        String beanInfoName = fileName.substring(0, ndx); // cut trailing BeanInfo.java
        // check if file exists
        File beanFile = new File(dirName + "/" + beanInfoName + Constants.JAVA_SOURCE_EXTENSION);
        if (!beanFile.exists()) {
          getLog().error("no matching java-file for " + fileName);
          errorCount++;
          continue;
        }
        if (!beanInfoName.contains("Abstract")) {
          // don't add abstract classes even if they provide a BeanInfo-file.
          // this is just to provide common settings
          ps.println();
          ps.println("Name: " + beanInfoName + ".class");
          ps.println("Java-Bean: True");
        }
      }
    }

    getLog().info(getPathRelativeToBasedir(dirName) + ": " +
                  fileNames.length + " files processed, " +
                  errorCount + " errors");

    totalErrors += errorCount;
  }


  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    if (sourceDir == null) {
      throw new MojoExecutionException("missing tentackle.sourceDir");
    }
  }

}
