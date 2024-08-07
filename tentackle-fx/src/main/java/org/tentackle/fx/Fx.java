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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Builder;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
   * Shows a stage.<br>
   * Should be preferred over {@link Stage#show()} to allow adding
   * application-specific bookkeeping.
   *
   * @param stage the stage to show
   */
  public static void show(Stage stage) {
    FxUtilities.getInstance().show(stage);
  }

  /**
   * Creates a scene.
   *
   * @param root the parent node
   * @return the scene
   */
  public static Scene createScene(Parent root) {
    return FxFactory.getInstance().createScene(root);
  }


  /**
   * Creates an object.<br>
   * FX objects should not be created via the builder factory and not
   * the traditional way by invoking a constructor.
   * Only the builder factory guarantees TT-compliant instances.
   * <p>
   * Throws {@link FxRuntimeException} if no builder for this type and/or
   * the type does not provide a no-arg constructor.
   *
   * @param <T> the requested FX standard type
   * @param <R> the expected type of the returned object
   * @param clazz the object's class
   * @return the created object
   */
  @SuppressWarnings("unchecked")
  public static <T, R extends T> R create(Class<T> clazz) {
    Builder<R> builder = (Builder<R>) FxFactory.getInstance().getBuilderFactory().getBuilder(clazz);
    if (builder != null) {
      return builder.build();
    }
    // else T == R! (for application-specific extensions, for example BlahTableView extends FxTableView)
    try {
      R bean = (R) clazz.getDeclaredConstructor().newInstance();
      Configurator<T> configurator = FxFactory.getInstance().getConfigurator(clazz);
      if (configurator != null) {
        configurator.configure(bean);
      }
      return bean;
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new FxRuntimeException("cannot create " + clazz.getName());
    }
  }


  /**
   * Shows an info dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param onClose optional runnable invoked when dialog is closed
   * @see FxUtilities#showInfoDialog(Object, String, Runnable)
   */
  public static void info(Object owner, String message, Runnable onClose) {
    FxUtilities.getInstance().showInfoDialog(owner, message, onClose);
  }

  /**
   * Shows an info dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @see FxUtilities#showInfoDialog(Object, String, Runnable)
   */
  public static void info(Object owner, String message) {
    info(owner, message, null);
  }


  /**
   * Shows a warning dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param onClose optional runnable invoked when dialog is closed
   * @see FxUtilities#showWarningDialog(Object, String, Runnable)
   */
  public static void warning(Object owner, String message, Runnable onClose) {
    FxUtilities.getInstance().showWarningDialog(owner, message, onClose);
  }

  /**
   * Shows a warning dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @see FxUtilities#showWarningDialog(Object, String, Runnable)
   */
  public static void warning(Object owner, String message) {
    warning(owner, message, null);
  }


  /**
   * Shows an error dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param t optional throwable
   * @param onClose optional runnable invoked when dialog is closed
   * @see FxUtilities#showErrorDialog(Object, String, Throwable, Runnable)
   */
  public static void error(Object owner, String message, Throwable t, Runnable onClose) {
    FxUtilities.getInstance().showErrorDialog(owner, message, t, onClose);
  }

  /**
   * Shows an error dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param t optional throwable
   * @see FxUtilities#showErrorDialog(Object, String, Throwable, Runnable)
   */
  public static void error(Object owner, String message, Throwable t) {
    error(owner, message, t, null);
  }

  /**
   * Shows an error dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @see FxUtilities#showErrorDialog(Object, String, Throwable, Runnable)
   */
  public static void error(Object owner, String message) {
    error(owner, message, null, null);
  }


  /**
   * Shows a question dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param answer the user's answer (invoked with {@link Boolean#TRUE} or {@link Boolean#FALSE}, never null)
   * @see FxUtilities#showQuestionDialog(Object, String, boolean, Consumer)
   */
  public static void question(Object owner, String message, boolean defaultYes, Consumer<Boolean> answer) {
    FxUtilities.getInstance().showQuestionDialog(owner, message, defaultYes, answer);
  }


  /**
   * Shows a question dialog.<br>
   * Short for {@link #question(Object, String, boolean, Consumer)} if answer is only checked for yes.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param yes invoked if user answers with yes
   * @see FxUtilities#showQuestionDialog(Object, String, boolean, Consumer)
   */
  public static void yes(Object owner, String message, boolean defaultYes, Runnable yes) {
    FxUtilities.getInstance().showQuestionDialog(owner, message, defaultYes, answer -> {
      if (answer) {
        yes.run();
      }
    });
  }

  /**
   * Shows a question dialog.<br>
   * Short for {@link #question(Object, String, boolean, Consumer)} if answer is only checked for no.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param no invoked if user answers with no
   * @see FxUtilities#showQuestionDialog(Object, String, boolean, Consumer)
   */
  public static void no(Object owner, String message, boolean defaultYes, Runnable no) {
    FxUtilities.getInstance().showQuestionDialog(owner, message, defaultYes, answer -> {
      if (!answer) {
        no.run();
      }
    });
  }


  /**
   * Creates the graphic node for a given realm and name.<br>
   * Throws IllegalArgumentException if no such icon and/or realm.
   *
   * @param realm the realm, null of empty if default tentackle realm
   * @param name the graphic name
   * @return the graphic node
   */
  public static Node createGraphic(String realm, String name) {
    return FxFactory.getInstance().createGraphic(realm, name);
  }

  /**
   * Creates the graphic node for a given name and the default realm.<br>
   * Throws IllegalArgumentException if no such icon.
   *
   * @param name the graphic name
   * @return the graphic node
   */
  public static Node createGraphic(String name) {
    return createGraphic("", name);
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


  /**
   * Terminates the FX client.
   */
  public static void terminate() {
    FxUtilities.getInstance().terminate();
  }


  private Fx() {
    // no instances
  }
}
