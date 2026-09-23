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
import org.testng.annotations.Test;

import javafx.stage.Stage;
import java.util.List;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@link LayerGraphResolver#validateLayers()}.
 */
public class LayerGraphResolverValidationTest {

  /**
   * Creates a wizard mojo configured with the given layers and leaf layer.<br>
   * {@link AbstractWizardMojo#getLayers()}/{@link AbstractWizardMojo#getLeafLayer()} are plain overridable getters,
   * so no Maven container or reflective field injection is needed here.
   *
   * @param leafLayer the leaf layer name, may be null
   * @param layers the layers
   * @return the mojo
   */
  private AbstractWizardMojo createMojo(String leafLayer, Layer... layers) {
    return new PdoMojo() {
      @Override
      public List<Layer> getLayers() {
        return List.of(layers);
      }

      @Override
      public String getLeafLayer() {
        return leafLayer;
      }

      @Override
      protected Stage getStage() {
        return null;
      }
    };
  }

  private Layer createLayer(String name, Dependency... dependencies) {
    Layer layer = new Layer();
    layer.setName(name);
    layer.setDependencies(List.of(dependencies));
    return layer;
  }

  private Dependency createDependency(String groupId, String artifactId, String version) {
    Dependency dependency = new Dependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setVersion(version);
    return dependency;
  }


  @Test
  public void testNoLayersIsNoop() throws MojoExecutionException {
    new LayerGraphResolver(createMojo(null)).validateLayers();
  }

  @Test
  public void testValidConfiguration() throws MojoExecutionException {
    Layer app = createLayer("app", createDependency("org.example", "app-model", "1.0"));
    new LayerGraphResolver(createMojo("app", app)).validateLayers();
  }

  @Test
  public void testMissingLeafLayer() {
    Layer app = createLayer("app", createDependency("org.example", "app-model", "1.0"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo(null, app)).validateLayers());
    assertTrue(ex.getMessage().contains("leafLayer"), ex.getMessage());
  }

  @Test
  public void testLeafLayerNotMatchingAnyLayer() {
    Layer app = createLayer("app", createDependency("org.example", "app-model", "1.0"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo("nosuch", app)).validateLayers());
    assertTrue(ex.getMessage().contains("nosuch"), ex.getMessage());
  }

  @Test
  public void testBlankLayerName() {
    Layer blank = createLayer(" ", createDependency("org.example", "app-model", "1.0"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo("app", blank)).validateLayers());
    assertTrue(ex.getMessage().contains("without a name"), ex.getMessage());
  }

  @Test
  public void testDuplicateLayerName() {
    Layer first = createLayer("lib", createDependency("org.example", "lib-v1", "1.0"));
    Layer second = createLayer("lib", createDependency("org.example", "lib-v2", "2.0"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo("lib", first, second)).validateLayers());
    assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
  }

  @Test
  public void testLayerWithoutDependencies() {
    Layer empty = createLayer("app");
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo("app", empty)).validateLayers());
    assertTrue(ex.getMessage().contains("no dependencies"), ex.getMessage());
  }

  @Test
  public void testDependencyMissingCoordinates() {
    Layer app = createLayer("app", createDependency("org.example", "app-model", null));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
            () -> new LayerGraphResolver(createMojo("app", app)).validateLayers());
    assertTrue(ex.getMessage().contains("groupId, artifactId or version"), ex.getMessage());
  }

}
