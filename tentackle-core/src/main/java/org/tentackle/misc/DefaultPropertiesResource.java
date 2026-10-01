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

package org.tentackle.misc;

import org.tentackle.common.FileHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Default properties resource based on {@link org.tentackle.common.FileHelper}.<br>
 * The name is first tried as a file and, if there is no such file, as a classpath resource.
 */
@PropertiesResourceService    // default priority (100, if this is the only resource)
public class DefaultPropertiesResource implements PropertiesResource {

  private static final Logger LOGGER = Logger.get(DefaultPropertiesResource.class);

  /**
   * Creates the default loader.
   */
  public DefaultPropertiesResource() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public boolean apply(String name, Properties properties) {
    try {
      try {
        FileHelper.loadProperties(name, false, properties);
        LOGGER.fine("properties {0} loaded from file", name);
      }
      catch (FileNotFoundException fx1) {
        try {
          // try as resource
          FileHelper.loadProperties(name, true, properties);
          LOGGER.fine("properties {0} loaded from resource", name);
        }
        catch (FileNotFoundException fx2) {
          LOGGER.fine("properties {0} not found: {1}, and {2}", name, fx1.getMessage(), fx2.getMessage());
          return false;
        }
      }
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("loading properties " + name + " failed", iox);
    }
    return true;
  }

}
