/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.model.impl;

import java.io.File;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;

/**
 * Entity as a component of a model.
 *
 * @author harald
 */
public class ModelEntity {

  private final Entity entity;      // the entity
  private final String fileName;    // the filename
  private final File file;          // its source file
  private final long lastModified;  // the last modification time


  /**
   * Creates a model entity.
   *
   * @param entity the entity
   * @param fileName the modelfile's filename
   * @throws ModelException if file does not exist or is not a regular file
   */
  public ModelEntity(Entity entity, String fileName) throws ModelException {
    this.entity = entity;
    this.fileName = fileName;
    file = new File(fileName);
    if (!file.exists()) {
      throw new ModelException(fileName + " does not exist");
    }
    if (!file.isFile()) {
      throw new ModelException(fileName + " is not a regular file");
    }
    lastModified = file.lastModified();
  }

  /**
   * Gets the entity.
   *
   * @return the entity
   */
  public Entity getEntity() {
    return entity;
  }

  /**
   * Gets the filename.
   *
   * @return the filename
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Gets the model file.
   *
   * @return the file
   */
  public File getFile() {
    return file;
  }

  /**
   * Returns whether the modelfile has changed.
   *
   * @return true if changed
   */
  public boolean hasChanged() {
    return file.lastModified() > lastModified;
  }

}
