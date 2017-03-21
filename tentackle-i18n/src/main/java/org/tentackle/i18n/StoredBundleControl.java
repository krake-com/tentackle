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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.Session;

/**
 * Bundle control to use stored bundles.
 *
 * @author harald
 */
public class StoredBundleControl extends ResourceBundle.Control implements DomainContextProvider {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(StoredBundleControl.class);

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

    if (baseName == null || locale == null ||
             format == null || loader == null) {
      throw new NullPointerException();
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

    StoredBundle bundle = on(StoredBundle.class).selectCachedByUniqueDomainKey(new StoredBundle.StoredBundleUDK(name, loc));
    if (bundle != null) {
      LOGGER.fine("bundle {0} {1} loaded", name, loc);
      return new StoredResourceBundle(bundle);
    }

    LOGGER.fine("no bundle {0} {1}", name, loc);
    return null;
  }


  /**
   * ResourceBundle implements a wrapper for the bundle keys.
   */
  private static class StoredResourceBundle extends ResourceBundle {

    private final Properties props;

    private StoredResourceBundle(StoredBundle bundle) {
      props = new Properties();
      for (StoredBundleKey bundleKey : bundle.getKeys()) {
        props.setProperty(bundleKey.getKey(), bundleKey.getValue());
      }
    }

    @Override
    protected Object handleGetObject(String key) {
      if (key == null) {
        throw new NullPointerException();
      }
      return props.get(key);
    }

    @Override
    public Enumeration<String> getKeys() {
      ResourceBundle p = this.parent;
      return new BundleEnumeration(props.stringPropertyNames(), (p != null) ? p.getKeys() : null);
    }
  }


  /**
   * Enumeration to provide the keys.
   */
  private static class BundleEnumeration implements Enumeration<String> {

    private final Set<String> keys;
    private final Iterator<String> iterator;
    private final Enumeration<String> parentEnumeration;
    private String next = null;

    /**
     * Creates a bundle enumeration.
     *
     * @param keys the bundle keys
     * @param parentEnumeration the parent key enumeration, null if no parent
     */
    public BundleEnumeration(Set<String> keys, Enumeration<String> parentEnumeration) {
      this.keys = keys;
      this.iterator = keys.iterator();
      this.parentEnumeration = parentEnumeration;
    }

    @Override
    public boolean hasMoreElements() {
      if (next == null) {
        if (iterator.hasNext()) {
          next = iterator.next();
        }
        else if (parentEnumeration != null) {
          while (next == null && parentEnumeration.hasMoreElements()) {
            next = parentEnumeration.nextElement();
            if (keys.contains(next)) {
              next = null;
            }
          }
        }
      }
      return next != null;
    }

    @Override
    public String nextElement() {
      if (hasMoreElements()) {
        String result = next;
        next = null;
        return result;
      }
      else {
        throw new NoSuchElementException();
      }
    }
  }

}
