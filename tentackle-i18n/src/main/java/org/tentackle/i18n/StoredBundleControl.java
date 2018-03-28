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

package org.tentackle.i18n;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.Session;

/**
 * Bundle control to use stored bundles.
 * <p>
 * Only used for non-modular applications! Not supported by jigsaw.
 *
 * @author harald
 */
public class StoredBundleControl extends ResourceBundle.Control implements DomainContextProvider {

  /**
   * Controls whether property file should be tried if no stored bundle found.<br>
   * Default is to fallback to
   */
  public static boolean fallbackToPropertyFile = true;

  private static final Logger LOGGER = Logger.get(StoredBundleControl.class);

  private static final List<String> FORMATS = Arrays.asList("");


  private DomainContext context;    // session local domain context

  @Override
  public DomainContext getDomainContext() {
    return context;
  }
  

  @Override
  public List<String> getFormats(String baseName) {
    if (Session.getCurrentSession() == null) {
      return super.getFormats(baseName);
    }
    if (baseName == null) {
      throw new NullPointerException();
    }
    return FORMATS;
  }

  @Override
  public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
          throws IllegalAccessException, InstantiationException, IOException {

    if (Session.getCurrentSession() == null) {
      return super.newBundle(baseName, locale, format, loader, reload);
    }

    if (context == null) {
      context = Pdo.createDomainContext();
    }

    if (baseName == null || locale == null || format == null || loader == null) {
      throw new IllegalArgumentException("baseName, locale, format and loader must not be null");
    }

    String bundleName = toBundleName(baseName, locale);
    String resourceName = toResourceName(bundleName, format);

    String name = resourceName.substring(0, resourceName.length() - 1);    // cut trailing dot
    String loc  = null;
    int ndx = name.indexOf('_');
    if (ndx >= 0) {
      loc = name.substring(ndx + 1);
      name = name.substring(0, ndx);
    }

    String locText = loc == null ? "" : loc;   // avoid null in logging below
    StoredBundle bundle = on(StoredBundle.class).selectCachedByUniqueDomainKey(new StoredBundle.StoredBundleUDK(name, loc));
    if (bundle != null) {
      LOGGER.info("stored bundle {0}[{1}] loaded", name, locText);
      return new StoredResourceBundle(bundle);
    }

    if (fallbackToPropertyFile) {
      LOGGER.fine("no stored bundle {0}[{1}] -> trying property file ...", name, locText);
      if (format.isEmpty()) {
        format = "java.properties";
      }
      ResourceBundle rb = super.newBundle(baseName, locale, format, loader, reload);
      if (rb != null) {
        LOGGER.info("property bundle {0}[{1}] loaded", name, locText);
      }
      return rb;
    }
    return null;
  }

}
