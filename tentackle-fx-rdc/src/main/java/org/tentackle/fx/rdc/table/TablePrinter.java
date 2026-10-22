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

package org.tentackle.fx.rdc.table;

import org.tentackle.common.Timestamp;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTableColumn;
import org.tentackle.fx.table.FxTreeTableColumn;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.TotalsTableView;
import org.tentackle.log.Logger;
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.AbstractSessionTask;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A controller to print a table.<br>
 * The visible columns are copied to a printing table, which is scaled down to the page width if necessary.
 * The rows are split into pages according to the actually rendered row heights, which is why the pages
 * are prepared in a background task that hops to the FX thread for each page.
 * A progress dialog shows the page being prepared or printed and allows canceling the job.
 * The totals of a table, if any, are printed as well.
 *
 * @author harald
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class TablePrinter extends AbstractFxController {

  /*
   * TableView and TreeTableView don't inherit from each other and thus
   * each spawn their own related types such as TableColumn, TreeTableColumn, etc...
   * As a result, since TablePrinter applies to both tables and tree tables,
   * a lot of semantically identical code is necessary.
   * However, it is not really duplicate code. There's not much we can do to reduce it.
   */

  /**
   * Prints a table.
   *
   * @param table the table to be printed
   * @param title the job title, null to take the table's configured name
   * @param job the printer job
   */
  public static void print(FxTableView<?> table, String title, PrinterJob job) {
    print(table, null, title, job);
  }

  /**
   * Prints a tree table.
   *
   * @param treeTable the tree table to be printed
   * @param title the job title, null to take the tree table's configured name
   * @param job the printer job
   */
  public static void print(FxTreeTableView<?> treeTable, String title, PrinterJob job) {
    print(null, treeTable, title, job);
  }

  @SuppressWarnings("rawtypes")
  private static void print(FxTableView<?> table, FxTreeTableView treeTable, String title, PrinterJob job) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, table != null ? table : treeTable);
    TablePrinter printer = Fx.load(TablePrinter.class);
    Scene scene = Fx.createScene(printer.getView());
    stage.setScene(scene);
    stage.setTitle(RdcFxRdcBundle.getString("Printing..."));
    String jobTitle;
    if (title == null) {
      if (table != null) {
        jobTitle = table.getConfiguration().getName();
      }
      else {
        jobTitle = treeTable.getConfiguration().getName();
      }
    }
    else {
      jobTitle = title;
    }
    if (jobTitle != null) {
      job.getJobSettings().setJobName(jobTitle);
    }
    if (table != null) {
      stage.setOnShown(e -> Platform.runLater(() -> printer.print(table, null, jobTitle, job, job.getJobSettings().getPageLayout(), stage)));
    }
    else {
      stage.setOnShown(e -> Platform.runLater(() -> printer.print(null, treeTable, jobTitle, job, job.getJobSettings().getPageLayout(), stage)));
    }
    stage.setOnCloseRequest(e -> Fx.question(stage, RdcFxRdcBundle.getString("cancel print job?"), false, answer -> {
      if (answer) {
        printer.terminationRequested = true;
      }
      else {
        e.consume();
      }
    }));
    Fx.show(stage);
  }

  private static final Logger LOGGER = Logger.get(TablePrinter.class);

  private static final String STYLE_TITLE = "title";
  private static final String STYLE_BOTTOMLINE = "bottomline";
  private static final String STYLE_TOTALS = "totals";

  @FXML
  private ResourceBundle resources;   // I18N resources

  @FXML
  private FxLabel titleLabel;

  @FXML
  private FxTableView<?> tableView;

  @FXML
  private FxHBox bottomLine;

  @FXML
  private FxLabel dateLabel;

  @FXML
  private FxLabel pageLabel;


  private int pageCount;                                          // number of pages
  private TimeKeeper jobTimeKeeper;                               // for logging only
  private int rowCount;                                           // number of rows to print (incl. totals)
  private int totalsRowCount;                                     // number of total rows, 0 = none
  private volatile boolean terminationRequested;                  // true if user closed the printing dialog
  private final AtomicInteger rowOffset = new AtomicInteger();    // offset in model


  /**
   * Creates a table printer controller.
   */
  public TablePrinter() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Cell factory to suppress not summable cells in totals.
   *
   * @param <S> The type of the TableView generic type (i.e. S == TableView&lt;S&gt;)
   * @param <T> The type of the content in all cells in this TableColumn.
   */
  private class PrintCellFactory<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {

    private final Callback<TableColumn<S, T>, TableCell<S, T>> orgTableCellFactory;

    public PrintCellFactory(Callback<TableColumn<S, T>, TableCell<S, T>> orgTableCellFactory) {
      this.orgTableCellFactory = orgTableCellFactory;
    }

    @Override
    public TableCell<S, T> call(TableColumn<S, T> column) {
      TableCell<S, T> cell = orgTableCellFactory.call(column);
      FxTableColumn<S, T> fxColumn = (FxTableColumn<S, T>) column;
      TableColumnConfiguration<S, T> columnConfig = fxColumn.getConfiguration();
      if (totalsRowCount > 0 && columnConfig != null) {
        // replace by cell aware of totals
        cell = new FxTableCell<>(columnConfig) {
          @Override
          protected void updateItem(T item, boolean empty) {
            int index = rowOffset.get() + getIndex();
            if (index >= rowCount - totalsRowCount) {
              if (columnConfig.isSummable() && index < rowCount) {
                if (!getStyleClass().contains(STYLE_TOTALS)) {
                  getStyleClass().add(STYLE_TOTALS);
                }
              }
              else {
                // totals row: columns not summable are rendered empty (see TotalsTableView)
                getStyleClass().remove(STYLE_TOTALS);
                setText(null);
                setGraphic(null);
                return;
              }
            }
            else {
              getStyleClass().remove(STYLE_TOTALS);
            }
            super.updateItem(item, empty);
          }
        };
      }
      return cell;
    }

  }


  @FXML
  private void initialize() {
    titleLabel.getStyleClass().add(STYLE_TITLE);
    bottomLine.getStyleClass().add(STYLE_BOTTOMLINE);
    pageLabel.getStyleClass().add(STYLE_BOTTOMLINE);
    dateLabel.getStyleClass().add(STYLE_BOTTOMLINE);
    jobTimeKeeper = new TimeKeeper();
  }


  /**
   * Prints a table or a tree table.
   *
   * @param table the table to be printed (treeTable == null)
   * @param treeTable the tree table (table == null)
   * @param title the job title
   * @param job the printer job
   * @param pageLayout the printing layout
   * @param stage the progress dialog's stage
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void print(FxTableView<?> table, FxTreeTableView<?> treeTable, String title, PrinterJob job, PageLayout pageLayout, Stage stage) {

    LOGGER.fine("table print job {0}", job);

    dateLabel.setText(FormatHelper.formatShortTimestamp(new Timestamp()));

    // copy the columns to the printing table
    double tableWidth = 0.0;
    tableView.getColumns().clear();

    if (table != null) {
      tableView.setConfiguration((TableConfiguration) table.getConfiguration());
      for (TableColumn<?, ?> column : table.getColumns()) {
        if (column.isVisible()) {
          TableColumnConfiguration<?, ?> columnConfig = column instanceof FxTableColumn ?
                                                        ((FxTableColumn) column).getConfiguration() : null;
          FxTableColumn printColumn = new FxTableColumn(columnConfig, column.getText());
          printColumn.setCellValueFactory(column.getCellValueFactory());
          printColumn.setCellFactory(new PrintCellFactory(column.getCellFactory()));
          printColumn.setMinWidth(column.getWidth());
          printColumn.setMaxWidth(column.getWidth());
          tableView.getColumns().add(printColumn);
          tableWidth += column.getWidth();
        }
      }
    }
    else {
      tableView.setConfiguration((TableConfiguration) treeTable.getConfiguration());
      for (TreeTableColumn<?, ?> column : treeTable.getColumns()) {
        if (column.isVisible()) {
          TableColumnConfiguration<?, ?> columnConfig = column instanceof FxTreeTableColumn ?
                                                        ((FxTreeTableColumn) column).getConfiguration() : null;
          FxTableColumn printColumn = new FxTableColumn(columnConfig, column.getText());
          if (columnConfig != null) {
            printColumn.setCellValueFactory(columnConfig.getTableColumn().getCellValueFactory()); // use fake column!
            printColumn.setCellFactory(columnConfig.getTableColumn().getCellFactory());   // no totals -> no PrintCell necessary
          }
          else {
            printColumn.setCellValueFactory(column.getCellValueFactory());
            printColumn.setCellFactory(new PrintCellFactory(column.getCellFactory()));
          }
          printColumn.setMinWidth(column.getWidth());
          printColumn.setMaxWidth(column.getWidth());
          tableView.getColumns().add(printColumn);
          tableWidth += column.getWidth();
        }
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
    tableView.setMinWidth(tableWidth - 1);
    tableView.setMaxWidth(tableWidth - 1);

    // set the table's height to fit the page height minus title and page count labels
    titleLabel.setText(title);
    updatePageLabel(1);
    getView().layout();
    double tableHeight = printableHeight / scaleXY - titleLabel.getHeight() - bottomLine.getHeight();
    tableView.setMinHeight(tableHeight);
    tableView.setMaxHeight(tableHeight);

    // scale to page width
    getView().getTransforms().add(new Scale(scaleXY, scaleXY));

    List items;
    if (table != null) {
      TotalsTableView<?> totalsTable = TotalsTableView.getTotalsTableView(table);
      if (totalsTable != null) {
        // add totals
        items = new ArrayList(table.getItems());
        items.addAll(totalsTable.getItems());
        totalsRowCount = totalsTable.getItems().size();
      }
      else {
        items = table.getItems();
        totalsRowCount = 0;
      }
    }
    else {
      items = treeTable.getItems();
      totalsRowCount = 0;
    }

    rowCount = items.size();

    //    Platform.runLater(() -> {
    //
    //      int printableRows = getVisibleRowCount(tableView);
    //      ... etc...
    //    doesn't always work
    //    hence -> Task running in background and invoking Fx.runAndWait from bg task.

    Rdc.bg(tableView, new PrinterTask(items, job, pageLayout, stage), null, null);
  }


  private void updatePageLabel(int pageNo) {
    pageLabel.setText(MessageFormat.format(resources.getString("page {0} of {1}"), pageNo, pageCount));
  }



  /**
   * Task using the ModificationTracker to print the table.
   */
  @SuppressWarnings({"serial", "rawtypes" })
  private class PrinterTask extends AbstractSessionTask implements Supplier<Void> {

    private final List items;
    private final PrinterJob job;
    private final PageLayout pageLayout;
    private final Stage stage;

    private volatile int printableRows;

    private PrinterTask(List items, PrinterJob job, PageLayout pageLayout, Stage stage) {
      this.items = items;
      this.job = job;
      this.pageLayout = pageLayout;
      this.stage = stage;
    }

    private void setPrintableRows(int printableRows) {
      this.printableRows = printableRows;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {

      // Info for one printable page
      record PageInfo(int rowStart, int rowCount) {

        @Override
        public String toString() {
          return "print " + rowCount + " rows starting at row " + rowStart;
        }
      }

      // calculate the page contents and number of pages
      List<PageInfo> pages = new ArrayList<>();
      int size  = items.size();
      rowOffset.set(0);
      while (rowOffset.get() < size && !terminationRequested) {
        FxUtilities.getInstance().runAndWait(() -> {
          // apply remaining items (works with varying row heights)
          tableView.setItems(FXCollections.observableList(items.subList(rowOffset.get(), size)));
          getView().layout();   // this does the trick to make getVisibleRowCount below work!
        });
        FxUtilities.getInstance().runAndWait(() -> setPrintableRows(getVisibleRowCount()));
        if (printableRows <= 0) {
          // no space to print (could be a rendering problem, whatsoever....)
          Platform.runLater(() -> {
            job.cancelJob();
            Fx.error(stage, resources.getString("no printable area"), null, stage::close);
          });
          return;
        }
        PageInfo page = new PageInfo(rowOffset.get(), printableRows);
        pages.add(page);
        Platform.runLater(() -> stage.setTitle(MessageFormat.format(resources.getString("preparing page {0}"), pages.size())));
        rowOffset.addAndGet(printableRows);
        LOGGER.fine("page {0}: {1}", pages.size(), page);
        getSession().setAlive(true);
      }

      if (terminationRequested) {
        LOGGER.info("job \"{0}\" canceled by user", titleLabel.getText());
      }
      else {
        // print the pages
        pageCount = pages.size();
        FxUtilities.getInstance().runAndWait(() -> {
          if (pageCount == 0) {
            Fx.info(getView(), resources.getString("no pages to print"), job::cancelJob);
          }
          else {
            int pageNo = 1;
            for (PageInfo page : pages) {
              rowOffset.set(page.rowStart);
              tableView.setItems(FXCollections.observableList(items.subList(page.rowStart, page.rowStart + page.rowCount)));
              tableView.getSelectionModel().clearSelection();
              stage.setTitle(MessageFormat.format(resources.getString("printing page {0}/{1}"),
                                                  pageNo, pages.size()));
              updatePageLabel(pageNo++);
              job.printPage(pageLayout, getView());
              if (terminationRequested) {
                break;
              }
              getSession().setAlive(true);
            }
            if (terminationRequested) {
              job.cancelJob();
              LOGGER.info("printing job \"{0}\" canceled by user", titleLabel.getText());
            }
            else {
              job.endJob();
              jobTimeKeeper.end();
              LOGGER.info("job \"{0}\", {1} pages printed, {2} s",
                          titleLabel.getText(), pages.size(), jobTimeKeeper.secondsToString());
            }
          }
          stage.close();
        });
      }
    }

    private int getVisibleRowCount() {
      VirtualFlow<?> flow = (VirtualFlow<?>) ((TableViewSkin<?>) tableView.getSkin()).getChildren().get(1);
      int start = 0;
      IndexedCell<?> cell = flow.getFirstVisibleCell();
      if (cell != null) {
        start = cell.getIndex();
      }
      cell = flow.getLastVisibleCell();
      int count = -1;
      if (cell != null) {
        int end = cell.getIndex();
        count = end - start;
        if (end >= flow.getCellCount() - 1) {
          // last cell:
          // in FX9 VirtualFlow.getLastVisibleCellWithinViewport is package scope
          // we use scrollTo to test whether last cell is within viewport
          tableView.scrollTo(end);
          cell = flow.getFirstVisibleCell();
          if (cell != null && cell.getIndex() == start) {
            // last cell completely visible
            count++;
          }
          else {
            tableView.scrollTo(start);
          }
        }
      }
      return count;
    }

    @Override
    public Void get() {
      run();
      return null;
    }
  }

}
