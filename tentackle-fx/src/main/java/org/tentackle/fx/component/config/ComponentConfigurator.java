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

import java.util.ArrayList;
import java.util.List;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.tentackle.fx.Configurator;
import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxUtilities;

/**
 * Configures a component.
 *
 * @author harald
 * @param <T> the control type
 */
public class ComponentConfigurator<T extends Control> implements Configurator<T> {

  /**
   * Configures the control.
   *
   * @param control the control
   */
  @Override
  public void configure(T control) {
    remapKeys(control);
    filterKeys(control);
    setupFocusHandling(control);
  }

  /**
   * Adds focus handling to sync with model.
   *
   * @param control the control
   */
  protected void setupFocusHandling(T control) {
    if (control instanceof FxComponent) {
      final FxComponent component = (FxComponent) control;
      control.focusedProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue) {
          FxUtilities.getInstance().focusGained(component);
          component.showErrorPopup();
        }
        else {
          FxUtilities.getInstance().focusLost(component);
          component.hideErrorPopup();
        }
      });
      control.hoverProperty().addListener((observable, oldValue, newValue) -> {
        if (!control.isFocused()) {
          if (newValue) {
            if (component.getError() == null) {
              component.showInfoPopup();
            }
            component.showErrorPopup();
          }
          else {
            component.hideInfoPopup();
            component.hideErrorPopup();
          }
        }
      });
    }
  }


  /**
   * Remaps certain keys.
   *
   * @param control the control
   */
  protected void remapKeys(T control) {

    List<KeyEvent> mappedEvents = new ArrayList<>();

    control.addEventFilter(KeyEvent.ANY, new EventHandler<>() {
      @Override
      public void handle(KeyEvent event) {
        if (mappedEvents.remove(event)) {
          return;
        }

        if (KeyCode.ENTER == event.getCode()) {
          KeyEvent newEvent = remap(event, KeyCode.TAB);
          mappedEvents.add(newEvent);
          event.consume();
          Event.fireEvent(event.getTarget(), newEvent);
        }
      }

      private KeyEvent remap(KeyEvent event, KeyCode code) {
        KeyEvent newEvent = new KeyEvent(
                event.getEventType(),
                event.getCharacter(),
                event.getText(),
                code,
                event.isShiftDown(),
                event.isControlDown(),
                event.isAltDown(),
                event.isMetaDown());

        return newEvent.copyFor(event.getSource(), event.getTarget());
      }
    });
  }


  /**
   * Filters certain keys for special features.
   *
   * @param control the control
   */
  protected void filterKeys(T control) {
    if (control instanceof FxComponent) {
      control.addEventFilter(KeyEvent.ANY, event -> {
        if (event.getCode() == KeyCode.Z && event.isControlDown() &&
            !event.isAltDown() && !event.isMetaDown() && !event.isShiftDown()) {
          FxComponent comp = (FxComponent) control;
          if (comp.isViewModified()) {
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
              // Ctrl-Z pressed and old view value saved: restore it!
              comp.setViewObject(comp.getSavedViewObject());
            }
            event.consume();
          }
        }
      });
    }
  }

}
