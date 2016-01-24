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

package org.tentackle.fx.table;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.misc.FormatHelper;

/**
 * Default configuration for a table column.<br>
 *
 * @param <S> type of the objects contained within the table's items list
 * @param <T> type of the content in all cells in this column
 *
 * @author harald
 */
public class DefaultTableColumnConfiguration<S,T> implements TableColumnConfiguration<S,T> {

  /**
   * The table config.
   */
  private final DefaultTableConfiguration<S> tableConfiguration;

  /**
   * The column name.
   */
  private final String name;

  /**
   * The displayed name.
   */
  private final String displayedName;

  /**
   * The table column.
   */
  private TableColumn<S,T> tableColumn;

  /**
   * The binding.
   */
  private FxTableBinding<S,T> binding;

  /**
   * The column class.
   */
  private Class<T> type;

  /**
   * The generic type.
   */
  private Type genericType;

  /**
   * For formatting.
   */
  private String pattern;

  /**
   * The decimal format.
   * null if pattern and scale is null.
   */
  private DecimalFormat numberFormat;

  /**
   * The date format.
   * nullif pattern is null.
   */
  private DateFormat dateFormat;

  /**
   * Alignment within cell.
   */
  private Pos alignment;

  /**
   * Blank out numeric fields if zero.
   */
  private Boolean blankZero;

  /**
   * Unsigned numeric field only.
   */
  private Boolean unsigned;

  /**
   * Autoselect cell contents when editing starts.
   */
  private Boolean autoSelect;

  /**
   * Max. columns in cell.
   */
  private Integer maxColumns;

  /**
   * The numeric scale.
   */
  private Integer scale;

  /**
   * Case conversion.
   */
  private Boolean caseConversion;



  /**
   * Create a column config.
   *
   * @param tableConfiguration the table config
   * @param name the column name
   * @param displayedName the displayed column name
   */
  public DefaultTableColumnConfiguration(DefaultTableConfiguration<S> tableConfiguration, String name, String displayedName) {
    this.tableConfiguration = tableConfiguration;
    this.name = name;
    this.displayedName = displayedName;
  }

  @Override
  public String toString() {
    return tableConfiguration.getName() + "." + name;
  }

  @Override
  public DefaultTableConfiguration<S> getTableConfiguration() {
    return tableConfiguration;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDisplayedName() {
    return displayedName;
  }

  @Override
  public void setType(Class<T> type) {
    this.type = type;
  }

  @Override
  public void setGenericType(Type genericType) {
    this.genericType = genericType;
  }

  @Override
  public Type getGenericType() {
    return genericType;
  }

  @Override
  public Class<T> getType() {
    return type;
  }

  @Override
  public void setPattern(String pattern) {
    this.pattern = pattern;
    numberFormat = null;
  }

  @Override
  public String getPattern() {
    return pattern;
  }

  @Override
  public DecimalFormat getNumberFormat() {
    if (numberFormat == null && (pattern != null || scale != null)) {
      // scale given but no pattern
      if (pattern == null) {
        pattern = FormatHelper.getIntegerPattern();
      }
      numberFormat = new DecimalFormat(pattern);
      if (scale == null) {
        // determine from format
        scale = 0;
        String p = getPattern();
        int dotNdx = p.lastIndexOf('.');
        if (dotNdx >= 0)  {
          // count zeros after dot
          for (++dotNdx; dotNdx < p.length(); dotNdx++)  {
            if (p.charAt(dotNdx) != '0') {
              break;
            }
            scale++;
          }
        }
      }
      else {
        // apply scale for numberFormat overriding the scale from the format
        FormatHelper.setScale(numberFormat, scale);
      }
    }
    return numberFormat;
  }

  @Override
  public DateFormat getDateFormat() {
    if (dateFormat == null && pattern != null) {
      dateFormat = new SimpleDateFormat(pattern);
    }
    return dateFormat;
  }

  @Override
  public void setAlignment(Pos alignment) {
    this.alignment = alignment;
  }

  @Override
  public Pos getAlignment() {
    return alignment;
  }

  @Override
  public void setBlankZero(Boolean blankZero) {
    this.blankZero = blankZero;
  }

  @Override
  public Boolean isBlankZero() {
    return blankZero;
  }

  @Override
  public Boolean isUnsigned() {
    return unsigned;
  }

  @Override
  public void setUnsigned(Boolean unsigned) {
    this.unsigned = unsigned;
  }

  @Override
  public void setAutoSelect(Boolean autoSelect) {
    this.autoSelect = autoSelect;
  }

  @Override
  public Boolean isAutoSelect() {
    return autoSelect;
  }

  @Override
  public void setMaxColumns(Integer maxColumns) {
    this.maxColumns = maxColumns;
  }

  @Override
  public Integer getMaxColumns() {
    return maxColumns;
  }

  @Override
  public void setScale(Integer scale) {
    this.scale = scale;
    numberFormat = null;
  }

  @Override
  public Integer getScale() {
    return scale;
  }

  @Override
  public void setCaseConversion(Boolean caseConversion) {
    this.caseConversion = caseConversion;
  }

  @Override
  public Boolean getCaseConversion() {
    return caseConversion;
  }

  @Override
  public boolean isSummable() {
    return type != null && Number.class.isAssignableFrom(type);
  }

  @Override
  public void setBinding(FxTableBinding<S, T> binding) {
    this.binding = binding;
  }

  @Override
  public FxTableBinding<S, T> getBinding() {
    return binding;
  }

  @Override
  public TableColumn<S, T> getTableColumn() {
    if (tableColumn == null) {
      tableColumn = createTableColumn();
    }
    return tableColumn;
  }


  /**
   * Creates the cell value factory.
   *
   * @return the value factory
   */
  @SuppressWarnings("unchecked")
  protected Callback<TableColumn.CellDataFeatures<S, T>, ObservableValue<T>> createCellValueFactory() {
    return (CellDataFeatures<S, T> p) -> {
      if (binding == null) {
        throw new FxRuntimeException("missing binding for " + this);
      }
      binding.setBoundRootObject(p.getValue());
      return new ReadOnlyObjectWrapper<>((T) binding.getModelValue());
    };
  }


  /**
   * Creates the cell factory.
   *
   * @return the cell factory
   */
  protected Callback<TableColumn<S,T>, TableCell<S,T>> createCellFactory() {
    return (TableColumn<S, T> param) -> new FxTableCell<>(this);
  }

  /**
   * Creates the table column.
   *
   * @return the table column
   */
  protected TableColumn<S, T> createTableColumn() {
    TableColumn<S, T> tc = new TableColumn<>(displayedName);
    tc.setCellValueFactory(createCellValueFactory());
    tc.setCellFactory(createCellFactory());
    return tc;
  }

}
