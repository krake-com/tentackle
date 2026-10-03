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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.common.StringHelper;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * The PDO type used by the entity combobox of the {@link org.tentackle.maven.plugin.wizard.fx.PdoBrowser}.
 */
public class PdoType {

  private final String name;
  private final Class<? extends PersistentDomainObject<?>> clazz;
  private String camelCase;

  /**
   * Creates a PDO type.
   *
   * @param name the displayed name
   * @param clazz the effective PDO interface
   */
  public PdoType(String name, Class<? extends PersistentDomainObject<?>> clazz) {
    this.name = name;
    this.clazz = clazz;
  }

  /**
   * Creates a PDO type from a PDO.<br>
   * The name is the simple name of the PDO interface, as in the entity combobox.
   *
   * @param pdo the PDO
   */
  public PdoType(PersistentDomainObject<?> pdo) {
    this(pdo.getEffectiveClass().getSimpleName(), pdo.getEffectiveClass());
  }

  /**
   * The displayed name.
   *
   * @return the name visible in the combobox
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * The PDO interface class.
   *
   * @return the effective PDO class
   */
  public Class<? extends PersistentDomainObject<?>> getClazz() {
    return clazz;
  }

  /**
   * The camel-case letters derived from the displayed name.<br>
   * Used for sorting and rendering in the combobox.
   *
   * @return the camel-case letters
   */
  public String getCamelCaseLetters() {
    if (camelCase == null) {
      camelCase = StringHelper.filterUpperCase(getName());
    }
    return camelCase;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PdoType pdoType = (PdoType) o;

    return name.equals(pdoType.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

}
