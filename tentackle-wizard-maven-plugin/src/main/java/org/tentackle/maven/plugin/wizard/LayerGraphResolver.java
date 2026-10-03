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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import org.tentackle.common.LayerGraphSpec;
import org.tentackle.common.LayerSpec;
import org.tentackle.common.ModuleLayerBuilder;
import org.tentackle.common.StringHelper;
import org.tentackle.maven.LayerDependencyResolver;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves {@link AbstractWizardMojo#getLayers()} into a real {@link ModuleLayer} graph, in-process,
 * at mojo-execution time.
 * <p>
 * Unlike {@code tentackle-jlink-maven-plugin}'s {@code LayerResolver}, which copies each layer's
 * resolved artifacts into a distributable jlink image's own module-path directories, this resolver
 * hands each layer's resolved local-repository jar files straight to
 * {@link java.lang.module.ModuleFinder#of(Path...)} (via {@link LayerSpec}) &mdash; {@code ModuleFinder}
 * accepts individual modular jars just as well as directories, so no copy step is needed for an
 * in-process consumer.
 *
 * @author harald
 */
public class LayerGraphResolver {

  private final AbstractWizardMojo mojo;

  /**
   * Creates a layer graph resolver.
   *
   * @param mojo the wizard mojo
   */
  public LayerGraphResolver(AbstractWizardMojo mojo) {
    this.mojo = mojo;
  }

  /**
   * Validates the {@link AbstractWizardMojo#getLayers()}/{@link AbstractWizardMojo#getLeafLayer()}
   * configuration, if any.
   *
   * @throws MojoExecutionException if invalid
   */
  public void validateLayers() throws MojoExecutionException {
    if (!mojo.isLayered()) {
      return;
    }
    if (StringHelper.isAllWhitespace(mojo.getLeafLayer())) {
      throw new MojoExecutionException("leafLayer missing: required if layers are configured");
    }
    Set<String> names = new HashSet<>();
    boolean leafFound = false;
    for (Layer layer : mojo.getLayers()) {
      if (StringHelper.isAllWhitespace(layer.getName())) {
        throw new MojoExecutionException("a layer without a name");
      }
      if (!names.add(layer.getName())) {
        throw new MojoExecutionException("duplicate layer name '" + layer.getName() + "'");
      }
      if (layer.getName().equals(mojo.getLeafLayer())) {
        leafFound = true;
      }
      if (layer.getDependencies() == null || layer.getDependencies().isEmpty()) {
        throw new MojoExecutionException("layer '" + layer.getName() + "' has no dependencies");
      }
      for (Dependency dependency : layer.getDependencies()) {
        if (StringHelper.isAllWhitespace(dependency.getGroupId()) ||
            StringHelper.isAllWhitespace(dependency.getArtifactId()) ||
            StringHelper.isAllWhitespace(dependency.getVersion())) {
          throw new MojoExecutionException("layer '" + layer.getName() +
                                           "' has a dependency without groupId, artifactId or version: " + dependency);
        }
      }
    }
    if (!leafFound) {
      throw new MojoExecutionException(
              "leafLayer '" + mojo.getLeafLayer() + "' does not match the name of any configured layer");
    }
  }

  /**
   * Resolves every configured layer's own dependencies and builds the module layer graph.
   *
   * @return the built layers, keyed by layer name
   * @throws MojoExecutionException if resolving a layer's dependencies or building the graph failed
   */
  public Map<String, ModuleLayer> build() throws MojoExecutionException {
    List<LayerSpec> layerSpecs = new ArrayList<>();
    for (Layer layer : mojo.getLayers()) {
      List<String> parentNames = StringHelper.split(layer.getParent() == null ? "" : layer.getParent(), ",")
              .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();

      List<File> files;
      try {
        files = LayerDependencyResolver.resolve(mojo.getRepositorySystem(), mojo.getMavenSession().getRepositorySession(),
                mojo.getMavenProject().getRemoteProjectRepositories(), layer.getDependencies());
      }
      catch (DependencyResolutionException ex) {
        throw new MojoExecutionException("resolving layer '" + layer.getName() + "' failed", ex);
      }

      List<Path> paths = files.stream().map(File::toPath).toList();
      layerSpecs.add(new LayerSpec(layer.getName(), parentNames, paths));
    }

    LayerGraphSpec spec = new LayerGraphSpec(layerSpecs, mojo.getLeafLayer(), null, "");
    try {
      return ModuleLayerBuilder.build(spec, ModuleLayer.boot(), AbstractWizardMojo.class.getClassLoader());
    }
    catch (RuntimeException ex) {   // TentackleRuntimeException, LayerInstantiationException, etc...
      throw new MojoExecutionException("building the module layer graph failed: " + ex.getMessage(), ex);
    }
  }

  /**
   * Gets the leaf layer's single classloader.
   *
   * @param layers the built layers, as returned by {@link #build()}
   * @return the leaf layer's classloader
   * @throws MojoExecutionException if the leaf layer was not built or has no modules
   */
  public ClassLoader getLeafClassLoader(Map<String, ModuleLayer> layers) throws MojoExecutionException {
    ModuleLayer leafLayer = layers.get(mojo.getLeafLayer());
    if (leafLayer == null) {
      throw new MojoExecutionException("leaf layer '" + mojo.getLeafLayer() + "' not built");
    }
    return leafLayer.modules().stream()
            .findFirst()
            .map(Module::getClassLoader)
            .orElseThrow(() -> new MojoExecutionException("leaf layer '" + mojo.getLeafLayer() + "' has no modules"));
  }

}
