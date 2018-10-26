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

import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.BuilderFactory;

import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.table.TableConfiguration;

import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;


interface FxFactoryHolder {
  FxFactory INSTANCE = ServiceFactory.createService(FxFactory.class);
}

/**
 * A factory for FX-related stuff.
 *
 * @author harald
 */
public interface FxFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static FxFactory getInstance() {
    return FxFactoryHolder.INSTANCE;
  }


  /**
   * Gets all classes annotated with {@link FxControllerService}.
   *
   * @return the classes
   */
  Collection<Class<FxController>> getControllerClasses();


  /**
   * Loads all controllers marked with {@link FxControllerService.CACHING#PRELOAD}.
   */
  void preloadControllers();


  /**
   * Gets the builder factory.
   *
   * @return the FX builder factory
   */
  BuilderFactory getBuilderFactory();


  /**
   * Gets the configurator for a given class.
   *
   * @param <T> the node type
   * @param clazz the class
   * @return the configurator, null if none
   */
  <T> Configurator<T> getConfigurator(Class<T> clazz);


  /**
   * Creates a value translator.
   *
   * @param <M> the model type
   * @param <V> the view type
   * @param modelClass the model's value class
   * @param viewClass the view's value class
   * @param component the fx component
   * @return the value translator best fitting for the requested types
   */
  <M,V> ValueTranslator<V,M> createValueTranslator(Class<M> modelClass, Class<V> viewClass, FxComponent component);


  /**
   * Creates a configured stage.
   *
   * @param stageStyle the style
   * @param modality the modality
   * @return the stage
   */
  Stage createStage(StageStyle stageStyle, Modality modality);


  /**
   * Creates a controller together with its FXML-based view.<br>
   * The controller class must be annotated with {@link FxControllerService}.
   *
   * @param <T> the controller type
   * @param controllerClass the controller class
   * @param fxmlUrl the URL to load the FXML from, null if derived from controllerClass or FxController annotation
   * @param resources the resource bundle, null if derived from controllerClass or FxController annotation
   * @param cssUrl the URL to load the CSS from, null if derived from controllerClass or FxController annotation
   * @return the initialized controller
   */
  <T extends FxController> T createController(Class<T> controllerClass, URL fxmlUrl, ResourceBundle resources, URL cssUrl);


  /**
   * Gets the given image.<br>
   * Throws IllegalArgumentException if no such image and/or realm.
   *
   * @param realm the realm, null of empty if tentackle images
   * @param name the image name
   * @return the image
   */
  Image getImage(String realm, String name);


  /**
   * Creates an empty configuration from a template object.
   *
   * @param <S> the type of the objects contained within the table's items list
   * @param template a template object
   * @param name the table's name, null if basename from effective class of template
   * @return the table configuration
   */
  <S> TableConfiguration<S> createTableConfiguration(S template, String name);

  /**
   * Creates an empty configuration for a PDO class.
   *
   * @param <S> the type of the objects contained within the table's items list
   * @param objectClass the object class
   * @param name the table's name, null if basename of objectclass
   * @return the table configuration
   */
  <S> TableConfiguration<S> createTableConfiguration(Class<S> objectClass, String name);

}
