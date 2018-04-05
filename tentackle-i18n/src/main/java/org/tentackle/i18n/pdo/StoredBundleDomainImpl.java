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

package org.tentackle.i18n.pdo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.pdo.DomainObjectService;

/**
 * StoredBundle domain implementation.
 *
 * @author harald
 */
@DomainObjectService(StoredBundle.class)
public class StoredBundleDomainImpl extends AbstractDomainObject<StoredBundle, StoredBundleDomainImpl> implements StoredBundleDomain {

  private static final long serialVersionUID = 1L;


  @SuppressWarnings("stateful-domain-logic")
  private transient Map<String,String> translations;    // cached translations


  public StoredBundleDomainImpl(StoredBundle pdo) {
    super(pdo);
  }

  public StoredBundleDomainImpl() {
    super();
  }

  @Override
  public StoredBundleUDK getUniqueDomainKey() {
    return new StoredBundleUDK(me());
  }

  @Override
  public StoredBundle findByUniqueDomainKey(Object domainKey) {
    StoredBundleUDK udk = (StoredBundleUDK) domainKey;
    return me().findByNameAndLocale(udk.name, udk.locale);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(me().getName());
    String locale = me().getLocale();
    if (locale != null) {
      buf.append('_').append(locale);
    }
    return buf.toString();
  }

  @Override
  public synchronized String getTranslation(String key) {
    if (translations == null) {
      translations = new HashMap<>();
      for (StoredBundleKey bundleKey: me().getKeys()) {
        translations.put(bundleKey.getKey(), bundleKey.getValue());
      }
    }
    return translations.get(key);
  }

  @Override
  public synchronized void setTranslation(String key, String value) {
    translations = null;
    for (Iterator<StoredBundleKey> iter = me().getKeys().iterator(); iter.hasNext(); ) {
      StoredBundleKey bundleKey = iter.next();
      if (key.equals(bundleKey.getKey())) {
        if (value == null) {
          iter.remove();
        }
        else {
          bundleKey.setValue(value);
        }
        return;
      }
    }
    // not found: add
    StoredBundleKey bundleKey = on(StoredBundleKey.class);
    bundleKey.setKey(key);
    bundleKey.setValue(value);
    me().getKeys().add(bundleKey);
  }

}
