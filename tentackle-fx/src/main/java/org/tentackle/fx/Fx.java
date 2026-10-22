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

import org.tentackle.fx.table.edit.ColumnWiseEditHandler;
import org.tentackle.fx.table.edit.RowWiseEditHandler;
import org.tentackle.fx.table.edit.RuleBasedEditHandler;
import org.tentackle.fx.table.edit.SimpleEditHandler;

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
 * Frequently used factory and helper methods of the FX layer.<br>
 * A static facade for {@link FxFactory} and {@link FxUtilities}.
 *
 * @author harald
 */
public class Fx {

  /**
   * Loads an object hierarchy from an FXML document.<br>
   * Uses the tentackle builder factory, so that the tentackle implementations of the nodes are created.
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
   * Loads an object hierarchy from an FXML document.<br>
   * Uses the tentackle builder factory, so that the tentackle implementations of the nodes are created.
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
   * Loads a controller with its view.
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
   * @param owner the owner window or a node of it, null if none
   * @return the stage
   */
  public static Stage createStage(StageStyle stageStyle, Modality modality, Object owner) {
    return FxFactory.getInstance().createStage(stageStyle, modality, owner);
  }

  /**
   * Creates a new decorated stage.<br>
   * Tentackle applications should use this factory method instead of <code>new Stage()</code>
   * because the stage will be configured to meet certain framework-wide conventions.
   *
   * @param modality the modality
   * @param owner the owner window or a node of it, null if none
   * @return the stage
   */
  public static Stage createStage(Modality modality, Object owner) {
    return createStage(StageStyle.DECORATED, modality, owner);
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
   * FX objects should be created via the builder factory and not
   * the traditional way by invoking a constructor.
   * Only the builder factory guarantees TT-compliant instances.
   * If there is no builder for the type, the object is created via its no-arg constructor
   * and configured by the {@link Configurator} registered for the type, if any.
   * <p>
   * Throws {@link FxRuntimeException} if there is no builder for this type and
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
      throw new FxRuntimeException("cannot create " + clazz.getName(), e);
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
   * Creates the graphic node for a given realm and name.
   *
   * @param realm the realm, null or empty for the framework's default realm
   * @param name the graphic name
   * @return the graphic node
   * @throws IllegalArgumentException if there is no graphic provider for the realm
   * @throws FxRuntimeException if the provider has no such graphic
   */
  public static Node createGraphic(String realm, String name) {
    return FxFactory.getInstance().createGraphic(realm, name);
  }

  /**
   * Creates the graphic node for a given name and the default realm.
   *
   * @param name the graphic name
   * @return the graphic node
   * @throws FxRuntimeException if there is no such graphic
   */
  public static Node createGraphic(String name) {
    return createGraphic("", name);
  }

  /**
   * Creates a table edit handler that ends editing when a value is committed.
   *
   * @param <S> type of the objects contained within the table's item list
   * @return the handler
   */
  public static <S> SimpleEditHandler<S> simpleTableEditHandler() {
    return FxFactory.getInstance().simpleTableEditHandler();
  }

  /**
   * Creates a table edit handler that continues with the next or previous editable cell in the row,
   * wrapping to the next or previous row.
   *
   * @param <S> type of the objects contained within the table's item list
   * @return the handler
   */
  public static <S> RowWiseEditHandler<S> rowWiseTableEditHandler() {
    return FxFactory.getInstance().rowWiseTableEditHandler();
  }

  /**
   * Creates a table edit handler that continues with the editable cell below or above in the same column.
   *
   * @param <S> type of the objects contained within the table's item list
   * @return the handler
   */
  public static <S> ColumnWiseEditHandler<S> columnWiseTableEditHandler() {
    return FxFactory.getInstance().columnWiseTableEditHandler();
  }

  /**
   * Creates a table edit handler that is configured by rules per column and navigates row-wise.
   *
   * @param <S> type of the objects contained within the table's item list
   * @return the handler
   */
  public static <S> RuleBasedEditHandler<S> ruleBasedTableEditHandler() {
    return FxFactory.getInstance().ruleBasedTableEditHandler();
  }


  /**
   * Gets the stage for a node.
   *
   * @param node the node
   * @return the stage, null if the node does not belong to a scene or the scene does not belong to a stage
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
   * Copies a text to the clipboard.
   *
   * @param text the message to copy
   */
  public static void copyToClipboard(String text) {
    FxUtilities.getInstance().copyToClipboard(text);
  }


  /**
   * Terminates the FX client.
   *
   * @see FxUtilities#terminate()
   */
  public static void terminate() {
    FxUtilities.getInstance().terminate();
  }


  private Fx() {
    // no instances
  }
}
