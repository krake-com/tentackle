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
import org.tentackle.fx.bind.FxTableBinding;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableColumn;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Configuration for a table or treetable column.
 *
 * @param <S> type of the objects contained within the table's or tree-table's item list
 * @param <T> type of the content in all cells in this column
 *
 * @author harald
 */
public interface TableColumnConfiguration<S, T> {

  /**
   * Gets the table configuration.
   *
   * @return the table configuration
   */
  TableConfiguration<S> getTableConfiguration();

  /**
   * Gets the column name.<br>
   * This is usually the binding path.<br>
   * The name is also used as a key to the table preferences.
   *
   * @return the name of the column
   */
  String getName();

  /**
   * Gets the column name displayed in the column header.
   *
   * @return the display name of the column
   */
  String getDisplayedName();

  /**
   * Sets the column name displayed in the column header.
   *
   * @param displayName the display name of the column
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setDisplayedName(String displayName);

  /**
   * Gets the column type.
   *
   * @return the column class
   */
  Class<T> getType();

  /**
   * Sets the column type.
   *
   * @param type the type
   */
  void setType(Class<T> type);

  /**
   * Sets the generic type.
   *
   * @param genericType the generic type
   */
  void setGenericType(Type genericType);

  /**
   * Gets the generic type.
   *
   * @return the generic type
   */
  Type getGenericType();

  /**
   * Gets the specific table cell type.
   *
   * @return the cell type, null to determine from the column type
   * @see #getEffectiveCellType()
   */
  TableCellType<T> getCellType();

  /**
   * Sets the specific table cell type.
   *
   * @param cellType the cell type, null to determine from the column type
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setCellType(TableCellType<T> cellType);

  /**
   * Gets the effective table cell type.<br>
   * This is either the specific cell type or the one determined from the column type.
   *
   * @return the cell type, never null
   */
  default TableCellType<T> getEffectiveCellType() {
    TableCellType<T> cellType = getCellType();
    return cellType == null ? getTableConfiguration().getTableCellType(getType()) : cellType;
  }

  /**
   * Gets the text displayed for a value of this column.
   *
   * @param value the value; may be null
   * @return the displayed text, null if none
   * @see TableCellType#getText(TableColumnConfiguration, Object)
   */
  default String getText(T value) {
    return value == null ? null : getEffectiveCellType().getText(this, value);
  }

  /**
   * Gets the graphic displayed for a value of this column.
   *
   * @param value the value; may be null
   * @return a newly created graphic, null if the value is rendered as text only
   * @see TableCellType#getGraphic(TableColumnConfiguration, Object)
   */
  default Node getGraphic(T value) {
    return value == null ? null : getEffectiveCellType().getGraphic(this, value);
  }

  /**
   * Defines the format for numeric or date/time-types.<br>
   * If null is returned, a default format will be used according to the column class.
   *
   * @return the format pattern or null if default
   */
  String getPattern();

  /**
   * Sets the formatting string.
   *
   * @param pattern the format pattern
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setPattern(String pattern);

  /**
   * Gets the number format.
   *
   * @return the number format, null if default
   */
  DecimalFormat getNumberFormat();

  /**
   * Gets the date or time formatter.
   *
   * @return the formatter, null if default
   */
  DateTimeFormatter getDateTimeFormatter();

  /**
   * Defines the alignment of the column.
   *
   * @return the alignment or null if default
   */
  Pos getAlignment();

  /**
   * Sets the alignment within the cell.
   *
   * @param alignment the alignment
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setAlignment(Pos alignment);

  /**
   * Defines the {@code blankzero} attribute of the column.<br>
   * Notice that the built-in cell types don't evaluate this attribute.
   *
   * @return true if blank zeros, false if not, null to use default from the editor
   */
  Boolean isBlankZero();

  /**
   * Sets whether to blank out numeric zero values.
   *
   * @param blankZero true if blank zeros, false if not, null to use default from the editor
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setBlankZero(Boolean blankZero);

  /**
   * Sets whether a numeric field is unsigned or signed.
   *
   * @param unsigned true if unsigned, null to use default from the editor
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setUnsigned(Boolean unsigned);

  /**
   * Returns whether a numeric field is unsigned or signed.
   *
   * @return true if unsigned, null to use default from the editor
   */
  Boolean isUnsigned();

  /**
   * Sets allowed characters.
   *
   * @param validChars the valid characters, null to use default from the editor
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setValidChars(String validChars);

  /**
   * Gets allowed characters.
   *
   * @return the valid characters, null to use default from the editor
   */
  String getValidChars();

  /**
   * Sets invalid characters.
   *
   * @param invalidChars the invalid characters, null to use default from the editor
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setInvalidChars(String invalidChars);

  /**
   * Gets invalid characters.
   *
   * @return the invalid characters, null to use default from the editor
   */
  String getInvalidChars();

  /**
   * Gets the autoselect flag.
   *
   * @return true if autoselect, false if not, null to use default from the editor
   */
  Boolean isAutoSelect();

  /**
   * Sets the autoselect feature.
   *
   * @param autoSelect true if autoselect, false if not, null to use default from the editor
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setAutoSelect(Boolean autoSelect);

  /**
   * Gets the maximum columns for text cell editors.
   *
   * @return the max columns, null if not defined
   */
  Integer getMaxColumns();

  /**
   * Sets the max columns.
   *
   * @param maxColumns the max columns, null if not defined
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setMaxColumns(Integer maxColumns);

  /**
   * Gets the scale for fractional numeric cell editors.
   *
   * @return the scale, null if not defined
   */
  Integer getScale();

  /**
   * Sets the numeric scale.
   *
   * @param scale the scale, null if not defined
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setScale(Integer scale);

  /**
   * Gets the case conversion.
   *
   * @return true = convert to uppercase, false = lowercase, null = no conversion (default)
   */
  Boolean getCaseConversion();

  /**
   * Sets the case conversion.
   *
   * @param caseConversion true = convert to uppercase, false = lowercase, null = no conversion (default)
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setCaseConversion(Boolean caseConversion);

  /**
   * Determines whether the column is summable.<br>
   * If not set explicitly, numeric columns are summable.
   *
   * @return true if the column is summable
   */
  boolean isSummable();

  /**
   * Sets whether the column is summable.
   *
   * @param summable {@link Boolean#TRUE} if summable, {@link Boolean#FALSE} if not, NULL to derive from type
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setSummable(Boolean summable);

  /**
   * Returns whether the column is summable.
   *
   * @return {@link Boolean#TRUE} if summable, {@link Boolean#FALSE} if not, NULL to derive from type
   */
  Boolean getSummable();

  /**
   * Determines whether the column provides a filter UI in its header.<br>
   * Filtering must be enabled for the whole table as well.
   *
   * @return true if the column can be filtered by the user
   * @see TableConfiguration#isFilterEnabled()
   */
  default boolean isFilterable() {
    return !Boolean.FALSE.equals(getFilterable()) &&
           getTableConfiguration() != null && getTableConfiguration().isFilterEnabled();
  }

  /**
   * Sets whether the column provides a filter UI in its header.
   *
   * @param filterable {@link Boolean#FALSE} to suppress the filter for this column, else null or {@link Boolean#TRUE}
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setFilterable(Boolean filterable);

  /**
   * Returns whether the column provides a filter UI in its header.
   *
   * @return {@link Boolean#FALSE} if the filter is suppressed for this column, else null or {@link Boolean#TRUE}
   */
  Boolean getFilterable();

  /**
   * Returns whether the column is editable.
   *
   * @return {@link Boolean#TRUE} if editable, {@link Boolean#FALSE} if not, NULL if depends on the table configuration's edit handler
   */
  Boolean getEditable();

  /**
   * Sets whether the column is editable.
   *
   * @param editable {@link Boolean#TRUE} if editable, {@link Boolean#FALSE} if not, NULL if depends on the table configuration's edit handler
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setEditable(Boolean editable);

  /**
   * Returns whether the column is editable.<br>
   * A column is editable if configured as such. If the table configuration provides an edit handler,
   * the column is editable unless configured as not editable.
   *
   * @return true if editable, the default is false
   * @see TableConfiguration#setEditHandler
   */
  boolean isEditable();

  /**
   * Gets the editor component.
   *
   * @return the editor; null to use default from {@link TableCellType#getEditor()}
   */
  FxComponent getEditor();

  /**
   * Sets the editor component.
   *
   * @param editor the editor; null to use default from {@link TableCellType#getEditor()}
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setEditor(FxComponent editor);

  /**
   * Gets the binding for this column.
   *
   * @return the binding, null if none
   */
  FxTableBinding<S,T> getBinding();

  /**
   * Sets the binding.
   *
   * @param binding the binding
   * @return this configuration
   */
  TableColumnConfiguration<S, T> setBinding(FxTableBinding<S, T> binding);

  /**
   * Gets or creates the table column.<br>
   * Used if configuration applies to a {@link javafx.scene.control.TableView}.
   *
   * @return the table column, never null
   */
  TableColumn<S,T> getTableColumn();

  /**
   * Gets or creates the tree table column.<br>
   * Used if configuration applies to a {@link javafx.scene.control.TreeTableView}.
   *
   * @return the tree table column, never null
   */
  TreeTableColumn<S,T> getTreeTableColumn();

}
