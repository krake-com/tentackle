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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.bind.Bindable;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.Map;

/**
 * Wrapper for a PDO.<br>
 * Adds the UDK property.
 */
public class PdoWrapper {

  private PersistentDomainObject<?> pdo;            // the PDO
  private String udk;                               // lazily created UDK
  private DataObject dataObject;                    // lazily created object tree
  private Map<String, Boolean> codeConfiguration;   // lazily created code configuration

  /**
   * Creates a wrapper.
   *
   * @param pdo the PDO
   */
  public PdoWrapper(PersistentDomainObject<?> pdo) {
    this.pdo = pdo;
  }

  /**
   * Gets the wrapped PDO.
   *
   * @return the PDO
   */
  public PersistentDomainObject<?> getPdo() {
    return pdo;
  }

  /**
   * Reloads the pdo from the database.
   */
  public void reload() {
    PersistentDomainObject<?> reloaded = pdo.reload();
    if (reloaded != null) {
      pdo = reloaded;
      dataObject = null;
    }
  }

  /**
   * Gets the data object tree.
   *
   * @return the tree
   */
  public DataObject getDataObject() {
    if (dataObject == null) {
      dataObject = DataNodeFactory.create(pdo);
    }
    return dataObject;
  }

  /**
   * Gets the code configuration.
   *
   * @return the config map, empty if no configuration available
   */
  public Map<String, Boolean> getCodeConfiguration() {
    if (codeConfiguration == null) {
      codeConfiguration = CodeConfigurationFactory.createConfig(getDataObject());
    }
    return codeConfiguration;
  }

  /**
   * Gets the object ID.
   *
   * @return the ID
   */
  @Bindable
  public long getId() {
    return pdo.getId();
  }

  /**
   * Gets the unique domain key string.<br>
   * If the PDO implements {@link ShortLongText}, the long description is added in parentheses.
   *
   * @return the UDK, empty string if none
   */
  @Bindable
  public String getUDK() {
    if (udk == null) {
      String udkStr = "";
      if (pdo instanceof ShortLongText shortLongText) {
        udkStr = shortLongText.getShortText() + " (" + shortLongText.getLongText() + ")";
      }
      else if (pdo.isUniqueDomainKeyProvided()) {
        Object uniqueDomainKey = pdo.getUniqueDomainKey();
        if (uniqueDomainKey != null) {
          udkStr = uniqueDomainKey.toString();
        }
      }
      else {
        udkStr = pdo.toString();
      }
      udk = udkStr;
    }
    return udk;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PdoWrapper that = (PdoWrapper) o;

    return pdo.equals(that.pdo);
  }

  @Override
  public int hashCode() {
    return pdo.hashCode();
  }

  @Override
  public String toString() {
    return pdo.toGenericString();
  }

}
