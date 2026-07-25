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

package org.tentackle.fx.table.filter;

import org.tentackle.common.StringNormalizer;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.skin.TableFilterPopupSkin;
import org.tentackle.fx.table.TableColumnConfiguration;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The filter popup of a table column.<p>
 * Opened from the filter button in the column's header, it provides — as known from the autofilter
 * of spreadsheet applications — shortcuts to sort the column, a search field, the list of the
 * column's distinct values with a checkbox each, and a condition editor.
 * <p>
 * The filter is modified when the user confirms the popup, not while editing, so that leaving the
 * popup with escape or by clicking somewhere else discards the changes.
 *
 * @param <S> type of the objects contained within the table's or treetable's items list
 * @param <T> type of the content in all cells of the filtered column
 * @author harald
 * @see TableFilterSupport
 */
public class TableFilterPopup<S,T> extends PopupControl {

  /** the style class of the popup. */
  public static final String STYLE_CLASS = "tt-filter-popup";

  /** the default number of values displayed without scrolling. */
  public static final int DEFAULT_VISIBLE_ROW_COUNT = 12;


  private final TableFilterSupport<S> support;
  private final TableColumnFilter<S,T> columnFilter;

  private final ObservableList<TableFilterValue> values = FXCollections.observableArrayList();
  private final FilteredList<TableFilterValue> displayedValues = new FilteredList<>(values);
  private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");
  private final ObjectProperty<TableFilterOperator> operator =
          new SimpleObjectProperty<>(this, "operator", TableFilterOperator.NONE);
  private final StringProperty firstOperand = new SimpleStringProperty(this, "firstOperand", "");
  private final StringProperty secondOperand = new SimpleStringProperty(this, "secondOperand", "");
  private final IntegerProperty visibleRowCount =
          new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

  private int totalValueCount;
  private boolean valuesTruncated;
  private boolean updatingSelection;


  /**
   * Creates the filter popup of a column.
   *
   * @param support the filter support
   * @param columnFilter the column filter
   */
  public TableFilterPopup(TableFilterSupport<S> support, TableColumnFilter<S,T> columnFilter) {
    this.support = support;
    this.columnFilter = columnFilter;

    getStyleClass().add(STYLE_CLASS);
    setAutoHide(true);
    setHideOnEscape(true);

    searchText.addListener((_, _, newValue) -> updateSearchPredicate(newValue));
    updateSearchPredicate("");
  }


  /**
   * Gets the filter support.
   *
   * @return the support
   */
  public TableFilterSupport<S> getSupport() {
    return support;
  }

  /**
   * Gets the filter of the column this popup belongs to.
   *
   * @return the column filter
   */
  public TableColumnFilter<S,T> getColumnFilter() {
    return columnFilter;
  }

  /**
   * Gets the configuration of the column this popup belongs to.
   *
   * @return the column configuration
   */
  public TableColumnConfiguration<S,T> getColumnConfiguration() {
    return columnFilter.getColumnConfiguration();
  }

  /**
   * Gets the values displayed in the value list.<br>
   * These are the collected values reduced by the search text.
   *
   * @return the displayed values
   */
  public ObservableList<TableFilterValue> getDisplayedValues() {
    return displayedValues;
  }

  /**
   * Gets all collected values.
   *
   * @return all values
   */
  public ObservableList<TableFilterValue> getValues() {
    return values;
  }

  /**
   * Gets the total number of distinct values of the column.
   *
   * @return the number of distinct values
   */
  public int getTotalValueCount() {
    return totalValueCount;
  }

  /**
   * Returns whether not all distinct values are offered in the value list.
   *
   * @return true if truncated
   * @see TableFilterSupport#getMaxDistinctValues()
   */
  public boolean isValuesTruncated() {
    return valuesTruncated;
  }

  /**
   * The search text property.
   *
   * @return the property
   */
  public StringProperty searchTextProperty() {
    return searchText;
  }

  /**
   * The condition's operator property.
   *
   * @return the property
   */
  public ObjectProperty<TableFilterOperator> operatorProperty() {
    return operator;
  }

  /**
   * The condition's first operand property.
   *
   * @return the property
   */
  public StringProperty firstOperandProperty() {
    return firstOperand;
  }

  /**
   * The condition's second operand property.
   *
   * @return the property
   */
  public StringProperty secondOperandProperty() {
    return secondOperand;
  }

  /**
   * Gets the number of values displayed without scrolling.
   *
   * @return the row count, defaults to {@value #DEFAULT_VISIBLE_ROW_COUNT}
   */
  public int getVisibleRowCount() {
    return visibleRowCount.get();
  }

  /**
   * Sets the number of values displayed without scrolling.
   *
   * @param visibleRowCount the row count
   */
  public void setVisibleRowCount(int visibleRowCount) {
    this.visibleRowCount.set(visibleRowCount);
  }

  /**
   * The visible row count property.
   *
   * @return the property
   */
  public IntegerProperty visibleRowCountProperty() {
    return visibleRowCount;
  }


  /**
   * Shows the popup below a node, usually the column header's filter button.<br>
   * Collects the column's values and initializes the editors from the current filter.
   *
   * @param anchor the node to align the popup to
   */
  public void showPopup(Node anchor) {    // don't use the name show() as this would override!
    if (isShowing()) {
      return;
    }
    Point2D location = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
    if (location == null) {
      return;
    }
    loadValues();
    loadCondition();
    Scene scene = anchor.getScene();
    Parent contentPane = getContentPane();
    if (scene != null && contentPane != null) {
      // popups live in a scene of their own and don't inherit the application's stylesheets
      contentPane.getStylesheets().setAll(scene.getStylesheets());
      contentPane.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyEvent);
    }
    show(anchor, location.getX(), location.getY());
  }

  /**
   * Applies the editors to the column filter and hides the popup.
   */
  public void apply() {
    Set<String> selectedTexts = new LinkedHashSet<>();
    boolean blankSelected = true;
    boolean allSelected = true;
    for (TableFilterValue value : values) {
      switch (value.getKind()) {
        case SELECT_ALL -> { }
        case BLANK -> {
          blankSelected = value.isSelected();
          allSelected &= blankSelected;
        }
        case VALUE -> {
          if (value.isSelected()) {
            selectedTexts.add(value.getText());
          }
          else {
            allSelected = false;
          }
        }
      }
    }
    // all values selected: don't restrict by the value set at all (also covers newly added rows)
    columnFilter.apply(allSelected && !valuesTruncated ? null : selectedTexts, blankSelected,
                       operator.get(), emptyToNull(firstOperand.get()), emptyToNull(secondOperand.get()));
    hide();
  }

  /**
   * Discards the editors and hides the popup.
   */
  public void cancel() {
    hide();
  }

  /**
   * Clears the column's filter and hides the popup.
   */
  public void clearFilter() {
    columnFilter.clear();
    hide();
  }

  /**
   * Sorts the column in ascending order.<br>
   * The popup stays open so the user can keep refining sort and filter.
   */
  public void sortAscending() {
    sort(true);
  }

  /**
   * Sorts the column in descending order.<br>
   * The popup stays open so the user can keep refining sort and filter.
   */
  public void sortDescending() {
    sort(false);
  }

  /**
   * Selects or deselects all values.
   *
   * @param selected true to select all
   */
  public void selectAll(boolean selected) {
    if (updatingSelection) {
      return;
    }
    updatingSelection = true;
    try {
      for (TableFilterValue value : displayedValues) {
        if (!value.isPseudo() || value.getKind() == TableFilterValue.KIND.BLANK) {
          value.setSelected(selected);
        }
      }
    }
    finally {
      updatingSelection = false;
    }
    updateSelectAll();
  }

  /**
   * Updates the state of the "select all" pseudo value from the selected values.
   */
  public void updateSelectAll() {
    if (updatingSelection || values.isEmpty()) {
      return;
    }
    boolean allSelected = true;
    for (TableFilterValue value : values) {
      if ((!value.isPseudo() || value.getKind() == TableFilterValue.KIND.BLANK) && !value.isSelected()) {
        allSelected = false;
        break;
      }
    }
    updatingSelection = true;
    try {
      values.getFirst().setSelected(allSelected);
    }
    finally {
      updatingSelection = false;
    }
  }


  @Override
  protected Skin<?> createDefaultSkin() {
    return new TableFilterPopupSkin<>(this);
  }

  /**
   * Gets the popup's content pane, creating the skin if necessary.<br>
   * The skin would be created lazily during the first CSS pass, which is too late to configure
   * the content before the popup becomes visible.
   *
   * @return the content pane, null if the skin's node is not a parent
   */
  protected Parent getContentPane() {
    Skin<?> skin = getSkin();
    if (skin == null) {
      skin = createDefaultSkin();
      setSkin(skin);
    }
    return skin.getNode() instanceof Parent parent ? parent : null;
  }

  /**
   * Collects the column's distinct values.
   */
  protected void loadValues() {
    TableFilterSupport.CollectedValues collected = support.collectValues(columnFilter);
    totalValueCount = collected.totalCount();
    valuesTruncated = collected.truncated();
    values.setAll(collected.values());
    for (TableFilterValue value : values) {
      value.selectedProperty().addListener((_, _, _) -> updateSelectAll());
    }
  }

  /**
   * Initializes the condition editors from the column filter.
   */
  protected void loadCondition() {
    operator.set(columnFilter.getOperator());
    firstOperand.set(nullToEmpty(columnFilter.getFirstOperand()));
    secondOperand.set(nullToEmpty(columnFilter.getSecondOperand()));
    searchText.set("");
  }

  /**
   * Applies the search text to the displayed values.
   *
   * @param text the search text
   */
  protected void updateSearchPredicate(String text) {
    String search = normalize(text);
    displayedValues.setPredicate(value -> value.isPseudo() || search.isEmpty() ||
                                          normalize(value.getDisplayedText()).contains(search));
  }

  /**
   * Gets the operators applicable to the filtered column.
   *
   * @return the operators
   */
  public List<TableFilterOperator> getApplicableOperators() {
    return TableFilterOperator.applicableTo(getColumnConfiguration().getType());
  }

  /**
   * Handles the key events of the popup's content.
   *
   * @param event the key event
   */
  protected void handleKeyEvent(KeyEvent event) {
    if (event.getCode() == KeyCode.ENTER) {
      apply();
      event.consume();
    }
    else if (event.getCode() == KeyCode.ESCAPE) {
      cancel();
      event.consume();
    }
  }

  /**
   * Sorts the filtered column.
   *
   * @param ascending true to sort ascending, false for descending
   */
  protected void sort(boolean ascending) {
    FxTableView<S> table = support.getTable();
    if (table != null) {
      TableColumn<S,T> column = getColumnConfiguration().getTableColumn();
      column.setSortType(ascending ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
      table.getSortOrder().setAll(new ArrayList<>(List.of(column)));
    }
    else {
      FxTreeTableView<S> treeTable = support.getTreeTable();
      TreeTableColumn<S,T> column = getColumnConfiguration().getTreeTableColumn();
      column.setSortType(ascending ? TreeTableColumn.SortType.ASCENDING : TreeTableColumn.SortType.DESCENDING);
      treeTable.getSortOrder().setAll(new ArrayList<>(List.of(column)));
    }
  }


  private String normalize(String text) {
    if (text == null) {
      return "";
    }
    String normalized = StringNormalizer.getInstance().unDiacrit(text, true);
    return (normalized == null ? text : normalized).toUpperCase(Locale.ENGLISH);
  }

  private static String nullToEmpty(String text) {
    return text == null ? "" : text;
  }

  private static String emptyToNull(String text) {
    return text == null || text.isEmpty() ? null : text;
  }

}
