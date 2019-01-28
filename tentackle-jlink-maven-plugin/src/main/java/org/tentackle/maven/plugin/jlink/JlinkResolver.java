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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;

import org.tentackle.maven.ToolRunner;

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

/**
 * Determines the strategy how to invoke jlink.
 */
public class JlinkResolver {

  /**
   * Holds the resolver results.
   */
  public static class Result {

    private final List<ModularArtifact> jlinkModules;
    private final List<String> runtimeModuleNames;
    private final List<ModularArtifact> modulePath;
    private final List<Artifact> classPath;
    private boolean confAddedToClasspath;
    private boolean modular;
    private boolean isPlainModular;

    public Result() {
      jlinkModules = new ArrayList<>();
      runtimeModuleNames = new ArrayList<>();
      modulePath = new ArrayList<>();
      classPath = new ArrayList<>();
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
      return modulePath;
    }

    /**
     * Gets the artifacts to add to the class path explicitly.
     *
     * @return the class path
     */
    public List<Artifact> getClassPath() {
      return classPath;
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
     * Plain modular means that all requirements are met by real modules.
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
        buf.append("\nmodule path: ");
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
        buf.append("\nclass path: ");
        boolean needComma = false;
        for (Artifact artifact: classPath) {
          if (needComma) {
            buf.append(", ");
          }
          else {
            needComma = true;
          }
          buf.append(artifact.getFile().getName());
        }
      }
      return buf.toString();
    }

    /**
     * Gets whether the conf directory should be added to the classpath.
     *
     * @return true to add
     */
    public boolean isConfAddedToClasspath() {
      return confAddedToClasspath;
    }

    /**
     * Sets whether the conf directory should be added to the classpath.
     *
     * @param confAddedToClasspath true to add
     */
    public void setConfAddedToClasspath(boolean confAddedToClasspath) {
      this.confAddedToClasspath = confAddedToClasspath;
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
          buf.append("mp").append(File.separatorChar).append(artifact.getFileName());
        }
      }
      return buf.toString();
    }

    /**
     * Generates the class path option to be passed to bin/java.
     *
     * @return the option, empty if no extra class path
     */
    public String generateClassPath() {
      StringBuilder buf = new StringBuilder();
      boolean needSep = false;
      if (confAddedToClasspath) {
        buf.append("conf");
        needSep = true;
      }
      for (Artifact artifact : classPath) {
        if (needSep) {
          buf.append(File.pathSeparatorChar);
        }
        else {
          needSep = true;
        }
        buf.append("cp").append(File.separatorChar).append(artifact.getFile().getName());
      }
      return buf.toString();
    }
  }



  private final Set<Artifact> artifacts;
  private final AbstractJlinkMojo mojo;

  private final Map<String, ModularArtifact> automaticModules;      // <module-name>:<artifact>
  private final Map<String, ModularArtifact> modules;               // <module-name>:<artifact>
  private final Set<String> requiredModules;                        // all required application modules
  private final Set<String> runtimeModules;                         // all required java runtime modules

  /**
   * Creates a resolver.
   *
   * @param mojo the base mojo
   * @param artifacts the maven artifacts
   */
  public JlinkResolver(AbstractJlinkMojo mojo, Set<Artifact> artifacts) {
    this.artifacts = artifacts;
    this.mojo = mojo;

    automaticModules = new LinkedHashMap<>();
    modules = new LinkedHashMap<>();
    requiredModules = new HashSet<>();
    runtimeModules = new HashSet<>();
  }

  /**
   * Resolves the maven artifacts for passing to jlink.
   *
   * @return the resolver result
   * @throws MojoExecutionException
   */
  public Result resolve() throws MojoExecutionException {

    Result result = new Result();

    for (Map.Entry<Artifact, JavaModuleDescriptor> entry: loadArtifacts().entrySet()) {
      Artifact artifact = entry.getKey();
      File artifactFile = artifact.getFile();
      JavaModuleDescriptor moduleDescriptor = entry.getValue();
      if (moduleDescriptor == null) {
        mojo.getLog().info(artifact + " neither module nor automatic module -> added to classpath");
        result.addToClassPath(artifact);  // cannot be used on module path -> put to classpath
        jdeps(artifact);
      }
      else {
        String moduleName = moduleDescriptor.name();

        if (moduleDescriptor.isAutomatic()) {
          if (moduleName.startsWith("javafx.") && moduleName.endsWith("Empty")) {
            mojo.getLog().debug(moduleName + " is an automatic dummy javafx module -> skipped");
          }
          else {
            mojo.getLog().debug(moduleName + " is an automatic module");
            automaticModules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
          }
        }
        else {
          mojo.getLog().debug(moduleName + " is a real module");
          modules.put(moduleName, new ModularArtifact(artifact, moduleDescriptor));
        }

        for (JavaModuleDescriptor.JavaRequires requires : moduleDescriptor.requires()) {
          addRequired(requires.name());
          mojo.getLog().debug(moduleName + " requires " + requires.name());
        }
      }
    }

    // move module requirements that start with java. or jdk. back from the runtimeModules to the requiredModules
    // those are old school modules (like java.mail) that are not runtime modules but simple dependencies
    for (Iterator<String> iter = runtimeModules.iterator(); iter.hasNext(); ) {
      String moduleName = iter.next();
      if (automaticModules.get(moduleName) != null ||
          modules.get(moduleName) != null) {
        iter.remove();
        requiredModules.add(moduleName);
      }
    }


    result.setModular(isModularApplication());

    if (result.isModular()) {
      mojo.getLog().debug("building a modular application");
      // check if all requirements are met by real modules.
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

      if (unresolvedModules.isEmpty()) {
        mojo.getLog().debug("YEAH! can build a full modular application!");
        result.addJlinkModules(modules.values());
        result.setPlainModular(true);
      }
      else {
        for (ModularArtifact unresolvedModule: unresolvedModules) {
          mojo.getLog().info("required " + unresolvedModule + " is not a module");
          result.addToModulePath(unresolvedModule);   // must go to modulepath!
        }
        mojo.getLog().info("all dependencies moved to --module-path");
        result.addRuntimeModuleNames(runtimeModules);
        for (ModularArtifact artifact: modules.values()) {
          addToModuleOrClasspath(artifact, result);
        }
      }
    }
    else {
      mojo.getLog().debug("building a classpath application");
      result.addRuntimeModuleNames(runtimeModules);
      for (Artifact artifact: artifacts) {
        result.addToClassPath(artifact);
      }
    }

    // add modules not referenced by anyone
    List<ModularArtifact> allModules = new ArrayList<>();
    allModules.addAll(automaticModules.values());    // automatic come first because they may provide services
    allModules.addAll(modules.values());
    for (ModularArtifact artifact: allModules) {
      String moduleName = artifact.getModuleName();
      if (!moduleName.equals(mojo.getMainModule()) && !requiredModules.contains(moduleName)) {
        addToModuleOrClasspath(artifact, result);
      }
    }

    // add explicit additional modules, such as jdk.jcmd
    result.addRuntimeModuleNames(mojo.getAddModules());

    return result;
  }


  private Map<Artifact, JavaModuleDescriptor> loadArtifacts() throws MojoExecutionException {
    Map<File, Artifact> artifactMap = new LinkedHashMap<>();  // keep order
    for (Artifact artifact: artifacts) {
      File artifactFile = artifact.getFile();
      artifactMap.put(artifactFile, artifact);
      jdeps(artifact);
    }
    ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(artifactMap.keySet());
    if (mojo.getToolChain() instanceof DefaultJavaToolChain) {
      request.setJdkHome(new File(((DefaultJavaToolChain) mojo.getToolChain()).getJavaHome()));
    }
    try {
      Map<Artifact, JavaModuleDescriptor> artifactJavaModuleDescriptorMap = new LinkedHashMap<>();
      for (Map.Entry<File, JavaModuleDescriptor> entry: mojo.getLocationManager().resolvePaths(request).getPathElements().entrySet()) {
        artifactJavaModuleDescriptorMap.put(artifactMap.get(entry.getKey()), entry.getValue());
      }
      return artifactJavaModuleDescriptorMap;
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot resolve paths", e);
    }
  }

  private boolean isModularApplication() throws MojoExecutionException {
    if (mojo.getMainModule() != null) {
      if (modules.get(mojo.getMainModule()) != null) {
        return true;
      }
      throw new MojoExecutionException("no such main module: " + mojo.getMainModule());
    }
    return false;
  }

  private void jdeps(Artifact artifact) throws MojoExecutionException {
    mojo.getLog().info("analyzing " + artifact);
    for (String line: new ToolRunner(mojo.getJdepsTool()).arg("--list-deps").arg(artifact.getFile()).run().getOutput()) {
      line = line.trim();
      if (!line.isEmpty() && !line.contains(" ")) {   // a valid module name (no "not found" and such)
        mojo.getLog().debug(artifact + " (non-module): " + line);
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
    if (moduleName.startsWith("java.") || moduleName.startsWith("jdk.")) {
      runtimeModules.add(moduleName);
    }
    else {
      requiredModules.add(moduleName);
    }
  }

  private void addToModuleOrClasspath(ModularArtifact artifact, Result result) {
    if (mojo.isClasspathDependency(artifact.getArtifact()) || !result.isModular()) {
      result.addToClassPath(artifact.getArtifact());
    }
    else {
      result.addToModulePath(artifact);
    }
  }
}
