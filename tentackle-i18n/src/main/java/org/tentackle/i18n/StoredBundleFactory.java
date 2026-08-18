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
import java.util.MissingResourceException;
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
   * Controls whether loading bundles from the backend is enabled at all.<br>
   * Volatile: switched at runtime from arbitrary threads.
   */
  private static volatile boolean enabled = true;

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
    ResourceBundle bundle = bundleMap.get(topKey);
    if (bundle == null) {
      // Load outside the map, never from within computeIfAbsent: loading walks the whole parent chain,
      // and each step hits the database or even a remote server. A ConcurrentHashMap holds the bin lock
      // while the mapping function runs, which would block unrelated lookups mapped to the same bin for
      // the duration of the query. Worse, anything reached from the load may look up a bundle itself,
      // for example, for a validation or error message, and re-entering the map from within its own
      // mapping function is answered with an IllegalStateException ("Recursive update").
      StoredResourceBundle storedBundle;
      try {
        storedBundle = loadStoredBundleChain(topKey, control::loadStoredBundle);
      }
      catch (RuntimeException rx) {
        // The backend is unreachable or the query failed. Texts must keep working nevertheless, all the
        // more since the message of the very exception being reported is itself looked up in a bundle.
        // The fallback is deliberately not cached: it must not outlive the failure.
        LOGGER.warning("loading stored bundle " + topKey.name() + " failed -> falling back to properties", rx);
        return super.getBundle(support, locale);
      }
      if (storedBundle == null) {
        // nothing stored at all
        if (!StoredBundleControl.isFallbackToProperties()) {
          throw new MissingResourceException("no stored bundle for " + topKey.name() +
                                             ", locale " + locale, topKey.name(), "");
        }
        bundle = super.getBundle(support, locale);
      }
      else {
        // Let keys that none of the stored bundles defines fall through to the property files, the same
        // way the standard ResourceBundle machinery does for the non-modular path. Without it, a key
        // added to a property file but not yet pushed to the database would be unresolvable.
        if (StoredBundleControl.isFallbackToProperties()) {
          appendPropertiesFallback(storedBundle, support, locale);
        }
        bundle = storedBundle;
      }
      ResourceBundle cachedBundle = bundleMap.putIfAbsent(topKey, bundle);
      if (cachedBundle != null) {
        // another thread was faster: use its bundle and drop ours.
        // Loading is idempotent, so both are equivalent and all callers end up with the same instance.
        bundle = cachedBundle;
      }
    }
    return bundle;
  }

  /**
   * Appends the bundle loaded from the property files to the end of a chain of stored bundles.
   * <p>
   * Does nothing if there is no property file at all: a bundle may well live only in the database.
   *
   * @param storedBundle the head of the chain of stored bundles
   * @param support the bundle support
   * @param locale the locale
   */
  private void appendPropertiesFallback(StoredResourceBundle storedBundle, BundleSupport support, Locale locale) {
    try {
      storedBundle.getLastStoredParent().setParentBundle(super.getBundle(support, locale));
    }
    catch (MissingResourceException mrx) {
      LOGGER.fine("no properties for {0} -> stored bundles only", support.getBundleName());
    }
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
          currentBundle.setParentBundle(parentBundle);
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
   * Clears the caches only once, on the first lookup that sees a session. Two threads racing here
   * would simply clear twice, which is harmless.
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
