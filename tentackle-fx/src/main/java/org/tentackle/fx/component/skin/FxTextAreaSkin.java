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

import org.tentackle.fx.FxFactory;
import org.tentackle.fx.component.FxTextArea;

import javafx.beans.InvalidationListener;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.layout.StackPane;

/**
 * Skin for {@link FxTextArea}.<br>
 * Adds the expand-button to expandable text areas.
 * <p>
 * The button is placed at the right edge of the component, and the text is laid out in the
 * remaining space so that it never disappears underneath the icon.
 *
 * @author harald
 * @see FxTextArea#setExpandable(boolean)
 */
public class FxTextAreaSkin extends TextAreaSkin {

  private static final String GRAPHIC_NAME = "expand";
  private static final String BUNDLE_KEY = "EXPAND";

  /**
   * The style class of both the expand- and the collapse-button.
   */
  public static final String STYLE_CLASS = "tt-expand-button";


  private final FxTextArea textArea;
  private final InvalidationListener expandableListener;

  private StackPane expandButton;      // != null if the expand button is shown


  /**
   * Creates the skin.
   *
   * @param textArea the text area
   */
  public FxTextAreaSkin(FxTextArea textArea) {
    super(textArea);
    this.textArea = textArea;
    expandableListener = observable -> updateExpandButton();
    textArea.expandableProperty().addListener(expandableListener);
    updateExpandButton();
  }


  @Override
  public void dispose() {
    textArea.expandableProperty().removeListener(expandableListener);
    super.dispose();
  }


  /**
   * Adds or removes the expand-button according to the text area's current configuration.
   */
  protected void updateExpandButton() {
    boolean expandable = textArea.isExpandable();
    if (expandable && expandButton == null) {
      expandButton = FxFactory.getInstance().createButton(STYLE_CLASS, GRAPHIC_NAME, BUNDLE_KEY,
                                                          textArea::expand);
      getChildren().add(expandButton);
      textArea.requestLayout();
    }
    else if (!expandable && expandButton != null) {
      getChildren().remove(expandButton);
      expandButton = null;
      textArea.requestLayout();
    }
  }


  @Override
  protected void layoutChildren(double x, double y, double w, double h) {
    if (expandButton == null) {
      super.layoutChildren(x, y, w, h);
    }
    else {
      double buttonWidth = snapSizeX(expandButton.prefWidth(-1));
      double buttonHeight = snapSizeY(expandButton.prefHeight(-1));
      super.layoutChildren(x, y, Math.max(0, w - buttonWidth), h);
      // place the button at the top right corner
      expandButton.resizeRelocate(snapPositionX(x + w - buttonWidth), snapPositionY(y), buttonWidth, buttonHeight);
    }
  }

}
