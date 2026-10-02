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

import org.tentackle.fx.component.AutoCompletionPopup;
import org.tentackle.misc.SubString;

import javafx.beans.InvalidationListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import java.util.List;

/**
 * Skin for {@link AutoCompletionPopup}.
 */
public class AutoCompletionPopupSkin implements Skin<AutoCompletionPopup> {

  private static final String STYLE_CLASS_STACK_PANE = "autocompletion-pane";
  private static final String STYLE_CLASS_LIST = "autocompletion-list";


  private final ListView<List<SubString>> listView;

  private AutoCompletionPopup popup;
  private StackPane stackPane;
  private boolean suggestionsChanged;

  /**
   * Creates the skin.
   *
   * @param popup the auto-completion popup control
   */
  public AutoCompletionPopupSkin(AutoCompletionPopup popup) {
    this.popup = popup;

    suggestionsChanged = true;
    listView = new ListView<>(popup.getSuggestions()){
      @Override
      protected void layoutChildren() {
        super.layoutChildren();
        if (suggestionsChanged) {
          if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
            listView.scrollTo(0);
          }
          suggestionsChanged = false;
        }
      }
    };

    listView.getItems().addListener((InvalidationListener) observable -> {
      suggestionsChanged = true;
      updateHeight();
    });
    listView.getStyleClass().add(STYLE_CLASS_LIST);
    listView.setFixedCellSize(popup.getFixedCellSize());
    popup.getFixedCellSizeProperty().addListener(observable -> listView.setFixedCellSize(popup.getFixedCellSize()));

    popup.getCellFactoryProperty().addListener((observable, oldVal, newVal) -> {
      if (newVal != null) {
        listView.setCellFactory(newVal);
      }
    });
    Callback<ListView<List<SubString>>, ListCell<List<SubString>>> cellCallback = popup.getCellFactoryProperty().get();
    if (cellCallback != null) {
      listView.setCellFactory(cellCallback);
    }

    stackPane = new StackPane();
    stackPane.getChildren().add(new Group(listView));
    stackPane.getStyleClass().add(STYLE_CLASS_STACK_PANE);
    listView.prefWidthProperty().bind(popup.getComponent().widthProperty());
    listView.maxWidthProperty().bind(popup.getComponent().widthProperty());
    listView.minWidthProperty().bind(popup.getComponent().widthProperty());
    listView.setOnMouseClicked(mouseEvent -> {
      if (mouseEvent.getButton() == MouseButton.PRIMARY) {
        selectItem();
      }
    });

    listView.setOnKeyPressed(keyEvent -> {
      switch (keyEvent.getCode()) {
        case ENTER -> selectItem();
        case ESCAPE -> popup.hide();
        default -> {}
      }
    });
  }

  @Override
  public AutoCompletionPopup getSkinnable() {
    return popup;
  }

  @Override
  public Node getNode() {
    return stackPane;
  }

  @Override
  public void dispose() {
    popup = null;
    stackPane = null;
  }

  private void selectItem() {
    List<SubString> item = listView.getSelectionModel().getSelectedItem();
    if (item == null && !listView.getItems().isEmpty()) {
      // fallback to first item
      listView.getSelectionModel().select(0);
      item = listView.getSelectionModel().getSelectedItem();
    }
    if (item != null) {
      popup.getController().updateItem(item.getFirst().getString());
    }
    popup.hide();
  }

  private void updateHeight() {
    final double height = Math.min(listView.getItems().size(), popup.getVisibleRowCount()) * listView.getFixedCellSize();
    listView.setPrefHeight(height + listView.getFixedCellSize() / 2.0);
  }

}
