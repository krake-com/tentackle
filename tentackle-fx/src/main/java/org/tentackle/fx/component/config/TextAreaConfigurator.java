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

import com.sun.javafx.scene.control.behavior.TextAreaBehavior;
import com.sun.javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.ConfiguratorService;

/**
 * Configures a TextArea.
 *
 * @author harald
 * @param <T> the textarea type
 */
@ConfiguratorService(TextArea.class)
public class TextAreaConfigurator<T extends TextArea> extends TextInputControlConfigurator<T> {

  /**
   * Remaps certain keys.
   *
   * @param control the control
   */
  @Override
  protected void remapKeys(T control) {

    control.addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {
      TextAreaBehavior behavior = ((TextAreaSkin) control.getSkin()).getBehavior();

      switch (event.getCode()) {
        case TAB:
          if (!event.isAltDown() && !event.isMetaDown()) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
              if (event.isControlDown()) {
                behavior.callAction("TraverseOrInsertTab");
              }
              else {
                if (event.isShiftDown()) {
                  behavior.callAction("TraversePrevious");
                }
                else {
                  behavior.callAction("TraverseNext");
                }
              }
            }
            event.consume();
          }
          break;

        case ENTER:
          if (!event.isShiftDown() &&
              !event.isAltDown() &&
              !event.isMetaDown()) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
              if (event.isControlDown()) {
                behavior.callAction("InsertNewLine");
              }
              else {
                behavior.callAction("TraverseNext");
              }
            }
            event.consume();
          }
          break;
      }
    });
  }

}
