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

package org.tentackle.fx.component;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.stage.PopupWindow;
import javafx.util.Duration;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.InfoPopupSupported;

import static org.tentackle.fx.FxComponentDelegate.INFO_STYLE;

/**
 * Extended button.<br>
 * Notice that a button is not an FxControl.
 *
 * @author harald
 */
public class FxButton extends Button implements InfoPopupSupported {

  /**
   * Press-time in ms for doClick().
   */
  public static double doClickPressTime = 200.0;

  /**
   * The info message.<br>
   * This message is shown to the user in a tooltip.
   */
  private String infoMessage;

  /**
   * The info popup shown.<br>
   * null if not shown.
   */
  private PopupWindow infoPopup;


  /**
   * Creates a button with an empty string for its label.
   */
  public FxButton() {
    configure();
  }


  /**
   * Programmatically performs a "click".<br>
   * This does the same thing as if the user had pressed and released the button.
   * The button stays visually "pressed" for <code>pressTime</code> milliseconds.
   * <p>
   * Missing feature from Swing.
   *
   * @param pressTime the time to "hold down" the button, in milliseconds
   */
  public void doClick(double pressTime) {
    PauseTransition pause = new PauseTransition(Duration.millis(pressTime));
    pause.setOnFinished(e -> {
      disarm();
      fire();
    });
    arm();
    pause.play();
  }

  /**
   * Programmatically performs a "click".<br>
   * This does the same thing as if the user had pressed and released the button.
   * <p>
   * Missing feature from Swing.
   */
  public void doClick() {
    doClick(doClickPressTime);
  }

  @Override
  public void fire() {
    if (!isFocusTraversable() && !isCancelButton()) {
      /*
       * If the button is not focus traversable, it will not receive the focus when clicked.
       * As a consequence, an opposite FxComponent will not lose its focus which in turn
       * means, that the model is not updated. If the action handler of this button
       * depends on it, it will not see the last user's input.
       * In such cases we must perform the model update explicitly before the event gets fired.
       */
      Node node = getScene().getFocusOwner();
      if (node instanceof FxComponent) {
        FxUtilities.getInstance().focusLost((FxComponent) node);
      }
    }
    super.fire();
  }


  @Override
  public void showInfoPopup() {
    hideInfoPopup();
    infoPopup = FxUtilities.getInstance().showInfoPopup(this);
  }

  @Override
  public void hideInfoPopup() {
    if (infoPopup != null) {
      infoPopup.hide();
      infoPopup = null;
    }
  }

  @Override
  public String getInfo() {
    return infoMessage;
  }

  @Override
  public void setInfo(String infoMessage) {
    boolean wasInfoMessage = this.infoMessage != null;
    this.infoMessage = infoMessage;
    if (wasInfoMessage == (infoMessage == null)) {
      // update style
      if (wasInfoMessage) {
        getStyleClass().remove(INFO_STYLE);
      }
      else if (!getStyleClass().contains(INFO_STYLE)) {
        getStyleClass().add(INFO_STYLE);
      }
      if (isHover()) {
        showInfoPopup();
      }
    }
  }


  /**
   * Configures this button.
   */
  protected void configure() {
    hoverProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        showInfoPopup();
      }
      else {
        hideInfoPopup();
      }
    });
  }

}
