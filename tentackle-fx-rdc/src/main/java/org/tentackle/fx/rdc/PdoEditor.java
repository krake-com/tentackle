/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc;

import java.util.TreeSet;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PdoHolder;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.validate.ValidationMapper;

/**
 * FxController to edit a PDO.
 *
 * @author harald
 * @param <T> the pdo type
 */
public abstract class PdoEditor<T extends PersistentDomainObject<T>> extends AbstractFxController
                implements PdoHolder<T>, DomainContextProvider {

  /**
   * Requests the initial focus after stage is shown.
   */
  public abstract void requestInitialFocus();

  @Override
  public DomainContext getDomainContext() {
    T pdo = getPdo();
    return pdo == null ? null : pdo.getDomainContext();
  }


  /**
   * Returns whether the user has sufficient permissions to view the PDO.
   *
   * @return true if allowed
   */
  public boolean isViewAllowed() {
    return getPdo().isViewAllowed();
  }


  /**
   * Returns whether the user has sufficient permissions to edit the PDO.
   *
   * @return true if allowed
   */
  public boolean isEditAllowed() {
    return getPdo().isEditAllowed();
  }

  /**
   * Returns whether the user is allowed to edit the PDO and the PDO is removable.
   *
   * @return true if remove allowed
   */
  public boolean isRemoveAllowed() {
    return isEditAllowed() && getPdo().isRemovable();
  }

  /**
   * Returns whether the user is allowed to create a new PDO.
   *
   * @return true if creation allowed
   */
  public boolean isNewAllowed() {
    return isEditAllowed();
  }

  /**
   * Gets the validation mappers to map the validation results to the controls.
   *
   * @return the mappers, null if none
   */
  public TreeSet<ValidationMapper> getValidationMappers() {
    return null;
  }

  /**
   * Validates the form before the PDO get saved.<br>
   * This can be used for validations that are not covered by the persistence model,
   * for example password confirmation.
   *
   * @return true if ok
   */
  public boolean validateForm() {
    return true;
  }

  /**
   * Prints the current PDO.
   */
  public void print() {
    // the default implementation just prints the contents of this editor
    FxUtilities.getInstance().print(getView());
  }

  /**
   * Set the changeable property of the editor's view.
   *
   * @param changeable true if changeable
   */
  public void setChangeable(boolean changeable) {
    getContainer().setChangeable(changeable);
  }

  /**
   * Get the changeable property of the editor's view.
   *
   * @return true if changeable
   */
  public boolean isChangeable() {
    return getContainer().isChangeable();
  }

}
