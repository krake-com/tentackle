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

import java.util.Enumeration;
import java.util.Properties;
import java.util.ResourceBundle;
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;

/**
 * ResourceBundle loaded from the database.
 *
 * @author harald
 */
public class StoredResourceBundle extends ResourceBundle {

  private final Properties props;
  private final String name;

  /**
   * Creates a resource bundle from a stored bundle.
   *
   * @param bundle the stored bundle
   */
  public StoredResourceBundle(StoredBundle bundle) {
    props = new Properties();
    name = bundle.toString();
    for (StoredBundleKey bundleKey : bundle.getKeys()) {
      props.setProperty(bundleKey.getKey(), bundleKey.getValue());
    }
  }

  /**
   * Sets the parent stored bundle.
   *
   * @param parent the parent bundle
   */
  void setParent(StoredResourceBundle parent) {
    super.setParent(parent);
  }

  @Override
  protected Object handleGetObject(String key) {
    if (key == null) {
      throw new NullPointerException("key is null");
    }
    return props.get(key);
  }

  @Override
  public Enumeration<String> getKeys() {
    ResourceBundle p = this.parent;
    return new BundleEnumeration(props.stringPropertyNames(), p != null ? p.getKeys() : null);
  }

  @Override
  public String toString() {
    return name;
  }

}
