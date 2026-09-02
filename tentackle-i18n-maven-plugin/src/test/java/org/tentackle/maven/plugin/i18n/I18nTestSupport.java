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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import org.tentackle.maven.ProjectClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Common helpers for the i18n mojo tests.
 * <p>
 * The mojos are instantiated directly and their {@code @Parameter}-fields injected the same way
 * maven's plexus container would do it &mdash; by reflection &mdash; instead of booting a container
 * via the maven-plugin-testing-harness. They are driven through {@code prepareExecute()},
 * {@code validate()} and {@code executeImpl()} rather than {@code execute()}, because the latter
 * also installs a global {@code java.util.logging} handler (see
 * {@code AbstractTentackleMojo#installJavaLoggingHandler}) which would reset the log configuration
 * of the whole test JVM.
 * <p>
 * Two things have to be replaced in the mojos, both provided by the test subclasses below:
 * <ul>
 *   <li>{@code createProjectClassLoader()} casts {@code getClass().getClassLoader()} to a
 *       {@link URLClassLoader}. That holds for the plugin's plexus class realm at build time, but
 *       not in a surefire fork, where the class loader is the application class loader.</li>
 *   <li>{@code isExecutionRecursive()} needs a {@code MavenSession} and a {@code MojoExecution},
 *       neither of which exists outside a running build. The tests always run against the single
 *       project they set up, which is what a recursive execution does.</li>
 * </ul>
 * Everything else is real: the bundles are registered through {@code META-INF/bundles} on a real
 * classpath and the mojos talk to a real (in-memory) database, see {@link AbstractI18nMojoTest}.
 *
 * @author harald
 */
public final class I18nTestSupport {

  private I18nTestSupport() {
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
        try {
          field.setAccessible(true);
          field.set(target, value);
        }
        catch (IllegalAccessException ex) {
          throw new IllegalStateException("cannot set field '" + name + "'", ex);
        }
        return;
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
   * Creates the maven project the mojos work on.<br>
   * {@code MavenProject#getBasedir} is derived from the pom file, which need not exist.
   * The resource directory is the one the {@code pull} goal writes the property files to,
   * the classes directory the one the bundles are loaded from.
   *
   * @param basedir the project's base directory
   * @param resourceDir the project's resource directory
   * @return the project
   */
  public static MavenProject createProject(File basedir, File resourceDir) {
    MavenProject project = new MavenProject();
    project.setFile(new File(basedir, "pom.xml"));
    project.getBuild().setDirectory(new File(basedir, "target").getPath());
    Resource resource = new Resource();
    resource.setDirectory(resourceDir.getPath());
    project.getBuild().addResource(resource);
    return project;
  }

  /**
   * Creates the classloader the mojos load the bundles from.
   * <p>
   * Deliberately cut off from the test classpath: the tentackle jars the tests themselves run
   * with register bundles of their own ({@code META-INF/bundles}), which the mojos would happily
   * process along with the ones of the temporary project. The classloader only has to serve
   * resources - no class is ever loaded through it - so the bootstrap loader is parent enough.
   *
   * @param classpathElements the classpath elements
   * @return the classloader
   * @throws MalformedURLException if a classpath element is malformed
   */
  public static ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
    // ProjectClassLoader insists on a URLClassLoader parent, which it only uses as the parent
    return new ProjectClassLoader(new URLClassLoader(new URL[0], null), classpathElements);
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

  /**
   * Reads a file.
   *
   * @param file the file
   * @return the contents
   */
  public static String readFile(File file) {
    try {
      return Files.readString(file.toPath());
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }


  private static Field declaredField(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    }
    catch (NoSuchFieldException ex) {
      return null;
    }
  }


  /**
   * A push mojo that runs outside a maven build.
   */
  public static class TestPushMojo extends PushMojo {

    @Override
    protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
      return I18nTestSupport.createProjectClassLoader(classpathElements);
    }

    @Override
    protected boolean isExecutionRecursive() {
      return true;
    }
  }


  /**
   * A pull mojo that runs outside a maven build.
   */
  public static class TestPullMojo extends PullMojo {

    @Override
    protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
      return I18nTestSupport.createProjectClassLoader(classpathElements);
    }

    @Override
    protected boolean isExecutionRecursive() {
      return true;
    }
  }


  /**
   * A verify mojo that runs outside a maven build.
   */
  public static class TestVerifyMojo extends VerifyMojo {

    @Override
    protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
      return I18nTestSupport.createProjectClassLoader(classpathElements);
    }

    @Override
    protected boolean isExecutionRecursive() {
      return true;
    }
  }


  /**
   * A cleanup mojo that runs outside a maven build.
   */
  public static class TestCleanupMojo extends CleanupMojo {

    @Override
    protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
      return I18nTestSupport.createProjectClassLoader(classpathElements);
    }

    @Override
    protected boolean isExecutionRecursive() {
      return true;
    }
  }


  /**
   * A maven log recording everything written to it.<br>
   * The mojos report each finding via the log and only the totals via the exception, so this is
   * the only way to assert what exactly they complained about.
   */
  public static class RecordingLog implements Log {

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
     * Checks whether any of the given messages contains a text.
     *
     * @param messages the messages
     * @param text the text to look for
     * @return true if found
     */
    public static boolean contains(List<String> messages, String text) {
      return messages.stream().anyMatch(message -> message.contains(text));
    }

    @Override
    public boolean isDebugEnabled() {
      return debugEnabled;
    }

    @Override
    public void debug(CharSequence content) {
      debugMessages.add(String.valueOf(content));
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
      debug(content);
    }

    @Override
    public void debug(Throwable error) {
      debug(error.getMessage());
    }

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public void info(CharSequence content) {
      infoMessages.add(String.valueOf(content));
    }

    @Override
    public void info(CharSequence content, Throwable error) {
      info(content);
    }

    @Override
    public void info(Throwable error) {
      info(error.getMessage());
    }

    @Override
    public boolean isWarnEnabled() {
      return true;
    }

    @Override
    public void warn(CharSequence content) {
      warnMessages.add(String.valueOf(content));
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
      warn(content);
    }

    @Override
    public void warn(Throwable error) {
      warn(error.getMessage());
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public void error(CharSequence content) {
      errorMessages.add(String.valueOf(content));
    }

    @Override
    public void error(CharSequence content, Throwable error) {
      error(content);
    }

    @Override
    public void error(Throwable error) {
      error(error.getMessage());
    }
  }

}
