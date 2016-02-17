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
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.tentackle.buildsupport.AbstractTentackleProcessor;
import org.wurbelizer.misc.Constants;



/**
 * Generates code and meta-information prior to wurbeling and compiling the sources.
 * <p>
 * Picks up all annotations annotated with {@code org.tentackle.common.Analyze}.
 *
 * @author harald
 */
public abstract class AbstractTentackleAnnotationProcessingMojo extends AbstractTentackleMojo {

  /**
   * Show compile output.
   */
  @Parameter(defaultValue = "${tentackle.showCompileOutput}")
  protected boolean showCompileOutput;



  /**
   * Directory holding the sources to be processed.
   */
  private File sourceDir;

  /**
   * Project classpath.
   */
  private List<String> classpathElements;

  /**
   * Total number of errors.
   */
  private int totalErrors;

  /**
   * Annotation processors.
   */
  private List<AbstractTentackleProcessor> processors;



  /**
   * Sets the mojo parameters.
   *
   * @param sourceDir the directory holding the sources to be processed
   * @param classpathElements the mavenProject classpath
   */
  public void setMojoParameters(File sourceDir, List<String> classpathElements) {
    this.sourceDir = sourceDir;
    this.classpathElements = classpathElements;
  }


  /**
   * Adds an annotation processor.
   *
   * @param processor the annotation processor
   */
  public void addProcessor(AbstractTentackleProcessor processor) {
    if (processors == null) {
      processors = new ArrayList<>();
    }
    processors.add(processor);
  }


  /**
   * Creates any missing directories.
   */
  protected void createMissingDirs() {
    // nothing by default
  }


  /**
   * Prepares the execution.<br>
   * Invoked before validate().
   */
  public abstract void prepareExecute();



  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    // sets up parameters etc...
    prepareExecute();

    // validate configuration
    validate();

    // create any missing directories
    createMissingDirs();

    totalErrors = 0;

    // wurbel
    if (filesets != null && filesets.size() > 0) {
      // explicit filesets given instead of source dir
      for (FileSet fileSet: filesets) {
        processFileSet(fileSet);
      }
    }
    else {
      // all from source dir with default java-extension
      if (sourceDir.isDirectory() && sourceDir.list().length > 0) {
        final FileSet fs = new FileSet();
        fs.setDirectory(sourceDir.getPath());
        fs.addInclude("**/*" + Constants.JAVA_SOURCE_EXTENSION);
        processFileSet(fs);
      }
      else {
        getLog().warn("no or empty sourceDir " + sourceDir.getAbsolutePath());
      }
    }

    if (processors != null) {
      for (AbstractTentackleProcessor processor: processors) {
        try {
          processor.cleanup();
        }
        catch (IOException ex) {
          getLog().error("annotation processor cleanup failed", ex);
          totalErrors++;
        }
      }
    }

    if (totalErrors > 0) {
      throw new MojoFailureException(totalErrors + " wurbel errors");
    }
  }



  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    if (sourceDir == null) {
      throw new MojoExecutionException("missing tentackle.sourceDir");
    }
  }


  /**
   * Initializes a processor.
   *
   * @param processor the annotation processor
   * @param srcDir the source dir
   */
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) {
    processor.setSourceDir(srcDir);
  }


  /**
   * Initializes all processors.
   *
   * @param srcDir the current file set directory
   */
  protected void initializeProcessors(File srcDir) {
    if (processors != null) {
      for (AbstractTentackleProcessor processor : processors) {
        initializeProcessor(processor, srcDir);
      }
    }
  }



  /**
   * Filters the files to be processed.<br>
   * If overridden, allows to skip the annotation processing.
   *
   * @param dirName the base directory
   * @param fileNames the file names relative to the directory
   * @return the filtered file names
   */
  protected String[] filterFileNames(String dirName, String[] fileNames) {
    return fileNames;
  }


  /**
   * Process all files in a fileset.
   *
   * @param fileSet the set of files
   * @throws MojoExecutionException
   */
  private void processFileSet(FileSet fileSet) throws MojoExecutionException {

    if (fileSet.getDirectory() == null) {
      // directory missing: use sourceDir as default
      fileSet.setDirectory(sourceDir.getAbsolutePath());
    }

    File dir = new File(fileSet.getDirectory());
    initializeProcessors(dir);

    String dirName = getCanonicalPath(dir);

    if (verbosityLevel.isDebug()) {
      getLog().info("analyzing files in " + dirName);
    }

    int errorCount = 0;           // compiler errors
    int compileErrorCount = 0;    // source compilation errors

    String[] fileNames = new FileSetManager(getLog(), verbosityLevel.isDebug()).getIncludedFiles(fileSet);
    fileNames = filterFileNames(dirName, fileNames);

    if (fileNames.length > 0) {

      for (int i=0; i < fileNames.length; i++) {
        fileNames[i] = dirName + "/" + fileNames[i];
        if (verbosityLevel.isDebug()) {
          getLog().info("analyzing " + fileNames[i]);
        }
      }

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler == null) {
        throw new MojoExecutionException(
                "No Java compiler! Make sure you're using the JDK or tools.jar is in your classpath.");
      }
      DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

      List<String> options = new ArrayList<>();
      options.add("-proc:only");    // run only the annotation processing
      options.add("-classpath");
      StringBuilder buf = new StringBuilder();
      for (String element: classpathElements) {
        if (buf.length() > 0) {
          buf.append(File.pathSeparatorChar);
        }
        buf.append(element);
      }
      options.add(buf.toString());
      // options started with -A are passed to the annotation processors (without leading -A)
      options.add("-Averbosity=" + verbosityLevel);

      CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, options, null,
                                              fileManager.getJavaFileObjects(fileNames));

      if (processors != null) {
        task.setProcessors(processors);
      }
      else  {
        getLog().warn("no annotation processors defined");
      }

      task.call();

      // extract errors
      for (Diagnostic<? extends JavaFileObject> diag: diagnosticCollector.getDiagnostics()) {
        if (showCompileOutput) {
          JavaFileObject source = diag.getSource();
          if (source == null) {
            getLog().info(diag.getMessage(null));
          }
          else  {
            String filename = source.getName();
            int ndx = filename.lastIndexOf(File.separatorChar);
            if (ndx > 0) {
              filename = filename.substring(ndx + 1);
            }
            getLog().info(filename + ":" + diag.getLineNumber() + ":" + diag.getColumnNumber() + ": " + diag.getMessage(null));
          }
        }
        if (diag.getKind() == Diagnostic.Kind.ERROR) {
          compileErrorCount++;
        }
      }
    }

    getLog().info(getPathRelativeToBasedir(dirName) + ": " +
                  fileNames.length + " files analyzed, " +
                  errorCount + " errors");

    if (compileErrorCount > 0) {
      getLog().warn(compileErrorCount +
                    " Java compilation errors! Some annotations may have been processed improperly or not processed at all");
    }

    totalErrors += errorCount;
  }

}
