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

package org.tentackle.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.BundleFactory;
import org.tentackle.common.BundleSupport;
import org.tentackle.common.DefaultBundleFactory;
import org.tentackle.common.Service;
import org.tentackle.i18n.pdo.StoredBundle;

/**
 * Factory for stored bundles.<br>
 * Replaces the {@link DefaultBundleFactory}.
 */
@Service(BundleFactory.class)
public class StoredBundleFactory extends DefaultBundleFactory {

  /**
   * Controls whether loading bundles from backend is enabled at all.
   */
  private static boolean enabled = true;

  /**
   * Returns whether loading bundles from backend is enabled at all.
   *
   * @return true if enabled (default)
   */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether loading bundles from backend is enabled at all.
   *
   * @param enabled true if enabled (default)
   */
  public static void setEnabled(boolean enabled) {
    StoredBundleFactory.enabled = enabled;
  }


  private final Map<StoredBundle.StoredBundleUDK, ResourceBundle> bundleMap;      // bundlename_locale..._... : stored bundle
  private final StoredBundleControl control;                                      // bundle control used to load the stored bundle

  /**
   * Creates the bundle factory.
   */
  public StoredBundleFactory() {
    bundleMap = new ConcurrentHashMap<>();
    control = new StoredBundleControl();
  }

  @Override
  public void clearCache() {
    super.clearCache();
    bundleMap.clear();
  }

  @Override
  protected ResourceBundle getBundle(BundleSupport support, Locale locale) {
    if (enabled) {
      StoredBundle.StoredBundleUDK topKey = control.createUDK(support.getBundleName(), locale);
      return bundleMap.computeIfAbsent(topKey, storedBundleUDK -> {
        StoredBundle.StoredBundleUDK key = storedBundleUDK;
        StoredResourceBundle bundle = control.loadStoredBundle(key);
        while (bundle == null && (key = createParentKey(key)) != null) {
          // try parent
          bundle = control.loadStoredBundle(key);
        }
        if (bundle == null) {
          // fallback to standard ResourceBundle
          return super.getBundle(support, locale);
        }
        // set parents, if any
        StoredResourceBundle currentBundle = bundle;
        while ((key = createParentKey(key)) != null) {
          StoredResourceBundle parentBundle = control.loadStoredBundle(key);
          if (parentBundle == null) {
            break;
          }
          currentBundle.setParent(parentBundle);
          currentBundle = parentBundle;
        }
        return bundle;
      });
    }
    else {
      return super.getBundle(support, locale);
    }
  }

  /**
   * Creates a bundle key for the parent locale.
   *
   * @param key the bundle key
   * @return the parent key, null if key is already for the topmost default locale
   */
  private StoredBundle.StoredBundleUDK createParentKey(StoredBundle.StoredBundleUDK key) {
    StoredBundle.StoredBundleUDK parentKey = null;
    if (key.locale != null && !key.locale.isEmpty()) {
      String parentLocaleName;
      int ndx = key.locale.lastIndexOf('_');
      if (ndx > 0) {
        parentLocaleName = key.locale.substring(0, ndx);
      }
      else {
        parentLocaleName = null;
      }
      parentKey = new StoredBundle.StoredBundleUDK(key.name, parentLocaleName);
    }
    return parentKey;
  }
}
