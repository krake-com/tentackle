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

import org.tentackle.bind.BindableElement;
import org.tentackle.bind.BindableType;
import org.tentackle.common.BundleFactory;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
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
    else {
      // no provider: check if elemClass is annotated with BindableType
      BindableType bindableType = elemClass.getAnnotation(BindableType.class);
      if (bindableType != null) {
        TableConfiguration<T> configuration = FxFactory.getInstance().createTableConfiguration(elemClass, null);
        if (addBindableColumns(configuration, "", "", elemClass) > 0) {
          // try to shorten dotted names, if the remaining tail is unique among all columns
          Collection<TableColumnConfiguration<T, ?>> columnConfigurations = configuration.getColumnConfigurations();
          for (TableColumnConfiguration column : columnConfigurations) {
            String displayedName = column.getDisplayedName();
            if (displayedName != null) {
              int ndx = displayedName.lastIndexOf('.');
              if (ndx >= 0) {
                String shortName = displayedName.substring(ndx + 1);
                if (!StringHelper.isAllWhitespace(shortName)) {
                  boolean shorten = true;
                  for (TableColumnConfiguration otherColumn : columnConfigurations) {
                    if (otherColumn != column) {
                      String otherName = otherColumn.getDisplayedName();
                      if (displayedName.equals(otherName) || shortName.equals(otherName)) {
                        shorten = false;
                        break;
                      }
                    }
                  }
                  if (shorten) {
                    column.setDisplayedName(shortName);
                  }
                }
              }
            }
          }
        }
        if (bindableType.declaredOnly()) {
          configuration.getBinder().bind();
        }
        else {
          configuration.getBinder().bindAllInherited();
        }
        configuration.getBinder().assertAllBound();
        configuration.configure(tableView);
      }
    }
    // else: no configuration possible -> must be done in application explicitly
  }

  /**
   * Recursively adds all bindable columns including components.<br>
   * The classes must be annotated with {@link org.tentackle.bind.BindableType} to be analyzed for
   * the {@link org.tentackle.bind.Bindable}-annotated methods.
   *
   * @param configuration the table configuration
   * @param columnPrefix the prefix for the column names
   * @param displayPrefix the prefix for the display names
   * @param objectClass the object class
   * @return the number of bound columns
   */
  protected int addBindableColumns(TableConfiguration<T> configuration, String columnPrefix, String displayPrefix, Class<?> objectClass) {
    int boundTotal = 0;
    BindableType bindableType = objectClass.getAnnotation(BindableType.class);
    if (bindableType != null) {
      ResourceBundle bundle = null;
      if (bundle == null) {
        String bundleName = bindableType.resources();
        if (!BindableType.RESOURCES_NONE.equals(bundleName)) {
          bundle = BundleFactory.getBundle(bundleName.isEmpty() ? objectClass.getName() : bundleName,
                                           LocaleProvider.getInstance().getLocale());
        }
      }

      for (Map.Entry<String, BindableElement> entry :
          FxBindingFactory.getInstance().getBindableCache().getBindableMap(objectClass, bindableType.declaredOnly()).entrySet()) {

        String columnName = columnPrefix + StringHelper.firstToLower(entry.getKey());
        String displayName = displayPrefix + (bundle != null ? bundle.getString(entry.getKey()) : entry.getKey());
        int boundCount = addBindableColumns(configuration, columnName + ".", displayName + ".", entry.getValue().getType());
        if (boundCount == 0) {
          configuration.addColumn(columnName, displayName);
          boundTotal++;
        }
        else {
          boundTotal += boundCount;
        }
      }
    }
    return boundTotal;
  }

}
