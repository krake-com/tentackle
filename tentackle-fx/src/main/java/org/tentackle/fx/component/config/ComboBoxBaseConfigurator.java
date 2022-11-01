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

import javafx.scene.control.ComboBoxBase;
import javafx.scene.input.KeyEvent;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxControl;

/**
 * Configures a {@link ComboBoxBase}.
 *
 * @author harald
 * @param <T> the combobox type
 */
public abstract class ComboBoxBaseConfigurator<T extends ComboBoxBase<?>> extends ComponentConfigurator<T> {

  /**
   * Parent constructor.
   */
  public ComboBoxBaseConfigurator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void configure(T control) {
    super.configure(control);
    // make change visible immediately (not only after focus lost)
    if (control instanceof FxControl fxControl) {
      control.setOnAction(e -> updateModelAndView(fxControl));
    }
  }


  @Override
  protected void filterKeys(T control) {
    super.filterKeys(control);

    if (control instanceof FxComponent) {
      control.addEventFilter(KeyEvent.ANY, event -> {
        if (!control.isEditable() && ((FxComponent) control).isCellEditor()) {
          // don't allow to navigate within the cells of a table if combobox is used as an editor
          // @todo: verify if still necessary in newer versions than java 9
          switch (event.getCode()) {
            case PAGE_DOWN:
            case PAGE_UP:
            case HOME:
            case END:
            case LEFT:
            case RIGHT:
              event.consume();
          }
        }
      });
    }
  }

}
