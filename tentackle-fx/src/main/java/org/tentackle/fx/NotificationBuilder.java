/*
 * Tentackle - https://tentackle.org
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
 */

package org.tentackle.fx;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import java.net.URL;

/**
 * Builder for notifications.<br>
 * Notifications are a lightweight alternative to {@link javafx.scene.control.Alert}s.
 * While the latter are {@link javafx.scene.control.Dialog}s, tentackle notifications
 * come as {@link javafx.scene.Parent} nodes that can be used in any container,
 * e.g. a {@link javafx.stage.Popup}, a {@link javafx.scene.control.Dialog}, or as part of a scene graph.
 * <p>
 * Not to be mixed up with {@link org.tentackle.fx.component.Note}s.
 */
public interface NotificationBuilder {

  /**
   * The notification type.
   */
  enum Type {

    /**
     * No dedicated purpose. Used by applications, if nothing else fits.
     * There is no default title and no default graphic.
     */
    NONE,

    /**
     * Displays some information.
     */
    INFORMATION,

    /**
     * Displays a warning.
     */
    WARNING,

    /**
     * Displays an error.
     */
    ERROR,

    /**
     * Displays some information that the user must confirm.
     */
    CONFIRMATION,

    /**
     * Displays a question that the user must answer.
     */
    QUESTION

  }


  /**
   * Controls when and how the close button is displayed.
   */
  enum CloseButtonMode {

    /**
     * This is the default.<br>
     * If there are no buttons defined and the hide action is set,
     * a close button will be shown.
     */
    AUTO(false, false),

    /**
     * Same as <code>AUTO</code>, but the close button will be shown as the default button,
     * i.e. <code>ENTER</code> closes the dialog.
     */
    AUTO_DEFAULT(true, false),

    /**
     * No close button will ever be displayed.
     */
    NEVER(false, false),

    /**
     * A close button will always be displayed, if the hide action is set.
     */
    ALWAYS(false, true),

    /**
     * Same as <code>ALWAYS</code>, but the close button will be shown as the default button,
     * i.e. <code>ENTER</code> closes the dialog.
     */
    ALWAYS_DEFAULT(true, true);


    private final boolean defaultButton;
    private final boolean alwaysShown;

    CloseButtonMode(boolean defaultButton, boolean alwaysShown) {
      this.defaultButton = defaultButton;
      this.alwaysShown = alwaysShown;
    }

    /**
     * Returns whether this is the default button.
     *
     * @return true if default button
     */
    public boolean isDefaultButton() {
      return defaultButton;
    }

    /**
     * Returns whether the close button is always shown.
     *
     * @return shown even if there are other buttons defined
     */
    public boolean isAlwaysShown() {
      return alwaysShown;
    }
  }


  /**
   * Sets the notification type.
   *
   * @param type the type
   * @return the builder
   */
  NotificationBuilder type(Type type);

  /**
   * Sets the graphic.
   *
   * @param graphic the graphic
   * @return the builder
   */
  NotificationBuilder graphic(Node graphic);

  /**
   * Sets the title.
   *
   * @param title the title
   * @return the builder
   */
  NotificationBuilder title(String title);

  /**
   * Sets the text message.
   *
   * @param text the message
   * @return the builder
   */
  NotificationBuilder text(String text);

  /**
   * Sets the detail message.<br>
   * A stacktrace, for example.
   * <p>
   * Notice that details are ignored if {@link #content(Node)} is set.
   *
   * @param details the details
   * @return the builder
   */
  NotificationBuilder details(String details);

  /**
   * Sets a node as an alternative to details.
   *
   * @param content the content node
   * @return the builder
   */
  NotificationBuilder content(Node content);

  /**
   * Sets a fadein effect to show the notification smoothly.
   *
   * @param fadeIn the time to fade in [ms]
   * @return the builder
   */
  NotificationBuilder fadeIn(long fadeIn);

  /**
   * Sets a duration to close the notification automatically.<br>
   * Ignored if there is no hide action.
   *
   * @param duration the time the dialog should be visible [ms]
   * @return the builder
   */
  NotificationBuilder duration(long duration);

  /**
   * Sets a fadeout effect to close the notification smoothly.<br>
   * Ignored if there is no hide action.
   *
   * @param fadeOut the time to fade out [ms]
   * @return the builder
   */
  NotificationBuilder fadeOut(long fadeOut);

  /**
   * Adds a button.
   *
   * @param text the button's text
   * @param graphic the button's graphic
   * @param isDefault true if this is the default button
   * @param action the action to perform when clicked
   * @return the builder
   */
  NotificationBuilder button(String text, Node graphic, boolean isDefault, Runnable action);

  /**
   * Adds a button.
   *
   * @param button the button to add
   * @param isDefault true if this is the default button
   * @param action the action to perform when clicked
   * @return the builder
   */
  NotificationBuilder button(Button button, boolean isDefault, Runnable action);

  /**
   * Sets the close button mode.
   *
   * @param closeButtonMode the close button mode
   * @return the builder
   */
  NotificationBuilder closeButton(CloseButtonMode closeButtonMode);

  /**
   * Sets the hide action.<br>
   * Invoked when a button is pressed, the close button is clicked, or the duration (including the fadeout) has elapsed.
   *
   * @param hide the action to close or hide the notification
   * @return the builder
   */
  NotificationBuilder hide(Runnable hide);

  /**
   * Applies a CSS stylesheet.<br>
   * Overrides the default stylesheet <code>org/tentackle/fx/notification.css</code>.
   *
   * @param css the stylesheet
   * @return the builder
   */
  NotificationBuilder css(URL css);


  /**
   * Creates the notification.
   *
   * @return the notification
   */
  Parent build();

  /**
   * Returns the title of the notification.<br>
   * It depends on the implementation whether the title is already part of the node
   * or of a window title or not visible at all.
   *
   * @return the title
   */
  String getTitle();

}
