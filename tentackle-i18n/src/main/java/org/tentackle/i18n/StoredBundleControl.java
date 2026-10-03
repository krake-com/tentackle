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
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoListener;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Bundle control that loads resource bundles from the database.
 * <p>
 * {@link #newBundle} loads the {@link StoredBundle} for the requested base name and locale and wraps it in a
 * {@link StoredResourceBundle}. If nothing is stored, it falls back to the property file, unless
 * {@link #setFallbackToProperties(boolean) fallbackToProperties} is turned off. A load that fails, because the
 * backend is unreachable, for example, falls back to the property file as well, but the result is not cached
 * (see {@link #getTimeToLive}).<br>
 * As long as there is no current {@link Session} or {@link StoredBundleFactory#isEnabled() loading from the
 * backend is disabled}, the control behaves like the standard {@link ResourceBundle.Control}.
 * <p>
 * The control is handed out by the {@link StoredBundleControlProvider} in non-modular applications. {@link StoredBundleFactory} does not use it as a control, but reuses {@link #createUDK} and
 * {@link #loadStoredBundle} to load the bundles of named modules.
 *
 * @author harald
 */
public class StoredBundleControl extends ResourceBundle.Control implements DomainContextProvider {

  // volatile: switched at runtime from arbitrary threads
  private static volatile boolean fallbackToProperties = true;

  private static final Logger LOGGER = Logger.get(StoredBundleControl.class);

  private static final List<String> FORMATS = Collections.singletonList("");

  /**
   * Guards {@link #listenerRegistered}.<br>
   * Must be a lock of its own: the flag is static, but there is more than one control instance
   * (see {@link StoredBundleControlProvider} and {@link StoredBundleFactory}), so an instance
   * monitor would not keep two of them from registering the listener twice.
   */
  private static final Object LISTENER_LOCK = new Object();

  private static volatile boolean listenerRegistered;   // true if modification listener registered for bundle factory

  /**
   * Set by {@link #newBundle} if loading from the backend failed, consumed by {@link #getTimeToLive}.<br>
   * Thread-local because {@link ResourceBundle} invokes both in sequence on the same thread.
   */
  private static final ThreadLocal<Boolean> LOAD_FAILED = new ThreadLocal<>();

  /**
   * Returns whether the property files are used if a bundle or key is not stored in the database.
   * <p>
   * Applies to the bundles loaded by this control as well as to those loaded by {@link StoredBundleFactory}.
   *
   * @return true if falling back to the property files (default)
   */
  public static boolean isFallbackToProperties() {
    return fallbackToProperties;
  }

  /**
   * Sets whether the property files are used if a bundle or key is not stored in the database.
   *
   * @param fallbackToProperties true to fall back to the property files, false to throw a
   *                             {@link java.util.MissingResourceException} instead
   */
  public static void setFallbackToProperties(boolean fallbackToProperties) {
    StoredBundleControl.fallbackToProperties = fallbackToProperties;
  }



  // volatile: written under the instance lock but read without it, both here and via getDomainContext()
  private volatile DomainContext context;    // session local domain context

  /**
   * Creates a stored bundle control object.
   */
  public StoredBundleControl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }
  

  @Override
  public List<String> getFormats(String baseName) {
    return isBackendLoadable() ? FORMATS : super.getFormats(baseName);
  }

  @Override
  public long getTimeToLive(String baseName, Locale locale) {
    if (LOAD_FAILED.get() != null) {
      // The bundle just loaded is the properties fallback for a failed backend load. ResourceBundle caches
      // without expiration by default, which would pin the bundle to its property file long after the
      // backend became available again.
      // Relies on ResourceBundle invoking getTimeToLive() right after newBundle(), on the same thread.
      LOAD_FAILED.remove();
      return TTL_DONT_CACHE;
    }
    return super.getTimeToLive(baseName, locale);
  }

  @Override
  public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
          throws IllegalAccessException, InstantiationException, IOException {

    Objects.requireNonNull(baseName, "baseName");
    Objects.requireNonNull(locale, "locale");

    ResourceBundle bundle = null;
    StoredBundle.StoredBundleUDK key = null;

    LOAD_FAILED.remove();   // clear the outcome of an earlier load on this thread

    if (isBackendLoadable()) {
      key = createUDK(baseName, locale);
      try {
        bundle = loadStoredBundle(key);
      }
      catch (RuntimeException rx) {
        // The backend is unreachable or the query failed. Fall back to the properties below instead of
        // letting database hiccups break every text lookup, but remember the failure so that the
        // fallback does not get cached (see getTimeToLive).
        LOGGER.warning("loading stored bundle " + key.name() + " failed -> falling back to properties", rx);
        LOAD_FAILED.set(Boolean.TRUE);
      }
    }

    if (bundle == null && fallbackToProperties) {
      if (format == null || loader == null) {
        throw new IllegalArgumentException("format and loader must not be null");
      }
      if (format.isEmpty()) {
        format = "java.properties";
      }
      bundle = super.newBundle(baseName, locale, format, loader, reload);
      if (bundle != null && key != null) {
        LOGGER.info("no stored bundle for {0} -> resource bundle from properties loaded instead", key.name());
      }
    }

    return bundle;
  }


  /**
   * Returns whether bundles can be loaded from the backend.
   * <p>
   * Requires a current session and {@link StoredBundleFactory#isEnabled()}. The latter must be checked
   * here as well, since in non-modular applications, bundles are loaded through this control
   * and never reach the factory's own check.
   *
   * @return true if loading from the backend is possible
   */
  protected boolean isBackendLoadable() {
    return StoredBundleFactory.isEnabled() && Session.getCurrentSession() != null;
  }

  /**
   * Creates the unique domain key to load a stored bundle.
   * <p>
   * The locale suffix is derived from the locale, not by searching the bundle name for an underscore:
   * base names may contain underscores themselves, and {@code org.myapp.Texts_v2} must not be read as
   * the bundle {@code org.myapp.Texts} for the locale {@code v2}.
   *
   * @param baseName the base bundle name of the resource bundle
   * @param locale the locale
   * @return the unique domain key
   */
  protected StoredBundle.StoredBundleUDK createUDK(String baseName, Locale locale) {
    // accept slashed resource names as well as dotted ones
    String name = baseName.replace('/', '.');
    // whatever toBundleName() appends to the base name is the locale suffix
    String bundleName = toBundleName(baseName, locale);
    String loc = bundleName.length() > baseName.length() ?
                 bundleName.substring(baseName.length() + 1) :    // skip the separating '_'
                 null;
    return new StoredBundle.StoredBundleUDK(name, loc);
  }

  /**
   * Loads the bundle from storage.
   * <p>
   * A failure the backend reports as temporary (the modification tracker is not running yet, for example)
   * is treated like a missing bundle, any other exception is thrown.
   *
   * @param key the unique domain key
   * @return the stored resource bundle or null if no such bundle in the database
   */
  protected StoredResourceBundle loadStoredBundle(StoredBundle.StoredBundleUDK key) {
    if (!isBackendLoadable()) {
      // nothing can be loaded from the backend (yet), try again later...
      return null;
    }

    DomainContext domainContext = context;
    if (domainContext == null) {
      synchronized (this) {
        domainContext = context;
        if (domainContext == null) {
          domainContext = Pdo.createDomainContext();    // thread-local domain context
          context = domainContext;
        }
      }
    }

    try {
      registerModificationListener();

      StoredBundle bundle = on(StoredBundle.class).selectCachedByUniqueDomainKey(key);
      if (bundle != null) {
        LOGGER.info("stored bundle {0} loaded", bundle);
        return new StoredResourceBundle(bundle);
      }
    }
    catch (PersistenceException px) {
      if (px.isTemporary()) {
        // ModificationTracker not running yet, no session configured yet, try again later.
        // This is not a warning, since it happens regularly during client startup (login).
        LOGGER.fine("stored bundle {0} temporarily not loadable -> fallback to Properties ({1})",
                    key, px.getMessage());
      }
      else {
        throw px;
      }
    }
    return null;
  }

  /**
   * Registers the modification listener that invalidates the bundle caches, once per JVM.
   * <p>
   * We cannot do that in the constructor of {@link StoredBundleFactory} because the modification tracker
   * isn't running at application start.<br>
   * Registration is deliberately independent of the domain context: it is retried on every load until it
   * succeeds, so a listener is never lost because the context happened to be created first.
   */
  private static void registerModificationListener() {
    if (!listenerRegistered) {
      synchronized (LISTENER_LOCK) {
        if (!listenerRegistered) {
          ModificationTracker.getInstance().addModificationListener(new PdoListener(StoredBundle.class) {
            @Override
            public void dataChanged(ModificationEvent ev) {
              BundleFactory.getInstance().clearCache();
              // the PdoCache in StoredBundlePersistenceImpl is a preloading cache
            }
          });
          listenerRegistered = true;
          LOGGER.info("bundle factory modification listener registered");
        }
      }
    }
  }

}
