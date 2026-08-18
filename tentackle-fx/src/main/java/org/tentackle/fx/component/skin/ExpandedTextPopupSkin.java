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
import org.tentackle.fx.component.ExpandedTextPopup;
import org.tentackle.fx.component.FxTextArea;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;

/**
 * Skin for {@link ExpandedTextPopup}.<br>
 * Shows the multi-line editor with the collapse button in its upper right corner,
 * i.e., exactly where the expand-button of the collapsed text area was.
 *
 * @author harald
 */
public class ExpandedTextPopupSkin implements Skin<ExpandedTextPopup> {

  private static final String STYLE_CLASS_PANE = "tt-expanded-text-pane";
  private static final String GRAPHIC_NAME = "collapse";
  private static final String BUNDLE_KEY = "COLLAPSE";

  private ExpandedTextPopup popup;
  private StackPane stackPane;


  /**
   * Creates the skin.
   *
   * @param popup the expanded text popup
   */
  public ExpandedTextPopupSkin(ExpandedTextPopup popup) {
    this.popup = popup;

    FxTextArea textArea = popup.getTextArea();
    TextArea editor = popup.getEditor();
    // the expanded editor is as wide as the text area so that both are aligned
    editor.prefWidthProperty().bind(textArea.widthProperty());
    editor.minWidthProperty().bind(textArea.widthProperty());
    editor.maxWidthProperty().bind(textArea.widthProperty());

    Node collapseButton = FxFactory.getInstance().createButton(FxTextAreaSkin.STYLE_CLASS,
                                                               GRAPHIC_NAME, BUNDLE_KEY,
                                                               popup::collapse);
    StackPane.setAlignment(collapseButton, Pos.TOP_RIGHT);
    StackPane.setMargin(collapseButton, new Insets(1, 1, 0, 0));

    stackPane = new StackPane(editor, collapseButton);
    stackPane.getStyleClass().add(STYLE_CLASS_PANE);
  }


  @Override
  public ExpandedTextPopup getSkinnable() {
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

}
