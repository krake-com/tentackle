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
 *
 */

package org.tentackle.model;

import java.net.URL;
import java.util.List;

/**
 * A directory holding model files.<br>
 * The model directory is capable of detecting changes, usually model files added by the Wurbelizer.
 *
 * @author harald
 */
public interface ModelDirectory {

  /**
   * Gets the directory path.
   *
   * @return the canonical path
   */
  String getPath();

  /**
   * Gets the model file URLs.<br>
   * Hidden files are ignored.
   *
   * @return the list of URLs, never null
   * @throws ModelException if a file cannot be converted to a URL
   */
  List<URL> getURLs() throws ModelException;

  /**
   * Gets the model defaults.
   *
   * @return the defaults, null if none
   */
  ModelDefaults getModelDefaults();

  /**
   * Gets the entity aliases.
   *
   * @return the aliases, null if none
   */
  EntityAliases getEntityAliases();

  /**
   * Returns whether the directory has changed since it was scanned, i.e. files were added or removed.<br>
   * Changes of the file contents are detected per entity by {@link EntityInfo#hasChanged()}.
   *
   * @return true if changed
   */
  boolean hasChanged();

  /**
   * Marks this directory dirty.
   */
  void markDirty();

}
