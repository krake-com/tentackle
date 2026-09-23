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

import javafx.scene.control.DatePicker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.ConfiguratorService;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.InputAlert;

/**
 * Configures a {@link DatePicker}.
 *
 * @author harald
 * @param <T> the datepicker type
 */
@ConfiguratorService(DatePicker.class)
public class DatePickerConfigurator<T extends DatePicker> extends ComponentConfigurator<T> {

  /**
   * Creates the configurator for a {@link DatePicker}.
   */
  public DatePickerConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void configure(T control) {
    super.configure(control);

    // first keystroke triggers check for modification
    control.getEditor().textProperty().addListener(o -> ((FxComponent) control).triggerViewModified());

    // Shift-Backspace clears the field
    control.addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {
      if (event.getCode() == KeyCode.BACK_SPACE && event.isShiftDown() &&
          !event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
        if (event.getEventType() == KeyEvent.KEY_PRESSED &&
            !control.getEditor().isDisabled() && control.getEditor().isEditable()) {
          control.getEditor().clear();
        }
        event.consume();
      }
    });

    // alert if backspace has nothing to delete
    control.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (InputAlert.isBackspaceAtBegin(event, control.getEditor())) {
        InputAlert.getInstance().alert(control, InputAlert.Reason.BEGIN_OF_TEXT);
      }
    });
  }

}
