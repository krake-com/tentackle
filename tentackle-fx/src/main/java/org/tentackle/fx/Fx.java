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

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Often used factory and helper methods for FX-related stuff.
 *
 * @author harald
 */
public class Fx {

  /**
   * Loads an object hierarchy from a FXML document.<br>
   *
   * @param <T> the object's type
   * @param location the url
   * @return the object
   * @throws IOException if loading failed
   */
  public static <T> T load(URL location) throws IOException {
    FXMLLoader loader = new FXMLLoader(location, null, FxFactory.getInstance().getBuilderFactory());
    return loader.load();
  }

  /**
   * Loads an object hierarchy from a FXML document.<br>
   *
   * @param <T> the object's type
   * @param location the url
   * @param resources the optional resources
   * @return the object
   * @throws IOException if loading failed
   */
  public static <T> T load(URL location, ResourceBundle resources) throws IOException {
    FXMLLoader loader = new FXMLLoader(location, resources, FxFactory.getInstance().getBuilderFactory());
    return loader.load();
  }

  /**
   * Loads a controller with its FXML-view.
   *
   * @param <T> the controller type
   * @param controllerClass the controller class
   * @return the controller
   */
  @SuppressWarnings("unchecked")
  public static <T extends FxController> T load(Class<T> controllerClass) {
    return FxFactory.getInstance().createController(controllerClass, null, null, null);
  }

  /**
   * Creates a new stage.<br>
   * Tentackle applications should use this factory method instead of <code>new Stage()</code>
   * because the stage will be configured to meet certain framework-wide conventions.
   *
   * @param stageStyle the style
   * @param modality the modality
   * @return the stage
   */
  public static Stage createStage(StageStyle stageStyle, Modality modality) {
    return FxFactory.getInstance().createStage(stageStyle, modality);
  }

  /**
   * Creates a new decorated stage.<br>
   * Tentackle applications should use this factory method instead of <code>new Stage()</code>
   * because the stage will be configured to meet certain framework-wide conventions.
   *
   * @param modality the modality
   * @return the stage
   */
  public static Stage createStage(Modality modality) {
    return createStage(StageStyle.DECORATED, modality);
  }

  /**
   * Creates a node.<br>
   * Nodes should not be created via the builder factory and not
   * the tradional way by invoking a constructor.
   * Only the builder factory guarantees TT-compliant instances.
   *
   * @param <T> the requested FX standard node type
   * @param <R> the expected type of the returned node
   * @param clazz the node's class
   * @return the node instance
   */
  @SuppressWarnings("unchecked")
  public static <T, R extends T> R createNode(Class<T> clazz) {
    return (R) FxFactory.getInstance().getBuilderFactory().getBuilder(clazz).build();
  }

  /**
   * Shows an info dialog.
   *
   * @param message the message
   * @param title optional title
   */
  public static void info(String message, String title) {
    FxUtilities.getInstance().showInfoDialog(message, title);
  }

  /**
   * Shows an info dialog.
   *
   * @param message the message
   */
  public static void info(String message) {
    FxUtilities.getInstance().showInfoDialog(message, null);
  }

  /**
   * Shows a warning dialog.
   *
   * @param message the message
   * @param title optional title
   */
  public static void warning(String message, String title) {
    FxUtilities.getInstance().showWarningDialog(message, title);
  }

  /**
   * Shows a warning dialog.
   *
   * @param message the message
   */
  public static void warning(String message) {
    FxUtilities.getInstance().showWarningDialog(message, null);
  }

  /**
   * Shows a question dialog.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param title optional title
   * @param answer the user's answer (invoked with Boolean.TRUE or Boolean.FALSE, never null)
   */
  public static void question(String message, boolean defaultYes, String title, Consumer<Boolean> answer) {
    FxUtilities.getInstance().showQuestionDialog(message, defaultYes, title, answer);
  }

  /**
   * Shows a question dialog.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param answer the user's answer (invoked with Boolean.TRUE or Boolean.FALSE, never null)
   */
  public static void question(String message, boolean defaultYes, Consumer<Boolean> answer) {
    FxUtilities.getInstance().showQuestionDialog(message, defaultYes, null, answer);
  }

  /**
   * Shows a question dialog.<br>
   * Short for {@link #question(String, boolean, Consumer)} if answer is only checked for yes.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param yes invoked if user answers with yes
   */
  public static void yes(String message, boolean defaultYes, Runnable yes) {
    FxUtilities.getInstance().showQuestionDialog(message, defaultYes, null, answer -> {
      if (answer) {
        yes.run();
      }
    });
  }

  /**
   * Shows a question dialog.<br>
   * Short for {@link #question(String, boolean, Consumer)} if answer is only checked for no.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param no invoked if user answers with no
   */
  public static void no(String message, boolean defaultYes, Runnable no) {
    FxUtilities.getInstance().showQuestionDialog(message, defaultYes, null, answer -> {
      if (!answer) {
        no.run();
      }
    });
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   * @param t optional throwable
   * @param title optional title
   */
  public static void error(String message, Throwable t, String title) {
    FxUtilities.getInstance().showErrorDialog(message, t, title);
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   * @param t optional throwable
   */
  public static void error(String message, Throwable t) {
    FxUtilities.getInstance().showErrorDialog(message, t, null);
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   */
  public static void error(String message) {
    FxUtilities.getInstance().showErrorDialog(message, null, null);
  }


  /**
   * Creates an image view for an image.
   *
   * @param realm the realm, null or empty if tentackle images
   * @param name the image's name
   * @return the view
   */
  public static ImageView createImageView(String realm, String name) {
    return new ImageView(FxFactory.getInstance().getImage(realm, name));
  }

  /**
   * Creates an image view for a tentackle image.
   *
   * @param name the image's name
   * @return the view
   */
  public static ImageView createImageView(String name) {
    return createImageView("", name);
  }


  /**
   * Gets the stage for a node.
   *
   * @param node the node
   * @return the stage, null if node does not belong to a scene or scene does not belong to a stage.
   */
  public static Stage getStage(Node node) {
    return FxUtilities.getInstance().getStage(node);
  }


  /**
   * Returns whether the stage is modal.
   *
   * @param stage the stage
   * @return true if effectively modal
   */
  public static boolean isModal(Stage stage) {
    return FxUtilities.getInstance().isModal(stage);
  }


  private Fx() {
    // no instances
  }
}
