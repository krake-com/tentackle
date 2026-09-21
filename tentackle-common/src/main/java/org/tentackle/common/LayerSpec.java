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

import java.util.List;
import java.util.Objects;

/**
 * The declarative description of one named {@link ModuleLayer}.
 * <p>
 * A layer is defined by the module-path directories it is built from and the names of the
 * layers it is parented on. An empty {@link #parentNames()} means the layer is parented
 * directly on the boot layer.
 *
 * @param name the layer name, unique within a {@link LayerGraphSpec}
 * @param parentNames the names of the parent layers, empty if parented on the boot layer
 * @param directories the module-path directories (or exploded module directories) building this layer
 *
 * @author harald
 */
public record LayerSpec(String name, List<String> parentNames, List<java.nio.file.Path> directories) {

  public LayerSpec {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(parentNames, "parentNames");
    Objects.requireNonNull(directories, "directories");
    if (name.isBlank()) {
      throw new TentackleRuntimeException("layer name must not be blank");
    }
    if (directories.isEmpty()) {
      throw new TentackleRuntimeException("layer '" + name + "' has no directories");
    }
    parentNames = List.copyOf(parentNames);
    directories = List.copyOf(directories);
  }

}
