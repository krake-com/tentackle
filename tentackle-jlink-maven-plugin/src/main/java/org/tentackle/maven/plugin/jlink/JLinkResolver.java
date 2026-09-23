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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.eclipse.aether.artifact.Artifact;

import org.tentackle.common.LayerBootstrap;
import org.tentackle.common.StringHelper;
import org.tentackle.common.ToolRunner;
import org.tentackle.maven.ProjectDependencies;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Determines the strategy how to invoke jlink.
 */
public class JLinkResolver {

  /**
   * Jdeps output cache.
   * Speeds up the build process if multiple images are created within the same maven run.
   */
  private static final Map<Artifact, List<String>> JDEPS_MAP = new ConcurrentHashMap<>();

  /**
   * Name of the module hosting {@link LayerBootstrap}.<br>
   * Never diverted to the leaf layer's own module-path (see {@link #addToModuleOrClasspath}), since it
   * is exactly what boots the leaf layer's {@code ModuleLayer} in the first place.
   * <p>
   * Deliberately a literal, not {@code LayerBootstrap.class.getModule().getName()}: this plugin itself
   * runs inside Maven's own (non-modular) plugin classloader, where that reflective lookup would not
   * reliably reflect tentackle-common's real module name.
   */
  private static final String BOOTSTRAP_MODULE_NAME = "org.tentackle.common";


  /**
   * Holds the resolver results.
   */
  public class Result {

    private final List<ModularArtifact> jlinkModules;
    private final List<String> runtimeModuleNames;
    private final Set<ModularArtifact> modulePath;
    private final Set<ModularArtifact> leafModulePath;
    private final Set<Artifact> classPath;
    private final Set<String> extraClassPathElements;
    private boolean modular;
    private boolean isPlainModular;
    private String imagePathPrefix;

    public Result() {
      jlinkModules = new ArrayList<>();
      runtimeModuleNames = new ArrayList<>();
      modulePath = new LinkedHashSet<>();
      leafModulePath = new LinkedHashSet<>();
      classPath = new LinkedHashSet<>();
      extraClassPathElements = new LinkedHashSet<>();
    }

    /**
     * Gets the prefix to the runtime image path.
     *
     * @return the path, empty if jlink image, else path to runtime image
     */
    public String getImagePathPrefix() {
      if (imagePathPrefix == null) {
        imagePathPrefix = mojo.getImagePathPrefix();
        if (!imagePathPrefix.isEmpty() && !imagePathPrefix.endsWith("/")) {
          imagePathPrefix += "/";
        }
      }
      return imagePathPrefix;
    }

    /**
     * Gets the modules to be passed to jlink.
     *
     * @return the modules
     */
    public List<ModularArtifact> getJlinkModules() {
      return jlinkModules;
    }

    /**
     * Gets the modules to add to the module path explicitly.
     *
     * @return the modules not passed to jlink
     */
    public List<ModularArtifact> getModulePath() {
      return new ArrayList<>(modulePath);
    }

    /**
     * Gets the artifacts to add to the class path explicitly.
     *
     * @return the class path
     */
    public List<Artifact> getClassPath() {
      return new ArrayList<>(classPath);
    }

    /**
     * Gets the modules that must not be part of the boot layer's own module-path, because a layered
     * application's leaf layer (see {@link AbstractJLinkMojo#isLayered()}) needs to resolve them itself,
     * independently, inside a child {@code ModuleLayer} it may share with sibling layers holding a
     * different version of the same module. Always empty for an unlayered application.
     *
     * @return the leaf layer's own module-path, never shared with {@link #getModulePath()}
     */
    public List<ModularArtifact> getLeafModulePath() {
      return new ArrayList<>(leafModulePath);
    }

    private void addJlinkModules(Collection<ModularArtifact> modules) {
      jlinkModules.addAll(modules);
    }

    private void addRuntimeModuleNames(Collection<String> moduleNames) {
      runtimeModuleNames.addAll(moduleNames);
    }

    private void addToModulePath(ModularArtifact module) {
      modulePath.add(module);
    }

    private void addToLeafModulePath(ModularArtifact module) {
      leafModulePath.add(module);
    }

    private void addToClassPath(Artifact artifact) {
      classPath.add(artifact);
    }

    private void setModular(boolean modular) {
      this.modular = modular;
    }

    /**
     * Returns whether this is a modular application.
     *
     * @return true if modular
     * @see #isPlainModular()
     */
    public boolean isModular() {
      return modular;
    }

    private void setPlainModular(boolean plainModular) {
      isPlainModular = plainModular;
    }

    /**
     * Returns whether this is a plain modular application.<br>
     * Plain modular means that real modules meet all requirements.
     * If not plain, the jlink tool is used to build a runtime image only
     * and the application modules are placed on the module-path explicitly.
     *
     * @return true if plain modular, else mixed modular or classpath application
     * @see #isModular()
     */
    public boolean isPlainModular() {
      return isPlainModular;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      if (!runtimeModuleNames.isEmpty()) {    // Jlink only used to create the runtime w/o application modules
        buf.append("runtime modules passed to jlink: ");
        boolean needComma = false;
        for (String name: runtimeModuleNames) {
          if (needComma) {
            buf.append(", ");
          }
          else {
            needComma = true;
          }
          buf.append(name);
        }
      }

      if (!jlinkModules.isEmpty()) {
        buf.append("application modules passed to jlink: ");
        boolean needComma = false;
        for (ModularArtifact artifact: jlinkModules) {
          if (needComma) {
            buf.append(", ");
          }
          else {
            needComma = true;
          }
          buf.append(artifact.getModuleName());
        }
      }

      if (!modulePath.isEmpty()) {
        buf.append("\nmodule-path passed to bin/java: ");
        boolean needComma = false;
        for (ModularArtifact artifact: modulePath) {
          if (needComma) {
            buf.append(", ");
          }
          else {
            needComma = true;
          }
          buf.append(artifact.getModuleName());
        }
      }

      if (!classPath.isEmpty()) {
        buf.append("\nclasspath passed to bin/java: ");
        boolean needComma = false;
        for (Artifact artifact: classPath) {
          if (needComma) {
            buf.append(", ");
          }
          else {
            needComma = true;
          }
          buf.append(ProjectDependencies.getFile(artifact).getName());
        }
      }
      return buf.toString();
    }

    /**
     * Adds additional elements to the classpath.<br>
     * Such as the conf directory.
     *
     * @param element the classpath element to add
     */
    public void addToClasspath(String element) {
      extraClassPathElements.add(element);
    }

    /**
     * Gets the additional elements added to the classpath.
     *
     * @return the extra classpath elements
     */
    public List<String> getExtraClassPathElements() {
      return new ArrayList<>(extraClassPathElements);
    }

    /**
     * Generates the module-path option to be passed to jlink.
     *
     * @param jlinkRunner the jlink tool runner
     */
    public void generateJlinkModulePath(ToolRunner jlinkRunner) {
      if (!jlinkModules.isEmpty()) {
        jlinkRunner.arg("--module-path");
        boolean needSep = false;
        StringBuilder buf = new StringBuilder();
        for (ModularArtifact module : jlinkModules) {
          if (needSep) {
            buf.append(File.pathSeparatorChar);
          }
          else {
            needSep = true;
          }
          buf.append(module.getPath());
        }
        jlinkRunner.arg(buf);
      }
    }

    /**
     * Generates the module names option passed to jlink.
     *
     * @param jlinkRunner the jlink tool runner
     */
    public void generateJlinkModules(ToolRunner jlinkRunner) {
      if (!runtimeModuleNames.isEmpty() || !jlinkModules.isEmpty()) {
        jlinkRunner.arg("--add-modules");
        boolean needComma = false;
        StringBuilder buf = new StringBuilder();
        for (String name : runtimeModuleNames) {
          if (needComma) {
            buf.append(',');
          }
          else {
            needComma = true;
          }
          buf.append(name);
        }
        for (ModularArtifact module : jlinkModules) {
          if (needComma) {
            buf.append(',');
          }
          else {
            needComma = true;
          }
          buf.append(module.getModuleName());
        }
        jlinkRunner.arg(buf);
      }
    }

    /**
     * Returns whether the module path is not empty.
     *
     * @return true if the module-path must be configured
     */
    public boolean isModulePathRequired() {
      return modular && !modulePath.isEmpty();
    }

    /**
     * Generates the module path option to be passed to bin/java.
     *
     * @return the option, empty if no extra module path
     */
    public String generateModulePath() {
      StringBuilder buf = new StringBuilder();
      if (modular) {
        boolean needSep = false;
        for (ModularArtifact artifact : modulePath) {
          if (needSep) {
            buf.append(File.pathSeparatorChar);
          }
          else {
            needSep = true;
          }
          buf.append(getImagePathPrefix()).append(AbstractJLinkMojo.DEST_MODULEPATH).append('/').append(artifact.getFileName());
        }
      }
      return buf.toString();
    }

    /**
     * Gets a modular artifact by its name.
     *
     * @param moduleName the module name
     * @return the artifact, null if no such artifact
     */
    public ModularArtifact getModuleArtifact(String moduleName) {
      if (modular) {
        for (ModularArtifact artifact : modulePath) {
          if (artifact.getModuleName().equals(moduleName)) {
            return artifact;
          }
        }
        for (ModularArtifact artifact : jlinkModules) {
          if (artifact.getModuleName().equals(moduleName)) {
            return artifact;
          }
        }
      }
      return null;
    }

    /**
     * Returns whether the class path is not empty.
     *
     * @return true if the classpath must be configured
     */
    public boolean isClassPathRequired() {
      return !extraClassPathElements.isEmpty() || !classPath.isEmpty();
    }

    /**
     * Generates the class path option to be passed to bin/java.
     *
     * @return the option, empty if no extra class path
     */
    public String generateClassPath() {
      StringBuilder buf = new StringBuilder();
      boolean needSep = false;
      String prefix = getImagePathPrefix();
      for (String element : extraClassPathElements) {
        if (needSep) {
          buf.append(File.pathSeparatorChar);
        }
        else {
          needSep = true;
        }
        if (".".equals(element) && !prefix.isEmpty()) {
          // translate dot prefixed by a directory to the directory alone (without the trailing slash)
          buf.append(prefix, 0, prefix.length() - 1);
        }
        else {
          buf.append(prefix).append(element);
        }
      }
      for (Artifact artifact : classPath) {
        if (needSep) {
          buf.append(File.pathSeparatorChar);
        }
        else {
          needSep = true;
        }
        buf.append(prefix).append(AbstractJLinkMojo.DEST_CLASSPATH).append('/')
           .append(ProjectDependencies.getFile(artifact).getName());
      }
      return buf.toString();
    }
  }



  private final Set<Artifact> artifacts;                            // project's dependencies (resolved artifacts)
  private final AbstractJLinkMojo mojo;                             // the jlink mojo

  private final Map<String, ModularArtifact> automaticModules;      // <module-name>:<artifact>
  private final Map<String, ModularArtifact> modules;               // <module-name>:<artifact>
  private final Set<String> requiredModules;                        // all required application modules
  private final Set<String> runtimeModules;                         // all required java runtime modules

  /**
   * Creates a resolver.
   *
   * @param mojo the base mojo
   * @param artifacts the resolved artifacts
   */
  public JLinkResolver(AbstractJLinkMojo mojo, Set<Artifact> artifacts) {
    this.artifacts = filterArtifacts(artifacts);
    this.mojo = mojo;

    automaticModules = new LinkedHashMap<>();
    modules = new LinkedHashMap<>();
    requiredModules = new HashSet<>();
    runtimeModules = new HashSet<>();
  }

  /**
   * Resolves the artifacts for passing to jlink.
   *
   * @return the resolver result
   * @throws MojoExecutionException if loading artifacts failed
   */
  public Result resolve() throws MojoExecutionException {

    Result result = new Result();

    for (Map.Entry<Artifact, JavaModuleDescriptor> entry: loadArtifacts().entrySet()) {
      Artifact artifact = entry.getKey();
      JavaModuleDescriptor moduleDescriptor = entry.getValue();
      String moduleName = moduleDescriptor.name();

      if (isEmptyFxDummyArtifact(moduleDescriptor)) {
        mojo.getLogger().debug("{} is an automatic dummy javafx module -> skipped", moduleName);
      }
      else if (moduleDescriptor.isAutomatic()) {
        mojo.getLogger().debug("{} is an automatic module", moduleName);
        automaticModules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
      }
      else if (mojo.isClasspathDependency(artifact)) {
        mojo.getLogger().debug("full-blown module {} treated as an automatic module", moduleName);
        // will be logged again in addToModuleOrClasspath below
        automaticModules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
      }
      else if (mojo.isModulePathOnly(moduleName)) {
        mojo.getLogger().info("full-blown module {} moved to the module-path", moduleName);
        automaticModules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
        if (mojo.isLayered() && moduleName.equals(mojo.getMainModule())) {
          // the main module is deferred off the jlink path (see AbstractJLinkMojo#isModulePathOnly),
          // but its own requirements still need to be part of the closure - just not itself, so that
          // its deferral alone does not force the rest of an otherwise plain-modular app into mixed mode
          for (JavaModuleDescriptor.JavaRequires requires : moduleDescriptor.requires()) {
            if (requires.modifiers() == null || !requires.modifiers().contains(JavaModuleDescriptor.JavaRequires.JavaModifier.STATIC)) {
              addRequired(requires.name());
              mojo.getLogger().debug("{} requires {}", moduleName, requires.name());
            }
          }
        }
      }
      else {
        mojo.getLogger().debug("{} is a full-blown module", moduleName);
        modules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
        addRequired(moduleName);
        for (JavaModuleDescriptor.JavaRequires requires : moduleDescriptor.requires()) {
          if (requires.modifiers() == null || !requires.modifiers().contains(JavaModuleDescriptor.JavaRequires.JavaModifier.STATIC)) {
            addRequired(requires.name());
            mojo.getLogger().debug("{} requires {}", moduleName, requires.name());
          }
        }
      }
    }

    // move module requirements that start with java. or jdk. back from the runtimeModules to the requiredModules
    // those are old school modules (like java.mail) that are not runtime modules but simple dependencies
    for (Iterator<String> iter = runtimeModules.iterator(); iter.hasNext(); ) {
      String moduleName = iter.next();
      if (automaticModules.get(moduleName) != null || modules.get(moduleName) != null) {
        iter.remove();
        requiredModules.add(moduleName);
      }
    }

    result.setModular(isModularApplication());

    if (result.isModular()) {
      mojo.getLogger().debug("building a modular application");
      // check if real modules meet all requirements.
      // this is the only way to use jlink for application modules.
      Set<ModularArtifact> unresolvedModules = new LinkedHashSet<>();
      for (String requiredModule : requiredModules) {
        if (modules.get(requiredModule) == null) {
          ModularArtifact artifact = automaticModules.get(requiredModule);
          if (artifact == null) {
            throw new MojoExecutionException("required module not found in dependencies: " + requiredModule);
          }
          unresolvedModules.add(artifact);
        }
      }

      if (unresolvedModules.isEmpty() && !mojo.isModulePathOnly()) {
        mojo.getLogger().debug("YEAH! can build a full-blown modular application!");
        result.addJlinkModules(modules.values());
        result.setPlainModular(true);
      }
      else {
        Boolean failOnAutomaticRequired = mojo.getFailOnAutomaticRequired();
        boolean failDueToAutomaticRequired = false;
        for (ModularArtifact unresolvedModule: unresolvedModules) {
          if (mojo.isClasspathDependency(unresolvedModule.getArtifact())) {
            // moved to the classpath explicitly (already moved to the automatic modules, see above)
            result.addToClassPath(unresolvedModule.getArtifact());
          }
          else {
            String msg = "required " + unresolvedModule + " is not a full-blown module";
            if (Boolean.TRUE.equals(failOnAutomaticRequired)) {
              failDueToAutomaticRequired = true;
              mojo.getLogger().error(msg);
            }
            else {
              if (failOnAutomaticRequired == null) {
                mojo.getLogger().warn(msg);
              }
              else {
                mojo.getLogger().info(msg);
              }
            }
            if (mojo.isLayered()) {
              // must not share the boot layer's module-path with the leaf layer's own ModuleLayer, see addToModuleOrClasspath
              result.addToLeafModulePath(unresolvedModule);
            }
            else {
              result.addToModulePath(unresolvedModule);   // must go to the module path!
            }
          }
        }
        if (failDueToAutomaticRequired) {
          throw new MojoExecutionException("required automatic modules found");
        }
        mojo.getLogger().warn("linking runtime modules only: project dependencies moved to the module-path");
        result.addRuntimeModuleNames(runtimeModules);
        for (ModularArtifact artifact: modules.values()) {
          addToModuleOrClasspath(artifact, result);
        }
      }
    }
    else {
      mojo.getLogger().debug("building a classpath application");
      result.addRuntimeModuleNames(runtimeModules);
      for (Artifact artifact: artifacts) {
        result.addToClassPath(artifact);
      }
    }

    if (mojo.isLayered()) {
      // the main module was deliberately forced into automaticModules (see AbstractJLinkMojo#isModulePathOnly)
      // so it never gets linked into the jimage; neither branch above adds it (isPlainModular only jlinks
      // modules.values(), and the mixed-mode branch only iterates modules.values() as well), and the
      // "not referenced by anyone" loop below explicitly excludes the main module too - so it is added
      // here, exactly once, routed to the leaf layer's own module-path by addToModuleOrClasspath.
      ModularArtifact leafModule = automaticModules.get(mojo.getMainModule());
      if (leafModule == null) {
        throw new MojoExecutionException("layered application: main module '" + mojo.getMainModule() + "' not found");
      }
      addToModuleOrClasspath(leafModule, result);
    }

    // add modules not referenced by anyone
    List<ModularArtifact> allModules = new ArrayList<>();
    allModules.addAll(automaticModules.values());    // automatic modules come first because they may provide services
    allModules.addAll(modules.values());
    for (ModularArtifact artifact: allModules) {
      String moduleName = artifact.getModuleName();
      if (!moduleName.equals(mojo.getMainModule()) && !requiredModules.contains(moduleName)) {
        addToModuleOrClasspath(artifact, result);
      }
    }

    // add explicit additional modules, such as jdk.jcmd
    result.addRuntimeModuleNames(mojo.getAddModules());

    // add explicit extra classpath elements
    if (mojo.getExtraClasspathElements() != null) {
      for (String element: mojo.getExtraClasspathElements()) {
        result.addToClasspath(element);
      }
    }

    return result;
  }


  private Set<Artifact> filterArtifacts(Set<Artifact> artifacts) {
    Set<Artifact> filteredArtifacts = new LinkedHashSet<>();    // keep order
    for (Artifact artifact : artifacts) {
      if (!"pom".equals(artifact.getExtension())) {
        // skip BOM/POM files (their deps are included in the artifact list)
        filteredArtifacts.add(artifact);
      }
    }
    return filteredArtifacts;
  }

  private Map<Artifact, JavaModuleDescriptor> loadArtifacts() throws MojoExecutionException {
    // build file to the artifact map
    Map<File, Artifact> artifactMap = new LinkedHashMap<>();  // keep order
    for (Artifact artifact: artifacts) {
      File artifactFile = ProjectDependencies.getFile(artifact);
      artifactMap.put(artifactFile, artifact);
    }

    // resolve modules and automatic modules
    ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(artifactMap.keySet());
    Toolchain toolchain = mojo.getToolchain();
    try {
      File javaHomeDir = mojo.getJavaHome(toolchain);
      if (javaHomeDir != null) {
        request.setJdkHome(javaHomeDir);
      }
    }
    catch (MojoExecutionException mx) {
      // don't abort, just continue with the current JDK and hope for the best
      mojo.getLogger().warn(mx.getMessage());
    }

    Map<Artifact, JavaModuleDescriptor> artifactJavaModuleDescriptorMap = new LinkedHashMap<>();    // keep order
    int maxNameLength = 0;
    try {
      for (Map.Entry<File, JavaModuleDescriptor> entry: mojo.getLocationManager().resolvePaths(request).getPathElements().entrySet()) {
        File file = entry.getKey();
        JavaModuleDescriptor descriptor = entry.getValue();
        if (descriptor == null) {
          // there are cases that the descriptor is null for old artifacts with Java > 11.0.2
          String name = mojo.toDescriptorName(file);
          mojo.getLogger().warn("missing descriptor for {} -> creating default descriptor '{}'", file, name);
          descriptor = JavaModuleDescriptor.newAutomaticModule(name).build();
        }
        if (descriptor.name() != null && descriptor.name().length() > maxNameLength) {
          maxNameLength = descriptor.name().length();
        }
        artifactJavaModuleDescriptorMap.put(artifactMap.get(file), descriptor);
      }
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot resolve paths", e);
    }

    if (maxNameLength == 0) {
      maxNameLength = 1;    // a zero column width is illegal in a format string (see below)
    }

    // run jdeps on non-JPMS modules
    for (Artifact artifact: artifacts) {
      JavaModuleDescriptor moduleDescriptor = artifactJavaModuleDescriptorMap.get(artifact);
      if (moduleDescriptor != null) {
        if (isEmptyFxDummyArtifact(moduleDescriptor)) {
          mojo.getLogger().debug("empty dummy FX artifact {} skipped", moduleDescriptor.name());
        }
        else {
          if (moduleDescriptor.isAutomatic()) {
            mojo.getLogger().info(String.format("automatic module   %-" + maxNameLength + "s  %s", moduleDescriptor.name(), artifact));
            runJdeps(artifact);
          }
          else {
            mojo.getLogger().info(String.format("full-blown module  %-" + maxNameLength + "s  %s", moduleDescriptor.name(), artifact));
          }
        }
      }
      else {
        mojo.getLogger().info(String.format("artifact           %-" + maxNameLength + "s  %s", "", artifact));
        runJdeps(artifact);
      }
    }

    return artifactJavaModuleDescriptorMap;
  }

  private boolean isModularApplication() throws MojoExecutionException {
    if (!StringHelper.isAllWhitespace(mojo.getMainModule())) {
      if (modules.get(mojo.getMainModule()) != null) {
        return true;
      }
      if (mojo.isLayered() && automaticModules.get(mojo.getMainModule()) != null) {
        // deliberately forced off the plain-modular jlink path (see AbstractJLinkMojo#isModulePathOnly),
        // so ModuleLayerBuilder can find it later as a loose file, not baked into the jimage
        return true;
      }
      throw new MojoExecutionException("no such main module: " + mojo.getMainModule());
    }
    return false;
  }

  private void runJdeps(Artifact artifact) throws MojoExecutionException {
    List<String> lines = JDEPS_MAP.get(artifact);   // no computeIfAbsent due to checked exception

    if (lines == null) {
      ToolRunner runner = new ToolRunner(mojo.getJdepsTool()).arg("--list-deps").arg("--ignore-missing-deps")
                                                             .arg(ProjectDependencies.getFile(artifact)).run();
      int errCode = runner.getErrCode();
      if (errCode != 0) {
        if (runner.getOutputAsString().contains("multi-release")) {
          runner = new ToolRunner(mojo.getJdepsTool()).arg("--list-deps").arg("--ignore-missing-deps")
                                                      .arg("--multi-release").arg(mojo.getJavaMajorRuntimeVersion())
                                                      .arg(ProjectDependencies.getFile(artifact)).run();
          errCode = runner.getErrCode();
        }
        if (errCode != 0) {
          throw new MojoExecutionException(runner + " failed for " + artifact +
                                           "\nerror code: " + errCode +
                                           "\nstdout: " + runner.getOutputAsString() +
                                           "\nstderr: " + runner.getErrorsAsString());
        }
      }
      lines = runner.getOutput();
      JDEPS_MAP.put(artifact, lines);
    }

    for (String line : lines) {
      line = line.trim();
      if (!line.isEmpty() && !line.contains(" ")) {   // a valid module name (no "not found" and such)
        mojo.getLogger().debug("{} (non-module): {}", artifact, line);
        addRequired(line);
      }
    }
  }

  private void addRequired(String moduleName) {
    // remove optional package name, if any
    int ndx = moduleName.indexOf('/');
    if (ndx > 0) {
      moduleName = moduleName.substring(0, ndx);
    }

    if (mojo.getExcludeModules().contains(moduleName)) {
      mojo.getLogger().debug("module {} excluded", moduleName);
    }
    else {
      if (moduleName.startsWith("java.") || moduleName.startsWith("jdk.")) {
        // this is only a first guess.
        // if it turns out later that this is a dependency, it will be moved back to application artifacts
        runtimeModules.add(moduleName);
      }
      else {
        requiredModules.add(moduleName);
      }
    }
  }

  private void addToModuleOrClasspath(ModularArtifact artifact, Result result) {
    if (!result.isModular()) {
      result.addToClassPath(artifact.getArtifact());
    }
    else if (mojo.isClasspathDependency(artifact.getArtifact())) {
      mojo.getLogger().info("artifact {} moved to the class-path", artifact);
      result.addToClassPath(artifact.getArtifact());
    }
    else if (mojo.isLayered() && !BOOTSTRAP_MODULE_NAME.equals(artifact.getModuleName())) {
      // never share the boot layer's own module-path with the leaf layer's ModuleLayer (see ModuleLayerBuilder):
      // everything except the bootstrap module itself must be resolved independently by the leaf layer,
      // so that a sibling layer holding a different version of the same module stays possible
      mojo.getLogger().debug("artifact {} moved to the leaf layer's own module-path", artifact);
      result.addToLeafModulePath(artifact);
    }
    else {
      result.addToModulePath(artifact);
    }
  }

  private boolean isEmptyFxDummyArtifact(JavaModuleDescriptor moduleDescriptor) {
    return moduleDescriptor.isAutomatic() &&
           moduleDescriptor.name().startsWith("javafx.") && moduleDescriptor.name().endsWith("Empty");
  }

}
