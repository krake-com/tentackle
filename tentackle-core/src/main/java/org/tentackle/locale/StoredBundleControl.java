/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.locale;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * Bundle control to use stored bundles.
 *
 * @author harald
 */
public class StoredBundleControl extends ResourceBundle.Control {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(StoredBundleControl.class);

  @Override
  public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
          throws IllegalAccessException, InstantiationException, IOException {

//    String bundleName = toBundleName(baseName, locale);
//    String resourceName = toResourceName(bundleName, format);
//    LOGGER.info("bundleName = {0}, resourceName = {1}", bundleName, resourceName);
    return super.newBundle(baseName, locale, format, loader, reload);
  }

}
