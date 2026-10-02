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

package org.tentackle.fx.component;

import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.fx.component.auto.AutoCompletionCellFactory;
import org.tentackle.fx.component.auto.AutoCompletionController;
import org.tentackle.fx.component.skin.AutoCompletionPopupSkin;
import org.tentackle.misc.SubString;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableIntegerProperty;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;
import javafx.util.Callback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Popup for auto-completion.<br>
 * Shows the suggestions of an {@link AutoCompletionController} below the text input component.
 * The visible row count and the row height are styleable via {@code -tt-autocomplete-visible-row-count}
 * and {@code -tt-autocomplete-fixed-cell-size}.
 */
public class AutoCompletionPopup extends PopupControl {

  private static final String STYLE_CLASS = "autocompletion";
  private static final String CSS_VISIBLE_ROW_COUNT = "-tt-autocomplete-visible-row-count";
  private static final int DEFAULT_VISIBLE_ROW_COUNT = 16;
  private static final String CSS_FIXED_CELL_SIZE = "-tt-autocomplete-fixed-cell-size";
  private static final double DEFAULT_FIXED_CELL_SIZE = 0.0;    // 0.0 = derive from text component

  private final AutoCompletionController controller;
  private final TextInputControl component;
  private final ObservableList<List<SubString>> suggestions;
  private final ObjectProperty<Callback<ListView<List<SubString>>, ListCell<List<SubString>>>> cellFactoryProperty;

  /**
   * Creates an auto-completion popup.
   *
   * @param controller the auto-completion controller
   */
  public AutoCompletionPopup(AutoCompletionController controller) {
    this.controller = controller;
    component = controller.getComponent();
    suggestions = FXCollections.observableArrayList();
    cellFactoryProperty = new SimpleObjectProperty<>(createCellFactory());
    setAutoHide(true);
    getStyleClass().add(STYLE_CLASS);
  }

  /**
   * Gets the auto-completion controller.
   *
   * @return the controller
   */
  public AutoCompletionController getController() {
    return controller;
  }

  /**
   * Gets the text input component.
   *
   * @return the component
   */
  public TextInputControl getComponent() {
    return component;
  }

  /**
   * Gets the list of suggestions.
   *
   * @return the suggestions
   */
  public ObservableList<List<SubString>> getSuggestions() {
    return suggestions;
  }

  /**
   * Shows the popup below the text input component.
   */
  public void showPopup() {   // don't use the name show() as this would override!
    if (!isShowing()) {
      if (getFixedCellSize() == DEFAULT_FIXED_CELL_SIZE) {
        setFixedCellSize(component.getHeight());
      }
      Window window = FxUtilities.getInstance().getWindow(component);
      Scene scene = component.getScene();
      show(component,
           window.getX() + component.localToScene(0, 0).getX() + scene.getX(),
           window.getY() + component.localToScene(0, 0).getY() + scene.getY() + component.getHeight());
    }
  }

  /**
   * Gets the list cell factory to display the suggested items in the popup.
   *
   * @return the cell factory
   */
  public ObjectProperty<Callback<ListView<List<SubString>>, ListCell<List<SubString>>>> getCellFactoryProperty() {
    return cellFactoryProperty;
  }

  /**
   * Gets the number of rows displayed in the list popup.
   *
   * @return the row count (defaults to 16)
   */
  public int getVisibleRowCount() {
    return visibleRowCountProperty.get();
  }

  /**
   * Sets the number of rows displayed in the list popup.<br>
   * This is a styleable property: {@value CSS_VISIBLE_ROW_COUNT}.
   *
   * @param rowCount the row count
   */
  public void setVisibleRowCount(int rowCount) {
    visibleRowCountProperty.set(rowCount);
  }

  /**
   * Gets the visible row count property.
   *
   * @return the property
   */
  public IntegerProperty getVisibleRowCountProperty() {
    return visibleRowCountProperty;
  }

  /**
   * Gets the height of a popup row.
   *
   * @return the list cell's size (defaults to the height of the text component)
   */
  public double getFixedCellSize() {
    return fixedCellSizeProperty.get();
  }

  /**
   * Sets the height of a popup row.<br>
   * This is a styleable property: {@value CSS_FIXED_CELL_SIZE}.
   *
   * @param size the list cell's size
   */
  public void setFixedCellSize(double size) {
    fixedCellSizeProperty.set(size);
  }

  /**
   * Gets the fixed cell size property.
   *
   * @return the property
   */
  public DoubleProperty getFixedCellSizeProperty() {
    return fixedCellSizeProperty;
  }


  @Override
  protected Skin<?> createDefaultSkin() {
    return new AutoCompletionPopupSkin(this);
  }

  /**
   * Creates the auto-completion cell factory.
   *
   * @return the cell factory
   */
  protected Callback<ListView<List<SubString>>, ListCell<List<SubString>>> createCellFactory() {
    return new AutoCompletionCellFactory(ThemeUtilities.getInstance().getDefaultFont());
  }


  // ------------------ CSS styling --------------------

  private static final CssMetaData<AutoCompletionPopup, Number> VISIBLE_ROW_META_DATA =
    new CssMetaData<>(CSS_VISIBLE_ROW_COUNT, StyleConverter.getSizeConverter(), DEFAULT_VISIBLE_ROW_COUNT) {

      @Override
      public boolean isSettable(AutoCompletionPopup node) {
        return !node.visibleRowCountProperty.isBound();
      }

      @Override
      public StyleableProperty<Number> getStyleableProperty(AutoCompletionPopup node) {
        return node.visibleRowCountProperty;
      }
    };

  private final SimpleStyleableIntegerProperty visibleRowCountProperty = new SimpleStyleableIntegerProperty(
    VISIBLE_ROW_META_DATA, AutoCompletionPopup.this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

  private static final CssMetaData<AutoCompletionPopup, Number> FIXED_CELL_SIZE_META_DATA =
    new CssMetaData<>(CSS_FIXED_CELL_SIZE, StyleConverter.getSizeConverter(), DEFAULT_FIXED_CELL_SIZE) {

      @Override
      public boolean isSettable(AutoCompletionPopup node) {
        return !node.fixedCellSizeProperty.isBound();
      }

      @Override
      public StyleableProperty<Number> getStyleableProperty(AutoCompletionPopup node) {
        return node.fixedCellSizeProperty;
      }
    };
  private final SimpleStyleableDoubleProperty fixedCellSizeProperty = new SimpleStyleableDoubleProperty(
    FIXED_CELL_SIZE_META_DATA, AutoCompletionPopup.this, "fixedCellSize", DEFAULT_FIXED_CELL_SIZE);


  private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

  static {
    List<CssMetaData<? extends Styleable, ?>> temp = new ArrayList<>(PopupControl.getClassCssMetaData());
    temp.add(VISIBLE_ROW_META_DATA);
    temp.add(FIXED_CELL_SIZE_META_DATA);
    STYLEABLES = Collections.unmodifiableList(temp);
  }

  /**
   * Gets the CSS metadata of this class.
   *
   * @return the CSS metadata, including the styleable properties of {@link PopupControl}
   */
  public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
    return STYLEABLES;
  }

  @Override
  public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
    return getClassCssMetaData();
  }

}
