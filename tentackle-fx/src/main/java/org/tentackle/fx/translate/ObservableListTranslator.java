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

import java.util.List;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.ValueTranslatorService;

/**
 * List translator.<br>
 * Used for components maintaining a list of elements, such as a table.
 *
 * @author harald
 * @param <T> the element type
 * @param <C> the collection type
 */
@ValueTranslatorService(modelClass = List.class, viewClass = ObservableList.class)
public class ObservableListTranslator<T, C extends List<T>> extends ValueTranslator<C,ObservableList<T>> {

  /**
   * Creates a translator for components maintaining a list of objects.
   *
   * @param component the component
   */
  public ObservableListTranslator(FxComponent component) {
    super(component);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<C, ObservableList<T>> toViewFunction() {
    return m -> {
      ObservableList<T> items = m instanceof ObservableList ? (ObservableList<T>) m : FXCollections.observableList(m);
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

}
