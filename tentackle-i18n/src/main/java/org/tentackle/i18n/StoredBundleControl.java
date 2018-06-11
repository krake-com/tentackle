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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.tentackle.common.BundleFactory;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoListener;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;

/**
 * Bundle control to use stored bundles.<br>
 * Used by {@link StoredBundleFactory} in modular apps as well as by {@link ResourceBundle} in non-modular apps.
 *
 * @author harald
 */
public class StoredBundleControl extends ResourceBundle.Control implements DomainContextProvider {

  /**
   * Controls whether loading bundles from backend is enabled at all.
   */
  public static boolean enabled = true;

  /**
   * Controls whether properties should be tried if no stored bundle found.
   */
  public static boolean fallbackToProperties = true;

  private static final Logger LOGGER = Logger.get(StoredBundleControl.class);

  private static final List<String> FORMATS = Collections.singletonList("");


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

    if (baseName == null || locale == null) {
      throw new IllegalArgumentException("baseName and locale must not be null");
    }

    ResourceBundle bundle = null;
    StoredBundle.StoredBundleUDK key = null;

    if (enabled && Session.getCurrentSession() != null) {
      // if loading from backend possible
      key = createUDK(baseName, locale);
      bundle = loadStoredBundle(key);
    }

    if (bundle == null && (!enabled || fallbackToProperties)) {
      if (format == null || loader == null) {
        throw new IllegalArgumentException("format and loader must not be null");
      }
      if (format.isEmpty()) {
        format = "java.properties";
      }
      bundle = super.newBundle(baseName, locale, format, loader, reload);
      if (bundle != null && key != null) {
        LOGGER.info("no stored bundle for {0} -> resource bundle from properties loaded instead", key.name);
      }
    }

    return bundle;
  }


  /**
   * Creates the unique domain key to load a stored bundle.
   *
   * @param baseName the base bundle name of the resource bundle
   * @param locale the locale
   * @return the unique domain key
   */
  protected StoredBundle.StoredBundleUDK createUDK(String baseName, Locale locale) {
    String bundleName = toBundleName(baseName, locale);
    String resourceName = toResourceName(bundleName, "");
    String name = resourceName.substring(0, resourceName.length() - 1);    // cut trailing dot
    String loc  = null;
    int ndx = name.indexOf('_');
    if (ndx >= 0) {
      loc = name.substring(ndx + 1);
      name = name.substring(0, ndx);
    }
    return new StoredBundle.StoredBundleUDK(name, loc);
  }

  /**
   * Loads the bundle from storage.
   *
   * @param key the unique domain key
   * @return the stored resource bundle or null if no such bundle in database
   */
  protected StoredResourceBundle loadStoredBundle(StoredBundle.StoredBundleUDK key) {
    synchronized (this) {
      if (context == null) {
        Session session = Session.getCurrentSession();
        if (session == null) {
          return null;    // fallback to Resource Bundle
        }
        context = Pdo.createDomainContext(session);

        // we cannot do that in the constructor of StoredBundleFactory because the modtracker isnt running at
        // application start
        ModificationTracker.getInstance().addModificationListener(new PdoListener(StoredBundle.class) {
          @Override
          public void dataChanged(ModificationEvent ev) {
            BundleFactory.getInstance().clearCache();
            // the PdoCache in StoredBundlePersistenceImpl is a non-preloading cache, i.e.
            // only the changed or added bundles are effectively reloaded from the database.
          }
        });
      }
    }
    StoredBundle bundle = on(StoredBundle.class).selectCachedByUniqueDomainKey(key);
    if (bundle != null) {
      LOGGER.info("stored bundle {0} loaded", bundle);
      return new StoredResourceBundle(bundle);
    }
    return null;
  }

}
