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

import org.tentackle.fx.Fx;
import org.tentackle.fx.component.skin.NoteSkin;

import java.net.URL;

/**
 * A note control.<br>
 * Notes display information related to another linkedNode. Tentackle uses notes to display errors due to bad user input
 * in a linkedNode, e.g. after a ParseException.
 * <p>
 * A note is displayed "nearby" its linkedNode. The default position is to the right side of the linkedNode. However,
 * if there is not enough space left on the screen, the note may be displayed below, left or on top of its
 * linkedNode.<p>
 * The link between the note and its linkedNode is visualized by a straight line and a circle above the linkedNode's
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
     * error message.
     */
    ERROR("error"),

    /**
     * just an info.
     */
    INFO("info"),

    /**
     * a sticky note.
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
  }


  /**
   * display position relative to linked node.
   */
  public enum Position {
    /**
     * to the right side of the node.
     */
    RIGHT("right"),

    /**
     * to the left side of the node.
     */
    LEFT("left"),

    /**
     * above the node.
     */
    TOP("top"),

    /**
     * below the node.
     */
    BOTTOM("bottom"),

    /**
     * in the middle of the node.
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

  }

  private static final String CSS_NAME = "note.css";


  private final Position position;
  private final Type type;
  private final StackPane rootPane;

  private Node contentNode;
  private ObjectProperty<Node> contentNodeProperty;

  private class FadeEffect {

    private final double fadeIn;
    private final double pause;
    private final double fadeOut;

    private FadeEffect(double fadeIn, double pause, double fadeOut) {
      this.fadeIn = fadeIn;
      this.pause = pause;
      this.fadeOut = fadeOut;
    }

    private void play() {
      FadeTransition inTransition = new FadeTransition(new Duration(fadeIn), getRootPane());
      inTransition.setFromValue(0.0);
      inTransition.setToValue(1.0);

      PauseTransition pauseTransition = new PauseTransition(new Duration(pause));

      FadeTransition outTransition = new FadeTransition(new Duration(fadeOut), getRootPane());
      outTransition.setFromValue(1.0);
      outTransition.setToValue(0.0);

      SequentialTransition mainTransition = new SequentialTransition(inTransition, pauseTransition, outTransition);
      mainTransition.setOnFinished(ae -> hide());
      mainTransition.play();
    }
  }

  private FadeEffect fadeEffect;



  /**
   * Creates a note.
   *
   * @param position the position
   * @param type the note type
   */
  public Note(Position position, Type type) {
    this.position = position;
    this.type = type;

    getStyleClass().add("note");
    rootPane = new StackPane();
    URL noteCSS = Note.class.getResource(CSS_NAME);
    if (noteCSS != null) {
      rootPane.getStylesheets().add(noteCSS.toExternalForm());
    }
    getStyleClass().add(type.getCssClass());
    getStyleClass().add(position.getCssClass());

    contentNode = new Label("???");   // just to have a non-null default
  }

  /**
   * Configures an animation to fade-in, pause showing and fade-out again.
   *
   * @param fadeIn time to fade-in in [ms]
   * @param pause time to pause showing in [ms]
   * @param fadeOut time to fade-out in [ms]
   */
  public void setFadeEffect(double fadeIn, double pause, double fadeOut) {
    fadeEffect = new FadeEffect(fadeIn, pause, fadeOut);
  }

  /**
   * Clears the fade effect.
   */
  public void clearFadeEffect() {
    fadeEffect = null;
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new NoteSkin(this);
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
   * Returns the content shown by the pop-over.
   * <p>
   * @return the content linkedNode property
   */
  public ObjectProperty<Node> contentNodeProperty() {
    if (contentNodeProperty == null) {
      contentNodeProperty = new SimpleObjectProperty<>(this, "contentNode");
    }
    return contentNodeProperty;
  }

  /**
   * Gets the content node displayed by this note.
   * <p>
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
   * <p>
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
   * Shows the note popup.
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
   * Sets the text to be shown.
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
   * @return the text
   */
  public String getText() {
    Node contentNode = getContentNode();
    return contentNode instanceof Label label ? label.getText() : null;
  }

}
