/**
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

import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.i18n.pdo.StoredBundleKey;
import org.tentackle.i18n.pdo.StoredBundleDomain;
import java.util.Iterator;
import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.pdo.AbstractDomainObject;
import org.tentackle.pdo.DomainObjectService;

/**
 * StoredBundle domain implementation.
 *
 * @author harald
 */
@DomainObjectService(StoredBundle.class)
public class StoredBundleDomainImpl extends AbstractDomainObject<StoredBundle, StoredBundleDomainImpl> implements StoredBundleDomain {

  private static final long serialVersionUID = 1L;


  public StoredBundleDomainImpl(StoredBundle pdo) {
    super(pdo);
  }

  public StoredBundleDomainImpl() {
    super();
  }

  @Override
  public StoredBundleUDK getUniqueDomainKey() {
    return new StoredBundleUDK(pdo());
  }

  @Override
  public StoredBundle findByUniqueDomainKey(Object domainKey) {
    StoredBundleUDK udk = (StoredBundleUDK) domainKey;
    return pdo().findByNameAndLocale(udk.name, udk.locale);
  }

  @Override
  public String toString() {
    return pdo().getName() + "[" + pdo().getLocale() + "]";
  }

  @Override
  public String getTranslation(String key) {
    for (StoredBundleKey bundleKey: pdo().getKeys()) {
      if (key.equals(bundleKey.getKey())) {
        return bundleKey.getValue();
      }
    }
    return null;
  }

  @Override
  public void setTranslation(String key, String value) {
    for (Iterator<StoredBundleKey> iter = pdo().getKeys().iterator(); iter.hasNext(); ) {
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
    pdo().getKeys().add(bundleKey);
  }

}
