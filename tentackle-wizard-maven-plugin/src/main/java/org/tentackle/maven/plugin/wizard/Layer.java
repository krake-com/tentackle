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

import java.util.List;

/**
 * A named, isolated {@code ModuleLayer} for {@link AbstractWizardMojo#getLayers()}.
 * <p>
 * Unlike a project's own dependencies, a layer's {@link #getDependencies()} are resolved
 * <em>independently</em>, via Maven's own dependency resolver, exactly as if the layer were its
 * own tiny project. This is what makes it possible for two layers to each pin a different,
 * otherwise conflicting version of the same dependency: Maven itself only ever resolves one
 * version per {@code groupId:artifactId} within a single project's own graph, so two versions
 * can never both appear in the plugin's own {@code <dependencies>} in the first place.
 *
 * @author harald
 */
public class Layer {

  private String name;
  private String parent;
  private List<Dependency> dependencies;

  /**
   * Gets the layer's name, unique among all configured layers.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the layer's name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the comma-separated names of this layer's own parent layers.
   *
   * @return the parent layer names, null or empty if parented directly on the boot layer
   */
  public String getParent() {
    return parent;
  }

  /**
   * Sets the comma-separated names of this layer's own parent layers.
   *
   * @param parent the parent layer names, null or empty for the boot layer
   */
  public void setParent(String parent) {
    this.parent = parent;
  }

  /**
   * Gets the layer's own dependencies, resolved independently (with their own transitive closure)
   * via Maven's dependency resolver.
   *
   * @return the dependencies, each with an explicit groupId, artifactId, and version
   */
  public List<Dependency> getDependencies() {
    return dependencies;
  }

  /**
   * Sets the layer's own dependencies.
   *
   * @param dependencies the dependencies
   */
  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
  }

  @Override
  public String toString() {
    return name;
  }

}
