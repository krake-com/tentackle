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

package org.tentackle.fx.rdc.translate;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.translate.ObservableListTranslator;
import org.tentackle.log.Logger;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.collections.ObservableList;
import java.util.List;

/**
 * List translator.<br>
 * Replaces the {@code ObservableListTranslator} and adds PDO detection for the automatic
 * configuration of tables via the {@link org.tentackle.fx.rdc.GuiProvider}.
 * Falls back to the default table configuration provider if there is no GUI provider for the element type.
 *
 * @author harald
 * @param <T> the PDO type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = List.class, viewClass = ObservableList.class)
public class PdoObservableListTranslator<T extends PersistentDomainObject<T>, C extends List<T>>
       extends ObservableListTranslator<T, C> {

  private static final Logger LOGGER = Logger.get(PdoObservableListTranslator.class);

  /**
   * Creates a translator for components maintaining a list of PDOs.
   *
   * @param component the component
   */
  public PdoObservableListTranslator(FxComponent component) {
    super(component);
  }


  @Override
  protected TableConfigurationProvider<T> createTableConfigurationProvider(Class<T> elemClass) {
    if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
      // is a PDO: get table configuration from gui provider
      try {
        // GuiProvider is a TableConfigurationProvider
        return Rdc.createGuiProvider(Pdo.create(elemClass));
      }
      catch (RdcRuntimeException | PdoRuntimeException px) {
        // may be an abstract type?
        LOGGER.warning("no GuiProvider for " + elemClass + " (" + px.getMessage() + ") -> fallback to default table configuration provider");
      }
    }

    return super.createTableConfigurationProvider(elemClass);
  }

}
