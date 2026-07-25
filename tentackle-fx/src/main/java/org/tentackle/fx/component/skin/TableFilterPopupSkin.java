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

package org.tentackle.fx.component.skin;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.table.filter.TableFilterOperator;
import org.tentackle.fx.table.filter.TableFilterPopup;
import org.tentackle.fx.table.filter.TableFilterValue;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.text.MessageFormat;

/**
 * Skin for {@link TableFilterPopup}.<br>
 * Arranges the sort shortcuts, the search field, the value list and the condition editor
 * top down, with the action buttons at the bottom.
 *
 * @param <S> type of the objects contained within the table's or treetable's items list
 * @param <T> type of the content in all cells of the filtered column
 * @author harald
 */
public class TableFilterPopupSkin<S,T> implements Skin<TableFilterPopup<S,T>> {

  /** the style class of the popup's content pane. */
  public static final String STYLE_CLASS_PANE = "tt-filter-popup-pane";

  /** the style class of the search field. */
  public static final String STYLE_CLASS_SEARCH = "tt-filter-search";

  /** the style class of the value list. */
  public static final String STYLE_CLASS_LIST = "tt-filter-list";

  /** the style class of the condition editor. */
  public static final String STYLE_CLASS_CONDITION = "tt-filter-condition";

  /** the style class of the hint shown if the value list is truncated. */
  public static final String STYLE_CLASS_HINT = "tt-filter-hint";

  /** the height of a value list row. */
  private static final double ROW_HEIGHT = 24.0;


  private TableFilterPopup<S,T> popup;
  private VBox pane;


  /**
   * Creates the skin.
   *
   * @param popup the filter popup
   */
  public TableFilterPopupSkin(TableFilterPopup<S,T> popup) {
    this.popup = popup;

    pane = new VBox();
    pane.getChildren().addAll(createSearchField(),
                              createValueList(),
                              createConditionBox(),
                              new Separator(),
                              createButtonBox());
    pane.getStyleClass().add(STYLE_CLASS_PANE);
  }


  @Override
  public TableFilterPopup<S,T> getSkinnable() {
    return popup;
  }

  @Override
  public Node getNode() {
    return pane;
  }

  @Override
  public void dispose() {
    popup = null;
    pane = null;
  }


  /**
   * Creates the search field reducing the value list.
   *
   * @return the node
   */
  protected Node createSearchField() {
    TextField searchField = new TextField();
    searchField.getStyleClass().add(STYLE_CLASS_SEARCH);
    searchField.setPromptText(FxFxBundle.getString("SEARCH"));
    searchField.textProperty().bindBidirectional(popup.searchTextProperty());
    return searchField;
  }

  /**
   * Creates the list of the column's distinct values.
   *
   * @return the node
   */
  protected Node createValueList() {
    ListView<TableFilterValue> listView = new ListView<>(popup.getDisplayedValues());
    listView.getStyleClass().add(STYLE_CLASS_LIST);
    listView.setFixedCellSize(ROW_HEIGHT);
    listView.setCellFactory(_ -> new ValueCell());
    listView.prefHeightProperty().bind(
            Bindings.min(popup.visibleRowCountProperty(), Bindings.size(popup.getDisplayedValues()).add(1))
                    .multiply(ROW_HEIGHT).add(2.0));
    if (popup.isValuesTruncated()) {
      Label hint = new Label(MessageFormat.format(FxFxBundle.getString("SHOWING FIRST {0} OF {1} VALUES"),
                                                  popup.getSupport().getMaxDistinctValues(),
                                                  popup.getTotalValueCount()));
      hint.getStyleClass().add(STYLE_CLASS_HINT);
      hint.setWrapText(true);
      VBox box = new VBox(listView, hint);
      VBox.setVgrow(listView, Priority.ALWAYS);
      return box;
    }
    return listView;
  }

  /**
   * Creates the condition editor.
   *
   * @return the node
   */
  protected Node createConditionBox() {
    ComboBox<TableFilterOperator> operatorBox = new ComboBox<>();
    operatorBox.getItems().setAll(popup.getApplicableOperators());
    operatorBox.valueProperty().bindBidirectional(popup.operatorProperty());
    operatorBox.setMaxWidth(Double.MAX_VALUE);

    TextField firstField = new TextField();
    firstField.textProperty().bindBidirectional(popup.firstOperandProperty());
    TextField secondField = new TextField();
    secondField.textProperty().bindBidirectional(popup.secondOperandProperty());

    Label andLabel = new Label(FxFxBundle.getString("AND"));

    bindVisibility(firstField, 1);
    bindVisibility(secondField, 2);
    bindVisibility(andLabel, 2);

    VBox box = new VBox(new Label(FxFxBundle.getString("CONDITION")),
                        operatorBox, firstField, andLabel, secondField);
    box.getStyleClass().add(STYLE_CLASS_CONDITION);
    box.setFillWidth(true);
    return box;
  }

  /**
   * Creates the action buttons.
   *
   * @return the node
   */
  protected Node createButtonBox() {
    Button clear = createButton("CLEAR", null, popup::clearFilter);
    Button cancel = createButton("CANCEL", null, popup::cancel);
    Button ok = createButton("OK", null, popup::apply);
    ok.setDefaultButton(true);
    cancel.setCancelButton(true);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox box = new HBox(clear, spacer, cancel, ok);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setSpacing(5);
    return box;
  }


  /**
   * Creates a button.
   *
   * @param bundleKey the key of the button's text
   * @param graphicName the name of the button's graphic, null if none
   * @param action the action to run
   * @return the button
   */
  protected Button createButton(String bundleKey, String graphicName, Runnable action) {
    Button button = new Button(FxFxBundle.getString(bundleKey));
    if (graphicName != null) {
      button.setGraphic(Fx.createGraphic(graphicName));
    }
    button.setOnAction(_ -> action.run());
    return button;
  }

  /**
   * Shows a node only if the condition's operator requires the given number of operands.
   *
   * @param node the node
   * @param operandCount the minimum number of operands
   */
  protected void bindVisibility(Node node, int operandCount) {
    node.visibleProperty().bind(Bindings.createBooleanBinding(
            () -> popup.operatorProperty().get() != null &&
                  popup.operatorProperty().get().getOperandCount() >= operandCount,
            popup.operatorProperty()));
    node.managedProperty().bind(node.visibleProperty());
  }


  /**
   * The cell of the value list.<br>
   * Shows a checkbox with the value's displayed text and graphic.
   */
  protected class ValueCell extends ListCell<TableFilterValue> {

    private final CheckBox checkBox = new CheckBox();
    private final ChangeListener<Boolean> selectedListener = (_, _, newValue) -> checkBox.setSelected(newValue);
    private TableFilterValue boundValue;

    /**
     * Creates a cell.
     */
    protected ValueCell() {
      setPadding(new Insets(0, 0, 0, 2));
      checkBox.setOnAction(_ -> {
        if (boundValue != null) {
          if (boundValue.getKind() == TableFilterValue.KIND.SELECT_ALL) {
            popup.selectAll(checkBox.isSelected());
          }
          else {
            boundValue.setSelected(checkBox.isSelected());
          }
        }
      });
    }

    @Override
    protected void updateItem(TableFilterValue item, boolean empty) {
      super.updateItem(item, empty);
      if (boundValue != null) {
        boundValue.selectedProperty().removeListener(selectedListener);
        boundValue = null;
      }
      if (empty || item == null) {
        setGraphic(null);
      }
      else {
        boundValue = item;
        item.selectedProperty().addListener(selectedListener);
        checkBox.setSelected(item.isSelected());
        checkBox.setText(item.getDisplayedText());
        checkBox.setGraphic(item.getGraphic());
        setGraphic(checkBox);
      }
    }
  }

}
