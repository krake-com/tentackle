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
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.ConfiguratorService;

/**
 * Configures a TextField.
 *
 * @author harald
 * @param <T> the textfield type
 */
@ConfiguratorService(TextField.class)
public class TextFieldConfigurator<T extends TextField> extends TextInputControlConfigurator<T> {

  @Override
  protected void remapKeys(T control) {

    control.addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {

      switch (event.getCode()) {
        case ENTER:
          if (!event.isControlDown() && !event.isAltDown() && !event.isMetaDown() &&
                  event.getEventType() == KeyEvent.KEY_PRESSED) {
            if (event.isShiftDown()) {
              NodeHelper.traverse(control, Direction.PREVIOUS);
            }
            else {
              NodeHelper.traverse(control, Direction.NEXT);
            }
          }
          break;
      }
    });
  }

}
