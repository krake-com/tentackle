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

package org.tentackle.fx.component.skin;

import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.component.Note.Position;

import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Window;

/**
 * Skin for the {@link Note} control.
 *
 * @author harald
 */
public class NoteSkin implements Skin<Note> {

  private static final double LINE_LENGTH = 15;
  private static final double CIRCLE_RADIUS = 3;

  private final Node linkedNode;
  private Note note;
  private StackPane rootPane;


  /**
   * Creates the note skin.
   *
   * @param note the note
   */
  public NoteSkin(Note note) {
    this.note = note;
    this.rootPane = note.getRootPane();

    linkedNode = note.getOwnerNode();
    if (linkedNode == null) {
      throw new FxRuntimeException("owner node not set");
    }

    Bindings.bindContent(rootPane.getStyleClass(), note.getStyleClass());

    Node link = createLink(note.getPosition());

    switch(note.getPosition()) {
      case BOTTOM:
        VBox bottomNote = new VBox();
        bottomNote.setAlignment(Pos.CENTER);
        bottomNote.getChildren().add(link);
        bottomNote.getChildren().add(note.getContentNode());
        bottomNote.getStyleClass().add("box");
        rootPane.getChildren().add(bottomNote);
        break;

      case TOP:
        VBox topNote = new VBox();
        topNote.setAlignment(Pos.CENTER);
        topNote.getChildren().add(note.getContentNode());
        topNote.getChildren().add(link);
        topNote.getStyleClass().add("box");
        rootPane.getChildren().add(topNote);
        break;

      case LEFT:
        HBox leftNote = new HBox();
        leftNote.setAlignment(Pos.CENTER);
        leftNote.getChildren().add(note.getContentNode());
        leftNote.getChildren().add(link);
        leftNote.getStyleClass().add("box");
        rootPane.getChildren().add(leftNote);
        break;

      case RIGHT:
        HBox rightNote = new HBox();
        rightNote.setSpacing(0.0);
        rightNote.setAlignment(Pos.CENTER);
        rightNote.getChildren().add(link);
        rightNote.getChildren().add(note.getContentNode());
        rightNote.getStyleClass().add("box");
        rootPane.getChildren().add(rightNote);
        break;

      default:  // CENTER
        HBox centerNote = new HBox();
        centerNote.setSpacing(0.0);
        centerNote.setAlignment(Pos.CENTER);
        // no link for center
        centerNote.getChildren().add(note.getContentNode());
        centerNote.getStyleClass().add("box");
        rootPane.getChildren().add(centerNote);
    }

    // to move the popup along with the linked node/window
    Window ownerWindow = linkedNode.getScene().getWindow();
    ownerWindow.xProperty().addListener(new WeakInvalidationListener(o -> updateLocation()));
    ownerWindow.yProperty().addListener(new WeakInvalidationListener(o -> updateLocation()));
    note.setOnShown(e -> updateLocation());
  }


  @Override
  public Note getSkinnable() {
    return note;
  }

  @Override
  public StackPane getNode() {
    return rootPane;
  }

  @Override
  public void dispose() {
    note = null;
    rootPane = null;
  }


  /**
   * Updates the location when the effective size is known.
   */
  protected void updateLocation() {
    Bounds linkedNodeBounds = linkedNode.localToScreen(linkedNode.getBoundsInLocal());
    Point2D linkedNodeLocation = linkedNode.localToScreen(0.0, 0.0);
    if (linkedNodeBounds != null && linkedNodeLocation != null) {
      switch (note.getPosition()) {
        case BOTTOM:
          note.setAnchorX(linkedNodeLocation.getX() + linkedNodeBounds.getWidth() / 2 - note.getWidth() / 2);
          note.setAnchorY(linkedNodeLocation.getY() + linkedNodeBounds.getHeight() - CIRCLE_RADIUS);
          break;

        case TOP:
          note.setAnchorX(linkedNodeLocation.getX() + linkedNodeBounds.getWidth() / 2 - note.getWidth() / 2);
          note.setAnchorY(linkedNodeLocation.getY() - note.getHeight() + CIRCLE_RADIUS);
          break;

        case LEFT:
          note.setAnchorX(linkedNodeLocation.getX() - note.getWidth() + CIRCLE_RADIUS);
          note.setAnchorY(linkedNodeLocation.getY() + linkedNodeBounds.getHeight() / 2 - note.getHeight() / 2);
          break;

        case RIGHT:
          note.setAnchorX(linkedNodeLocation.getX() + linkedNodeBounds.getWidth() - CIRCLE_RADIUS);
          note.setAnchorY(linkedNodeLocation.getY() + linkedNodeBounds.getHeight() / 2 - note.getHeight() / 2);
          break;

        default:  // CENTER
          note.setAnchorX(linkedNodeLocation.getX() + linkedNodeBounds.getWidth() / 2 - note.getWidth() / 2);
          note.setAnchorY(linkedNodeLocation.getY() + linkedNodeBounds.getHeight() / 2 - note.getHeight() / 2);
      }
    }
  }


  /**
   * Creates the link.
   *
   * @param alignment the position
   * @return the link node
   */
  protected Node createLink(Position alignment) {

    Circle circle = new Circle();
    circle.getStyleClass().add("circle");
    circle.setRadius(CIRCLE_RADIUS);

    Line line = new Line();
    line.getStyleClass().add("line");

    switch(alignment) {
      case BOTTOM:
        circle.setCenterX(CIRCLE_RADIUS);
        circle.setCenterY(CIRCLE_RADIUS);
        line.setStartX(CIRCLE_RADIUS);
        line.setStartY(CIRCLE_RADIUS);
        line.setEndX(CIRCLE_RADIUS);
        line.setEndY(CIRCLE_RADIUS + LINE_LENGTH);
        break;

      case TOP:
        line.setStartX(CIRCLE_RADIUS);
        line.setStartY(0);
        line.setEndX(CIRCLE_RADIUS);
        line.setEndY(LINE_LENGTH);
        circle.setCenterX(CIRCLE_RADIUS);
        circle.setCenterY(CIRCLE_RADIUS + LINE_LENGTH);
        break;

      case LEFT:
        line.setStartX(0);
        line.setStartY(CIRCLE_RADIUS);
        line.setEndX(LINE_LENGTH);
        line.setEndY(CIRCLE_RADIUS);
        circle.setCenterX(CIRCLE_RADIUS + LINE_LENGTH);
        circle.setCenterY(CIRCLE_RADIUS);
        break;

      default:  // RIGHT
        circle.setCenterX(CIRCLE_RADIUS);
        circle.setCenterY(CIRCLE_RADIUS);
        line.setStartX(CIRCLE_RADIUS);
        line.setStartY(CIRCLE_RADIUS);
        line.setEndX(CIRCLE_RADIUS + LINE_LENGTH);
        line.setEndY(CIRCLE_RADIUS);
    }

    Group group = new Group();
    group.getStyleClass().add("link");
    group.getChildren().add(circle);
    group.getChildren().add(line);
    return group;
  }

}
