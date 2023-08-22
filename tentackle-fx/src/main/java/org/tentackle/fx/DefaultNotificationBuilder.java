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

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of a notification builder.<br>
 * @see FxFactory#createNotificationBuilder()
 */
public class DefaultNotificationBuilder implements NotificationBuilder {

  private Type type;
  private Node graphic;
  private String title;
  private String text;
  private String details;
  private Duration duration;
  private Duration fadeOut;
  private final List<ButtonConfig> buttonConfigs = new ArrayList<>();
  private ButtonConfig defaultButtonConfig;
  private Runnable hideAction;


  public DefaultNotificationBuilder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public NotificationBuilder type(Type type) {
    this.type = type;
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
  public NotificationBuilder duration(Duration duration) {
    this.duration = duration;
    return this;
  }

  @Override
  public NotificationBuilder fadeOut(Duration fadeOut) {
    this.fadeOut = fadeOut;
    return this;
  }

  @Override
  public NotificationBuilder addButton(ButtonConfig buttonConfig, boolean isDefault) {
    buttonConfigs.add(buttonConfig);
    if (isDefault) {
      defaultButtonConfig = buttonConfig;
    }
    return this;
  }

  @Override
  public NotificationBuilder hideAction(Runnable hideAction) {
    this.hideAction = hideAction;
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
    headerBox.getChildren().add(new Label(text));

    borderPane.setTop(headerBox);

    if (details != null) {
      StackPane detailsStackPane = new StackPane();
      detailsStackPane.setAlignment(Pos.TOP_RIGHT);
      TextArea textArea = new TextArea(details);
      textArea.setEditable(false);
      detailsStackPane.getChildren().add(textArea);
      Button copyButton = new Button("", Fx.createGraphic("copy"));
      copyButton.setId("copyButton");
      copyButton.setFocusTraversable(false);
      copyButton.setTooltip(new Tooltip(FxFxBundle.getString("COPY TO CLIPBOARD")));
      copyButton.setOnAction(event -> {
        ClipboardContent cbc = new ClipboardContent();
        cbc.putString(details);
        Clipboard.getSystemClipboard().setContent(cbc);
      });
      detailsStackPane.getChildren().add(copyButton);
      borderPane.setCenter(detailsStackPane);
    }

    HBox buttonBox = new HBox();
    buttonBox.setId("buttonBox"); // HBox does not have a style class
    if (buttonConfigs.isEmpty()) {
      if (hideAction != null) {
        // no buttons: add small close button
        StackPane borderStackPane = new StackPane();
        parent = borderStackPane;
        borderStackPane.setAlignment(Pos.TOP_RIGHT);
        borderStackPane.getChildren().add(borderPane);
        Button closeButton = new Button("", Fx.createGraphic("close"));
        closeButton.setId("closeButton");
        closeButton.setTooltip(new Tooltip(FxFxBundle.getString("CLOSE")));
        closeButton.setOnAction(event -> hide(parent));
        borderStackPane.getChildren().add(closeButton);
      }
      else {
        parent = borderPane;    // ESC key will close as well
      }
    }
    else {
      parent = borderPane;
      for (ButtonConfig buttonConfig : buttonConfigs) {
        Button button = new Button(buttonConfig.text(), buttonConfig.graphic());
        button.setOnAction(event -> {
          if (buttonConfig.action() != null) {
            buttonConfig.action().run();
          }
          if (hideAction != null) {
            hide(parent);
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

    String stylesheetUrl = NotificationBuilder.class.getResource("notification.css").toExternalForm();
    parent.getStylesheets().add(0, stylesheetUrl);
    parent.getStyleClass().add("tt-notification");

    if (duration != null && hideAction != null) {
      PauseTransition pauseTransition = new PauseTransition(duration);
      SequentialTransition transition = new SequentialTransition(pauseTransition);
      if (fadeOut != null) {
        FadeTransition outTransition = new FadeTransition(fadeOut, parent);
        outTransition.setFromValue(1.0);
        outTransition.setToValue(0.0);
        transition.getChildren().add(outTransition);
      }
      transition.setOnFinished(event -> hideAction.run());
      transition.play();
    }

    return parent;
  }

  @Override
  public String getTitle() {
    return title != null ? title :
             switch (getType()) {
               case INFORMATION -> FxFxBundle.getString("INFORMATION");
               case WARNING -> FxFxBundle.getString("WARNING");
               case ERROR -> FxFxBundle.getString("ERROR");
               case CONFIRMATION -> FxFxBundle.getString("INFORMATION");
               case QUESTION -> FxFxBundle.getString("QUESTION");
               case NONE -> null;
             };
  }


  private Type getType() {
    return type == null ? Type.NONE : type;
  }

  private Node getGraphic() {
    return graphic != null ? graphic :
             switch (getType()) {
               case INFORMATION -> new FontIcon("mdi2i-information-outline");
               case WARNING -> new FontIcon("mdi2a-alert-circle-outline");
               case ERROR -> new FontIcon("mdi2a-alert-circle-outline");
               case CONFIRMATION -> new FontIcon("mdi2i-information-outline");
               case QUESTION -> new FontIcon("mdi2c-comment-question-outline");
               case NONE -> null;
             };
  }

  private void hide(Parent parent) {
    if (duration == null && fadeOut != null) {
      SequentialTransition transition = new SequentialTransition(createFadeOutTransition(parent));
      transition.setOnFinished(event -> hideAction.run());
      transition.play();
    }
    else {
      hideAction.run();
    }
  }

  private FadeTransition createFadeOutTransition(Parent parent) {
    FadeTransition fadeOutTransition = new FadeTransition(fadeOut, parent);
    fadeOutTransition.setFromValue(1.0);
    fadeOutTransition.setToValue(0.0);
    return fadeOutTransition;
  }

}
