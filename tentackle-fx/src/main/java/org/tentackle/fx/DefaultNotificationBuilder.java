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

import org.kordamp.ikonli.javafx.FontIcon;

import org.tentackle.fx.component.FxLabel;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of a notification builder.<br>
 * @see FxFactory#createNotificationBuilder()
 */
public class DefaultNotificationBuilder implements NotificationBuilder {

  /**
   * Configuration for a button.
   *
   * @param text the button's text
   * @param graphic the button's graphic
   * @param button a created button as an alternative to text and graphic
   * @param action the action to perform when clicked
   */
  private record ButtonConfig(String text, Node graphic, Button button, Runnable action) {

    private Button getButton() {
      return button == null ? new Button(text, graphic) : button;
    }
  }


  private static final String CSS_NAME = "notification.css";

  private Type type = Type.NONE;
  private Node graphic;
  private String title;
  private String text;
  private String details;
  private Node content;
  private Duration duration;
  private Duration fadeOut;
  private Duration fadeIn;
  private final List<ButtonConfig> buttonConfigs = new ArrayList<>();
  private ButtonConfig defaultButtonConfig;
  private Runnable hide;
  private URL css;
  private CloseButtonMode closeButtonMode = CloseButtonMode.AUTO;


  /**
   * Creates a notification builder.
   */
  public DefaultNotificationBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public NotificationBuilder type(Type type) {
    this.type = type == null ? Type.NONE : type;
    return this;
  }

  @Override
  public NotificationBuilder graphic(Node graphic) {
    this.graphic = graphic;
    return this;
  }

  @Override
  public NotificationBuilder title(String title) {
    this.title = title;
    return this;
  }

  @Override
  public NotificationBuilder text(String text) {
    this.text = text;
    return this;
  }

  @Override
  public NotificationBuilder details(String details) {
    this.details = details;
    return this;
  }

  @Override
  public NotificationBuilder content(Node content) {
    this.content = content;
    return this;
  }

  @Override
  public NotificationBuilder duration(long duration) {
    this.duration = duration > 0 ? new Duration(duration) : null;
    return this;
  }

  @Override
  public NotificationBuilder fadeOut(long fadeOut) {
    this.fadeOut = fadeOut > 0 ? new Duration(fadeOut) : null;
    return this;
  }

  @Override
  public NotificationBuilder fadeIn(long fadeIn) {
    this.fadeIn = fadeIn > 0 ? new Duration(fadeIn) : null;
    return this;
  }

  @Override
  public NotificationBuilder button(String text, Node graphic, boolean isDefault, Runnable action) {
    return addButtonConfig(new ButtonConfig(text, graphic, null, action), isDefault);
  }

  @Override
  public NotificationBuilder button(Button button, boolean isDefault, Runnable action) {
    return addButtonConfig(new ButtonConfig(null, null, button, action), isDefault);
  }

  @Override
  public NotificationBuilder hide(Runnable hide) {
    this.hide = hide;
    return this;
  }

  @Override
  public NotificationBuilder css(URL css) {
    this.css = css;
    return this;
  }

  @Override
  public NotificationBuilder closeButton(CloseButtonMode closeButtonMode) {
    this.closeButtonMode = closeButtonMode == null ? CloseButtonMode.AUTO : closeButtonMode;
    return this;
  }

  @Override
  public Parent build() {
    Parent parent;

    BorderPane borderPane = new BorderPane();
    borderPane.setId("notificationPane"); // BorderPane does not have a style class
    HBox headerBox = new HBox();
    headerBox.setId("headerBox"); // HBox does not have a style class
    Node icon = getGraphic();
    if (icon != null) {
      if (type == Type.WARNING) {
        icon.setId("warningIcon");
      }
      else if (type == Type.ERROR) {
        icon.setId("errorIcon");
      }
      else {
        icon.setId("standardIcon");
      }
      headerBox.getChildren().add(icon);
    }
    String headerTitle = getTitle();
    if (headerTitle != null && !headerTitle.isBlank()) {
      FxLabel titleLabel = new FxLabel();
      titleLabel.setId("titleLabel");
      titleLabel.setText(headerTitle);
      headerBox.getChildren().add(titleLabel);
    }
    if (text != null) {
      FxLabel label = new FxLabel();
      label.setText(text);
      label.setId("textLabel");
      label.setContextMenuEnabled(true);
      headerBox.getChildren().add(label);
    }
    if (!headerBox.getChildren().isEmpty()) {
      borderPane.setTop(headerBox);
    }

    if (content != null) {
      borderPane.setCenter(content);
    }
    else if (details != null) {
      StackPane detailsStackPane = new StackPane();
      detailsStackPane.setAlignment(Pos.TOP_RIGHT);
      TextArea textArea = new TextArea(details);
      textArea.setEditable(false);
      detailsStackPane.getChildren().add(textArea);
      Button copyButton = new Button("", Fx.createGraphic("copy"));
      copyButton.setId("copyButton");
      copyButton.setFocusTraversable(false);
      copyButton.setTooltip(new Tooltip(FxFxBundle.getString("COPY TO CLIPBOARD")));
      copyButton.setOnAction(_ -> Fx.copyToClipboard(details));
      detailsStackPane.getChildren().add(copyButton);
      borderPane.setCenter(detailsStackPane);
    }

    if (hide != null && closeButtonMode != CloseButtonMode.NEVER &&
        (buttonConfigs.isEmpty() || closeButtonMode.isAlwaysShown())) {
      // add a small close button
      StackPane borderStackPane = new StackPane();
      parent = borderStackPane;
      borderStackPane.setAlignment(Pos.TOP_RIGHT);
      borderStackPane.getChildren().add(borderPane);
      Button closeButton = new Button("", Fx.createGraphic("close"));
      closeButton.setId("closeButton");
      closeButton.setFocusTraversable(false);
      if (closeButtonMode.isDefaultButton()) {
        closeButton.setDefaultButton(true);
      }
      closeButton.setTooltip(new Tooltip(FxFxBundle.getString("CLOSE")));
      closeButton.setOnAction(_ -> hide.run());
      borderStackPane.getChildren().add(closeButton);
    }
    else {
      parent = borderPane;    // ESC will close as well
    }

    if (!buttonConfigs.isEmpty()) {
      HBox buttonBox = new HBox();
      buttonBox.setId("buttonBox"); // HBox does not have a style class
      int buttonIndex = 0;
      for (ButtonConfig buttonConfig : buttonConfigs) {
        Button button = buttonConfig.getButton();
        button.setId("button" + buttonIndex++);
        button.setOnAction(_ -> {
          if (buttonConfig.action() != null) {
            buttonConfig.action().run();
          }
          if (hide != null) {
            hide.run();
          }
        });
        button.setFocusTraversable(false);
        if (buttonConfig == defaultButtonConfig) {
          button.setDefaultButton(true);
        }
        buttonBox.getChildren().add(button);
      }
      borderPane.setBottom(buttonBox);
    }

    URL cssURL = css == null ? getDefaultCSS() : css;
    if (cssURL != null) {
      parent.getStylesheets().addFirst(cssURL.toExternalForm());
    }
    parent.getStyleClass().add("tt-notification");

    if (fadeIn != null || duration != null || fadeOut != null) {
      SequentialTransition transition = new SequentialTransition();
      if (fadeIn != null) {
        FadeTransition fadeInTransition = new FadeTransition(fadeIn, parent);
        fadeInTransition.setFromValue(0.0);
        fadeInTransition.setToValue(1.0);
        transition.getChildren().add(fadeInTransition);
      }
      if (hide != null) {
        if (duration != null) {
          transition.getChildren().add(new PauseTransition(duration));
        }
        if (fadeOut != null) {
          FadeTransition fadeOutTransition = new FadeTransition(fadeOut, parent);
          fadeOutTransition.setFromValue(1.0);
          fadeOutTransition.setToValue(0.0);
          transition.getChildren().add(fadeOutTransition);
        }
        transition.setOnFinished(_ -> hide.run());
      }
      transition.play();
    }

    return parent;
  }

  /**
   * {@inheritDoc}
   * <p>
   * If neither a title nor a text is set, the title defaults to the localized name of the type.
   */
  @Override
  public String getTitle() {
    return title != null || text != null ? title :
             switch (type) {
               case INFORMATION -> FxFxBundle.getString("INFORMATION");
               case WARNING -> FxFxBundle.getString("WARNING");
               case ERROR -> FxFxBundle.getString("ERROR");
               case CONFIRMATION -> FxFxBundle.getString("CONFIRMATION");
               case QUESTION -> FxFxBundle.getString("QUESTION");
               case NONE -> null;
             };
  }


  /**
   * Gets the default CSS URL.
   *
   * @return the URL
   */
  protected URL getDefaultCSS() {
    return ThemeUtilities.getInstance().getResourceURL(NotificationBuilder.class, CSS_NAME);
  }


  private Node getGraphic() {
    return graphic != null ? graphic :
             switch (type) {
               case INFORMATION -> new FontIcon("mdi2i-information-outline");
               case WARNING -> new FontIcon("mdi2a-alert-circle-outline");
               case ERROR -> new FontIcon("mdi2a-alert-outline");
               case CONFIRMATION -> new FontIcon("mdi2c-check-circle-outline");
               case QUESTION -> new FontIcon("mdi2c-comment-question-outline");
               case NONE -> null;
             };
  }

  private NotificationBuilder addButtonConfig(ButtonConfig buttonConfig, boolean isDefault) {
    buttonConfigs.add(buttonConfig);
    if (isDefault) {
      // there can only be one default button
      defaultButtonConfig = buttonConfig;   // the last wins...
    }
    return this;
  }

}
