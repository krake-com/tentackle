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

package org.tentackle.fx.table;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.tentackle.fx.component.FxTableView;

/**
 * A table displaying the totals of another table.<br>
 * Displays any number of rows (1 for totals, 3 for totals and min + max, for example).<br>
 * The items must contain the total values.
 *
 * @author harald
 * @param <S> the row type
 */
public class TotalsTableView<S> extends FxTableView<S> {

  private FxTableView<S> boundTable;


  /**
   * Listener to sync column order.
   */
  private final ListChangeListener<TableColumn<S, ?>> columnListener
          = (ListChangeListener.Change<? extends TableColumn<S, ?>> c) -> {

            while (c.next()) {
              if (c.wasAdded()) {
                ObservableList<TableColumn<S, ?>> totalsColumns = FXCollections.observableArrayList(getColumns());
                int j = c.getFrom();      // will always be 0, but...
                for (TableColumn<S, ?> col : c.getAddedSubList()) {   // contains all columns!
                  int i = c.getRemoved().indexOf(col);                // contains all columns as well!
                  if (i >= 0) {   // just for sure...
                    totalsColumns.set(j, getColumns().get(i));
                    j++;
                  }
                }
                getColumns().setAll(totalsColumns);
              }
            }
          };



  /**
   * Creates an unbound totals table.
   */
  public TotalsTableView() {
    applyCSS();
    // allow ctrl-c copy of multiple cells (see TableViewConfigurator)
    getSelectionModel().setCellSelectionEnabled(true);
    getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    setCopyToClipboardEnabled(true);
  }


  /**
   * Sets the bound table.<br>
   * This is the table holding the data to summarize in this table.
   *
   * @param boundTable the bound table
   */
  public void setBoundTable(FxTableView<S> boundTable) {
    unbind();
    this.boundTable = boundTable;
    bind();
  }

  /**
   * Gets the bound table.
   *
   * @return the bound table
   */
  public TableView<S> getBoundTable() {
    return boundTable;
  }

  /**
   * Applies the {@code totalstable.css}-file.
   */
  protected void applyCSS() {
    // the totals table uses its own CSS file that makes the header and horizontal scrollbar invisible
    getStylesheets().addAll(TotalsTableView.class.getResource("totalstable.css").toExternalForm());
    getStyleClass().add("totalstable");
  }

  /**
   * Binds the current table.
   */
  protected void bind() {
    if (boundTable != null) {
      for (TableColumn<S,?> boundColumn: boundTable.getColumns()) {
        getColumns().add(createTotalsColumn(boundColumn));
      }
      boundTable.getColumns().addListener(columnListener);
      boundTable.setTotalsTableView(this);
    }
  }

  /**
   * Unbinds the table.
   */
  protected void unbind() {
    if (boundTable != null) {
      boundTable.setTotalsTableView(null);
      boundTable.getColumns().removeListener(columnListener);
    }
    getColumns().clear();
  }

  /**
   * Creates a totals column from the original bound column.
   *
   * @param boundColumn the original column
   * @return the totals column
   */
  @SuppressWarnings("unchecked")
  protected TableColumn<S,?> createTotalsColumn(TableColumn<S,?> boundColumn) {
    TableColumn totalsColumn = new TableColumn<>();
    if (isSummable(boundColumn)) {
      totalsColumn.setCellValueFactory(boundColumn.getCellValueFactory());
      totalsColumn.setCellFactory(boundColumn.getCellFactory());
    }
    // sync column widths
    totalsColumn.prefWidthProperty().bind(boundColumn.widthProperty());
    // sync visibility
    totalsColumn.visibleProperty().bind(boundColumn.visibleProperty());
    return totalsColumn;
  }

  /**
   * Returns whether a column is summable.
   *
   * @param boundColumn the column
   * @return true if summable
   */
  protected boolean isSummable(TableColumn<S,?> boundColumn) {
    return boundColumn instanceof FxTableColumn && ((FxTableColumn) boundColumn).getConfiguration().isSummable();
  }

}
