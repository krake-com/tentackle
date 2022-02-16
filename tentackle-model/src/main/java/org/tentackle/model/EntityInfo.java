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

import java.io.File;
import java.net.URL;

/**
 * Entity model information.
 *
 * @author harald
 */
public interface EntityInfo {

  /**
   * Gets the entity.
   *
   * @return the entity
   */
  Entity getEntity();

  /**
   * Gets the URL of the model file.
   *
   * @return the URL
   */
  URL getURL();

  /**
   * Gets the model file.
   *
   * @return the file
   * @throws ModelException if the URL does not represent a file in the filesystem
   */
  File getFile() throws ModelException;

  /**
   * Gets the source text of the model for this entity.
   *
   * @return the source text
   */
  String getModelSource();

  /**
   * Gets the model defaults used when the entity was created from the model source.
   *
   * @return the defaults, null if none
   */
  ModelDefaults getModelDefaults();

  /**
   * Returns whether the model file has changed.
   *
   * @return true if changed, always false if URL is not a file
   */
  boolean hasChanged();

}
