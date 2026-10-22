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
 * Adds the UDK property and caches the data object tree and the code configuration.
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
   * Reloads the PDO from the database.<br>
   * Clears the cached data object tree, code configuration and UDK.
   */
  public void reload() {
    PersistentDomainObject<?> reloaded = pdo.reload();
    if (reloaded != null) {
      pdo = reloaded;
      dataObject = null;
      codeConfiguration = null;
      udk = null;
    }
  }

  /**
   * Gets the data object tree.
   *
   * @param nodeFactory the factory to use for creating the tree, only used if not cached yet
   * @return the tree
   */
  public DataObject getDataObject(DataNodeFactory nodeFactory) {
    if (dataObject == null) {
      dataObject = nodeFactory.create(pdo);
    }
    return dataObject;
  }

  /**
   * Gets the code configuration.<br>
   * Creating the configuration also sets the configuration paths in the data object tree.
   *
   * @param nodeFactory the factory to use for creating the tree, only used if not cached yet
   * @return the config map, empty if no configuration available
   */
  public Map<String, Boolean> getCodeConfiguration(DataNodeFactory nodeFactory) {
    if (codeConfiguration == null) {
      codeConfiguration = CodeConfigurationFactory.createConfig(getDataObject(nodeFactory));
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
   * If the PDO implements {@link ShortLongText}, the short text followed by the long text in parentheses
   * is returned instead. If the PDO provides no unique domain key, its {@code toString()} is used.
   *
   * @return the UDK, empty string if the unique domain key is null
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
