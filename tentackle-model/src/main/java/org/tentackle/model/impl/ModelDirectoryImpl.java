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

import org.tentackle.model.EntityAliases;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelDirectory;
import org.tentackle.model.ModelException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of a {@link ModelDirectory}.
 *
 * @author harald
 */
public class ModelDirectoryImpl implements ModelDirectory {

  private final File dir;                       // the directory file
  private final String path;                    // canonical path
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
    try {
      path = dir.getCanonicalPath();
    }
    catch (IOException e) {
      throw new ModelException("cannot determine path of model directory " + dir, e);
    }
    lastModified = dir.lastModified();
    modelDefaults = defaults;
    entityAliases = aliases;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public List<URL> getURLs() throws ModelException {
    List<URL> paths = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && !file.isHidden()) {
          try {
            paths.add(new File(path, file.getName()).toURI().toURL());
          }
          catch (MalformedURLException e) {
            throw new ModelException("cannot convert file '" + file + "' to URL", e);
          }
        }
      }
    }
    return paths;
  }

  @Override
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  @Override
  public EntityAliases getEntityAliases() {
    return entityAliases;
  }

  @Override
  public boolean hasChanged() {
    return dir.lastModified() > lastModified;
  }

  @Override
  public void markDirty() {
    lastModified = 0;
  }

}
