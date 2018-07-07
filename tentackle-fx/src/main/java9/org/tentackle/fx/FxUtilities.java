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

package org.tentackle.fx;

import com.sun.javafx.css.StyleManager;
import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.StringTokenizer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import org.tentackle.bind.Bindable;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.component.Note;
import org.tentackle.common.StringHelper;

interface FxUtilities$Singleton {
  FxUtilities INSTANCE = ServiceFactory.createService(FxUtilities.class, FxUtilities.class);
}

/**
 * Utility methods for Fx.
 *
 * @author harald
 */
@Service(FxUtilities.class)    // defaults to self
public class FxUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static FxUtilities getInstance() {
    return FxUtilities$Singleton.INSTANCE;
  }



  /**
   * The help url.
   * prefix for online help, e.g.: http://localhost/manual/index.html
   */
  private String helpURL;



  /**
   * Opens the online help for a given component.
   *
   * @param control the Fx control
   */
  public void showHelp(FxControl control)  {
    if (control != null) {
      String url = control.getHelpUrl();

      if (helpURL != null) {    // with global prefix?
        if (url != null)  {
          url = helpURL + url;
        }
        else  {
          url = helpURL;
        }
      }

      if (url != null)  {
        try {
          Desktop.getDesktop().browse(URI.create(url));
        }
        catch (IOException | RuntimeException ex) {
          Fx.error(MessageFormat.format(FxFxBundle.getString("CAN'T OPEN HELP FOR <{0}>"), url), ex);
        }
      }
    }
  }


  /**
   * Gets the stage for a node.
   *
   * @param node the node
   * @return the stage, null if node does not belong to a scene or scene does not belong to a stage.
   */
  public Stage getStage(Node node) {
    Stage stage = null;
    if (node != null) {
      Scene scene = node.getScene();
      if (scene != null) {
        Window window = scene.getWindow();
        if (window instanceof Stage) {
          stage = (Stage) window;
        }
      }
    }
    return stage;
  }


  /**
   * Returns whether the stage is modal.
   *
   * @param stage the stage
   * @return true if effectively modal
   */
  public boolean isModal(Stage stage) {
    boolean modal = false;
    if (stage != null) {
      switch(stage.getModality()) {
        case APPLICATION_MODAL:
          modal = true;
          break;

        case WINDOW_MODAL:
          modal = stage.getOwner() != null;
          break;

        default:
          // NONE
      }
    }
    return modal;
  }


  /**
   * Closes the hierarchy of stages from the given node stopping at given stage.
   *
   * @param node the node
   * @param stopStage the stop stage, null to close the whole application
   */
  public void closeStageHierarchy(Node node, Stage stopStage) {
    Stage stage = getStage(node);
    while (stage != null && stage != stopStage) {
      stage.close();
      Window owner = stage.getOwner();
      if (owner instanceof Stage) {
        stage = (Stage) owner;
      }
      else {
        break;
      }
    }
  }


  /**
   * Dumps the component hierarchy.
   *
   * @param node the node
   * @return the formatted string
   */
  public String dumpComponentHierarchy(Node node) {
    StringBuilder buf = new StringBuilder();
    buf.append(">>> component hierarchy within ");
    Stage stage = getStage(node);
    if (stage != null) {
      buf.append("stage '").append(stage.getTitle()).append("'");
    }
    buf.append('\n');
    while (node != null) {
      buf.append(node).append(" @ [").append(node.getLayoutX()).append("/").append(node.getLayoutY()).append("]\n");
      node = node.getParent();
    }
    buf.append("<<<");
    return buf.toString();
  }


  /**
   * Shows an info dialog.
   *
   * @param message the message
   */
  public void showInfoDialog(String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(FxFxBundle.getString("INFORMATION"));
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.setResizable(true);   // RT-38998
    alert.showAndWait();
  }

  /**
   * Shows a question dialog.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @return true if yes, false if no
   */
  public boolean showQuestionDialog(String message, boolean defaultYes) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(FxFxBundle.getString("QUESTION"));
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.setResizable(true);   // RT-38998
    alert.getButtonTypes().clear();
    alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
    Button noButton = (Button) alert.getDialogPane().lookupButton(ButtonType.NO);
    Button yesButton = (Button) alert.getDialogPane().lookupButton(ButtonType.YES);
    noButton.setDefaultButton(!defaultYes);
    yesButton.setDefaultButton(defaultYes);
    Optional<ButtonType> result = alert.showAndWait();
    return result.get() == ButtonType.YES;
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   * @param t optional throwable
   */
  public void showErrorDialog(String message, Throwable t) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(FxFxBundle.getString("ERROR"));
    alert.setHeaderText(null);
    alert.setContentText(message);

    if (t != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      String exceptionText = sw.toString();
      Label label = new Label("STACKTRACE:");
      TextArea textArea = new TextArea(exceptionText);
      textArea.setEditable(false);
      textArea.setWrapText(true);
      textArea.setMaxWidth(Double.MAX_VALUE);
      textArea.setMaxHeight(Double.MAX_VALUE);
      GridPane expContent = new GridPane();
      expContent.setMaxWidth(Double.MAX_VALUE);
      expContent.add(label, 0, 0);
      expContent.add(textArea, 0, 1);
      GridPane.setVgrow(textArea, Priority.ALWAYS);
      GridPane.setHgrow(textArea, Priority.ALWAYS);
      alert.getDialogPane().setExpandableContent(expContent);
      // because of resize bug in FX8 (@todo: remove in later versions if fixed)
      alert.getDialogPane().expandedProperty().addListener((l) -> {
        Platform.runLater(() -> {
          alert.getDialogPane().requestLayout();
          Window w = alert.getDialogPane().getScene().getWindow();
          w.sizeToScene();
        });
      });
    }

    alert.showAndWait();
  }


  /**
   * Shows an error popup for a component.
   *
   * @param component the component
   * @return the popup, null if no errormessage in component
   */
  public PopupWindow showErrorPopup(ErrorPopupSupported component) {
    PopupWindow popup = null;
    String errorMessage = component.getError();
    if (!StringHelper.isAllWhitespace(errorMessage)) {
      Note note = new Note(Note.Position.RIGHT, Note.Type.ERROR);
      note.setText(errorMessage);
      note.show((Node) component);
      popup = note;
    }
    return popup;
  }

  /**
   * Shows an info popup for a component.
   *
   * @param component the component
   * @return the popup, null if no infomessage in component
   */
  public PopupWindow showInfoPopup(InfoPopupSupported component) {
    PopupWindow popup = null;
    String infoMessage = component.getInfo();
    if (!StringHelper.isAllWhitespace(infoMessage)) {
      Note note = new Note(Note.Position.RIGHT, Note.Type.INFO);
      note.setText(infoMessage);
      note.show((Node) component);
      popup = note;
    }
    return popup;
  }


  /**
   * Performs all necessary operations on an FxComponent when it gained the focus.
   *
   * @param component the fx component
   */
  public void focusGained(FxComponent component) {
    if (component.getError() == null) {  // don't override view if there is a parsing error
      component.updateView();
      if (component.isChangeable() && component instanceof FxTextComponent) {
        // By default, FX autoselects a field if on re-focus.
        // This cannot be configured and is hard wired (for whatever reason).
        // In TT, this is configurable via the autoSelect feature.
        // in FX components are autoselected if the field is focused by TAB (or ENTER in TT).
        // If by mouse, the cursor is positioned where the mouse points to and nothing is selected.
        // For numeric fields, however, (and where desired) this behaviour leads to unexpected data entry.
        Platform.runLater(((FxTextComponent) component)::autoSelect);
      }
    }
  }

  /**
   * Performs all necessary operations on an FxComponent when it lost the focus.
   *
   * @param component the fx component
   */
  public void focusLost(FxComponent component) {
    component.updateModel();    // some components will do nothing here and...
    // ... isModelUpdate() will return false.
    if (component.isModelUpdated() && component.getError() == null) {    // no conversion error
      // update view again to match the converted value
      component.updateView();   // this will clear modelUpdated
      ValueTranslator<?, ?> translator = component.getValueTranslator();
      if (translator != null && translator.needsToModelTwice()) {
        component.updateModel();
      }
    }
  }


  /**
   * Prints a node.<br>
   * The user selects the printer and the node is scaled down if too large for the paper.
   *
   * @param node the node to print
   */
  public void print(Node node) {
    PrinterJob job = PrinterJob.createPrinterJob();
    if (job != null && job.showPrintDialog(Fx.getStage(node))) {
      PageLayout pageLayout = job.getJobSettings().getPageLayout();
      double scaleX = 1.0;
      if (pageLayout.getPrintableWidth() < node.getBoundsInParent().getWidth()) {
        scaleX = pageLayout.getPrintableWidth() / node.getBoundsInParent().getWidth();
      }
      double scaleY = 1.0;
      if (pageLayout.getPrintableHeight() < node.getBoundsInParent().getHeight()) {
        scaleY = pageLayout.getPrintableHeight() / node.getBoundsInParent().getHeight();
      }
      double scaleXY = Double.min(scaleX, scaleY);
      Scale scale = new Scale(scaleXY, scaleXY);
      node.getTransforms().add(scale);
      boolean success = job.printPage(node);
      node.getTransforms().remove(scale);
      if (success) {
        job.endJob();
      }
    }
  }


  /**
   * Adds a stylesheet for the whole application
   *
   * @param url The file URL, either relative or absolute, as a String
   */
  public void addStyleSheet(String url) {
    // @todo StyleManager may become public in future releases
    StyleManager.getInstance().addUserAgentStylesheet(url);
  }


  /**
   * Configures the application-wide CSS.
   */
  public void addStyleSheets() {
    // tentackle styles
    addStyleSheet("/org/tentackle/fx/tentackle.css");
  }


  /**
   * Applies the bindable options to a text component.
   *
   * @param comp the component
   * @param member the binding member
   * @param options the options from the @{@link Bindable} annotation
   */
  public void applyBindingOptions(FxTextComponent comp, BindingMember member, String options) {
    if (options != null) {
      StringTokenizer stok = new StringTokenizer(options.toUpperCase(), ",");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        if (!applyBindingOption(comp, member, token))  {
          throw new BindingException("unsupported @Bindable option \"" + token + "\") in " + member);
        }
      }
    }
  }


  /**
   * Applies a single binding option to a text component.
   *
   * @param comp the component
   * @param member the binding member
   * @param option the option
   * @return true if option known and applied, false if unknown option
   */
  public boolean applyBindingOption(FxTextComponent comp, BindingMember member, String option) {

    boolean processed = false;

    if      (Constants.BIND_UC.equals(option)) {
      comp.setCaseConversion(CaseConversion.UPPER_CASE);
      processed = true;
    }
    else if (Constants.BIND_LC.equals(option)) {
      comp.setCaseConversion(CaseConversion.LOWER_CASE);
      processed = true;
    }
    else if (Constants.BIND_AUTOSELECT.equals(option)) {
      comp.setAutoSelect(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_AUTOSELECT).equals(option)) {
      comp.setAutoSelect(false);
      processed = true;
    }
    else if (Constants.BIND_UTC.equals(option)) {
      comp.setUTC(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_UTC).equals(option)) {
      comp.setUTC(false);
      processed = true;
    }
    else if (Constants.BIND_UNSIGNED.equals(option)) {
      comp.setUnsigned(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_UNSIGNED).equals(option)) {
      comp.setUnsigned(false);
      processed = true;
    }
    else if (option.startsWith(Constants.BIND_MAXCOL + "=")) {
      try {
        comp.setMaxColumns(Integer.parseInt(option.substring(Constants.BIND_MAXCOL.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_MAXCOL + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_COL + "=")) {
      try {
        comp.setColumns(Integer.parseInt(option.substring(Constants.BIND_COL.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_COL + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_SCALE + "=")) {
      try {
        comp.setScale(Integer.parseInt(option.substring(Constants.BIND_SCALE.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_SCALE + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }

    return processed;
  }



  /**
   * Resizes the width of all columns of a table to fit the displayed content.<br>
   * Unlike the "double-click on column separator"-feature this method computes
   * the column widths according to the content currently displayed. For tables
   * with a large number of rows this is much more efficient than scanning the whole table.
   *
   * @param table the table view
   */
  @SuppressWarnings("unchecked")
  public void resizeColumnsToFitContent(TableView<?> table) {
    TableViewSkin<?> tableSkin = (TableViewSkin<?>) table.getSkin();
    VirtualFlow<?> virtualFlow = (VirtualFlow<?>) tableSkin.getChildren().get(1);
    IndexedCell<?> iCell = virtualFlow.getFirstVisibleCell();
    if (iCell != null) {
      int first = iCell.getIndex();
      iCell = virtualFlow.getLastVisibleCell();
      if (iCell != null) {
        int last = iCell.getIndex();
        for (TableColumn column: table.getColumns()) {
          if (column.isVisible()) {
            Callback cellFactory = column.getCellFactory();
            if (cellFactory != null) {
              TableCell cell = (TableCell) cellFactory.call(column);
              if (cell != null) {
                Font cellFont = cell.getFont();
                // BOLD may not work if header is styled with CSS, but that's ok for default configs
                Font headerFont = Font.font(cellFont.getFamily(), FontWeight.BOLD, cellFont.getSize());
                cell.getProperties().put("deferToParentPrefWidth", Boolean.TRUE); // see TableCellSkin
                double maxWidth = 20;   // don't go below this so that column remains visible
                // apply header text first
                cell.updateTableColumn(column);
                cell.updateTableView(table);
                cell.setText(column.getText());
                cell.setGraphic(column.getGraphic());
                cell.setFont(headerFont);
                maxWidth = maxColumnWidth(maxWidth, cell, tableSkin);
                cell.setFont(cellFont);
                for (int row=first; row <= last; row++) {
                  cell.updateIndex(row);
                  maxWidth = maxColumnWidth(maxWidth, cell, tableSkin);
                }
                table.resizeColumn(column, maxWidth - column.getWidth());
              }
            }
          }
        }
      }
    }
  }


  /**
   * Delivers a lis of all showing stages.
   *
   * @return the stages
   */
  public ObservableList<Stage> getAllShowingStages() {
    ObservableList<Stage> stages = FXCollections.observableArrayList();
    for (Window w : Window.getWindows()) {
      if (w instanceof Stage) {
        stages.add((Stage) w);
      }
    }
    return stages;
  }


  /**
   * Calculates the location of a window so that it will be centered on the screen.
   *
   * @param window the window
   * @return the location (top left corner)
   */
  public Point2D determineCenteredLocation(Window window)  {
    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();
    // align sizes (for computation below)
    double windowWidth = window.getWidth();
    double windowHeight = window.getHeight();
    if (window.getWidth() > screenSize.getWidth())  {
      windowWidth = screenSize.getWidth();
    }
    if (windowHeight > screenSize.getHeight())  {
      windowHeight = screenSize.getHeight();
    }
    return new Point2D((screenSize.getWidth()  - windowWidth) / 2.0,
                       (screenSize.getHeight() - windowHeight) / 2.0);
  }


  /**
   * Calculates the position of a stage on the screen so that
   * it is being display in an optimal manner.
   *
   * @param stage the stage to be positioned on the screen
   * @return the location
   */
  public Point2D determinePreferredStageLocation(Stage stage)  {

    Point2D location = new Point2D(stage.getX(), stage.getY());

    if (location.getX() == 0.0 && location.getY() == 0.0) {   // if no position yet
      // place in the middle of the owner if possibe
      Window owner = stage.getOwner();
      if (owner != null) {
        location = new Point2D(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0,
                               owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
      }
      else  {
        // not much we can do: center it
        location = determineCenteredLocation(stage);
      }
    }

    return determineAlignedStageLocation(stage, location);
  }


  /**
   * Calculates the location of a stage so that it
   * is completely visible on the screen, using a "free" spot.
   *
   * @param stage the current stage
   * @param location the desired (not necessarily current!) location
   * @return the aligned location
   */
  public Point2D determineAlignedStageLocation(Stage stage, Point2D location) {

    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();

    double maxWidth  = screenSize.getWidth() - screenSize.getWidth() / XY_STEP_DIVISOR;
    double maxHeight = screenSize.getHeight() - screenSize.getHeight() / XY_STEP_DIVISOR;
    double minX      = screenSize.getWidth() / XY_STEP_DIVISOR;
    double minY      = screenSize.getHeight() / XY_STEP_DIVISOR;

    if (location.getX() + stage.getWidth() > maxWidth) {
      location = new Point2D(maxWidth - stage.getWidth(), location.getY());
    }
    if (location.getX() < 0.0) {
      location = new Point2D(0.0, location.getY());
    }
    else if (location.getX() < minX && location.getX() + stage.getWidth() < maxWidth) {
      location = new Point2D(minX, location.getY());
    }

    if (location.getY() + stage.getHeight() > maxHeight) {
      location = new Point2D(location.getX(), maxHeight - stage.getHeight());
    }
    if (location.getY() < 0.0) {
      location = new Point2D(location.getX(), 0.0);
    }
    else if (location.getY() < minY && location.getY() + stage.getHeight() < maxHeight) {
      location = new Point2D(location.getX(), minY);
    }

    return determineFreeStageLocation(stage, location);
  }






  private double maxColumnWidth(double maxWidth, TableCell cell, TableViewSkin<?> tableSkin) {
    if ((cell.getText() != null && !cell.getText().isEmpty()) || cell.getGraphic() != null) {
      tableSkin.getChildren().add(cell);
      cell.applyCss();
      maxWidth = Math.max(maxWidth, cell.prefWidth(-1));
      tableSkin.getChildren().remove(cell);
    }
    return maxWidth;
  }


  private static final double XY_STEP_DIVISOR = 32.0;
  private static final int MAX_LOCATION_LOOP = 4;

  /**
   * Determines a location for a stage that does not overlap with stages belonging to the same owner.
   *
   * @param stage the stage
   * @param startLocation the start location
   * @return the free location
   */
  private Point2D determineFreeStageLocation(Stage stage, Point2D startLocation)  {

    // get screensize
    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();

    final double stepX = screenSize.getWidth() / XY_STEP_DIVISOR;
    final double stepY = screenSize.getHeight() / XY_STEP_DIVISOR;

    // compute the center of the window
    double x = startLocation.getX() + stage.getWidth() / 2.0;
    double y = startLocation.getY() + stage.getHeight() / 2.0;

    // initial diff-stepping
    double dx;
    double dy;

    if (x > screenSize.getWidth() / 2.0) {
      dx = -stepX;
    }
    else {
      dx = stepX;
    }

    if (y > screenSize.getHeight() / 2.0) {
      dy = -stepY;
    }
    else {
      dy = stepY;
    }

    for (int loop=0; loop < MAX_LOCATION_LOOP; loop++)  {

      boolean abort = false;
      Point2D location = startLocation;

      while (!abort && isStageOverlapping(stage, location, dx, dy))  {
        location = location.add(dx, dy);
        if (location.getX() < 0.0) {
          location = new Point2D(0.0, location.getY());
          abort = true;
        }
        if (location.getX() + stage.getWidth() > screenSize.getWidth())  {
          location = new Point2D(screenSize.getWidth() - stage.getWidth(), location.getY());
          abort = true;
        }
        if (location.getY() < 0.0) {
          location = new Point2D(location.getX(), 0.0);
          abort = true;
        }
        if (location.getY() + stage.getHeight() > screenSize.getHeight())  {
          location = new Point2D(location.getX(), screenSize.getHeight() - stage.getHeight());
          abort = true;
        }
      }

      if (!abort) {
        // !isStageOverlapping
        return location;
      }

      // try other direction clockwise
      if (dx > 0.0 && dy > 0.0) {
        dx = -stepX;
      }
      else if (dx < 0.0 && dy > 0.0) {
        dy = -stepY;
      }
      else if (dx < 0.0 && dy < 0.0) {
        dx = stepX;
      }
      else if (dx > 0 && dy < 0) {
        dy = stepY;
      }
    }

    return startLocation;
  }


  /**
   * Checks if a stage would overlay any other stage that belongs to the same owner.
   *
   * @param stage the stage
   * @param location the requested location for this stage
   * @param dx maximum overlapping width
   * @param dy maximum overlapping height
   * @return true if overlapping, false if on free space
   */
  private boolean isStageOverlapping(Stage stage, Point2D location, double dx, double dy)  {
    Window owner = stage.getOwner();
    if (dx < 0.0) {
      dx = -dx;
    }
    if (dy < 0.0) {
      dy = -dy;
    }
    for (Stage w: getAllShowingStages())  {
      Window o = w.getOwner();
      if (w != stage && o == owner &&
          ((location.getX() <= w.getX() + dx &&
            location.getX() + stage.getWidth() + dx >= w.getX() + w.getWidth()) ||
           (location.getY() <= w.getY() + dy &&
            location.getY() + stage.getHeight() + dy >= w.getY() + w.getHeight())))  {
        return true;
      }
    }
    return false;
  }

}
