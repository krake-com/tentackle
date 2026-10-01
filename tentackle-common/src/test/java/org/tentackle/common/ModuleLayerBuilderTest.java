/*
 * Tentackle - https://tentackle.org.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link ModuleLayerBuilder} and its interaction with {@link DefaultServiceFinder}, proving that
 * two versions of the same module (same module name, different content) can be resolved side by side
 * in sibling {@link ModuleLayer}s, each isolated from the other.
 * <p>
 * Two tiny modules ({@code test.lib}, once as "v1" and once as "v2") and one small {@code test.leaf}
 * module (requiring {@code test.lib} and exposing a {@code META-INF/services} provider) are compiled
 * on the fly with the platform compiler into temporary exploded module directories.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class ModuleLayerBuilderTest {

  private java.nio.file.Path tempDir;
  private java.nio.file.Path libV1Dir;
  private java.nio.file.Path libV2Dir;
  private java.nio.file.Path leafDir;

  @BeforeClass
  public void compileFixtureModules() throws IOException {
    tempDir = Files.createTempDirectory("tentackle-modulelayer-test-");

    libV1Dir = compileLib("v1");
    libV2Dir = compileLib("v2");
    leafDir = compileLeaf();
  }

  @AfterClass
  public void removeFixtureModules() throws IOException {
    if (tempDir != null) {
      try (var stream = Files.walk(tempDir)) {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          }
          catch (IOException iox) {
            // best effort cleanup
          }
        });
      }
    }
  }

  @Test
  public void testVersionIsolationAndServiceLookup() throws Exception {
    assertVersionResolves(libV1Dir, "v1");
    assertVersionResolves(libV2Dir, "v2");
  }

  @Test
  public void testSiblingLayersCannotBothBeParents() {
    LayerSpec v1 = new LayerSpec("lib-v1", List.of(), List.of(libV1Dir));
    LayerSpec v2 = new LayerSpec("lib-v2", List.of(), List.of(libV2Dir));
    LayerSpec conflictLeaf = new LayerSpec("leaf", List.of("lib-v1", "lib-v2"), List.of(leafDir));

    LayerGraphSpec spec = new LayerGraphSpec(List.of(v1, v2, conflictLeaf), "leaf", "test.leaf", "test.leaf.Main");

    assertThrows(TentackleRuntimeException.class,
            () -> ModuleLayerBuilder.build(spec, ModuleLayer.boot(), ModuleLayerBuilderTest.class.getClassLoader()));
  }

  private void assertVersionResolves(java.nio.file.Path libDir, String expectedVersion) throws Exception {
    LayerSpec libLayer = new LayerSpec("lib", List.of(), List.of(libDir));
    LayerSpec leafLayer = new LayerSpec("leaf", List.of("lib"), List.of(leafDir));
    LayerGraphSpec spec = new LayerGraphSpec(List.of(libLayer, leafLayer), "leaf", "test.leaf", "test.leaf.MarkerServiceImpl");

    Map<String, ModuleLayer> layers =
            ModuleLayerBuilder.build(spec, ModuleLayer.boot(), ModuleLayerBuilderTest.class.getClassLoader());

    ModuleLayer leaf = layers.get("leaf");
    assertTrue(leaf.modules().stream().anyMatch(m -> m.getName().equals("test.leaf")));

    ClassLoader leafLoader = leaf.modules().stream().findFirst().orElseThrow().getClassLoader();

    // 1) direct reflection proves the correct sibling version was loaded
    Class<?> implClass = Class.forName("test.leaf.MarkerServiceImpl", true, leafLoader);
    Object instance = implClass.getDeclaredConstructor().newInstance();
    Method getVersion = implClass.getMethod("getVersion");
    assertEquals(getVersion.invoke(instance), expectedVersion);

    // 2) layer-aware ServiceFinder resolves the same provider from within the leaf layer
    ServiceFinder finder = new DefaultServiceFinder(leaf, "META-INF/services/");
    assertEquals(finder.getModuleLayer(), leaf);
    Class<?> markerInterface = Class.forName("test.leaf.MarkerService", false, leafLoader);
    @SuppressWarnings("unchecked")
    Class<Object> serviceClass = (Class<Object>) markerInterface;
    Class<Object> providerClass = finder.findFirstServiceProvider(serviceClass);
    Object providerInstance = providerClass.getDeclaredConstructor().newInstance();
    Object version = providerClass.getMethod("getVersion").invoke(providerInstance);
    assertEquals(version, expectedVersion);

    // 3) the ServiceFactory keeps layer-bound and plain classloader-based finders apart
    ServiceFinder plainFinder = ServiceFactory.getServiceFinder(leafLoader, "META-INF/services/");
    ServiceFinder layerFinder = ServiceFactory.getServiceFinder(leaf, "META-INF/services");
    assertNull(plainFinder.getModuleLayer());
    assertEquals(layerFinder.getModuleLayer(), leaf);
    assertNotSame(layerFinder, plainFinder);
    assertSame(ServiceFactory.getServiceFinder(leaf, "/META-INF/services/"), layerFinder);
  }

  private java.nio.file.Path compileLib(String version) throws IOException {
    java.nio.file.Path srcDir = tempDir.resolve("src-lib-" + version);
    java.nio.file.Path outDir = tempDir.resolve("out-lib-" + version);

    writeSource(srcDir, "module-info.java",
            "module test.lib {",
            "  exports test.lib;",
            "}");
    writeSource(srcDir, "test/lib/Marker.java",
            "package test.lib;",
            "public final class Marker {",
            // deliberately a method, not a "static final String" field: such a field would be a compile-time
            // constant that javac inlines at every call site, defeating the point of this test (observing
            // which sibling version is actually linked and invoked at runtime)
            "  public static String version() { return \"" + version + "\"; }",
            "  private Marker() {}",
            "}");

    compile(srcDir, outDir);
    return outDir;
  }

  private java.nio.file.Path compileLeaf() throws IOException {
    java.nio.file.Path srcDir = tempDir.resolve("src-leaf");
    java.nio.file.Path outDir = tempDir.resolve("out-leaf");

    writeSource(srcDir, "module-info.java",
            "module test.leaf {",
            "  requires test.lib;",
            "  exports test.leaf;",
            "}");
    writeSource(srcDir, "test/leaf/MarkerService.java",
            "package test.leaf;",
            "public interface MarkerService {",
            "  String getVersion();",
            "}");
    writeSource(srcDir, "test/leaf/MarkerServiceImpl.java",
            "package test.leaf;",
            "public class MarkerServiceImpl implements MarkerService {",
            "  @Override",
            "  public String getVersion() {",
            "    return test.lib.Marker.version();",
            "  }",
            "}");

    // compiled against v1's API shape; the identical module name/API lets the same bytecode
    // run against either sibling version at runtime, since only Marker.version()'s body differs
    compile(srcDir, outDir, libV1Dir);

    java.nio.file.Path servicesDir = outDir.resolve("META-INF").resolve("services");
    Files.createDirectories(servicesDir);
    Files.writeString(servicesDir.resolve("test.leaf.MarkerService"), "test.leaf.MarkerServiceImpl" + System.lineSeparator());

    return outDir;
  }

  private static void writeSource(java.nio.file.Path srcDir, String relativePath, String... lines) throws IOException {
    java.nio.file.Path file = srcDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, String.join(System.lineSeparator(), lines));
  }

  private static void compile(java.nio.file.Path srcDir, java.nio.file.Path outDir,
                               java.nio.file.Path... modulePath) throws IOException {
    Files.createDirectories(outDir);

    List<String> sources;
    try (var stream = Files.walk(srcDir)) {
      sources = stream.filter(p -> p.toString().endsWith(".java"))
              .map(java.nio.file.Path::toString)
              .collect(Collectors.toList());
    }

    List<String> args = new ArrayList<>(List.of("-d", outDir.toString()));
    if (modulePath.length > 0) {
      args.add("--module-path");
      args.add(java.util.Arrays.stream(modulePath).map(java.nio.file.Path::toString)
              .collect(Collectors.joining(java.io.File.pathSeparator)));
    }
    args.addAll(sources);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    int rc = compiler.run(null, System.out, System.err, args.toArray(new String[0]));
    if (rc != 0) {
      throw new IOException("compilation of " + srcDir + " failed with exit code " + rc);
    }
  }

}
