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

package org.tentackle.fx;

import org.tentackle.common.ServiceFactory;

import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.net.URL;


interface InputAlertHolder {
  InputAlert INSTANCE = ServiceFactory.createService(InputAlert.class, DefaultInputAlert.class);
}


/**
 * Acoustic and visual feedback for rejected user input.<br>
 * Alerts the user if the input is too long or backspacing bounces at the beginning of the text.
 * <p>
 * The acoustic alert is played via the sound system (not the system speaker, as
 * {@code java.awt.Toolkit.beep()} does). The visual alert briefly sets the CSS pseudo-class
 * {@code :tt-alert} on the control.
 * <p>
 * By default, only the visual alert is enabled.
 *
 * @author harald
 */
public interface InputAlert {

  /**
   * To disable the alerts for a single control, invoke:<br>
   * <pre>
   *  control.getProperties().put(InputAlert.DISABLED, Boolean.TRUE);
   * </pre>
   */
  String DISABLED = "inputAlertDisabled";

  /**
   * The CSS pseudo-class set while the visual alert is shown.
   */
  String PSEUDO_CLASS = "tt-alert";


  /**
   * The reason for an alert.
   */
  enum Reason {
    /** the input exceeds the maximum number of columns. */
    TOO_LONG,
    /** backspace pressed at the beginning of the text. */
    BEGIN_OF_TEXT
  }


  /**
   * The singleton.
   *
   * @return the singleton
   */
  static InputAlert getInstance() {
    return InputAlertHolder.INSTANCE;
  }


  /**
   * Returns whether a key event is a backspace at the beginning of the text.<br>
   * Shift-Backspace is not considered, since it clears the field.
   *
   * @param event the key event
   * @param control the text input control
   * @return true if nothing to delete
   */
  static boolean isBackspaceAtBegin(KeyEvent event, TextInputControl control) {
    return event.getEventType() == KeyEvent.KEY_PRESSED &&
           event.getCode() == KeyCode.BACK_SPACE &&
           !event.isShiftDown() && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown() &&
           isNothingToDelete(control.getCaretPosition(), control.getSelection().getLength(),
                             control.isEditable(), control.isDisabled());
  }

  /**
   * Returns whether backspace has nothing to delete.
   *
   * @param caretPosition the caret position
   * @param selectionLength the length of the selection
   * @param editable true if control is editable
   * @param disabled true if control is disabled
   * @return true if nothing to delete
   */
  static boolean isNothingToDelete(int caretPosition, int selectionLength, boolean editable, boolean disabled) {
    return editable && !disabled && caretPosition == 0 && selectionLength == 0;
  }


  /**
   * Alerts the user.<br>
   * Must be invoked from within the FX thread.
   *
   * @param node the node related to the alert
   * @param reason the reason
   */
  void alert(Node node, Reason reason);

  /**
   * Property to enable the acoustic alert.
   *
   * @return the property, default is false
   */
  BooleanProperty soundEnabledProperty();

  /**
   * Property to enable the visual alert.
   *
   * @return the property, default is true
   */
  BooleanProperty visualEnabledProperty();

  /**
   * Sets the sound to play.
   *
   * @param url the URL of the sound (wav, aiff or au), null to use the default sound
   */
  void setSound(URL url);

  /**
   * Gets the sound to play.
   *
   * @return the URL of the sound, never null (unless the default sound is missing)
   */
  URL getSound();


  /**
   * Returns whether the acoustic alert is enabled.
   *
   * @return true if enabled
   */
  default boolean isSoundEnabled() {
    return soundEnabledProperty().get();
  }

  /**
   * Enables or disables the acoustic alert.
   *
   * @param soundEnabled true to enable
   */
  default void setSoundEnabled(boolean soundEnabled) {
    soundEnabledProperty().set(soundEnabled);
  }

  /**
   * Returns whether the visual alert is enabled.
   *
   * @return true if enabled
   */
  default boolean isVisualEnabled() {
    return visualEnabledProperty().get();
  }

  /**
   * Enables or disables the visual alert.
   *
   * @param visualEnabled true to enable
   */
  default void setVisualEnabled(boolean visualEnabled) {
    visualEnabledProperty().set(visualEnabled);
  }

}
