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

import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link LayerGraphSpec#parse(Properties, java.nio.file.Path)}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LayerGraphSpecTest {

  @Test
  public void testParse() {
    Properties props = new Properties();
    props.setProperty("layer.boot-libs-v1.parent", "");
    props.setProperty("layer.boot-libs-v1.dir", "mp/boot-libs-v1");
    props.setProperty("layer.boot-libs-v2.parent", "");
    props.setProperty("layer.boot-libs-v2.dir", "mp/boot-libs-v2");
    props.setProperty("layer.app.parent", "boot-libs-v1, boot-libs-v2");
    props.setProperty("layer.app.dir", "mp/app");
    props.setProperty("leaf", "app");
    props.setProperty("leaf.mainModule", "com.example.app");
    props.setProperty("leaf.mainClass", "com.example.app.Main");

    java.nio.file.Path baseDir = java.nio.file.Path.of("/image/root");
    LayerGraphSpec spec = LayerGraphSpec.parse(props, baseDir);

    assertEquals(spec.leafLayerName(), "app");
    assertEquals(spec.leafMainModule(), "com.example.app");
    assertEquals(spec.leafMainClass(), "com.example.app.Main");
    assertEquals(spec.layers().size(), 3);

    LayerSpec app = spec.layers().stream().filter(l -> l.name().equals("app")).findFirst().orElseThrow();
    assertEquals(app.parentNames(), List.of("boot-libs-v1", "boot-libs-v2"));
    assertEquals(app.directories(), List.of(baseDir.resolve("mp/app")));

    LayerSpec v1 = spec.layers().stream().filter(l -> l.name().equals("boot-libs-v1")).findFirst().orElseThrow();
    assertTrue(v1.parentNames().isEmpty());
  }

  @Test
  public void testParseWithoutMainModuleIsClasspathApplication() {
    Properties props = new Properties();
    props.setProperty("layer.app.dir", "mp/app");
    props.setProperty("leaf", "app");
    props.setProperty("leaf.mainClass", "com.example.app.Main");

    LayerGraphSpec spec = LayerGraphSpec.parse(props, java.nio.file.Path.of("."));
    assertNull(spec.leafMainModule());
  }

  @Test
  public void testParseFailsWithoutLeaf() {
    Properties props = new Properties();
    props.setProperty("layer.app.dir", "mp/app");
    props.setProperty("leaf.mainClass", "com.example.app.Main");

    assertThrows(TentackleRuntimeException.class, () -> LayerGraphSpec.parse(props, java.nio.file.Path.of(".")));
  }

  @Test
  public void testParseFailsWithoutMainClass() {
    Properties props = new Properties();
    props.setProperty("layer.app.dir", "mp/app");
    props.setProperty("leaf", "app");

    assertThrows(TentackleRuntimeException.class, () -> LayerGraphSpec.parse(props, java.nio.file.Path.of(".")));
  }

  @Test
  public void testParseFailsWithoutDirectory() {
    Properties props = new Properties();
    props.setProperty("layer.app.parent", "");
    props.setProperty("leaf", "app");
    props.setProperty("leaf.mainClass", "com.example.app.Main");

    assertThrows(TentackleRuntimeException.class, () -> LayerGraphSpec.parse(props, java.nio.file.Path.of(".")));
  }

}
