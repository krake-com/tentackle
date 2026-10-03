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

package org.tentackle.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the conversion of POM dependencies to resolver dependencies.
 */
public class ProjectDependenciesTest {

  private final Map<String, ArtifactType> types = Map.of(
          "jar", new DefaultArtifactType("jar", "jar", "", "java", true, false),
          "test-jar", new DefaultArtifactType("test-jar", "jar", "tests", "java", true, false));

  private final ArtifactTypeRegistry registry = types::get;

  private static Dependency dependency(String type) {
    Dependency dependency = new Dependency();
    dependency.setGroupId("org.example");
    dependency.setArtifactId("sample");
    dependency.setVersion("1.0");
    dependency.setType(type);
    return dependency;
  }

  /**
   * A system dependency keeps the properties of its type in addition to the local path.
   */
  @Test
  public void systemDependencyKeepsTypeProperties() {
    Dependency dependency = dependency("jar");
    dependency.setScope("system");
    dependency.setSystemPath("/opt/lib/sample.jar");

    Artifact artifact = ProjectDependencies.toAetherDependency(dependency, null, registry).getArtifact();

    assertEquals(artifact.getProperty(ArtifactProperties.LOCAL_PATH, null), "/opt/lib/sample.jar");
    assertEquals(artifact.getProperty(ArtifactProperties.TYPE, null), "jar");
    assertEquals(artifact.getProperty(ArtifactProperties.LANGUAGE, null), "java");
    assertEquals(artifact.getProperty(ArtifactProperties.CONSTITUTES_BUILD_PATH, null), "true");
  }

  /**
   * The type determines the extension and the default classifier.
   */
  @Test
  public void typeDeterminesExtensionAndClassifier() {
    Artifact artifact = ProjectDependencies.toAetherDependency(dependency("test-jar"), null, registry).getArtifact();
    assertEquals(artifact.getExtension(), "jar");
    assertEquals(artifact.getClassifier(), "tests");

    // unknown type: used as the extension
    artifact = ProjectDependencies.toAetherDependency(dependency("zip"), null, registry).getArtifact();
    assertEquals(artifact.getExtension(), "zip");
    assertEquals(artifact.getClassifier(), "");
  }

  /**
   * The scope defaults to "compile", unless given explicitly.
   */
  @Test
  public void scope() {
    Dependency dependency = dependency(null);
    assertEquals(ProjectDependencies.toAetherDependency(dependency, null, registry).getScope(), "compile");
    assertEquals(ProjectDependencies.toAetherDependency(dependency, "runtime", registry).getScope(), "runtime");
    dependency.setScope("provided");
    assertEquals(ProjectDependencies.toAetherDependency(dependency, null, registry).getScope(), "provided");
    assertEquals(ProjectDependencies.toAetherDependency(dependency, "", registry).getScope(), "provided");
  }

  /**
   * The optional flag and the exclusions are carried over.
   */
  @Test
  public void optionalAndExclusions() {
    Dependency dependency = dependency("jar");
    assertFalse(ProjectDependencies.toAetherDependency(dependency, null, registry).isOptional());

    dependency.setOptional(true);
    Exclusion exclusion = new Exclusion();
    exclusion.setGroupId("org.unwanted");
    exclusion.setArtifactId("*");
    dependency.addExclusion(exclusion);

    org.eclipse.aether.graph.Dependency converted = ProjectDependencies.toAetherDependency(dependency, null, registry);
    assertTrue(converted.isOptional());
    assertEquals(converted.getExclusions().size(), 1);
    org.eclipse.aether.graph.Exclusion convertedExclusion = converted.getExclusions().iterator().next();
    assertEquals(convertedExclusion.getGroupId(), "org.unwanted");
    assertEquals(convertedExclusion.getArtifactId(), "*");
  }
}
