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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

import org.tentackle.common.FileHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the independent dependency sets of {@link AbstractJLinkMojo#getLayers()} and writes both
 * their module-path directories and the {@code layers.properties} file consumed by
 * {@code org.tentackle.common.LayerBootstrap} at runtime.
 * <p>
 * Shared between the full {@code jlink}/{@code jpackage} pipeline (writing into the image directory)
 * and the lightweight {@link LayersMojo} used for local development and IDE run/debug configurations
 * (writing into a project-local directory instead).
 *
 * @author harald
 */
public class LayerResolver {

  /**
   * Name of the leaf layer's own, dedicated module-path subfolder.<br>
   * Never shared with {@link AbstractJLinkMojo#DEST_MODULEPATH}: see {@code JLinkResolver}'s
   * {@code BOOTSTRAP_MODULE_NAME} exclusion for why the two must stay physically separate.
   */
  public static final String DEST_LEAF_MODULEPATH = "mp-leaf";

  /**
   * Name of the generated layer graph configuration file, relative to the {@code conf} directory.
   */
  public static final String LAYERS_PROPERTIES = "layers.properties";


  private final AbstractJLinkMojo mojo;

  /**
   * Creates a layer resolver.
   *
   * @param mojo the jlink mojo
   */
  public LayerResolver(AbstractJLinkMojo mojo) {
    this.mojo = mojo;
  }

  /**
   * Resolves every configured layer's own dependencies and copies them into their own
   * {@code <moduleDir>/<layerName>/} subfolder.
   *
   * @param moduleDir the directory to create the per-layer subfolders in (usually {@code mp/})
   * @throws MojoExecutionException if resolution or copying failed
   */
  public void resolveAndCopyLayers(File moduleDir) throws MojoExecutionException {
    for (Layer layer : mojo.getLayers()) {
      File layerDir = new File(moduleDir, layer.getName());
      layerDir.mkdirs();
      for (File file : resolveLayerArtifacts(layer)) {
        try {
          FileHelper.copy(file, new File(layerDir, file.getName()));
        }
        catch (IOException ex) {
          throw new MojoExecutionException("copying " + file + " to " + layerDir + " failed", ex);
        }
      }
      mojo.getLog().info("layer '" + layer.getName() + "' resolved into " + layerDir);
    }
  }

  /**
   * Copies the leaf layer's own module-path artifacts (the application's own main module and
   * whatever else {@link JLinkResolver} diverted there) into their dedicated subfolder.
   *
   * @param imageDirectory the image root directory
   * @param result the resolver result
   * @throws MojoExecutionException if copying failed
   */
  public void copyLeafModulePath(File imageDirectory, JLinkResolver.Result result) throws MojoExecutionException {
    List<ModularArtifact> leafModulePath = result.getLeafModulePath();
    if (leafModulePath.isEmpty()) {
      return;
    }
    File leafDir = new File(imageDirectory, DEST_LEAF_MODULEPATH);
    leafDir.mkdirs();
    for (ModularArtifact artifact : leafModulePath) {
      try {
        FileHelper.copy(artifact.getFile(), new File(leafDir, artifact.getFileName()));
      }
      catch (IOException ex) {
        throw new MojoExecutionException("copying " + artifact.getFile() + " to " + leafDir + " failed", ex);
      }
    }
  }

  /**
   * Writes the {@code layers.properties} file describing the whole layer graph, for the packaged
   * jlink/jpackage image: each declared layer's directory is {@code mp/<name>}, and the leaf's own
   * directory is the dedicated {@link #DEST_LEAF_MODULEPATH} folder.
   *
   * @param confDir the {@code conf} directory to write the file into
   * @throws MojoExecutionException if writing failed
   */
  public void writeLayersProperties(File confDir) throws MojoExecutionException {
    writeLayersProperties(confDir, AbstractJLinkMojo.DEST_MODULEPATH, DEST_LEAF_MODULEPATH);
  }

  /**
   * Writes the {@code layers.properties} file describing the whole layer graph.
   *
   * @param confDir the directory to write the file into
   * @param layerDirPrefix the directory each declared layer's own subfolder is created under
   *        (relative to wherever {@code org.tentackle.common.LayerBootstrap} resolves paths from
   *        at runtime, i.e. its current working directory)
   * @param leafDirValue the leaf layer's own directory, relative the same way (or absolute)
   * @throws MojoExecutionException if writing failed
   */
  public void writeLayersProperties(File confDir, String layerDirPrefix, String leafDirValue) throws MojoExecutionException {
    confDir.mkdirs();

    Set<String> leafParents = new LinkedHashSet<>();
    for (Layer layer : mojo.getLayers()) {
      if (layer.isLeafParent()) {
        leafParents.add(layer.getName());
      }
    }

    StringBuilder buf = new StringBuilder();
    buf.append("# generated by tentackle-jlink-maven-plugin, do not edit\n");
    for (Layer layer : mojo.getLayers()) {
      buf.append("layer.").append(layer.getName()).append(".parent=")
         .append(layer.getParent() == null ? "" : layer.getParent()).append('\n');
      buf.append("layer.").append(layer.getName()).append(".dir=")
         .append(layerDirPrefix).append('/').append(layer.getName()).append('\n');
    }
    buf.append("layer.").append(mojo.getLeafLayer()).append(".parent=")
       .append(String.join(",", leafParents)).append('\n');
    buf.append("layer.").append(mojo.getLeafLayer()).append(".dir=").append(leafDirValue).append('\n');
    buf.append("leaf=").append(mojo.getLeafLayer()).append('\n');
    buf.append("leaf.mainModule=").append(mojo.getMainModule()).append('\n');
    buf.append("leaf.mainClass=").append(mojo.getMainClass()).append('\n');

    File file = new File(confDir, LAYERS_PROPERTIES);
    try {
      java.nio.file.Files.writeString(file.toPath(), buf.toString());
    }
    catch (IOException ex) {
      throw new MojoExecutionException("writing " + file + " failed", ex);
    }
  }

  /**
   * Resolves one layer's own dependencies, transitively, independently of the enclosing project's
   * own dependency graph.
   *
   * @param layer the layer
   * @return the resolved artifact files
   * @throws MojoExecutionException if resolution failed
   */
  private List<File> resolveLayerArtifacts(Layer layer) throws MojoExecutionException {
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setDependencies(layer.getDependencies().stream()
            .map(LayerResolver::toAetherDependency).collect(Collectors.toList()));
    collectRequest.setRepositories(mojo.getMavenProject().getRemoteProjectRepositories());

    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

    DependencyResult dependencyResult;
    try {
      dependencyResult = mojo.getRepositorySystem().resolveDependencies(
              mojo.getMavenSession().getRepositorySession(), dependencyRequest);
    }
    catch (DependencyResolutionException ex) {
      throw new MojoExecutionException("resolving layer '" + layer.getName() + "' failed", ex);
    }

    List<File> files = new ArrayList<>();
    for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
      files.add(artifactResult.getArtifact().getFile());
    }
    return files;
  }

  private static org.eclipse.aether.graph.Dependency toAetherDependency(Dependency dependency) {
    String type = dependency.getType() == null || dependency.getType().isBlank() ? "jar" : dependency.getType();
    String classifier = dependency.getClassifier() == null ? "" : dependency.getClassifier();
    DefaultArtifact artifact = new DefaultArtifact(
            dependency.getGroupId(), dependency.getArtifactId(), classifier, type, dependency.getVersion());
    return new org.eclipse.aether.graph.Dependency(artifact, "runtime");
  }

}
