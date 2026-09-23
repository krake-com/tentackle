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

package org.tentackle.maven.plugin.check;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import org.tentackle.maven.AbstractTentackleAnnotationProcessingMojo;
import org.tentackle.maven.ProjectClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Common helpers for the check mojo tests.
 * <p>
 * The mojos are instantiated directly and their {@code @Parameter}-fields injected the same way
 * maven's plexus container would do it - by reflection - instead of booting a container via the
 * maven-plugin-testing-harness. They are driven through {@code prepareExecute()},
 * {@code validate()}, {@code executeImpl()} and {@code finishExecute()} rather than
 * {@code execute()}, because the latter also installs a global {@code java.util.logging} handler
 * (see {@code AbstractTentackleMojo#installJavaLoggingHandler}) which would reset the log
 * configuration of the whole test JVM.
 * <p>
 * The annotation processing itself is <em>not</em> stubbed: the tests compile real java sources
 * with the real {@code javac} and the mojo's own processors, since the scanning of the syntax
 * tree is what these mojos are about. The sources are written to a temporary directory per test.
 * <p>
 * The only thing that has to be replaced is {@code setMojoParameters()}, which casts
 * {@code getClass().getClassLoader()} to a {@link URLClassLoader}. That holds for the plugin's
 * plexus class realm at build time, but not in a surefire fork, where the class loader is the
 * application class loader. The test mojos below therefore build the processing class loader
 * themselves.
 */
public final class CheckTestSupport {

  private CheckTestSupport() {
  }


  /**
   * Injects a mojo parameter.<br>
   * The field is looked up along the class hierarchy, starting at the object's class.
   *
   * @param target the mojo
   * @param name the field name
   * @param value the value to inject, may be null
   */
  public static void setField(Object target, String name, Object value) {
    for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      Field field = declaredField(clazz, name);
      if (field != null) {
        setField(field, target, value);
        return;
      }
    }
    throw new IllegalArgumentException("no such field '" + name + "' in " + target.getClass());
  }

  /**
   * Injects a field declared by a given class.<br>
   * Necessary because {@link AbstractCheckMojo} <em>shadows</em> the {@code sourceDir} and
   * {@code classpathElements} fields of {@link AbstractTentackleAnnotationProcessingMojo}: the
   * former are the mojo parameters, the latter the ones the processing actually uses.
   *
   * @param declaringClass the class declaring the field
   * @param target the mojo
   * @param name the field name
   * @param value the value to inject, may be null
   */
  public static void setField(Class<?> declaringClass, Object target, String name, Object value) {
    Field field = declaredField(declaringClass, name);
    if (field == null) {
      throw new IllegalArgumentException("no such field '" + name + "' in " + declaringClass);
    }
    setField(field, target, value);
  }

  /**
   * Reads a field.
   *
   * @param target the object
   * @param name the field name
   * @param <T> the value type
   * @return the value
   */
  @SuppressWarnings("unchecked")
  public static <T> T getField(Object target, String name) {
    for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      Field field = declaredField(clazz, name);
      if (field != null) {
        try {
          field.setAccessible(true);
          return (T) field.get(target);
        }
        catch (IllegalAccessException ex) {
          throw new IllegalStateException("cannot read field '" + name + "'", ex);
        }
      }
    }
    throw new IllegalArgumentException("no such field '" + name + "' in " + target.getClass());
  }

  /**
   * Invokes {@code AbstractTentackleMojo#validate}.<br>
   * Necessary because the method is protected and declared in another package.
   *
   * @param mojo the mojo
   * @return true if execution should continue
   * @throws Exception whatever the validation threw
   */
  public static boolean callValidate(Object mojo) throws Exception {
    for (Class<?> clazz = mojo.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      Method method;
      try {
        method = clazz.getDeclaredMethod("validate");
      }
      catch (NoSuchMethodException ex) {
        continue;
      }
      method.setAccessible(true);
      try {
        return (Boolean) method.invoke(mojo);
      }
      catch (InvocationTargetException ex) {
        if (ex.getCause() instanceof Exception cause) {
          throw cause;
        }
        throw ex;
      }
    }
    throw new IllegalArgumentException("no validate() in " + mojo.getClass());
  }


  /**
   * Creates a maven project for a given basedir.<br>
   * {@code MavenProject#getBasedir} is derived from the pom file, which need not exist.
   *
   * @param basedir the project's base directory
   * @return the project
   */
  public static MavenProject createProject(File basedir) {
    MavenProject project = new MavenProject();
    project.setFile(new File(basedir, "pom.xml"));
    project.getBuild().setDirectory(new File(basedir, "target").getPath());
    return project;
  }

  /**
   * Gets the classpath the tests themselves run with, plus extra elements.<br>
   * The processed sources are compiled against it, so everything the test classpath provides -
   * notably the tentackle validation annotations - can be referenced by them.
   *
   * @param extraElements directories or jars to prepend
   * @return the classpath elements
   */
  public static List<String> classpathElements(File... extraElements) {
    List<String> elements = new ArrayList<>();
    for (File extraElement : extraElements) {
      elements.add(extraElement.getPath());
    }
    String classPath = System.getProperty("java.class.path");
    if (classPath != null) {
      for (String element : classPath.split(File.pathSeparator)) {
        if (!element.isEmpty()) {
          elements.add(element);
        }
      }
    }
    return elements;
  }

  /**
   * Creates the classloader the mojos use to load bundles, validators and scripts.
   *
   * @param classpathElements the classpath elements
   * @return the classloader
   * @throws MojoFailureException if a classpath element is malformed
   */
  public static ClassLoader createProcessingClassLoader(List<String> classpathElements) throws MojoFailureException {
    try {
      // ProjectClassLoader insists on a URLClassLoader parent, which it only uses as the parent
      return new ProjectClassLoader(new URLClassLoader(new URL[0], CheckTestSupport.class.getClassLoader()),
                                    classpathElements);
    }
    catch (MalformedURLException mx) {
      throw new MojoFailureException("cannot create project classloader", mx);
    }
  }


  /**
   * Compiles the sources of a directory.
   * <p>
   * Both goals run in the {@code process-classes} phase, i.e. against a project that has already
   * been compiled - the validations goal even loads the annotated classes. The tests therefore
   * have to produce the class files the mojo expects to find on the project classpath.
   *
   * @param sourceDir the directory holding the sources
   * @param classesDir the directory to compile into, which must be on the classpath
   * @param classpathElements the classpath to compile against
   * @throws IllegalStateException if the sources did not compile
   */
  public static void compile(File sourceDir, File classesDir, List<String> classpathElements) {
    List<String> sources = new ArrayList<>();
    collectSources(sourceDir, sources);
    if (sources.isEmpty()) {
      return;
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      List<String> options = new ArrayList<>(List.of("-d", classesDir.getPath(),
                                                     "-proc:none",    // the mojo does the processing
                                                     "-classpath", String.join(File.pathSeparator, classpathElements)));
      boolean success = compiler.getTask(null, fileManager, diagnostics, options, null,
                                         fileManager.getJavaFileObjects(sources.toArray(new String[0]))).call();
      if (!success) {
        throw new IllegalStateException("cannot compile the test sources: " + diagnostics.getDiagnostics());
      }
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static void collectSources(File dir, List<String> sources) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          collectSources(file, sources);
        }
        else if (file.getName().endsWith(".java")) {
          sources.add(file.getPath());
        }
      }
    }
  }


  /**
   * Creates a directory below the given parent.
   *
   * @param parent the parent directory
   * @param name the name of the directory, slash-separated
   * @return the created directory
   */
  public static File createDir(File parent, String name) {
    File dir = new File(parent, name.replace('/', File.separatorChar));
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IllegalStateException("cannot create directory " + dir);
    }
    return dir;
  }

  /**
   * Creates a temporary directory.
   *
   * @param prefix the directory name prefix
   * @return the directory
   */
  public static File createTempDir(String prefix) {
    try {
      return Files.createTempDirectory(prefix).toFile();
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * Removes a directory and all its contents.
   *
   * @param dir the directory, may be null or non-existing
   */
  public static void deleteRecursively(File dir) {
    if (dir != null && dir.exists()) {
      try (var paths = Files.walk(dir.toPath())) {
        paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
      catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  /**
   * Creates a file along with all its missing parent directories.
   *
   * @param dir the base directory
   * @param relativePath the path relative to the base directory, slash-separated
   * @param contents the file contents
   * @return the created file
   */
  public static File writeFile(File dir, String relativePath, String contents) {
    File file = new File(dir, relativePath.replace('/', File.separatorChar));
    File parent = file.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      throw new IllegalStateException("cannot create directory " + parent);
    }
    try {
      Files.writeString(file.toPath(), contents);
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return file;
  }


  private static Field declaredField(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    }
    catch (NoSuchFieldException ex) {
      return null;
    }
  }

  private static void setField(Field field, Object target, Object value) {
    try {
      field.setAccessible(true);
      field.set(target, value);
    }
    catch (IllegalAccessException ex) {
      throw new IllegalStateException("cannot set field '" + field.getName() + "'", ex);
    }
  }


  /**
   * A bundles mojo that builds its processing classloader without the class realm.
   */
  public static class TestBundlesMojo extends CheckBundlesMojo {

    @Override
    public void setMojoParameters(File sourceDir, List<String> classpathElements) throws MojoFailureException {
      injectMojoParameters(this, sourceDir, classpathElements);
    }
  }


  /**
   * A validations mojo that builds its processing classloader without the class realm.
   */
  public static class TestValidationsMojo extends CheckValidationsMojo {

    @Override
    public void setMojoParameters(File sourceDir, List<String> classpathElements) throws MojoFailureException {
      injectMojoParameters(this, sourceDir, classpathElements);
    }
  }


  /**
   * A plain check mojo, i.e. one without any processor of its own.<br>
   * Used to test the shared configuration handling.
   */
  public static class TestCheckMojo extends AbstractCheckMojo {

    @Override
    public void setMojoParameters(File sourceDir, List<String> classpathElements) throws MojoFailureException {
      injectMojoParameters(this, sourceDir, classpathElements);
    }
  }


  /**
   * Does what {@code setMojoParameters()} does, but without casting the current classloader.
   *
   * @param mojo the mojo
   * @param sourceDir the directory holding the sources to be processed
   * @param classpathElements the project classpath
   * @throws MojoFailureException if the classloader could not be created
   */
  private static void injectMojoParameters(AbstractCheckMojo mojo, File sourceDir, List<String> classpathElements)
         throws MojoFailureException {
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "sourceDir", sourceDir);
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "classpathElements", classpathElements);
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "processingClassloader",
             createProcessingClassLoader(classpathElements));
  }


  /**
   * A maven log recording everything written to it.<br>
   * The mojos report each finding via the log and only the total via the exception, so this is
   * the only way to assert what exactly they complained about.
   */
  public static class RecordingLog extends LegacyAbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> debugMessages = new ArrayList<>();
    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> warnMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    private boolean debugEnabled;

    /**
     * Turns on maven's global debug flag.<br>
     * Without an explicit {@code verbosity} parameter this makes the mojos run with
     * {@code Verbosity.DEBUG}.
     *
     * @param debugEnabled true if debug enabled
     */
    public void setDebugEnabled(boolean debugEnabled) {
      this.debugEnabled = debugEnabled;
    }

    /**
     * Gets the debug messages.
     *
     * @return the messages
     */
    public List<String> getDebugMessages() {
      return debugMessages;
    }

    /**
     * Gets the info messages.
     *
     * @return the messages
     */
    public List<String> getInfoMessages() {
      return infoMessages;
    }

    /**
     * Gets the warn messages.
     *
     * @return the messages
     */
    public List<String> getWarnMessages() {
      return warnMessages;
    }

    /**
     * Gets the error messages.
     *
     * @return the messages
     */
    public List<String> getErrorMessages() {
      return errorMessages;
    }

    /**
     * Checks whether any message of the given list contains a text.
     *
     * @param messages the messages
     * @param text the text to look for
     * @return true if found
     */
    public static boolean contains(List<String> messages, String text) {
      return messages.stream().anyMatch(message -> message.contains(text));
    }

    @Override
    public boolean isTraceEnabled() {
      return false;
    }

    @Override
    public boolean isDebugEnabled() {
      return debugEnabled;
    }

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public boolean isWarnEnabled() {
      return true;
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public String getName() {
      return "recording";
    }

    /**
     * Records debug messages regardless of {@link #isDebugEnabled()}, which only reflects maven's global debug flag.
     */
    @Override
    public void debug(String msg) {
      debugMessages.add(msg);
    }

    @Override
    public void debug(String msg, Throwable t) {
      debugMessages.add(msg);
    }

    @Override
    public void debug(String format, Object arg) {
      debugMessages.add(MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
      debugMessages.add(MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void debug(String format, Object... arguments) {
      debugMessages.add(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    protected String getFullyQualifiedCallerName() {
      return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
                                               Object[] arguments, Throwable throwable) {
      String message = MessageFormatter.arrayFormat(messagePattern, arguments, throwable).getMessage();
      switch (level) {
        case DEBUG -> debugMessages.add(message);
        case INFO -> infoMessages.add(message);
        case WARN -> warnMessages.add(message);
        case ERROR -> errorMessages.add(message);
        default -> { }
      }
    }
  }

}
