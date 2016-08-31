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
import java.util.Arrays;
import java.util.List;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;

/**
 * A directory holding model files.
 *
 * @author harald
 */
public class ModelDirectory {

  private final File dir;                       // the directory file
  private final ModelDefaults modelDefaults;    // the model defaults used for that directory

  private long lastModified;                    // the last modification time


  /**
   * Creates a model directory object.
   *
   * @param modelDir the directory name
   * @param defaults the model defaults
   * @throws ModelException if directory does not exist or is not a directory
   */
  public ModelDirectory(String modelDir, ModelDefaults defaults) throws ModelException {
    dir = new File(modelDir);
    if (!dir.exists()) {
      throw new ModelException(modelDir + " does not exist");
    }
    if (!dir.isDirectory()) {
      throw new ModelException(modelDir + " is not a directory");
    }
    lastModified = dir.lastModified();
    modelDefaults = defaults;
  }


  /**
   * Gets the directory file.
   *
   * @return the file
   */
  public File getFile() {
    return dir;
  }

  /**
   * Gets the model files.
   *
   * @return the list of files, never null
   */
  public List<File> getFiles() {
    File[] files = getFile().listFiles();
    return files == null ? new ArrayList<>() : Arrays.asList(files);
  }

  /**
   * Gets the model defaults.
   *
   * @return the defaults
   */
  public ModelDefaults getModelDefaults() {
    return modelDefaults;
  }

  /**
   * Returns whether the directory has changed.
   *
   * @return true if changed
   */
  public boolean hasChanged() {
    return dir.lastModified() > lastModified;
  }

  /**
   * Marks this directory dirty.
   */
  public void markDirty() {
    lastModified = 0;
  }

}
