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

import org.tentackle.fx.component.skin.ExpandedTextPopupSkin;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyEvent;
import java.util.Objects;

/**
 * The multi-line editor shown while a one-line {@link FxTextArea} is expanded.<br>
 * The popup covers the text area, aligned to its upper left corner, so that the first line
 * stays where it is and the editor grows downwards, similar to the expandable text fields in IntelliJ.
 * <p>
 * The editor's text is written back to the text area on each keystroke, i.e., expanding is
 * merely a different view on the same component: bindings, validation, and the modification
 * tracking work exactly as if the user typed into the text area itself.
 * <p>
 * The text area keeps the focus within its own scene while the popup is showing — a popup window
 * is focused as long as its owner window is — so the model is updated by the text area's regular
 * focus handling as soon as the user moves on to another component.
 *
 * @author harald
 * @see FxTextArea#expand()
 */
public class ExpandedTextPopup extends PopupControl {

  private static final String STYLE_CLASS = "tt-expanded-text";

  private final FxTextArea textArea;         // the expanded text area
  private final TextArea editor;             // the multi-line editor
  private boolean textSyncing;               // true if within text synchronization
  private boolean focusRestored;             // true if the focus must be given back to the text area


  /**
   * Creates the popup for a text area.
   *
   * @param textArea the text area to expand
   */
  public ExpandedTextPopup(FxTextArea textArea) {
    this.textArea = textArea;
    this.editor = createEditor();

    getStyleClass().add(STYLE_CLASS);
    setAutoHide(true);
    setHideOnEscape(false);           // handled by the editor to restore the focus
    setConsumeAutoHidingEvents(false);   // let the click reach the component the user aimed at

    editor.textProperty().addListener((_, _, newValue) -> syncToTextArea(newValue));
    textArea.textProperty().addListener((_, _, newValue) -> {
      if (isShowing()) {    // whatever changes the text area meanwhile, e.g., auto-completion or a binding
        syncToEditor(newValue);
      }
    });
    editor.focusedProperty().addListener((_, _, newValue) -> {
      if (!newValue) {
        hide();
      }
    });
    editor.addEventFilter(KeyEvent.KEY_PRESSED, this::remapKeys);

    setOnHidden(_ -> commit());
  }


  /**
   * Gets the expanded text area.
   *
   * @return the text area
   */
  public FxTextArea getTextArea() {
    return textArea;
  }

  /**
   * Gets the multi-line editor.
   *
   * @return the editor
   */
  public TextArea getEditor() {
    return editor;
  }

  /**
   * Shows the popup on top of the text area.<br>
   * Does nothing if already showing or if the text area is not displayed.
   */
  public void showPopup() {   // don't use the name show() as this would override!
    if (!isShowing()) {
      Point2D location = textArea.localToScreen(0, 0);
      if (location != null) {
        // the text area must own the focus in its scene, else it would never update the model
        textArea.requestFocus();
        prepareEditor();
        show(textArea, location.getX(), location.getY());
        // the popup's scene gets its focus owner only after it is shown
        Platform.runLater(() -> {
          editor.requestFocus();
          editor.positionCaret(editor.getLength());
        });
      }
    }
  }

  /**
   * Collapses the popup and moves the focus back to the text area.<br>
   * In contrast to {@link #hide()}, which is invoked whenever the editor loses the focus,
   * this is the explicit collapse requested by the user.
   */
  public void collapse() {
    if (isShowing()) {
      focusRestored = true;
      hide();
    }
  }


  @Override
  protected Skin<?> createDefaultSkin() {
    return new ExpandedTextPopupSkin(this);
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
   * Creates the multi-line editor.
   * <p>
   * The editor is a plain {@link TextArea} that gets a {@link TextFormatter} with the text area's
   * delegate as its filter, so that valid chars, case conversion, and the maximum number of columns
   * apply to the expanded view as well.
   *
   * @return the editor
   */
  protected TextArea createEditor() {
    TextArea area = new TextArea();
    area.setTextFormatter(new TextFormatter<>(textArea.getDelegate()));
    area.setWrapText(true);   // long one-liners are the reason to expand in the first place
    return area;
  }

  /**
   * Remaps the keys of the editor.
   * <p>
   * Tab/shift-tab collapse and navigate to the next/previous component, ctrl-tab inserts the
   * character, and escape collapses without navigating, following the same conventions as
   * {@code TextAreaConfigurator}. Enter, however, keeps its FX standard meaning and inserts a
   * newline, since entering more than one line is the very reason to expand.
   *
   * @param event the key event
   */
  protected void remapKeys(KeyEvent event) {
    switch (event.getCode()) {
      case ESCAPE -> {
        collapse();
        event.consume();
      }
      case TAB -> {
        if (event.isControlDown()) {
          editor.insertText(editor.getCaretPosition(), "\t");
        }
        else {
          collapse();
          textArea.requestFocusTraversal(event.isShiftDown() ? TraversalDirection.PREVIOUS
                                                             : TraversalDirection.NEXT);
        }
        event.consume();
      }
      default -> { }
    }
  }

  /**
   * Prepares the editor before the popup is shown.<br>
   * Copies over everything that makes the editor look and behave like the text area.
   */
  protected void prepareEditor() {
    editor.getStyleClass().setAll(textArea.getStyleClass());
    editor.setPrefRowCount(textArea.getExpandedRowCount());
    editor.setFont(textArea.getFont());
    editor.setEditable(textArea.isEditable());
    editor.setPromptText(textArea.getPromptText());
    Scene scene = textArea.getScene();
    Parent contentPane = getContentPane();
    if (scene != null && contentPane != null) {
      // popups live in a scene of their own and don't inherit the application's stylesheets
      contentPane.getStylesheets().setAll(scene.getStylesheets());
    }
    syncToEditor(textArea.getText());
  }

  /**
   * Writes the text area's text to the editor.
   *
   * @param text the text area's text
   */
  protected void syncToEditor(String text) {
    if (!textSyncing && !Objects.equals(text, editor.getText())) {
      textSyncing = true;
      try {
        editor.setText(text);
      }
      finally {
        textSyncing = false;
      }
    }
  }

  /**
   * Writes the editor's text back to the text area.
   *
   * @param text the editor's text
   */
  protected void syncToTextArea(String text) {
    if (!textSyncing && !Objects.equals(text, textArea.getText())) {
      textSyncing = true;
      try {
        textArea.setText(text);
      }
      finally {
        textSyncing = false;
      }
    }
  }

  /**
   * Commits the edited text when the popup has been hidden.<br>
   * The text is in sync already unless a subclass suppressed it, so this is merely the last resort.
   * The model is left to the text area's focus handling.
   */
  protected void commit() {
    syncToTextArea(editor.getText());
    if (focusRestored) {
      focusRestored = false;
      textArea.requestFocus();
    }
  }

}
