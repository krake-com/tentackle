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

package org.tentackle.fx.table;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.misc.FormatHelper;
import org.tentackle.reflect.ReflectionHelper;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.util.Callback;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Default configuration for a table column.<br>
 *
 * @param <S> type of the objects contained within the table's item list
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
  private String displayedName;

  /**
   * The table column.
   */
  private FxTableColumn<S,T> tableColumn;

  /**
   * The tree table column.
   */
  private FxTreeTableColumn<S,T> treeTableColumn;

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
   * Non-default cell type.
   */
  private TableCellType<T> cellType;

  /**
   * The format pattern.
   */
  private String pattern;

  /**
   * The decimal format.<br>
   * null if pattern and scale are both null.
   */
  private DecimalFormat numberFormat;

  /**
   * Formatter for all date- and time-related types.<br>
   * null if the pattern is null.
   */
  private DateTimeFormatter dateTimeFormatter;

  /**
   * Alignment within the cell.
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
   * Valid input characters.
   */
  private String validChars;

  /**
   * Invalid input characters.
   */
  private String invalidChars;

  /**
   * Autoselect cell contents when editing starts.
   */
  private Boolean autoSelect;

  /**
   * Maximum columns in the cell.
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
   * Summable or not.
   */
  private Boolean summable;

  /**
   * Filterable or not.
   */
  private Boolean filterable;

  /**
   * Editable or not.
   */
  private Boolean editable;

  /**
   * Optional editor.
   */
  private FxComponent editor;


  /**
   * Create a column config.
   *
   * @param tableConfiguration the table config
   * @param name the column's binding path
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
  public void setDisplayedName(String displayedName) {
    this.displayedName = displayedName;
    if (tableColumn != null) {
      tableColumn.setText(displayedName);
    }
  }

  @Override
  public void setType(Class<T> type) {
    this.type = type;
  }

  @Override
  public Class<T> getType() {
    return type;
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
  public TableCellType<T> getCellType() {
    return cellType;
  }

  @Override
  public void setCellType(TableCellType<T> cellType) {
    this.cellType = cellType;
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
      // scale or pattern given
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
  public DateTimeFormatter getDateTimeFormatter() {
    if (dateTimeFormatter == null && pattern != null) {
      dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
    }
    return dateTimeFormatter;
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
  public String getValidChars() {
    return validChars;
  }

  @Override
  public void setValidChars(String validChars) {
    this.validChars = validChars;
  }

  @Override
  public String getInvalidChars() {
    return invalidChars;
  }

  @Override
  public void setInvalidChars(String invalidChars) {
    this.invalidChars = invalidChars;
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
    if (summable != null) {
      return summable;
    }
    return type != null &&
           Number.class.isAssignableFrom(
                   type.isPrimitive() ? ReflectionHelper.primitiveToWrapperClass(type) : type);
  }

  @Override
  public void setSummable(Boolean summable) {
    this.summable = summable;
  }

  @Override
  public Boolean getSummable() {
    return summable;
  }

  @Override
  public void setFilterable(Boolean filterable) {
    this.filterable = filterable;
  }

  @Override
  public Boolean getFilterable() {
    return filterable;
  }

  @Override
  public boolean isEditable() {
    return Boolean.TRUE.equals(editable) ||
           tableConfiguration != null && tableConfiguration.getEditMode() != TableConfiguration.EDITMODE.NO;
  }

  @Override
  public Boolean getEditable() {
    return editable;
  }

  @Override
  public void setEditable(Boolean editable) {
    this.editable = editable;
  }

  @Override
  public FxComponent getEditor() {
    return editor;
  }

  @Override
  public void setEditor(FxComponent editor) {
    this.editor = editor;
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
  public FxTableColumn<S, T> getTableColumn() {
    if (tableColumn == null) {
      tableColumn = createTableColumn();
    }
    return tableColumn;
  }

  @Override
  public FxTreeTableColumn<S, T> getTreeTableColumn() {
    if (treeTableColumn == null) {
      treeTableColumn = createTreeTableColumn();
    }
    return treeTableColumn;
  }


  // ------------- for TableView ------------------

  /**
   * Creates the cell value for a given row object.
   *
   * @param cdf the cell data features
   * @return the cell value to display
   */
  protected ObservableValue<T> createTableCellValue(TableColumn.CellDataFeatures<S, T> cdf) {
    if (binding == null) {
      throw new FxRuntimeException("missing binding for " + this);
    }
    binding.setBoundRootObject(cdf.getValue());
    return new ReadOnlyObjectWrapper<>(binding.getModelValue());
  }

  /**
   * Creates the cell value factory.
   *
   * @return the value factory
   */
  protected Callback<TableColumn.CellDataFeatures<S, T>, ObservableValue<T>> createTableCellValueFactory() {
    return this::createTableCellValue;
  }

  /**
   * Creates a table cell.<br>
   * The method reference can directly be used as a cell factory.
   *
   * @param column the table column
   * @return the table cell
   */
  protected TableCell<S,T> createTableCell(TableColumn<S, T> column) {
    return new FxTableCell<>(this);
  }

  /**
   * Gets the cell factory.
   *
   * @return the cell factory
   */
  protected Callback<TableColumn<S,T>, TableCell<S,T>> getTableCellFactory() {
    return this::createTableCell;
  }

  /**
   * Creates the table column.
   *
   * @return the table column
   */
  protected FxTableColumn<S, T> createTableColumn() {
    FxTableColumn<S, T> tc = new FxTableColumn<>(this, displayedName);
    tc.setCellValueFactory(createTableCellValueFactory());
    tc.setCellFactory(getTableCellFactory());
    return tc;
  }


  // ------------- for TreeTableView ------------------

  /**
   * Creates the treetable cell value for a given row object.
   *
   * @param cdf the cell data features
   * @return the cell value to display
   */
  protected ObservableValue<T> createTreeTableCellValue(TreeTableColumn.CellDataFeatures<S, T> cdf) {
    if (binding == null) {
      throw new FxRuntimeException("missing binding for " + this);
    }
    TreeItem<S> treeItem = cdf.getValue();
    binding.setBoundRootObject(treeItem == null ? null : treeItem.getValue());
    return new ReadOnlyObjectWrapper<>(binding.getModelValue());
  }

  /**
   * Creates the tree table cell value factory.
   *
   * @return the value factory
   */
  protected Callback<TreeTableColumn.CellDataFeatures<S, T>, ObservableValue<T>> createTreeTableCellValueFactory() {
    return this::createTreeTableCellValue;
  }

  /**
   * Creates a treetable cell.<br>
   * The method reference can directly be used as a cell factory.
   *
   * @param column the table column
   * @return the table cell
   */
  protected TreeTableCell<S,T> createTreeTableCell(TreeTableColumn<S, T> column) {
    return new FxTreeTableCell<>(this);
  }

  /**
   * Creates the treetable cell factory.
   *
   * @return the cell factory
   */
  protected Callback<TreeTableColumn<S,T>, TreeTableCell<S,T>> createTreeTableCellFactory() {
    return this::createTreeTableCell;
  }

  /**
   * Creates the tree table column.
   *
   * @return the tree table column
   */
  protected FxTreeTableColumn<S, T> createTreeTableColumn() {
    FxTreeTableColumn<S, T> tc = new FxTreeTableColumn<>(this, displayedName);
    tc.setCellValueFactory(createTreeTableCellValueFactory());
    tc.setCellFactory(createTreeTableCellFactory());
    return tc;
  }

}
