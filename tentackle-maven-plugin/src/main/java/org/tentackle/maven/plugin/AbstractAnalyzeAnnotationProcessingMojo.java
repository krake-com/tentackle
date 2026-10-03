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

package org.tentackle.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.tentackle.buildsupport.AnalyzeProcessor;
import org.tentackle.buildsupport.ResourceManager;
import org.tentackle.maven.AbstractTentackleAnnotationProcessingMojo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Base class of the {@code analyze} and {@code test-analyze} mojos.
 * <p>
 * Processes all annotations annotated with {@code @Analyze}, such as {@code @Service}, via the
 * {@link AnalyzeProcessor}. Depending on the handler of each annotation, the results are either written
 * as service descriptors to {@code servicesDir}, as analyze info to {@code analyzeDir}, or placed on heap
 * to be picked up by the wurblets.
 * <p>
 * The processing is incremental (see {@link #filterFileNames(String, String[])}). Because a service descriptor
 * is rewritten from the processed sources only, descriptors shared by several sources (such as the mapped
 * services) and the optional {@link #index} may be incomplete after an incremental run. A clean build
 * always produces the complete set.
 *
 * @author harald
 */
public abstract class AbstractAnalyzeAnnotationProcessingMojo extends AbstractTentackleAnnotationProcessingMojo {

  /**
   * Directory holding the analyze-results.
   */
  private File analyzeDir;

  /**
   * Directory holding the generated services.
   */
  private File servicesDir;

  /**
   * Optional index of the generated service resources, relative to {@code servicesDir}.<br>
   * A plain list of resource paths, one per line, as read by
   * {@code org.tentackle.common.ServiceFactory#applyResourceIndex}, for example in an OSGi activator.
   * <p>
   * Example: &lt;index&gt;META-INF/RESOURCE-INDEX.LIST&lt;/index&gt;
   */
  @Parameter
  protected String index;

  /**
   * The names of the generated service resources.
   */
  private Set<String> generatedServices;

  /**
   * Epochal time in ms when the mojo was created. Used to detect which of the analyze directories
   * were created within this run.
   */
  private final long startedMillis;

  /**
   * Creates a mojo.
   */
  public AbstractAnalyzeAnnotationProcessingMojo() {
    addProcessor(new AnalyzeProcessor());
    startedMillis = System.currentTimeMillis();
  }

  /**
   * Sets the mojo parameters.
   *
   * @param sourceDir the directory holding the sources to be processed
   * @param analyzeDir the directory holding the analyze-results
   * @param servicesDir the directory holding the generated services
   * @param classpathElements the mavenProject classpath
   * @throws MojoFailureException if the classloader could not be created
   */
  public void setMojoParameters(File sourceDir, File analyzeDir, File servicesDir, List<String> classpathElements)
         throws MojoFailureException {
    super.setMojoParameters(sourceDir, classpathElements);
    this.analyzeDir = analyzeDir;
    this.servicesDir = servicesDir;
  }


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (analyzeDir == null) {
        throw new MojoExecutionException("missing analyzeDir");
      }
      if (analyzeDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in analyzeDir: " + analyzeDir.getPath());
      }
      if (servicesDir == null) {
        throw new MojoExecutionException("missing servicesDir");
      }
      if (servicesDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in servicesDir: " + servicesDir.getPath());
      }
      return true;
    }
    return false;
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


  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) throws MojoFailureException {
    super.initializeProcessor(processor, srcDir);
    AnalyzeProcessor p = (AnalyzeProcessor) processor;
    p.setAnalyzeDir(analyzeDir);
    p.setServiceDir(servicesDir);
    p.setProcessingClassLoader(getProcessingClassloader());
  }


  @Override
  protected void cleanupProcessors(File srcDir) throws MojoFailureException {
    if (index != null && processors != null) {
      if (generatedServices == null) {
        generatedServices = new TreeSet<>();
      }
      for (AbstractTentackleProcessor processor: processors) {
        for (ResourceManager rm: processor.getResourceManagers()) {
          if (rm.getLocation().equals(servicesDir)) {
            generatedServices.addAll(rm.getResourceNames());
          }
        }
      }
    }
    super.cleanupProcessors(srcDir);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Each source file has a corresponding directory in {@code analyzeDir}, named after its path without the
   * extension. A source file is skipped if it is not newer than the oldest timestamp of this directory and its
   * direct entries. Sources whose directory is missing or was created within this run are always processed.
   * The latter also solves the problem of case-insensitive file systems, where a subpackage with the same name
   * as a class in the same package maps to the same directory.
   */
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
        long lastModified = outputDir.lastModified();
        if (lastModified < startedMillis) {
          // get the oldest timestamp of this directory and its files or subdirs
          minOutputLastModified = lastModified;
          File[] files = outputDir.listFiles();
          if (files != null) {
            for (File file : files) {
              lastModified = file.lastModified();
              if (lastModified < minOutputLastModified) {
                minOutputLastModified = lastModified;
              }
            }
          }
        }
        // else: created within this run: always add the source file!
        // this also solves the windows fs case-insensitivity problem:
        // sub-package with the same name as a class (within the same package)
      }
      else  {
        outputDir.mkdirs();   // create dir if it doesn't exist
      }

      if (minOutputLastModified < sourceLastModified) {
        filteredNames.add(fileName);
      }
      else if (verbosityLevel.isDebug()) {
        getLogger().info("skipped unchanged {}", sourceFilePath);
      }
    }

    return filteredNames.toArray(new String[0]);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Writes the optional {@link #index} and, if the sources contained compilation errors, the
   * {@value AnalyzeProcessor#COMPILE_ERROR_LOG} file in {@code analyzeDir}. The wurblets take its existence
   * as the signal that the analyze info may be incomplete. The file is not removed by a later run without
   * errors, only by a clean build.
   */
  @Override
  public void finishExecute() throws MojoExecutionException, MojoFailureException {
    if (index != null && generatedServices != null && !generatedServices.isEmpty()) {
      try (PrintWriter writer = new ResourceManager(servicesDir).getWriter(index)) {
        for (String generatedService: generatedServices) {
          writer.println(generatedService);
        }
      }
      catch (IOException ix) {
        throw new MojoFailureException("cannot create resource index", ix);
      }
    }

    if (getTotalCompileErrors() > 0) {
      // create a file that tells subsequent build phases that there were compilation errors
      // and the analyze-info may be incomplete
      try (PrintWriter writer = new ResourceManager(analyzeDir).getWriter(AnalyzeProcessor.COMPILE_ERROR_LOG)) {
        writer.print(getCompileErrorLog());
        writer.print(getTotalCompileErrors());
        writer.println(" compilation errors");
      }
      catch (IOException ix) {
        throw new MojoFailureException("cannot create compiler error log", ix);
      }
    }

    super.finishExecute();
  }

}
