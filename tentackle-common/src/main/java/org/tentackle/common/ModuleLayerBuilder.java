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

package org.tentackle.common;

import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a graph of {@link ModuleLayer}s from a {@link LayerGraphSpec}.
 * <p>
 * Each layer is defined with exactly one classloader for all its modules
 * ({@link ModuleLayer#defineModulesWithOneLoader(Configuration, List, ClassLoader)}), so a layer is the
 * unit of isolation: two sibling layers may hold different versions of the same module, but modules
 * placed in the very same layer are not isolated from each other.
 * <p>
 * Layers are built in an order such that a layer's parents always exist before the layer itself is
 * built. Unknown or cyclic parent relationships are detected and rejected.
 * <p>
 * The classloader of a layer is parented on the classloader of its <em>first</em> parent layer
 * (or on the given parent classloader for layers parented on the boot layer).
 * This affects only the lookup of classes not defined in any module.
 * <p>
 * A layer with several parents that each (still) define a different, conflicting module of the same
 * name is rejected explicitly (see {@link #checkDisjointAncestry}), since the JDK's own module
 * resolution does not reject this itself &mdash; it would otherwise silently bind to just one of them.
 *
 * @author harald
 */
public final class ModuleLayerBuilder {

  private ModuleLayerBuilder() {
  }

  /**
   * Builds the module layer graph described by the given spec.
   *
   * If the spec names a {@link LayerGraphSpec#leafMainModule()}, the package of the main class
   * is opened to tentackle-common, so that {@link LayerBootstrap} can invoke the main method reflectively.
   *
   * @param spec the layer graph spec
   * @param bootLayer the boot layer, usually {@link ModuleLayer#boot()}
   * @param parentLoader the classloader to use as the parent of layers directly parented on the boot layer
   * @return the built layers, keyed by layer name
   * @throws TentackleRuntimeException if layer names are duplicate, parents are unknown or cyclic,
   *         a layer cannot be resolved, a layer has conflicting parents, the leaf layer is not defined,
   *         or the leaf main module is not found in the leaf layer
   */
  public static Map<String, ModuleLayer> build(LayerGraphSpec spec, ModuleLayer bootLayer, ClassLoader parentLoader) {

    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(bootLayer, "bootLayer");

    Map<String, LayerSpec> specsByName = new LinkedHashMap<>();
    for (LayerSpec layerSpec : spec.layers()) {
      if (specsByName.putIfAbsent(layerSpec.name(), layerSpec) != null) {
        throw new TentackleRuntimeException("duplicate layer name '" + layerSpec.name() + "'");
      }
    }

    Map<String, ModuleLayer> layers = new LinkedHashMap<>();
    Map<String, ClassLoader> loaders = new LinkedHashMap<>();
    Set<String> remaining = new LinkedHashSet<>(specsByName.keySet());

    while (!remaining.isEmpty()) {
      String nextName = remaining.stream()
              .filter(name -> layers.keySet().containsAll(specsByName.get(name).parentNames()))
              .findFirst()
              .orElseThrow(() -> new TentackleRuntimeException(
                      "cannot resolve layer hierarchy (unknown or cyclic parents): " + remaining));

      ModuleLayer.Controller controller = buildLayer(specsByName.get(nextName), spec, bootLayer, parentLoader, layers, loaders);
      layers.put(nextName, controller.layer());
      loaders.put(nextName, resolveLoader(controller.layer(), parentLoader));

      if (nextName.equals(spec.leafLayerName()) && spec.leafMainModule() != null && !spec.leafMainModule().isBlank()) {
        // open the package holding the main class to this module, so LayerBootstrap can reflectively invoke it
        // even if the application never exports/opens that package itself (it usually doesn't need to)
        Module leafModule = controller.layer().findModule(spec.leafMainModule())
                .orElseThrow(() -> new TentackleRuntimeException(
                        "module '" + spec.leafMainModule() + "' not found in leaf layer '" + nextName + "'"));
        controller.addOpens(leafModule, packageOf(spec.leafMainClass()), ModuleLayerBuilder.class.getModule());
      }

      remaining.remove(nextName);
    }

    if (!layers.containsKey(spec.leafLayerName())) {
      throw new TentackleRuntimeException("leaf layer '" + spec.leafLayerName() + "' not defined");
    }

    return layers;
  }

  private static ModuleLayer.Controller buildLayer(LayerSpec layerSpec, LayerGraphSpec spec, ModuleLayer bootLayer,
                                                     ClassLoader parentLoader, Map<String, ModuleLayer> layers,
                                                     Map<String, ClassLoader> loaders) {

    List<ModuleLayer> parentLayers = layerSpec.parentNames().isEmpty() ?
            List.of(bootLayer) :
            layerSpec.parentNames().stream().map(layers::get).collect(Collectors.toList());

    checkDisjointAncestry(parentLayers, layerSpec.name());

    List<Configuration> parentConfigurations = parentLayers.stream()
            .map(ModuleLayer::configuration)
            .collect(Collectors.toList());

    ClassLoader parent = layerSpec.parentNames().isEmpty() ?
            parentLoader :
            loaders.get(layerSpec.parentNames().get(0));

    ModuleFinder finder = ModuleFinder.of(layerSpec.directories().toArray(new java.nio.file.Path[0]));

    Set<String> roots = finder.findAll().stream()
            .map(ref -> ref.descriptor().name())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (layerSpec.name().equals(spec.leafLayerName()) &&
        spec.leafMainModule() != null && !spec.leafMainModule().isBlank()) {
      roots.add(spec.leafMainModule());
    }

    Configuration configuration;
    try {
      configuration = Configuration.resolve(finder, parentConfigurations, ModuleFinder.of(), roots);
    }
    catch (FindException | ResolutionException | IllegalArgumentException ex) {
      throw new TentackleRuntimeException("cannot resolve module layer '" + layerSpec.name() + "'", ex);
    }

    return ModuleLayer.defineModulesWithOneLoader(configuration, parentLayers, parent);
  }

  /**
   * Checks that a layer's parents do not provide conflicting versions of the same module.
   * <p>
   * The JDK's own {@link Configuration#resolve} does <em>not</em> reject a child layer whose two
   * parents both define a module of the same name &mdash; it silently binds to whichever parent's
   * module happens to be found first. Since silently picking one of two coexisting versions would
   * defeat the entire purpose of this class, this is checked explicitly: a module name reachable
   * through more than one parent is fine as long as it is the very same {@link Module} instance
   * (i.e., a shared ancestor layer), but an outright conflict if two different parents each define
   * their own, distinct module of that name.
   *
   * @param parentLayers the layer's parents
   * @param layerName the layer being built, for the error message
   */
  private static void checkDisjointAncestry(List<ModuleLayer> parentLayers, String layerName) {
    if (parentLayers.size() < 2) {
      return;
    }
    Map<String, Module> seen = new HashMap<>();
    for (ModuleLayer parent : parentLayers) {
      for (Module module : allReachableModules(parent)) {
        Module previous = seen.putIfAbsent(module.getName(), module);
        if (previous != null && previous != module) {
          throw new TentackleRuntimeException("layer '" + layerName + "' has conflicting parents: module '" +
                  module.getName() + "' is defined differently by more than one parent layer");
        }
      }
    }
  }

  private static Set<Module> allReachableModules(ModuleLayer layer) {
    Set<Module> modules = new HashSet<>();
    Set<ModuleLayer> visited = new HashSet<>();
    collectReachableModules(layer, modules, visited);
    return modules;
  }

  private static void collectReachableModules(ModuleLayer layer, Set<Module> modules, Set<ModuleLayer> visited) {
    if (!visited.add(layer)) {
      return;
    }
    modules.addAll(layer.modules());
    layer.parents().forEach(parent -> collectReachableModules(parent, modules, visited));
  }

  private static String packageOf(String className) {
    int ndx = className.lastIndexOf('.');
    return ndx < 0 ? "" : className.substring(0, ndx);
  }

  private static ClassLoader resolveLoader(ModuleLayer layer, ClassLoader fallback) {
    return layer.modules().stream()
            .findFirst()
            .map(Module::getClassLoader)
            .orElse(fallback);
  }

}
