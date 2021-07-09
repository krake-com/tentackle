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

import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.ConfiguratorService;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.component.FxTextArea;

/**
 * Configures a TextArea.
 *
 * @author harald
 * @param <T> the textarea type
 */
@ConfiguratorService(TextArea.class)
public class TextAreaConfigurator<T extends TextArea> extends TextInputControlConfigurator<T> {

  private static final String NEXT = "to_next";
  private static final String PREVIOUS = "to_previous";

  @Override
  protected void remapKeys(T control) {
    control.addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {

      if ((event.getCode() == KeyCode.TAB || event.getCode() == KeyCode.ENTER) &&
          !event.isAltDown() && !event.isMetaDown() &&
          !NEXT.equals(event.getText()) && !PREVIOUS.equals(event.getText()) &&
          (!(control instanceof FxTextArea) || ((FxTextArea) control).isNavigateViaEnterEnabled())) {

        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
          if (event.isControlDown()) {
            control.insertText(control.getCaretPosition(), event.getCode() == KeyCode.TAB ? "\t" : "\n");
          }
          else if (event.getCode() == KeyCode.ENTER) {
            if (event.isShiftDown()) {
              focusPrevious(control);
            }
            else {
              focusNext(control);
            }
          }
        }

        event.consume();
      }
    });
  }

  @Override
  protected void focusNext(T control) {
    if (!(control instanceof FxComponent) || !((FxComponent) control).isCellEditor()) {
      //      NodeHelper.traverse(control, Direction.NEXT);   is non-public API :(
      KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, NEXT, KeyCode.TAB,
                                    // ctrl tab gets out of the textarea
                                    false, true, false, false);
      control.fireEvent(event);
    }
  }

  protected void focusPrevious(T control) {
    if (!(control instanceof FxComponent) || !((FxComponent) control).isCellEditor()) {
      //      NodeHelper.traverse(control, Direction.PREVIOUS);   is non public API :(
      KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, PREVIOUS, KeyCode.TAB,
                                    true, false, false, false);
      control.fireEvent(event);
    }
  }

}
