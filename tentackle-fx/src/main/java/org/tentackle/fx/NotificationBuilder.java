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
import javafx.util.Duration;

/**
 * Builder for notifications.<br>
 * Notifications are a lightweight alternative to {@link javafx.scene.control.Alert}s.
 * While the latter are {@link javafx.scene.control.Dialog}s, tentackle notifications
 * come as {@link javafx.scene.Parent} nodes, that can be used in any container,
 * i.e. a {@link javafx.stage.Popup}, {@link javafx.scene.control.Dialog}, part of scenegraph
 * or whatever.
 * <p>
 * Not to be mixed up with {@link org.tentackle.fx.component.Note}s.
 */
public interface NotificationBuilder {

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
     * Displays some information that the user must commit.
     */
    CONFIRMATION,

    /**
     * Displays a question that the user must answer.
     */
    QUESTION

  }

  /**
   * Configuration for a button.
   *
   * @param text the button's text
   * @param graphic the button's graphic
   * @param action the action to perform when clicked
   */
  record ButtonConfig(String text, Node graphic, Runnable action) {}


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
   *
   * @param details the details
   * @return the builder
   */
  NotificationBuilder details(String details);

  /**
   * Sets a duration to close the notification automatically.
   *
   * @param duration the time the dialog should be visible
   * @return the builder
   */
  NotificationBuilder duration(Duration duration);

  /**
   * Sets a fadeout effect to close the notification smoothly.
   *
   * @param fadeOut the time to fade out
   * @return the builder
   */
  NotificationBuilder fadeOut(Duration fadeOut);

  /**
   * Adds a button.
   *
   * @param buttonConfig the button configuration
   * @param isDefault true if this is the default button
   * @return the builder
   */
  NotificationBuilder addButton(ButtonConfig buttonConfig, boolean isDefault);

  /**
   * Sets the hide action when a button is pressed.
   *
   * @param hideAction the action to close or hide the notification
   * @return the builder
   */
  NotificationBuilder hideAction(Runnable hideAction);


  /**
   * Creates the notification.
   *
   * @return the notification
   */
  Parent build();

  /**
   * Returns the title of the notification.<br>
   * It depends on the implementation whether the title is already part of the node
   * or of a window title.
   *
   * @return the title
   */
  String getTitle();

}
