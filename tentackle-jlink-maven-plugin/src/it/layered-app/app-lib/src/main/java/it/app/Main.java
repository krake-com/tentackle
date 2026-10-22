package it.app;

import org.tentackle.common.LayerBootstrap;

import java.lang.reflect.Method;

/**
 * Deliberately never {@code requires it.lib}: a single module cannot read two modules of the same
 * name, so this reaches each isolated sibling layer indirectly, via {@link LayerBootstrap#getLayer}
 * plus reflection - exactly the pattern documented in {@code layers.md}.
 */
public class Main {

  public static void main(String[] args) throws Exception {
    System.out.println("LIB_V1=" + version("lib-v1"));
    System.out.println("LIB_V2=" + version("lib-v2"));
    // the launcher must pass the command line arguments through, and nothing else
    System.out.println("ARGS=" + String.join(",", args));
  }

  private static String version(String layerName) throws Exception {
    ModuleLayer layer = LayerBootstrap.getLayer(layerName);
    Module module = layer.findModule("it.lib")
            .orElseThrow(() -> new IllegalStateException("module it.lib not found in layer " + layerName));

    Class<?> markerClass = Class.forName("it.lib.Marker", true, module.getClassLoader());

    // this module reads neither sibling at compile time, so it must grant itself a runtime reads
    // edge before touching the class at all - the one extra step reflection across layers needs
    Main.class.getModule().addReads(markerClass.getModule());

    Method versionMethod = markerClass.getMethod("version");
    return (String) versionMethod.invoke(null);
  }

}
