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

import org.tentackle.fx.Fx;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.fx.component.skin.NoteSkin;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.net.URL;
import java.util.List;

/**
 * A note control.<br>
 * Notes display information related to another node. Tentackle uses notes to display errors due to bad user input
 * in a node, e.g., after a ParseException.
 * <p>
 * A note is displayed "nearby" its node, according to its {@link Position}.<p>
 * The link between the note and its node is visualized by a straight line and a circle above the node's
 * border.<p>
 * Notes come in 3 categories:
 * <ol>
 * <li>error (background colored light-red, link red, text red and bold)</li>
 * <li>info (standard colors, link green)</li>
 * <li>sticky (yellow background, black text, link gray)</li>
 * </ol>
 *
 * @author harald
 */
public class Note extends PopupControl {

  /**
   * The note type.
   */
  public enum Type {

    /**
     * Error message.
     */
    ERROR("error"),

    /**
     * Info message.
     */
    INFO("info"),

    /**
     * A sticky note.
     */
    STICKY("sticky");

    private final String cssClass;

    Type(String cssClass) {
      this.cssClass = cssClass;
    }

    /**
     * Gets the CSS class.
     *
     * @return the class
     */
    public String getCssClass() {
      return cssClass;
    }

    /**
     * Applies the CSS style.
     *
     * @param styleClass the style class list
     */
    public void apply(List<String> styleClass) {
      for (Type type : values()) {
        if (type == this) {
          if (!styleClass.contains(cssClass)) {
            styleClass.add(cssClass);
          }
        }
        else {
          styleClass.remove(type.cssClass);
        }
      }
    }
  }


  /**
   * Display position relative to the linked node.
   */
  public enum Position {
    /**
     * To the right side of the node.
     */
    RIGHT("right"),

    /**
     * To the left side of the node.
     */
    LEFT("left"),

    /**
     * Above the node.
     */
    TOP("top"),

    /**
     * Below the node.
     */
    BOTTOM("bottom"),

    /**
     * In the middle of the node.
     */
    CENTER("center");

    private final String cssClass;

    Position(String cssClass) {
      this.cssClass = cssClass;
    }

    /**
     * Gets the CSS class.
     *
     * @return the class
     */
    public String getCssClass() {
      return cssClass;
    }

    /**
     * Applies the CSS style.
     *
     * @param styleClass the style class list
     */
    public void apply(List<String> styleClass) {
      for (Position position : values()) {
        if (position == this) {
          if (!styleClass.contains(cssClass)) {
            styleClass.add(cssClass);
          }
        }
        else {
          styleClass.remove(position.cssClass);
        }
      }
    }
  }

  private static final String CSS_NAME = "note.css";
  private static final String CSS_NAME_DARK = "note-dark.css";


  private final StackPane rootPane;
  private Position position;
  private Type type;
  private Node contentNode;
  private ObjectProperty<Node> contentNodeProperty;

  private class FadeEffect {

    private final Duration fadeIn;
    private final Duration pause;
    private final Duration fadeOut;

    private FadeEffect(Duration fadeIn, Duration pause, Duration fadeOut) {
      this.fadeIn = fadeIn;
      this.pause = pause;
      this.fadeOut = fadeOut;
    }

    private void play() {
      SequentialTransition mainTransition = new SequentialTransition();
      if (fadeIn != null) {
        FadeTransition inTransition = new FadeTransition(fadeIn, getRootPane());
        inTransition.setFromValue(0.0);
        inTransition.setToValue(1.0);
        mainTransition.getChildren().add(inTransition);
      }

      if (pause != null) {
        mainTransition.getChildren().add(new PauseTransition(pause));
      }

      if (fadeOut != null) {
        FadeTransition outTransition = new FadeTransition(fadeOut, getRootPane());
        outTransition.setFromValue(1.0);
        outTransition.setToValue(0.0);
        mainTransition.getChildren().add(outTransition);
      }

      mainTransition.setOnFinished(event -> hide());
      mainTransition.play();
    }
  }

  private FadeEffect fadeEffect;



  /**
   * Creates a note.<br>
   * With {@link Position#CENTER} and {@link Type#INFO}.
   */
  public Note() {
    this.position = Position.CENTER;
    this.type = Type.INFO;

    getStyleClass().add("note");
    rootPane = new StackPane();
    URL noteCSS = getCSS();
    if (noteCSS != null) {
      rootPane.getStylesheets().add(noteCSS.toExternalForm());
    }
    getStyleClass().add(type.getCssClass());
    getStyleClass().add(position.getCssClass());

    contentNode = new Label("???");   // just to have a non-null default
  }


  /**
   * Configures an animation to fade-in, pause for showing, and fade-out again.
   *
   * @param fadeIn time to fade-in in [ms]
   * @param pause time to pause showing in [ms]
   * @param fadeOut time to fade-out in [ms]
   */
  public void setFadeEffect(long fadeIn, long pause, long fadeOut) {
    fadeEffect = new FadeEffect(fadeIn > 0 ? new Duration(fadeIn) : null,
                                pause > 0 ? new Duration(pause) : null,
                                fadeOut > 0 ? new Duration(fadeOut) : null);
  }

  /**
   * Clears the fade effect.
   */
  public void clearFadeEffect() {
    fadeEffect = null;
  }


  /**
   * Sets the position relative to the owner node.
   *
   * @param position the position
   */
  public void setPosition(Position position) {
    if (this.position != position) {
      this.position = position;
      position.apply(getStyleClass());
    }
  }

  /**
   * Gets the position.
   *
   * @return the position
   */
  public Position getPosition() {
    return position;
  }


  /**
   * Sets the note type.
   *
   * @param type the type
   */
  public void setType(Type type) {
    if (this.type != type) {
      this.type = type;
      type.apply(getStyleClass());
    }
  }

  /**
   * Gets the note type.
   *
   * @return the type
   */
  public Type getType() {
    return type;
  }


  /**
   * Gets the root pane of this note.
   *
   * @return the root pane
   */
  public StackPane getRootPane() {
    return rootPane;
  }

  /**
   * Gets the property of the content shown by the note.
   *
   * @return the content node property
   */
  public ObjectProperty<Node> contentNodeProperty() {
    if (contentNodeProperty == null) {
      // must be initialized with the current content, else it would be lost
      contentNodeProperty = new SimpleObjectProperty<>(this, "contentNode", contentNode);
    }
    return contentNodeProperty;
  }

  /**
   * Gets the content node displayed by this note.
   *
   * @return the content node
   */
  public Node getContentNode() {
    if (contentNodeProperty != null) {
      return contentNodeProperty.get();
    }
    return contentNode;
  }

  /**
   * Sets the content node displayed by this note.
   *
   * @param contentNode the content node
   */
  public void setContentNode(Node contentNode) {
    if (contentNodeProperty != null) {
      contentNodeProperty.set(contentNode);
    }
    else {
      this.contentNode = contentNode;
    }
  }

  /**
   * Shows the note popup.<br>
   * Does nothing if the owner node is null or does not belong to a stage.
   *
   * @param ownerNode the owner node
   */
  public void show(Node ownerNode) {
    if (ownerNode != null && Fx.getStage(ownerNode) != null) {
      if (fadeEffect != null) {
        fadeEffect.play();
      }
      super.show(ownerNode, 0.0, 0.0);
    }
  }

  /**
   * Sets the text to be shown.<br>
   * Replaces the content node with a label.
   *
   * @param text the text
   */
  public void setText(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("text");
    setContentNode(label);
  }

  /**
   * Gets the text shown.
   *
   * @return the text, null if the content node is not a label
   */
  public String getText() {
    Node contentNode = getContentNode();
    return contentNode instanceof Label label ? label.getText() : null;
  }


  @Override
  protected Skin<?> createDefaultSkin() {
    return new NoteSkin(this);
  }

  /**
   * Gets the CSS URL.
   *
   * @return the URL
   */
  protected URL getCSS() {
    return ThemeUtilities.getInstance().getResourceURL(
      Note.class, ThemeUtilities.getInstance().isDarkMode() ? CSS_NAME_DARK : CSS_NAME);
  }

}
