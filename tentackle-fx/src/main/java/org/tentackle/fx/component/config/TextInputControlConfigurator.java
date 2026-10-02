/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.fx.component.config;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.InputAlert;

import javafx.scene.TraversalDirection;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Configures a {@link TextInputControl}.<br>
 * In addition to the component's configuration, {@code Shift-BACKSPACE} clears the field and
 * the {@link InputAlert} is raised if backspace has nothing to delete.
 *
 * @author harald
 * @param <T> the text field type
 */
public abstract class TextInputControlConfigurator<T extends TextInputControl> extends ComponentConfigurator<T> {

  /**
   * Parent constructor.
   */
  public TextInputControlConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void configure(T control) {
    super.configure(control);

    // first keystroke triggers check for modification
    control.textProperty().addListener(_ -> ((FxComponent) control).triggerViewModified());

    // Shift-Backspace clears the field
    control.addEventFilter(KeyEvent.ANY, event -> {

      if (event.getCode() == KeyCode.BACK_SPACE && event.isShiftDown() &&
          !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {

        if (event.getEventType() == KeyEvent.KEY_PRESSED &&
            !control.isDisabled() && control.isEditable()) {
          control.clear();
        }
        event.consume();
      }
    });

    // alert if backspace has nothing to delete
    control.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (InputAlert.isBackspaceAtBegin(event, control)) {
        InputAlert.getInstance().alert(control, InputAlert.Reason.BEGIN_OF_TEXT);
      }
    });
  }


  /**
   * {@inheritDoc}
   * <p>
   * {@code ENTER} and {@code Shift-ENTER} move the focus to the next or previous component.
   */
  @Override
  protected void remapKeys(T control) {

    control.addEventFilter(KeyEvent.ANY, event -> {

      if (event.getCode() == KeyCode.ENTER &&
          !event.isControlDown() && !event.isAltDown() && !event.isMetaDown() &&
          event.getEventType() == KeyEvent.KEY_PRESSED) {

        if (event.isShiftDown()) {
          focusPrevious(control);
        }
        else {
          focusNext(control);
        }
      }
    });
  }


  /**
   * Moves the focus logically forward if not a table cell editor.
   *
   * @param control the control
   */
  protected void focusNext(T control) {
    if (!(control instanceof FxComponent) || !((FxComponent) control).isCellEditor()) {
      control.requestFocusTraversal(TraversalDirection.NEXT);
    }
  }

  /**
   * Moves the focus logically back if not a table cell editor.
   *
   * @param control the control
   */
  protected void focusPrevious(T control) {
    if (!(control instanceof FxComponent) || !((FxComponent) control).isCellEditor()) {
      control.requestFocusTraversal(TraversalDirection.PREVIOUS);
    }
  }

}
