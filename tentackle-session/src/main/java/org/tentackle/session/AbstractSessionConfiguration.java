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

package org.tentackle.session;

import org.tentackle.bind.Bindable;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.validator.NotNull;

import java.util.prefs.Preferences;

/**
 * Base class for {@link BackendConfiguration} and {@link DriverConfiguration}.
 */
public abstract class AbstractSessionConfiguration implements Validateable, ScopeConfigurator {

  private boolean persisted;      // loaded from preferences
  private String name;            // the config name


  /**
   * Parent constructor.
   */
  public AbstractSessionConfiguration() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the symbolic name.
   *
   * @return the name
   */
  @Bindable
  @NotNull
  public String getName() {
    return name;
  }

  /**
   * Sets the symbolic name.
   *
   * @param name the name
   */
  @Bindable
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns whether the configuration is loaded from preferences.
   *
   * @return true if persisted, false if just created in memory only
   */
  public boolean isPersisted() {
    return persisted;
  }

  /**
   * Sets whether the configuration is loaded from preferences.
   *
   * @param persisted true if persisted, false if just created in memory only
   */
  public void setPersisted(boolean persisted) {
    this.persisted = persisted;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    return new Class[] { MandatoryScope.class };
  }


  /**
   * Gets the configuration parent node.
   *
   * @param nodeName the topmost node
   * @param system true if load from system preferences, else user preferences
   * @return the node
   */
  protected static Preferences getPrefNode(String nodeName, boolean system) {
    Preferences prefs = system ? Preferences.systemNodeForPackage(AbstractSessionConfiguration.class) :
        Preferences.userNodeForPackage(AbstractSessionConfiguration.class);
    return prefs.node(nodeName);
  }

}
