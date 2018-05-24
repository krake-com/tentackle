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

package org.tentackle.fx.component.config;

import com.sun.javafx.scene.NodeHelper;
import com.sun.javafx.scene.traversal.Direction;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.FxComponent;

/**
 * Configures a TextInputControl.
 *
 * @author harald
 * @param <T> the text field type
 */
public class TextInputControlConfigurator<T extends TextInputControl> extends ComponentConfigurator<T> {

  @Override
  public void configure(T control) {
    super.configure(control);

    // first keystroke triggers check for modification
    control.textProperty().addListener(o -> ((FxComponent) control).triggerViewModified());

    // Shift-Backspace clears the field
    control.addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {
      if (event.getCode() == KeyCode.BACK_SPACE && event.isShiftDown() &&
          !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
        if (event.getEventType() == KeyEvent.KEY_PRESSED &&
            !control.isDisabled() && control.isEditable()) {
          control.clear();
        }
        event.consume();
      }
    });
  }


  /**
   * Moves the focus logically forward if not a table cell editor.
   *
   * @param control the control
   */
  protected void focusNext(T control) {
    if (!(control instanceof FxComponent) || ((FxComponent) control).getTableCell() == null) {
      NodeHelper.traverse(control, Direction.NEXT);
    }
  }

  /**
   * Moves the focus logically back if not a table cell editor.
   *
   * @param control the control
   */
  protected void focusPrevious(T control) {
    if (!(control instanceof FxComponent) || ((FxComponent) control).getTableCell() == null) {
      NodeHelper.traverse(control, Direction.PREVIOUS);
    }
  }

}
