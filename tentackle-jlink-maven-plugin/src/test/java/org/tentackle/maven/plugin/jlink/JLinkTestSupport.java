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

package org.tentackle.maven.plugin.jlink;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common helpers for the mojo tests.
 * <p>
 * As far as their logic is concerned, the mojos are plain objects: everything covered by the tests
 * in this package is a function of the mojo parameters, the project's artifacts and the filesystem.
 * The mojos are therefore instantiated directly and their {@code @Parameter}-fields injected the
 * same way maven's plexus container would do it - by reflection - instead of booting a container
 * via the maven-plugin-testing-harness.
 * <p>
 * The mojos are never invoked through {@code execute()}, because that also installs a global
 * {@code java.util.logging} handler (see {@code AbstractTentackleMojo#installJavaLoggingHandler})
 * which would reset the log configuration of the whole test JVM.
 * <p>
 * The two external tools are kept out of the tests altogether:
 * <ul>
 *   <li>{@code jdeps} is never forked, because {@link #seedJdepsCache} pre-fills the resolver's
 *       jdeps cache, and</li>
 *   <li>{@code jlink}/{@code jpackage} are never forked, because no test calls the mojo methods
 *       running them.</li>
 * </ul>
 * The JPMS descriptors of the dependencies are provided by the {@link FakeLocationManager}, so the
 * artifacts need not be real jar files either (except where a test copies them around).
 */
public final class JLinkTestSupport {

  private JLinkTestSupport() {
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
          return;
        }
        catch (IllegalAccessException ex) {
          throw new IllegalStateException("cannot set field '" + name + "'", ex);
        }
      }
    }
    throw new IllegalArgumentException("no such field '" + name + "' in " + target.getClass());
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
   * Invokes a non-public method.<br>
   * Necessary for the mojo internals that are not part of the API but carry logic worth testing,
   * such as {@code JPackageMojo#patchConfigFile}.
   *
   * @param target the object
   * @param name the method name
   * @param signature the parameter types
   * @param args the arguments
   * @param <T> the result type
   * @return the result, null for void methods
   * @throws Exception whatever the method threw
   */
  @SuppressWarnings("unchecked")
  public static <T> T invoke(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
    for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      Method method;
      try {
        method = clazz.getDeclaredMethod(name, signature);
      }
      catch (NoSuchMethodException ex) {
        continue;
      }
      method.setAccessible(true);
      try {
        return (T) method.invoke(target, args);
      }
      catch (InvocationTargetException ex) {
        if (ex.getCause() instanceof Exception cause) {
          throw cause;
        }
        throw ex;
      }
    }
    throw new IllegalArgumentException("no method '" + name + "' in " + target.getClass());
  }

  /**
   * Invokes {@code AbstractTentackleMojo#validate}.<br>
   * Necessary because the method is protected and declared in another package.
   *
   * @param mojo the mojo
   * @return true if execution should continue
   * @throws Exception whatever the validation threw
   */
  public static boolean callValidate(AbstractJLinkMojo mojo) throws Exception {
    return invoke(mojo, "validate", new Class<?>[0]);
  }


  /**
   * Creates a jar artifact.
   *
   * @param artifactId the artifact id
   * @param file the artifact's file, which need not exist
   * @return the artifact
   */
  public static Artifact createArtifact(String artifactId, File file) {
    return createArtifact("org.example", artifactId, "1.0", "jar", null, file);
  }

  /**
   * Creates an artifact.
   *
   * @param groupId the group id
   * @param artifactId the artifact id
   * @param version the version
   * @param type the artifact type, i.e. the file extension
   * @param classifier the classifier, may be null
   * @param file the artifact's file, which need not exist
   * @return the artifact
   */
  public static Artifact createArtifact(String groupId, String artifactId, String version,
                                        String type, String classifier, File file) {
    return new DefaultArtifact(groupId, artifactId, classifier, type, version, null, file);
  }

  /**
   * Creates a maven project.
   *
   * @param basedir the project's base directory
   * @return the project
   */
  public static MavenProject createProject(File basedir) {
    MavenProject project = new MavenProject();
    project.setFile(new File(basedir, "pom.xml"));
    project.setVersion("1.2.3-SNAPSHOT");
    project.getBuild().setDirectory(new File(basedir, "target").getPath());
    project.getBuild().setFinalName("app-1.2.3-SNAPSHOT");
    return project;
  }

  /**
   * Creates a mojo execution.
   *
   * @param goal the goal name
   * @param executionId the execution id
   * @param phase the lifecycle phase
   * @return the execution
   */
  public static MojoExecution createMojoExecution(String goal, String executionId, String phase) {
    MojoDescriptor descriptor = new MojoDescriptor();
    descriptor.setGoal(goal);
    MojoExecution execution = new MojoExecution(descriptor, executionId);
    execution.setLifecyclePhase(phase);
    return execution;
  }


  /**
   * Creates a directory below the given parent.
   *
   * @param parent the parent directory
   * @param name the name of the directory
   * @return the created directory
   */
  public static File createDir(File parent, String name) {
    File dir = new File(parent, name);
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

  /**
   * Lists the names of the files in a directory.
   *
   * @param dir the directory
   * @return the sorted names, empty if no such directory
   */
  public static List<String> fileNames(File dir) {
    String[] names = dir.list();
    List<String> fileNames = names == null ? new ArrayList<>() : new ArrayList<>(List.of(names));
    fileNames.sort(null);
    return fileNames;
  }


  /**
   * Pre-fills the resolver's jdeps cache for an artifact.<br>
   * Keeps {@link JLinkResolver} from forking the real {@code jdeps} tool for automatic modules
   * and non-modular artifacts.
   *
   * @param artifact the artifact
   * @param moduleNames the module names jdeps would report
   */
  public static void seedJdepsCache(Artifact artifact, String... moduleNames) {
    jdepsCache().put(artifact, List.of(moduleNames));
  }

  /**
   * Clears the resolver's jdeps cache.<br>
   * The cache is static and would otherwise leak between the tests.
   */
  public static void clearJdepsCache() {
    jdepsCache().clear();
  }

  @SuppressWarnings("unchecked")
  private static Map<Artifact, List<String>> jdepsCache() {
    try {
      Field field = JLinkResolver.class.getDeclaredField("JDEPS_MAP");
      field.setAccessible(true);
      return (Map<Artifact, List<String>>) field.get(null);
    }
    catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("cannot access the jdeps cache", ex);
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
   * A location manager serving canned module descriptors.<br>
   * Replaces the JPMS analysis of real jar files, which the resolver tests don't need: they are
   * about what the resolver makes of the descriptors, not about how the descriptors are read.
   */
  public static class FakeLocationManager extends LocationManager {

    private final Map<File, JavaModuleDescriptor> pathElements = new LinkedHashMap<>();

    /**
     * Adds a full-blown module.
     *
     * @param artifact the artifact
     * @param moduleName the module name
     * @param requires the required module names, prefix a name with {@code "static "} for a static requires
     * @return the artifact
     */
    public Artifact addModule(Artifact artifact, String moduleName, String... requires) {
      JavaModuleDescriptor.Builder builder = JavaModuleDescriptor.newModule(moduleName);
      for (String required : requires) {
        if (required.startsWith("static ")) {
          builder.requires(java.util.Set.of(JavaModuleDescriptor.JavaRequires.JavaModifier.STATIC),
                           required.substring("static ".length()));
        }
        else {
          builder.requires(required);
        }
      }
      pathElements.put(artifact.getFile(), builder.build());
      return artifact;
    }

    /**
     * Adds an automatic module.
     *
     * @param artifact the artifact
     * @param moduleName the module name
     * @return the artifact
     */
    public Artifact addAutomaticModule(Artifact artifact, String moduleName) {
      pathElements.put(artifact.getFile(), JavaModuleDescriptor.newAutomaticModule(moduleName).build());
      return artifact;
    }

    /**
     * Adds an artifact without any module descriptor.<br>
     * Reproduces the case of old artifacts the JPMS analysis cannot make sense of.
     *
     * @param artifact the artifact
     * @return the artifact
     */
    public Artifact addUnresolvable(Artifact artifact) {
      pathElements.put(artifact.getFile(), null);
      return artifact;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResolvePathsResult<T> resolvePaths(ResolvePathsRequest<T> request) {
      ResolvePathsResult<T> result = new ResolvePathsResult<>();
      try {
        Method method = ResolvePathsResult.class.getDeclaredMethod("setPathElements", Map.class);
        method.setAccessible(true);
        method.invoke(result, (Map<T, JavaModuleDescriptor>) pathElements);
      }
      catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("cannot fill in the path elements", ex);
      }
      return result;
    }
  }


  /**
   * A project helper recording the attached artifacts.
   */
  public static class RecordingProjectHelper implements MavenProjectHelper {

    /**
     * An attached artifact.
     *
     * @param type the artifact type
     * @param classifier the classifier
     * @param file the file
     */
    public record Attachment(String type, String classifier, File file) {
    }

    private final List<Attachment> attachments = new ArrayList<>();

    /**
     * Gets the attached artifacts in the order they were attached.
     *
     * @return the attachments
     */
    public List<Attachment> getAttachments() {
      return attachments;
    }

    @Override
    public void attachArtifact(MavenProject project, File artifactFile, String artifactClassifier) {
      attachments.add(new Attachment(null, artifactClassifier, artifactFile));
    }

    @Override
    public void attachArtifact(MavenProject project, String artifactType, File artifactFile) {
      attachments.add(new Attachment(artifactType, null, artifactFile));
    }

    @Override
    public void attachArtifact(MavenProject project, String artifactType, String artifactClassifier, File artifactFile) {
      attachments.add(new Attachment(artifactType, artifactClassifier, artifactFile));
    }

    @Override
    public void addResource(MavenProject project, String resourceDirectory, List<String> includes, List<String> excludes) {
    }

    @Override
    public void addTestResource(MavenProject project, String resourceDirectory, List<String> includes, List<String> excludes) {
    }
  }


  /**
   * A maven log recording everything written to it.<br>
   * The resolver reports most of its decisions via the log only, so this is the only way to
   * assert them.
   */
  public static class RecordingLog extends LegacyAbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> debugMessages = new ArrayList<>();
    @SuppressWarnings("serial")
    private final List<String> infoMessages = new ArrayList<>();
    @SuppressWarnings("serial")
    private final List<String> warnMessages = new ArrayList<>();
    @SuppressWarnings("serial")
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


  /**
   * A jlink mojo that keeps the toolchain and the java tools out of the tests.
   */
  public static class TestJLinkMojo extends JLinkMojo {

    @Override
    public Toolchain getToolchain() {
      return null;
    }

    @Override
    public String determineJavaToolVersion(File javaTool) {
      return "25";
    }
  }


  /**
   * A jpackage mojo that keeps the toolchain and the java tools out of the tests.
   */
  public static class TestJPackageMojo extends JPackageMojo {

    @Override
    public Toolchain getToolchain() {
      return null;
    }

    @Override
    public String determineJavaToolVersion(File javaTool) {
      return "25";
    }
  }


  /**
   * An init mojo that keeps the toolchain and the java tools out of the tests.
   */
  public static class TestInitMojo extends InitMojo {

    @Override
    public Toolchain getToolchain() {
      return null;
    }

    @Override
    public String determineJavaToolVersion(File javaTool) {
      return "25";
    }
  }

}
