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
 */

package org.tentackle.wurblet;

import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelHelper;

import org.tentackle.common.Service;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.impl.ModelImpl;

import java.io.Reader;
import java.net.URL;

/**
 * Extended model aware of heap-files and global loading exception.
 * <p>
 * Heap files are files with names starting with a dot. They are read via the Wurbelizer.<br>
 * The loading exception holds the first model error that could be associated with an entity.
 * {@link ModelWurblet} throws it only when wurbling an entity it is related to.<br>
 * Furthermore, the model uses the Wurbelizer's encoding charset.
 *
 * @author harald
 */
@Service(Model.class)   // replaces the base class
public class TentackleWurbletsModel extends ModelImpl {

  /**
   * Creates a model.
   *
   * @param name the model name
   * @throws ModelException if the model defaults or entity aliases given as system properties are malformed
   */
  public TentackleWurbletsModel(String name) throws ModelException {
    super(name);
    // use the charset from the wurbelizer plugin
    org.tentackle.common.Settings.setEncodingCharset(org.wurbelizer.misc.Settings.getEncodingCharset());
  }


  /**
   * Exception from loading the whole model.
   */
  private WurbelException loadingException;

  @Override
  protected Reader createReader(URL url) throws ModelException {
    String path = url.getPath();
    if ("file".equals(url.getProtocol()) && path.startsWith(".")) {
      try {
        return WurbelHelper.openReader(path);   // heap file
      }
      catch (WurbelException wex) {
        throw new ModelException("cannot create reader for " + path, wex);
      }
    }
    else {
      return super.createReader(url);
    }
  }

  /**
   * Gets the exception that occurred during the initial loading of the model.
   *
   * @return the exception, null if none
   */
  public WurbelException getLoadingException() {
    return loadingException;
  }

  /**
   * Sets the exception that occurred during the initial loading of the model.
   *
   * @param loadingException the exception, null if none
   */
  public void setLoadingException(WurbelException loadingException) {
    this.loadingException = loadingException;
  }

}
