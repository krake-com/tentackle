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

package org.tentackle.fx.translate;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * List translator.<br>
 * Replaces ObservableListTranslator and adds PDO detection for automatic
 * configuration via GuiProvider for tables.
 *
 * @author harald
 * @param <T> the element type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = List.class, viewClass = ObservableList.class)
public class PdoObservableListTranslator<T extends PersistentDomainObject<T>, C extends List<T>>
       extends ObservableListTranslator<T, C> {

  /**
   * Creates a translator for components maintaining a list of objects.
   *
   * @param component the component
   */
  @SuppressWarnings("unchecked")
  public PdoObservableListTranslator(FxComponent component) {
    super(component);

    if (component instanceof TableView) {
      Type genType = component.getGenericType();
      if (genType instanceof ParameterizedType) {
        Type[] typeArguments = ((ParameterizedType) genType).getActualTypeArguments();
        if (typeArguments != null && typeArguments.length == 1) {
          Class<T> elemClass = (Class<T>) typeArguments[0];
          if (PersistentDomainObject.class.isAssignableFrom(elemClass)) {
            // is a PDO: get table configurator
            T pdo = Pdo.create(elemClass);
            GuiProvider<T> provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
            TableConfiguration<T> config = provider.createTableConfiguration();
            if (config.getBindingType() == TableConfiguration.BINDING.YES) {
              config.getBinder().bind();
            }
            else if (config.getBindingType() == TableConfiguration.BINDING.INHERITED) {
              config.getBinder().bindAllInherited();
            }
            config.configure((TableView<T>) component);
          }
        }
      }
    }
  }

}
