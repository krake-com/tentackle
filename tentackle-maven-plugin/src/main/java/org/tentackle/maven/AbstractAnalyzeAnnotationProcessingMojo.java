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
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.buildsupport.AnalyzeProcessor;



/**
 * Generates code and meta-information prior to wurbeling and compiling the sources.
 * <p>
 * Processes up all annotations annotated with {@code @Analyze}, such as {@code @Service}.<br>
 * Processing results are either written to files in analyzeDir or placed on heap
 * for being picked up by wurblets, depending on the handler implementation for
 * each annotation.
 *
 * @author harald
 */
public abstract class AbstractAnalyzeAnnotationProcessingMojo extends AbstractTentackleAnnotationProcessingMojo {

  /**
   * Directory holding the analyze results.
   */
  private File analyzeDir;

  /**
   * Directory holding the generated services.
   */
  private File servicesDir;


  /**
   * Creates a mojo.
   */
  public AbstractAnalyzeAnnotationProcessingMojo() {
    addProcessor(new AnalyzeProcessor());
  }

  /**
   * Sets the mojo parameters.
   *
   * @param sourceDir the directory holding the sources to be processed
   * @param analyzeDir the directory holding the analyze results
   * @param servicesDir the directory holding the generated services
   * @param classpathElements the mavenProject classpath
   */
  public void setMojoParameters(File sourceDir, File analyzeDir, File servicesDir, List<String> classpathElements)
         throws MojoFailureException {
    super.setMojoParameters(sourceDir, classpathElements);
    this.analyzeDir = analyzeDir;
    this.servicesDir = servicesDir;
  }


  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    if (analyzeDir == null) {
      throw new MojoExecutionException("missing tentackle.analyzeDir");
    }
    if (analyzeDir.getPath().contains("${")) {
      throw new MojoExecutionException("undefined variable(s) in analyzeDir: " + analyzeDir.getPath());
    }
    if (servicesDir == null) {
      throw new MojoExecutionException("missing tentackle.serviceDir");
    }
    if (servicesDir.getPath().contains("${")) {
      throw new MojoExecutionException("undefined variable(s) in servicesDir: " + servicesDir.getPath());
    }
  }


  /**
   * Creates any missing directories.
   */
  @Override
  protected void createMissingDirs() {
    super.createMissingDirs();
    analyzeDir.mkdirs();
    servicesDir.mkdirs();
  }


  /**
   * Initializes a processor.
   *
   * @param processor the annotation processor
   * @param srcDir the sources dir
   */
  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) {
    super.initializeProcessor(processor, srcDir);
    AnalyzeProcessor p = (AnalyzeProcessor) processor;
    p.setAnalyzeDir(analyzeDir);
    p.setServiceDir(servicesDir);
    p.setProcessingClassLoader(getProcessingClassloader());
  }


  @Override
  protected String[] filterFileNames(String dirName, String[] fileNames) {
    List<String> filteredNames = new ArrayList<>();

    for (String fileName: fileNames) {

      String sourceFilePath = dirName + File.separator + fileName;
      long sourceLastModified = new File(sourceFilePath).lastModified();
      String outputDirPath = analyzeDir.getAbsolutePath() + File.separator + fileName;
      // cut trailing extension (.java)
      int ndx = outputDirPath.lastIndexOf('.');
      if (ndx > 0) {
        outputDirPath = outputDirPath.substring(0, ndx);
      }
      long minOutputLastModified = 0;
      File outputDir = new File(outputDirPath);
      if (outputDir.exists()) {
        // get the oldest timestamp
        minOutputLastModified = outputDir.lastModified();
        File[] files = outputDir.listFiles();
        if (files != null) {
          for (File file : files) {
            long lastModified = file.lastModified();
            if (lastModified < minOutputLastModified) {
              minOutputLastModified = lastModified;
            }
          }
        }
      }
      else  {
        outputDir.mkdirs();   // create dir if it doesnt
      }

      if (minOutputLastModified < sourceLastModified) {
        filteredNames.add(fileName);
      }
      else if (verbosityLevel.isDebug()) {
        getLog().info("skipped unchanged " + sourceFilePath);
      }
    }

    return filteredNames.toArray(new String[filteredNames.size()]);
  }

}
