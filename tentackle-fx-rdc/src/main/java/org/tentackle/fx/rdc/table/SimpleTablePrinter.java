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

package org.tentackle.fx.rdc.table;

import com.sun.javafx.scene.control.skin.TableViewSkin;
import com.sun.javafx.scene.control.skin.VirtualFlow;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.transform.Scale;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.table.TableConfiguration;

/**
 * A controller to print a table.<br>
 *
 * @author harald
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class SimpleTablePrinter extends AbstractFxController {

  private ResourceBundle resources;   // I18N resources
  private int pageCount;              // number of pages

  @FXML
  private FxLabel titleLabel;

  @FXML
  private FxTableView<?> tableView;

  @FXML
  private FxLabel pageLabel;


  private static class PageInfo {

    private final int rowStart;
    private final int rowCount;

    public PageInfo(int rowStart, int rowCount) {
      this.rowStart = rowStart;
      this.rowCount = rowCount;
    }
  }


  @Override
  @SuppressWarnings("unchecked")
  public void initialize(URL location, ResourceBundle resources) {
    this.resources = resources;
  }


  /**
   * Prints the table.
   *
   * @param table the table to be printed
   * @param title the job title
   * @param job the printer job
   * @param pageLayout the printing layout
   */
  @SuppressWarnings("unchecked")
  public void print(FxTableView<?> table, String title, PrinterJob job, PageLayout pageLayout) {

    // copy the columns to the printing table
    double tableWidth = 0;
    tableView.getColumns().clear();
    tableView.setConfiguration((TableConfiguration) table.getConfiguration());
    for (TableColumn<?,?> column: table.getColumns()) {
      if (column.isVisible()) {
        TableColumn printColumn = new TableColumn(column.getText());
        printColumn.setCellValueFactory(column.getCellValueFactory());
        printColumn.setCellFactory(column.getCellFactory());
        printColumn.setMinWidth(column.getWidth());
        printColumn.setMaxWidth(column.getWidth());
        tableView.getColumns().add(printColumn);
        tableWidth += column.getWidth();
      }
    }


    double printableWidth = pageLayout.getPrintableWidth();
    double printableHeight = pageLayout.getPrintableHeight();

    // scale to fit page width if necessary
    double scaleXY = 1.0;
    if (printableWidth < tableWidth) {
      scaleXY = printableWidth / tableWidth;
      tableWidth = printableWidth / scaleXY;
    }
    tableView.setMinWidth(tableWidth - 2);
    tableView.setMaxWidth(tableWidth - 2);

    // set the table's height to fit the page height minus title and page count labels
    titleLabel.setText(title);
    updatePageLabel(1);
    double tableHeight = (printableHeight - titleLabel.getHeight() - pageLabel.getHeight()) / scaleXY;
    tableView.setMinHeight(tableHeight);
    tableView.setMaxHeight(tableHeight);

    // scale to page width
    getView().getTransforms().add(new Scale(scaleXY, scaleXY));

    System.out.println("displayed rows = " + getVisibleRowCount(table));

    tableView.setItems((ObservableList) table.getItems());

    Platform.runLater(() -> {

      int visibleCount = getVisibleRowCount(tableView) - 1;
      System.out.println("visible rows = " + visibleCount);

      // calculate the page contents and number of pages
      List<PageInfo> pages = new ArrayList<>();

      int start = 0;
      int count = 0;

      ObservableList items = FXCollections.observableArrayList();
      tableView.setItems(items);

      for (Object item : table.getItems()) {
        items.add(item);

        if (visibleCount < count) {
          // item no more visible:
          pages.add(new PageInfo(start, count));
          start += count;
          items.clear();
          items.add(item);
          count = 1;    // one item should always fit
        }
        else {
          count++;
        }
      }

      if (count > 0) {
        pages.add(new PageInfo(start, count));
      }

      pageCount = pages.size();
      int pageNo = 1;

      // print all pages
      for (PageInfo page : pages) {
        tableView.setItems(FXCollections.observableList(
                (List) table.getItems().subList(page.rowStart, page.rowStart + page.rowCount)));
        tableView.getSelectionModel().clearSelection();
        updatePageLabel(pageNo++);
        job.printPage(pageLayout, getView());
      }

      job.endJob();
    });
  }



  private void updatePageLabel(int pageNo) {
    pageLabel.setText(MessageFormat.format(resources.getString("page {0} of {1}"), pageNo, pageCount));
  }


  private VirtualFlow<?> loadVirtualFlow(TableView<?> table) {
    return (VirtualFlow<?>) ((TableViewSkin<?>) table.getSkin()).getChildren().get(1);
  }

  private int getVisibleRowCount(TableView<?> table) {
    VirtualFlow<?> flow = loadVirtualFlow(table);
    int start = 0;
    int end = 0;
    IndexedCell<?> cell = flow.getFirstVisibleCell();
    if (cell != null) {
      start = cell.getIndex();
    }
    cell = flow.getLastVisibleCell();
    if (cell != null) {
      end = cell.getIndex();
    }
    return end - start;
  }

}
