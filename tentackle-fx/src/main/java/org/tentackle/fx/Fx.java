/*
 * Tentackle - http://www.tentackle.org.
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

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.tentackle.misc.ConcurrencyHelper;

/**
 * Often used factory and helper methods for FX-related stuff.
 *
 * @author harald
 */
public class Fx {

  private static final Executor BLOCKING_FX_EXECUTOR = ConcurrencyHelper.createBlockingExecutor(Platform::runLater);

  /**
   * Runs a command on the FX application thread and waits for its execution to finish.
   *
   * @param runnable the Runnable whose run method will be executed on the FX application thread
   * @throws FxRuntimeException if the calling thread is the FX application thread
   * @see Platform#runLater(java.lang.Runnable)
   */
  public static void runAndWait(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      throw new FxRuntimeException("calling thread is the FX application thread");
    }
    BLOCKING_FX_EXECUTOR.execute(runnable);
  }


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
    return FxFactory.getInstance().createController(controllerClass);
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
   */
  public static void info(String message) {
    FxUtilities.getInstance().showInfoDialog(message);
  }

  /**
   * Shows a question dialog.
   *
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @return true if yes, false if no
   */
  public static boolean question(String message, boolean defaultYes) {
    return FxUtilities.getInstance().showQuestionDialog(message, defaultYes);
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   * @param t optional throwable
   */
  public static void error(String message, Throwable t) {
    FxUtilities.getInstance().showErrorDialog(message, t);
  }

  /**
   * Shows an error dialog.
   *
   * @param message the message
   */
  public static void error(String message) {
    FxUtilities.getInstance().showErrorDialog(message, null);
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
