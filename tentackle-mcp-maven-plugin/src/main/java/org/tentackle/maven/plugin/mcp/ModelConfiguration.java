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

package org.tentackle.maven.plugin.mcp;

import org.tentackle.model.EntityAliases;
import org.tentackle.model.ModelDefaults;

import java.util.List;

/**
 * Everything the {@link ModelSession} needs to load a model.
 * <p>
 * Extracted from the mojo parameters so that the session can be created in a test without a maven session.
 *
 * @param modelName the model name, see {@link org.tentackle.model.ModelManager#DEFAULT_MODEL_NAME}
 * @param modelDirs the model directories, in load order (dependency models first)
 * @param fromResources true to additionally load the model from the plugin's classpath resources
 * @param modelDefaults the project-wide model defaults, null if none
 * @param entityAliases the fixed table aliases, null if none
 * @param schemaNameMapped true if schema names are mapped
 * @param backendNames the comma-separated backend names to validate against, null for none
 *
 * @author harald
 */
public record ModelConfiguration(String modelName,
                                 List<String> modelDirs,
                                 boolean fromResources,
                                 ModelDefaults modelDefaults,
                                 EntityAliases entityAliases,
                                 boolean schemaNameMapped,
                                 String backendNames) {

  /**
   * Creates a configuration.
   *
   * @param modelName the model name
   * @param modelDirs the model directories, in load order
   * @param fromResources true to additionally load from the classpath resources
   * @param modelDefaults the model defaults, null if none
   * @param entityAliases the entity aliases, null if none
   * @param schemaNameMapped true if schema names are mapped
   * @param backendNames the comma-separated backend names, null for none
   */
  public ModelConfiguration {
    modelDirs = List.copyOf(modelDirs);
  }

}
