/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.validate.ValidationMapper;
import org.tentackle.validate.ValidationUtilities;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Controller to edit a PDO.<br>
 * Created by {@link GuiProvider#createEditor()} and embedded in a {@link org.tentackle.fx.rdc.crud.PdoCrud},
 * which provides the toolbar, navigation and persistence actions.
 *
 * @author harald
 * @param <T> the PDO type
 */
public abstract class PdoEditor<T extends PersistentDomainObject<T>> extends AbstractFxController
                implements PdoController<T>, DomainContextProvider {

  /**
   * Parent constructor.
   */
  public PdoEditor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Requests the initial focus after the stage is shown.
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
   * Gets the validation root path.<br>
   * By default, the server returns validation results with a validation path that starts with the entity's basename
   * (the first character in lowercase). If the bound PDO variable of the editor uses a different name,
   * a {@link ValidationMapper} is necessary to map the results back to the form.<br>
   * This method is provided to reduce the amount of application code.
   *
   * @return the validation root path, default is null
   * @see #getValidationMappers()
   */
  public String getValidationPath() {
    return null;
  }

  /**
   * Gets the validation mappers to map the validation results to the controls.<br>
   * If {@link #getValidationPath()} returns a non-null value, a set with a default mapper
   * will be created.
   *
   * @return the mappers, null if none
   */
  public NavigableSet<ValidationMapper> getValidationMappers() {
    String validationPath = getValidationPath();
    if (validationPath != null) {
      NavigableSet<ValidationMapper> validationMappers = new TreeSet<>();
      validationMappers.add(new ValidationMapper(ValidationUtilities.getInstance().getDefaultValidationPath(getPdo()), getBinder(), validationPath, null));
      return validationMappers;
    }
    return null;
  }

  /**
   * Validates the form before the PDO gets saved.<br>
   * Invoked within the save transaction, before the PDO is persisted.<br>
   * This can be used for validations that are not covered by the persistence model,
   * for example password confirmation.
   * <p>
   * Notice: PdoEditor does <em>not</em> extend {@link org.tentackle.fx.AbstractValidateableFxController}, since
   * its validation mappers map the persistence operation's validation results of the PDO and not the validation
   * of the form!
   *
   * @return true if ok, false to abort saving
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
   * Sets the changeable property of the editor's view.
   *
   * @param changeable true if changeable
   */
  public void setChangeable(boolean changeable) {
    getContainer().setChangeable(changeable);
  }

  /**
   * Gets the changeable property of the editor's view.
   *
   * @return true if changeable
   */
  public boolean isChangeable() {
    return getContainer().isChangeable();
  }

}
