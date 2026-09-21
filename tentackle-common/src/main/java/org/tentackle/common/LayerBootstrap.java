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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

/**
 * Launcher building a {@link ModuleLayer} graph and handing off to the real application.
 * <p>
 * Applications never invoke this class themselves &mdash; it is what the {@code tentackle-jlink-maven-plugin}'s
 * generated launcher script runs instead of the real main class whenever {@code &lt;layers&gt;} are configured
 * (see the {@code jlinkpackage.md} and {@code layers.md} documentation), and what an IDE run/debug configuration
 * points at to run an application with isolated layers locally.
 * <p>
 * {@link #main(String[])} reads a {@link LayerGraphSpec} from a properties file (see {@link #LAYERS_PROPERTY}),
 * builds the layer graph via {@link ModuleLayerBuilder}, sets the leaf layer's classloader as the thread context
 * classloader &mdash; which is what makes every existing, layer-unaware {@code ServiceFactory.getServiceFinder()}
 * call transparently resolve services from within the leaf layer &mdash; and then reflectively invokes the real
 * application's {@code main(String[])} method.
 *
 * @author harald
 */
public final class LayerBootstrap {

  /**
   * System property naming the layers configuration file.<br>
   * Resolved relative to the current working directory if not absolute.
   */
  public static final String LAYERS_PROPERTY = "tentackle.layers";

  /**
   * Default location of the layers configuration file, relative to the current working directory.
   */
  public static final String DEFAULT_LAYERS_FILE = "conf/layers.properties";

  private static volatile Map<String, ModuleLayer> layers = Map.of();

  private LayerBootstrap() {
  }

  /**
   * Gets a layer built by this bootstrap, by name.<br>
   * This is how application code reaches a layer other than its own leaf layer &mdash; e.g. one
   * pinning a specific version of a dependency &mdash; typically to then resolve a service from it
   * via {@link ServiceFactory#getServiceFinder(ModuleLayer, String)}. See the {@code layers.md}
   * documentation for the full pattern, including why a leaf module should not itself directly
   * {@code requires} two sibling layers that pin conflicting versions of the same module.
   *
   * @param name the layer name, as configured
   * @return the layer
   * @throws TentackleRuntimeException if no such layer, or called before {@link #main(String[])} has run
   */
  public static ModuleLayer getLayer(String name) {
    ModuleLayer layer = layers.get(name);
    if (layer == null) {
      throw new TentackleRuntimeException("no such layer '" + name + "'");
    }
    return layer;
  }

  /**
   * Gets all layers built by this bootstrap, keyed by name.
   *
   * @return the layers, empty if {@link #main(String[])} has not run yet
   */
  public static Map<String, ModuleLayer> getLayers() {
    return layers;
  }

  /**
   * Builds the configured module layer graph and invokes the real application's main method.
   *
   * @param args the application arguments, passed through unchanged
   * @throws Exception if the layer graph cannot be built or the application's main method fails
   */
  public static void main(String[] args) throws Exception {

    java.nio.file.Path baseDir = java.nio.file.Path.of("").toAbsolutePath();
    java.nio.file.Path specFile = baseDir.resolve(System.getProperty(LAYERS_PROPERTY, DEFAULT_LAYERS_FILE));

    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(specFile)) {
      props.load(in);
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("cannot read layers configuration " + specFile, iox);
    }

    LayerGraphSpec spec = LayerGraphSpec.parse(props, baseDir);
    layers = Map.copyOf(ModuleLayerBuilder.build(spec, ModuleLayer.boot(), LayerBootstrap.class.getClassLoader()));
    ModuleLayer leafLayer = layers.get(spec.leafLayerName());

    ClassLoader leafLoader = leafLayer.modules().stream()
            .findFirst()
            .map(Module::getClassLoader)
            .orElseThrow(() -> new TentackleRuntimeException("leaf layer '" + spec.leafLayerName() + "' has no modules"));

    // makes every layer-unaware ServiceFactory.getServiceFinder() call resolve from within the leaf layer
    Thread.currentThread().setContextClassLoader(leafLoader);

    Class<?> mainClass;
    try {
      mainClass = Class.forName(spec.leafMainClass(), true, leafLoader);
    }
    catch (ClassNotFoundException ex) {
      throw new TentackleRuntimeException("main class '" + spec.leafMainClass() + "' not found", ex);
    }

    Method main;
    try {
      main = mainClass.getDeclaredMethod("main", String[].class);
      main.setAccessible(true);
    }
    catch (ReflectiveOperationException ex) {
      throw new TentackleRuntimeException("class '" + spec.leafMainClass() + "' has no accessible main(String[]) method", ex);
    }

    try {
      main.invoke(null, (Object) args);
    }
    catch (InvocationTargetException ex) {
      throw new TentackleRuntimeException("application main class threw an exception", ex.getCause());
    }
  }

}
