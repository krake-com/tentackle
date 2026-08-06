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

import org.tentackle.common.BundleFactory;
import org.tentackle.common.BundleSupport;
import org.tentackle.common.DefaultBundleFactory;
import org.tentackle.common.Service;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.log.Logger;
import org.tentackle.session.Session;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory for stored bundles.<br>
 * Replaces the {@link DefaultBundleFactory}.
 */
@Service(BundleFactory.class)
public class StoredBundleFactory extends DefaultBundleFactory {

  private static final Logger LOGGER = Logger.get(StoredBundleFactory.class);

  /**
   * Controls whether loading bundles from the backend is enabled at all.
   */
  private static boolean enabled = true;

  /**
   * Returns whether loading bundles from the backend is enabled at all.
   *
   * @return true if enabled (default)
   */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether loading bundles from the backend is enabled at all.
   *
   * @param enabled true if enabled (default)
   */
  public static void setEnabled(boolean enabled) {
    StoredBundleFactory.enabled = enabled;
  }


  private final Map<StoredBundle.StoredBundleUDK, ResourceBundle> bundleMap;      // bundlename_locale..._... : stored bundle
  private final StoredBundleControl control;                                      // bundle control used to load the stored bundle
  private volatile boolean sessionSeen;                                           // true once a session has become available

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
  public ResourceBundle findBundle(String baseName, Locale locale) {
    discardBundlesCachedWithoutSession();
    return super.findBundle(baseName, locale);
  }

  @Override
  public ResourceBundle findBundle(String baseName, Locale locale, ClassLoader loader) {
    discardBundlesCachedWithoutSession();
    return super.findBundle(baseName, locale, loader);
  }


  @Override
  protected ResourceBundle getBundle(BundleSupport support, Locale locale) {
    if (!enabled || Session.getCurrentSession() == null) {
      // Without a session nothing can be loaded from the backend. Fall back to the standard ResourceBundle,
      // but don't remember it: the stored bundles must take over as soon as a session becomes available.
      return super.getBundle(support, locale);
    }

    StoredBundle.StoredBundleUDK topKey = control.createUDK(support.getBundleName(), locale);
    return bundleMap.computeIfAbsent(topKey, storedBundleUDK -> {
      StoredResourceBundle bundle = loadStoredBundleChain(storedBundleUDK, control::loadStoredBundle);
      // fallback to standard ResourceBundle if nothing stored at all
      return bundle == null ? super.getBundle(support, locale) : bundle;
    });
  }

  /**
   * Loads the stored bundle for a key and chains the stored bundles of the parent locales to it.
   * <p>
   * Locales without a stored bundle are skipped, both when looking for the bundle itself and when linking
   * its parents, just as {@link ResourceBundle} does for its list of candidate locales. A stored
   * {@code Texts_de_DE}, for example, gets a stored {@code Texts} as its parent if there is no
   * {@code Texts_de}.
   *
   * @param topKey the key of the requested bundle
   * @param loader loads the stored bundle for a key, returns null if there is no such bundle
   * @return the bundle with its parents linked, null if nothing is stored for the key or any of its parent locales
   */
  protected StoredResourceBundle loadStoredBundleChain(StoredBundle.StoredBundleUDK topKey,
                                                       Function<StoredBundle.StoredBundleUDK, StoredResourceBundle> loader) {
    StoredBundle.StoredBundleUDK key = topKey;
    StoredResourceBundle bundle = loader.apply(key);
    while (bundle == null && (key = createParentKey(key)) != null) {
      // try parent
      bundle = loader.apply(key);
    }
    if (bundle != null) {
      // set parents, if any
      StoredResourceBundle currentBundle = bundle;
      while ((key = createParentKey(key)) != null) {
        StoredResourceBundle parentBundle = loader.apply(key);
        if (parentBundle != null) {
          currentBundle.setParent(parentBundle);
          currentBundle = parentBundle;
        }
        // else: nothing stored for that locale -> skip the level and link the next bundle found
      }
    }
    return bundle;
  }


  /**
   * Discards all bundles cached while no session was available.
   * <p>
   * As long as there is no session, nothing can be loaded from the backend and bundle lookups fall back to
   * the property files. Such results must not survive the transition, because otherwise the stored bundles
   * would never be picked up for bundles already looked up during application startup,
   * for example, by the login dialog.
   * <p>
   * Must not be invoked while a bundle is being computed in {@link #bundleMap}, since clearing a
   * {@link ConcurrentHashMap} from within one of its own mapping functions is not allowed.
   */
  private void discardBundlesCachedWithoutSession() {
    if (enabled && !sessionSeen && Session.getCurrentSession() != null) {
      sessionSeen = true;
      clearCache();
      LOGGER.info("bundle caches cleared: stored bundles are available from now on");
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
    if (key.locale() != null && !key.locale().isEmpty()) {
      String parentLocaleName;
      int ndx = key.locale().lastIndexOf('_');
      if (ndx > 0) {
        parentLocaleName = key.locale().substring(0, ndx);
      }
      else {
        parentLocaleName = null;
      }
      parentKey = new StoredBundle.StoredBundleUDK(key.name(), parentLocaleName);
    }
    return parentKey;
  }

}
