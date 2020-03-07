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

package org.tentackle.fx.translate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

/**
 * List translator.<br>
 * Used for components maintaining a list of elements, such as a table.
 *
 * @author harald
 * @param <T> the element type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = List.class, viewClass = ObservableList.class)
public class ObservableListTranslator<T, C extends List<T>> extends AbstractValueTranslator<C,ObservableList<T>> {

  /**
   * Creates a translator for components maintaining a list of objects.
   *
   * @param component the component
   */
  @SuppressWarnings("unchecked")
  public ObservableListTranslator(FxComponent component) {
    super(component);

    if (component instanceof FxTableView) {
      Type genType = component.getGenericType();
      if (genType instanceof ParameterizedType) {
        Type[] typeArguments = ((ParameterizedType) genType).getActualTypeArguments();
        if (typeArguments != null && typeArguments.length == 1) {
          configureTableView((FxTableView<T>) component, (Class<T>) typeArguments[0]);
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<C, ObservableList<T>> toViewFunction() {
    return m -> {
      ObservableList<T> items = m instanceof ObservableList ? (ObservableList<T>) m :
                                (m == null ? FXCollections.emptyObservableList() : FXCollections.observableList(m));
      if (getComponent() instanceof TableView) {
        boolean sortable = false;
        for (TableColumn<T,?> column: ((TableView<T>) getComponent()).getColumns()) {
          if (column.isSortable()) {
            sortable = true;
            break;
          }
        }
        if (sortable && !(items instanceof SortedList)) {
          SortedList<T> sortedResult = new SortedList<>(items);
          sortedResult.comparatorProperty().bind(((TableView<T>) getComponent()).comparatorProperty());
          items = sortedResult;
        }
      }
      return items;
    };
  }

  @Override
  public Function<ObservableList<T>, C> toModelFunction() {
    // no conversion back to model because list is updated by ObservableList from the view.
    throw new FxRuntimeException("conversion to model is not applicable to lists");
  }


  /**
   * Configures the tableview.
   *
   * @param tableView the tableview
   * @param elemClass the list's elememt class
   */
  protected void configureTableView(FxTableView<T> tableView, Class<T> elemClass) {
    // determine optional table configuration provider for elemClass
    TableConfigurationProvider<T> provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(elemClass);
    if (provider != null) {
      TableConfiguration<T> configuration = provider.createTableConfiguration();
      if (configuration.getBindingType() == TableConfiguration.BINDING.YES) {
        configuration.getBinder().bind();
      }
      else if (configuration.getBindingType() == TableConfiguration.BINDING.INHERITED) {
        configuration.getBinder().bindAllInherited();
      }
      configuration.configure(tableView);
    }

    // else: no configuration available -> must be done in application explicitly
  }

}
