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

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.log.Logger;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * A {@link ResourceBundle} holding the translations of a {@link StoredBundle}.
 * <p>
 * The translations are copied into an immutable map when the bundle is created, so lookups need no locking
 * and later modifications of the {@link StoredBundle} are not reflected. Bundles of parent locales and the
 * property-file fallback are chained as parent bundles.
 *
 * @author harald
 */
public class StoredResourceBundle extends ResourceBundle {

  private static final Logger LOGGER = Logger.get(StoredResourceBundle.class);

  private final Map<String, String> translations;   // immutable: read concurrently, never modified
  private final String name;

  /**
   * Creates a resource bundle from a stored bundle.
   *
   * @param bundle the stored bundle
   */
  public StoredResourceBundle(StoredBundle bundle) {
    name = bundle.toString();
    Map<String, String> map = new HashMap<>();
    for (StoredBundleKey bundleKey : bundle.getKeys()) {
      String key = bundleKey.getKey();
      String value = bundleKey.getValue();
      if (key != null && value != null) {
        map.put(key, value);
      }
      else {
        // both are @NotNull in the model, so the row must have reached the database without validation.
        // Skip it: a single bad row must not take down all texts of this bundle.
        LOGGER.warning("stored bundle {0} contains an entry without {1} -> translation ignored",
                       name, key == null ? "a key" : "a value");
      }
    }
    translations = Map.copyOf(map);
  }

  /**
   * Sets the parent bundle.
   * <p>
   * Cannot be named {@code setParent}: {@link ResourceBundle#setParent(ResourceBundle)} is protected,
   * and an override may not reduce its visibility.
   *
   * @param parent the parent bundle, a stored bundle of the next locale or the property-file fallback
   */
  protected void setParentBundle(ResourceBundle parent) {
    super.setParent(parent);
  }

  /**
   * Gets the last stored bundle in the chain of parents.
   * <p>
   * That is the bundle of the least specific locale, i.e. the one a fallback to the property files
   * has to be appended to.
   *
   * @return the last stored bundle, this if it has no stored parent
   */
  protected StoredResourceBundle getLastStoredParent() {
    StoredResourceBundle last = this;
    while (last.parent instanceof StoredResourceBundle storedParent) {
      last = storedParent;
    }
    return last;
  }

  @Override
  protected Object handleGetObject(String key) {
    return translations.get(Objects.requireNonNull(key, "key"));
  }

  @Override
  public Enumeration<String> getKeys() {
    ResourceBundle p = this.parent;
    return new BundleEnumeration(translations.keySet(), p != null ? p.getKeys() : null);
  }

  @Override
  public String toString() {
    return name;
  }

}
