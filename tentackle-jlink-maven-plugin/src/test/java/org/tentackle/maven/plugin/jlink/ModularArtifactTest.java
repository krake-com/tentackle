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

import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.eclipse.aether.artifact.Artifact;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.TreeSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the pairing of a maven artifact with its JPMS descriptor.
 * <p>
 * Both the module- and the class path are built from sets of these, so identity and ordering
 * decide whether an artifact ends up on a path twice or not at all.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ModularArtifactTest {

  private static final File JAR = new File("/repo/org/example/lib/1.0/lib-1.0.jar");


  /**
   * The module name comes from the descriptor, everything else from the artifact.
   */
  @Test
  public void delegatesToTheArtifactAndTheDescriptor() {
    ModularArtifact artifact = modularArtifact("lib", "1.0", "org.example.lib");

    assertEquals(artifact.getModuleName(), "org.example.lib");
    assertEquals(artifact.getFile(), JAR);
    assertEquals(artifact.getFileName(), "lib-1.0.jar");
    assertEquals(artifact.getPath(), JAR.getPath());
    assertEquals(artifact.toString(), "org.example.lib");
    assertEquals(artifact.getModuleDescriptor().name(), "org.example.lib");
  }

  /**
   * Identity is the artifact's, not the module name's: the same artifact resolved twice must not
   * end up on a path twice.
   */
  @Test
  public void equalityIsBasedOnTheArtifact() {
    ModularArtifact artifact = modularArtifact("lib", "1.0", "org.example.lib");
    ModularArtifact same = modularArtifact("lib", "1.0", "org.example.lib");
    ModularArtifact otherVersion = modularArtifact("lib", "2.0", "org.example.lib");
    ModularArtifact otherArtifact = modularArtifact("other", "1.0", "org.example.lib");

    assertEquals(artifact, same);
    assertEquals(artifact.hashCode(), same.hashCode());
    assertNotEquals(artifact, otherVersion);
    assertNotEquals(artifact, otherArtifact);
    assertNotEquals(artifact, null);
    assertNotEquals(artifact, "org.example.lib");
  }

  /**
   * The natural order is the artifact's, which sorts by group, artifact and version.
   */
  @Test
  public void sortsLikeTheArtifacts() {
    ModularArtifact app = modularArtifact("app", "1.0", "com.example.app");
    ModularArtifact lib1 = modularArtifact("lib", "1.0", "org.example.lib");
    ModularArtifact lib2 = modularArtifact("lib", "2.0", "org.example.lib");

    TreeSet<ModularArtifact> sorted = new TreeSet<>(List.of(lib2, app, lib1));

    assertEquals(List.copyOf(sorted), List.of(app, lib1, lib2));
    assertTrue(app.compareTo(lib1) < 0);
    assertEquals(app.compareTo(modularArtifact("app", "1.0", "whatever")), 0);
  }


  private ModularArtifact modularArtifact(String artifactId, String version, String moduleName) {
    Artifact artifact = JLinkTestSupport.createArtifact("org.example", artifactId, version, "jar", null, JAR);
    return new ModularArtifact(artifact, JavaModuleDescriptor.newModule(moduleName).build());
  }

}
