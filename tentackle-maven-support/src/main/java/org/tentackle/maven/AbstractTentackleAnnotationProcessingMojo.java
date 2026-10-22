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

package org.tentackle.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.wurbelizer.misc.Constants;

import org.tentackle.buildsupport.AbstractTentackleProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class of mojos running annotation processors over the sources, prior to wurbeling and compiling them.
 * <p>
 * The sources are passed to the in-process Java compiler with {@code -proc:only}, i.e. nothing is compiled,
 * and the registered {@link AbstractTentackleProcessor}s generate code and meta-information, such as the
 * analyze info and service descriptors. Errors reported by the processors fail the build, whereas
 * compilation errors in the sources are only logged as a warning, because the sources have not been
 * wurbeled yet.
 * <p>
 * Subclasses register the processors via {@link #addProcessor(AbstractTentackleProcessor)} and must
 * invoke {@link #setMojoParameters(File, List)} before the execution is validated.
 *
 * @author harald
 */
public abstract class AbstractTentackleAnnotationProcessingMojo extends AbstractTentackleMojo {

  /**
   * The diagnostic code javac assigns to messages printed by an annotation processor's
   * {@link javax.annotation.processing.Messager}.<br>
   * This is how an analyze error is told apart from an error in the (not yet wurbeled) sources.
   */
  private static final String PROCESSOR_DIAGNOSTIC_CODE = "compiler.err.proc.messager";

  /**
   * Shows the compiler diagnostics in the build log.<br>
   * Errors reported by the annotation processors are always shown.
   */
  @Parameter(defaultValue = "${tentackle.showCompileOutput}")
  protected boolean showCompileOutput;

  /**
   * The source file encoding.<br>
   * If not set, the compiler's default is used.
   */
  @Parameter(defaultValue = "${project.build.sourceEncoding}")
  protected String encoding;

  /**
   * Arguments to be passed to the compiler.<br>
   * (see maven-compiler-plugin)
   * <p>
   * Unless one of them is a {@code -proc:} option, {@code -proc:only} is added.
   */
  @Parameter
  protected List<String> compilerArgs;

  /**
   * A single argument to be passed to the compiler as is.<br>
   * (see maven-compiler-plugin)
   */
  @Parameter
  protected String compilerArgument;

  /**
   * The list of file sets.<br>
   * If set, they are processed instead of the whole source directory.
   * A file set without a directory defaults to the source directory.
   */
  @Parameter
  protected List<FileSet> filesets;

  /**
   * Directory holding the sources to be processed.
   */
  private File sourceDir;

  /**
   * Project classpath.
   */
  private List<String> classpathElements;

  /**
   * Total number of analyze errors.
   */
  private int totalErrors;

  /**
   * Annotation processors.
   */
  protected List<AbstractTentackleProcessor> processors;

  /**
   * A class loader that searches also the classpath elements.
   */
  private ClassLoader processingClassloader;

  /**
   * Total number of compile errors.
   */
  private int totalCompileErrors;

  /**
   * The logged compiler diagnostics.
   */
  private StringBuilder compileErrorLog;


  /**
   * Sets the mojo parameters.<br>
   * Must be invoked before the execution is validated, usually in {@link #prepareExecute()}.
   *
   * @param sourceDir the directory holding the sources to be processed
   * @param classpathElements the project's classpath
   * @throws MojoFailureException if the classloader could not be created
   */
  public void setMojoParameters(File sourceDir, List<String> classpathElements) throws MojoFailureException {
    this.sourceDir = sourceDir;
    this.classpathElements = classpathElements;
    try {
      processingClassloader = new ProjectClassLoader((URLClassLoader) getClass().getClassLoader(), classpathElements);
    }
    catch (MalformedURLException mx) {
      throw new MojoFailureException("cannot create project classloader", mx);
    }
  }

  /**
   * Gets a classloader that also searches the classpath elements of the project.
   *
   * @return the classloader, null if {@link #setMojoParameters(File, List)} has not been invoked yet
   */
  public ClassLoader getProcessingClassloader() {
    return processingClassloader;
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
   * Creates any missing directories.<br>
   * Invoked at the start of {@link #executeImpl()}.
   */
  protected void createMissingDirs() {
    // nothing by default
  }

  /**
   * Gets the number of compilation errors in the sources.
   *
   * @return 0 if the analyze phase completed, &gt; 0 if the analyze info may be incomplete due to errors
   */
  public int getTotalCompileErrors() {
    return totalCompileErrors;
  }

  /**
   * Gets the logged compiler diagnostics.<br>
   * Holds the analyze errors and, if {@link #showCompileOutput} is set, all other diagnostics as well.
   *
   * @return the log, empty if nothing logged
   */
  public String getCompileErrorLog() {
    return compileErrorLog == null ? "" : compileErrorLog.toString();
  }

  /**
   * Gets the number of analyze errors.<br>
   * Includes the errors reported by the annotation processors and the failed processor cleanups.
   *
   * @return the error count
   */
  public int getTotalErrors() {
    return totalErrors;
  }


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    // create any missing directories
    createMissingDirs();

    totalErrors = 0;
    totalCompileErrors = 0;
    compileErrorLog = new StringBuilder();

    if (filesets != null && !filesets.isEmpty()) {
      // explicit filesets given instead of source dir
      for (FileSet fileSet : filesets) {
        processFileSet(fileSet);
      }
    }
    else {
      // all java files of the source dir (not null, see validate())
      String[] files = sourceDir.isDirectory() ? sourceDir.list() : null;
      if (files != null && files.length > 0) {
        final FileSet fs = new FileSet();
        fs.setDirectory(sourceDir.getPath());
        fs.addInclude("**/*" + Constants.JAVA_SOURCE_EXTENSION);
        processFileSet(fs);
      }
      else {
        getLogger().info("{} source directory {}",
                         sourceDir.exists() ? "empty" : "missing", sourceDir.getAbsolutePath());
      }
    }

    if (processors != null) {
      for (AbstractTentackleProcessor processor : processors) {
        try {
          processor.cleanup();
        }
        catch (IOException ex) {
          getLogger().error("annotation processor cleanup failed", ex);
          totalErrors++;
        }
      }
    }

    if (totalErrors > 0) {
      throw new MojoFailureException(totalErrors + " analyze errors");
    }
  }



  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (sourceDir == null) {
        throw new MojoExecutionException("missing tentackle.sourceDir");
      }
      return true;
    }
    return false;
  }


  /**
   * Initializes a processor.<br>
   * Invoked for each file set before the sources are processed.
   * The default sets the processor's source directory.
   *
   * @param processor the annotation processor
   * @param srcDir the current file set directory
   * @throws MojoFailureException if initialization failed
   */
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) throws MojoFailureException {
    processor.setSourceDir(srcDir);
  }


  /**
   * Initializes all processors.
   *
   * @param srcDir the current file set directory
   * @throws MojoFailureException if initialization failed
   */
  protected void initializeProcessors(File srcDir) throws MojoFailureException {
    if (processors != null) {
      for (AbstractTentackleProcessor processor : processors) {
        initializeProcessor(processor, srcDir);
      }
    }
  }


  /**
   * Cleans up the processors.<br>
   * Invoked for each file set after the sources are processed.<br>
   * Not to be confused with {@link AbstractTentackleProcessor#cleanup()}, which is invoked once
   * at the end of {@link #executeImpl()}.
   *
   * @param srcDir the current file set directory
   * @throws MojoFailureException if cleanup failed
   */
  protected void cleanupProcessors(File srcDir) throws MojoFailureException {
    // the default does nothing
  }


  /**
   * Filters the files to be processed.<br>
   * Subclasses may override it to exclude files.
   * If no files are left, the compiler is not invoked at all.
   *
   * @param dirName the base directory
   * @param fileNames the file names relative to the directory
   * @return the filtered file names
   */
  protected String[] filterFileNames(String dirName, String[] fileNames) {
    return fileNames;
  }


  /**
   * Gets the {@code -proc:} option added to the compiler arguments if none is configured.<br>
   * The default is {@code -proc:only}, i.e. the processors run, but nothing is compiled.
   * Subclasses scanning attributed syntax trees instead of running processors return {@code -proc:none}
   * and override {@link #runCompilationTask(CompilationTask)}.
   *
   * @return the default {@code -proc:} option, never null
   */
  protected String getDefaultProcOption() {
    return "-proc:only";
  }


  /**
   * Runs the compilation task.<br>
   * The default invokes {@link CompilationTask#call()}.
   *
   * @param task the task with the processors set, if any
   */
  protected void runCompilationTask(CompilationTask task) {
    task.call();
  }


  /**
   * Returns whether a diagnostic is an error reported by one of the annotation processors.
   * <p>
   * Analyze errors fail the build, whereas errors in the sources are reported as a warning only,
   * because the sources have not been wurbeled yet at this point of the build.
   * <p>
   * If a future compiler no longer marks the messages of a {@link javax.annotation.processing.Messager}
   * with {@value #PROCESSOR_DIAGNOSTIC_CODE}, analyze errors are counted as compilation errors again,
   * i.e., the build is lenient rather than failing for the wrong reason.
   *
   * @param diag the diagnostic
   * @return true if reported by an annotation processor
   */
  protected boolean isAnalyzeError(Diagnostic<? extends JavaFileObject> diag) {
    return diag.getKind() == Diagnostic.Kind.ERROR && PROCESSOR_DIAGNOSTIC_CODE.equals(diag.getCode());
  }


  /**
   * Formats a diagnostic for the build log.
   *
   * @param diag the diagnostic
   * @return the message, prefixed with {@code file:line:column} if the diagnostic refers to a source file
   */
  protected String formatDiagnostic(Diagnostic<? extends JavaFileObject> diag) {
    JavaFileObject source = diag.getSource();
    if (source == null) {
      return diag.getMessage(null);
    }
    String filename = source.getName();
    int ndx = filename.lastIndexOf(File.separatorChar);
    if (ndx > 0) {
      filename = filename.substring(ndx + 1);
    }
    return filename + ":" + diag.getLineNumber() + ":" + diag.getColumnNumber() + ": " + diag.getMessage(null);
  }


  /**
   * Returns whether a compiler argument is one of the {@code -proc:} options.
   *
   * @param arg the compiler argument
   * @return true if {@code -proc:none}, {@code -proc:only}, {@code -proc:full}, etc.
   */
  private static boolean isProcOption(String arg) {
    return arg.startsWith("-proc:");
  }


  /**
   * Processes all files in a fileset.
   *
   * @param fileSet the set of files
   * @throws MojoFailureException if initializing or cleaning up the processors failed
   * @throws MojoExecutionException if no Java compiler is available or the directory is invalid
   */
  private void processFileSet(FileSet fileSet) throws MojoFailureException, MojoExecutionException {

    if (fileSet.getDirectory() == null) {
      if (sourceDir == null) {
        throw new MojoExecutionException("no sourceDir configured");
      }
      // directory missing: use sourceDir as default
      fileSet.setDirectory(sourceDir.getAbsolutePath());
    }

    File dir = new File(fileSet.getDirectory());
    initializeProcessors(dir);

    String dirName = getCanonicalPath(dir);

    if (verbosityLevel.isDebug()) {
      getLogger().info("analyzing files in {}", dirName);
    }

    int errorCount = 0;           // analyze errors, i.e., reported by the annotation processors
    int compileErrorCount = 0;    // source compilation errors

    String[] fileNames = getIncludedFiles(fileSet);
    fileNames = filterFileNames(dirName, fileNames);

    if (fileNames.length > 0) {

      for (int i=0; i < fileNames.length; i++) {
        fileNames[i] = dirName + "/" + fileNames[i];
        if (verbosityLevel.isDebug()) {
          getLogger().info("analyzing {}", fileNames[i]);
        }
      }

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler == null) {
        throw new MojoExecutionException("No Java compiler! jdk.compiler in module path?");
      }
      DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

      List<String> options = new ArrayList<>();
      if (encoding != null && !encoding.isEmpty()) {
        options.add("-encoding");
        options.add(encoding);
      }

      if (classpathElements != null && !classpathElements.isEmpty()) {
        options.add("-classpath");
        StringBuilder buf = new StringBuilder();
        for (String element : classpathElements) {
          if (!buf.isEmpty()) {
            buf.append(File.pathSeparatorChar);
          }
          buf.append(element);
        }
        options.add(buf.toString());

        // and to the module path as well, since the sources may be modular
        options.add("--module-path");
        options.add(buf.toString());
      }
      // options started with -A are passed to the annotation processors (without leading -A)
      options.add("-Averbosity=" + verbosityLevel);

      boolean procFound = false;
      if (compilerArgument != null && !compilerArgument.isEmpty()) {
        options.add(compilerArgument);
        procFound = isProcOption(compilerArgument);
      }

      if (compilerArgs != null) {
        for (String arg: compilerArgs) {
          options.add(arg);
          if (isProcOption(arg)) {
            procFound = true;
          }
        }
      }

      if (!procFound) {
        options.add(getDefaultProcOption());    // usually run only the annotation processing
      }

      CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, options, null,
                                              fileManager.getJavaFileObjects(fileNames));

      if (processors != null) {
        task.setProcessors(processors);
      }
      else if (!options.contains("-proc:none")) {
        getLogger().warn("no annotation processors defined");
      }

      runCompilationTask(task);

      // extract errors
      for (Diagnostic<? extends JavaFileObject> diag: diagnosticCollector.getDiagnostics()) {
        boolean analyzeError = isAnalyzeError(diag);
        if (analyzeError || showCompileOutput) {
          String msg = formatDiagnostic(diag);
          if (analyzeError) {
            // always report it: the build is going to fail because of it
            getLogger().error(msg);
          }
          else {
            getLogger().info(msg);
          }
          compileErrorLog.append(msg).append('\n');
        }
        if (diag.getKind() == Diagnostic.Kind.ERROR) {
          if (analyzeError) {
            errorCount++;
          }
          else {
            compileErrorCount++;
          }
        }
      }
    }

    cleanupProcessors(dir);

    getLogger().info("{}: {} files analyzed, {} errors",
                     getPathRelativeToBasedir(dirName), fileNames.length, errorCount);

    if (compileErrorCount > 0) {
      getLogger().warn("{} Java compilation errors! " +
                       "Some annotations may have been processed improperly or not processed at all",
                       compileErrorCount);
    }

    totalCompileErrors += compileErrorCount;
    totalErrors += errorCount;
  }

}
