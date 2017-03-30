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
import java.util.ArrayList;
import java.util.List;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;

/**
 * Default implementation of a {@link ModelDirectory}.
 *
 * @author harald
 */
public class ModelDirectoryImpl implements ModelDirectory {

  private final File dir;                       // the directory file
  private final ModelDefaults modelDefaults;    // the model defaults used for that directory
  private final EntityAliases entityAliases;    // the aliases used for that directory

  private long lastModified;                    // the last modification time


  /**
   * Creates a model directory object.
   *
   * @param modelDir the directory name
   * @param defaults optional model defaults, null if none
   * @param aliases optional entity aliases, null if none
   * @throws ModelException if directory does not exist or is not a directory
   */
  public ModelDirectoryImpl(String modelDir, ModelDefaults defaults, EntityAliases aliases) throws ModelException {
    dir = new File(modelDir);
    if (!dir.exists()) {
      throw new ModelException(modelDir + " does not exist");
    }
    if (!dir.isDirectory()) {
      throw new ModelException(modelDir + " is not a directory");
    }
    lastModified = dir.lastModified();
    modelDefaults = defaults;
    entityAliases = aliases;
  }

  /**
   * Gets the directory path.
   *
   * @return the path
   */
  @Override
  public String getPath() {
    return dir.getAbsolutePath();
  }

  /**
   * Gets the model file names.
   *
   * @return the list of names, never null
   */
  @Override
  public List<String> getFileNames() {
    List<String> names = new ArrayList<>();
    for (File file: dir.listFiles()) {
      names.add(file.getAbsolutePath());
    }
    return names;
  }

  /**
   * Gets the model defaults.
   *
   * @return the defaults, null if none
   */
  @Override
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  /**
   * Gets the entity aliases.
   *
   * @return the aliases, null if none
   */
  @Override
  public EntityAliases getEntityAliases() {
    return entityAliases;
  }

  /**
   * Returns whether the directory has changed.
   *
   * @return true if changed
   */
  @Override
  public boolean hasChanged() {
    return dir.lastModified() > lastModified;
  }

  /**
   * Marks this directory dirty.
   */
  @Override
  public void markDirty() {
    lastModified = 0;
  }

}
