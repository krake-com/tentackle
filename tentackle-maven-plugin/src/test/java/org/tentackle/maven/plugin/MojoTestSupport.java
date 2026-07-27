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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Common helpers for the mojo tests.
 * <p>
 * Mojos are plain objects as far as their logic is concerned: everything the tests below cover is
 * a function of the mojo parameters and the filesystem. They are therefore instantiated directly
 * and their {@code @Parameter}-fields are injected the same way Maven's plexus container would do
 * it - by reflection - instead of booting a container via the maven-plugin-testing-harness.
 * <p>
 * The mojos are invoked through {@code validate()} and {@code executeImpl()} rather than
 * {@code execute()}, because the latter also installs a global {@code java.util.logging} handler
 * (see {@code AbstractTentackleMojo#installJavaLoggingHandler}) which would reset the log
 * configuration of the whole test JVM.
 */
public final class MojoTestSupport {

  private MojoTestSupport() {
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
   * Injects a mojo parameter declared by a given class.<br>
   * Necessary whenever a field name is shadowed, i.e. the concrete mojo declares the
   * {@code @Parameter} and the abstract mojo the field it is copied to.
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
   * Reads a mojo field.
   *
   * @param target the mojo
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
   * Necessary for mojos not overriding it in this package, since the method is protected
   * and declared in another one.
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
    return project;
  }

  /**
   * Creates a jar artifact.
   *
   * @param groupId the group id
   * @param artifactId the artifact id
   * @param version the version
   * @return the artifact
   */
  public static Artifact createArtifact(String groupId, String artifactId, String version) {
    return new DefaultArtifact(groupId, artifactId, version, "compile", "jar", null,
                               new DefaultArtifactHandler("jar"));
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
   * Reads all lines of a file.
   *
   * @param file the file
   * @return the lines
   */
  public static List<String> readLines(File file) {
    try {
      return Files.readAllLines(file.toPath());
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * Reads a whole file.
   *
   * @param file the file
   * @return the contents
   */
  public static String readString(File file) {
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
   * A maven log recording everything written to it.<br>
   * Some mojos report their result via the log only, so this is the only way to assert it.
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
