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

package org.tentackle.model.impl;

import org.tentackle.model.Entity;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;

import java.io.File;

/**
 * Entity as a component of a model.
 *
 * @author harald
 */
public class EntityInfoImpl implements EntityInfo {

  private final Entity entity;            // the entity
  private final String filePath;          // the file path
  private final String modelSource;       // the model source text
  private final ModelDefaults defaults;   // the model defaults
  private final File file;                // its source file
  private final long lastModified;        // the last modification time


  /**
   * Creates a model entity.
   *
   * @param entity the entity
   * @param filePath the model file's path
   * @param modelSource the model source text
   * @param defaults the model defaults
   * @throws ModelException if file does not exist or is not a regular file
   */
  public EntityInfoImpl(Entity entity, String filePath, String modelSource, ModelDefaults defaults) throws ModelException {
    this.entity = entity;
    this.filePath = filePath;
    this.modelSource = modelSource;
    this.defaults = defaults;
    file = new File(filePath);
    if (!file.exists()) {
      throw new ModelException(filePath + " does not exist");
    }
    if (!file.isFile()) {
      throw new ModelException(filePath + " is not a regular file");
    }
    lastModified = file.lastModified();
  }

  @Override
  public String toString() {
    return entity.toString();
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public String getFilePath() {
    return filePath;
  }

  @Override
  public String getModelSource() {
    return modelSource;
  }

  @Override
  public ModelDefaults getModelDefaults() {
    return defaults;
  }

  @Override
  public boolean hasChanged() {
    return file.lastModified() > lastModified;
  }

}
