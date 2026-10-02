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

package org.tentackle.fx.rdc.search;

import javafx.beans.property.ObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.CaseConversion;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.rdc.PdoFinder;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.List;

/**
 * The simple default finder.<br>
 * Provides a normtext search pattern field, if the entity provides a normtext.
 * An empty pattern selects all PDOs.
 * Without a normtext, the finder is hidden and all PDOs are selected immediately.
 * <p>
 * All PDOs are selected via {@code selectAllCached()} if the entity has a preloading cache, else via {@code selectAll()}.
 * If the search runs immediately, the finder is hidden as well.
 *
 * @author harald
 * @param <T> the PDO type
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class DefaultPdoFinder<T extends PersistentDomainObject<T>> extends PdoFinder<T> {

  private T pdo;

  @FXML
  private FxTextField patternField;

  /**
   * Creates a default PDO finder.
   */
  public DefaultPdoFinder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public List<T> runSearch() {
    if (pdo != null) {
      if (pdo.isNormTextProvided()) {
        String pattern = patternField.getText();
        if (!StringHelper.isAllWhitespace(pattern)) {
          return pdo.selectByNormText(pattern);
        }
      }
      PdoCache<T> cache = pdo.getCache();
      if (cache != null && cache.isPreloading()) {
        return pdo.selectAllCached();
      }
      return pdo.selectAll();
    }
    return List.of();
  }


  @FXML
  private void initialize() {
    patternField.setCaseConversion(CaseConversion.UPPER_CASE);
  }

  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public void setPdo(T pdo) {
    this.pdo = pdo;
    patternField.clear(); // no preset of search criteria
  }

  @Override
  public boolean isSearchRunningImmediately() {
    return super.isSearchRunningImmediately() || (pdo != null && !pdo.isNormTextProvided());
  }

  @Override
  public boolean isVisible() {
    return super.isVisible() && pdo != null && pdo.isNormTextProvided() && !isSearchRunningImmediately();
  }

  @Override
  public void requestInitialFocus() {
    if (pdo != null && pdo.isNormTextProvided()) {
      patternField.requestFocus();
    }
  }

  @Override
  public ObjectProperty<EventHandler<ActionEvent>> getSearchActionProperty() {
    return patternField.onActionProperty();
  }

}
