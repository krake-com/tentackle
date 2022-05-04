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
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.BuilderFactory;

import org.tentackle.common.BundleFactory;
import org.tentackle.common.Constants;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.log.Logger;
import org.tentackle.reflect.DefaultClassMapper;
import org.tentackle.reflect.ReflectionHelper;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Default implementation of {@link FxFactory}.
 *
 * @author harald
 */
@Service(FxFactory.class)
public class DefaultFxFactory implements FxFactory {

  private static final Logger LOGGER = Logger.get(DefaultFxFactory.class);


  /**
   * The builder factory.
   */
  private final BuilderFactory builderFactory;

  /**
   * All configurators mapped by the classname served.
   */
  private final Map<String, Configurator<?>> configurators;

  /**
   * Map of view's class to class mapper of value-translators.
   */
  private final Map<Class<?>, DefaultClassMapper> viewTranslatorMap;

  /**
   * Map of FX control class to evaluated configurator.
   */
  private final Map<Class<?>, Optional<Configurator<?>>> fxToConfiguratorMap;   // Optional to denote: not present, null if not determined yet

  /**
   * all FX controller classes.
   */
  private final Collection<Class<FxController>> controllerClasses;

  /**
   * Map of realm to graphic providers.
   */
  private final Map<String, GraphicProvider> graphicProviders;



  /**
   * Creates the default factory.
   */
  @SuppressWarnings("rawtypes")
  public DefaultFxFactory() {

    builderFactory = createBuilderFactory();

    try {
      controllerClasses = ServiceFactory.getServiceFinder().findServiceProviders(FxController.class);
    }
    catch (ClassNotFoundException ex) {
      throw new FxRuntimeException("loading FX controller classes failed", ex);
    }

    configurators = new HashMap<>();
    Map<String,String> serviceMap = ServiceFactory.getServiceFinder().createNameMap(Configurator.class.getName());
    for (Map.Entry<String,String> entry: serviceMap.entrySet()) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends Configurator<?>> configuratorClass = (Class<? extends Configurator<?>>) Class.forName(entry.getValue());
        configurators.put(entry.getKey(), configuratorClass.getDeclaredConstructor().newInstance());
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
             InvocationTargetException | NoSuchMethodException ex) {
        throw new FxRuntimeException(ex);
      }
    }
    fxToConfiguratorMap = new HashMap<>();

    graphicProviders = new HashMap<>();
    try {
      for (Class<GraphicProvider> clazz: ServiceFactory.getServiceFinder().findServiceProviders(GraphicProvider.class)) {
        GraphicProviderService svc = clazz.getAnnotation(GraphicProviderService.class);
        if (svc != null) {
          graphicProviders.putIfAbsent(svc.value(), clazz.getDeclaredConstructor().newInstance());
        }
        else {
          LOGGER.severe("{0} not annotated with @GraphicProviderService", clazz);
        }
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
           InvocationTargetException | NoSuchMethodException ex) {
      throw new FxRuntimeException("loading image providers failed", ex);
    }

    record ValueTranslatorKey<M, V>(Class<M> modelClass, Class<V> viewClass) {}

    Map<ValueTranslatorKey<?,?>, Class<ValueTranslator>> translatorMap = new HashMap<>();
    try {
      for (Class<ValueTranslator> clazz: ServiceFactory.getServiceFinder().findServiceProviders(ValueTranslator.class)) {
        ValueTranslatorService svc = clazz.getAnnotation(ValueTranslatorService.class);
        if (svc != null) {
          ValueTranslatorKey<?,?> key = new ValueTranslatorKey<>(svc.modelClass(), svc.viewClass());
          translatorMap.putIfAbsent(key, clazz);
        }
        else {
          LOGGER.severe("{0} not annotated with @ValueTranslatorService", clazz);
        }
      }
    }
    catch (ClassNotFoundException ex) {
      throw new FxRuntimeException("loading value translators failed", ex);
    }
    viewTranslatorMap = new HashMap<>();
    for (Map.Entry<ValueTranslatorKey<?,?>, Class<ValueTranslator>> entry: translatorMap.entrySet()) {
      Class<?> viewClass = entry.getKey().viewClass();
      DefaultClassMapper translators = viewTranslatorMap.computeIfAbsent(viewClass, k ->
              new DefaultClassMapper(
                      viewClass.getSimpleName() + "-translator",
                      ServiceFactory.getClassLoader(Constants.DEFAULT_SERVICE_PATH, ValueTranslator.class.getName()),
                      new HashMap<>(), null));
      translators.getNameMap().put(entry.getKey().modelClass().getName(), entry.getValue().getName());
    }

  }



  @Override
  public BuilderFactory getBuilderFactory() {
    return builderFactory;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Configurator<T> getConfigurator(Class<T> clazz) {
    Configurator<?> configurator = null;
    Optional<Configurator<?>> optional = fxToConfiguratorMap.get(clazz);
    if (optional != null) {
      configurator = optional.orElse(null);
    }
    else {
      Class<?> cls = clazz;
      while (cls != Object.class) {
        configurator = configurators.get(cls.getName());
        if (configurator != null) {
          break;
        }
        cls = cls.getSuperclass();
      }
      fxToConfiguratorMap.put(clazz, Optional.ofNullable(configurator));
    }
    return (Configurator<T>) configurator;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <M, V> ValueTranslator<V, M> createValueTranslator(Class<M> modelClass, Class<V> viewClass, FxComponent component) {
    if (modelClass.isPrimitive()) {
      modelClass = (Class<M>) ReflectionHelper.primitiveToWrapperClass(modelClass);
    }
    DefaultClassMapper mapper = viewTranslatorMap.get(viewClass);
    if (mapper == null) {
      throw new FxRuntimeException("no value translators for view " + viewClass);
    }
    try {
      Class<ValueTranslator<M,V>> clazz = (Class<ValueTranslator<M,V>>) mapper.mapLenient(modelClass);
      for (Constructor<?> cons: clazz.getConstructors()) {
        if (cons.getParameterCount() == 1 &&
            FxComponent.class.isAssignableFrom(cons.getParameters()[0].getType())) {
          return (ValueTranslator<V, M>) cons.newInstance(component);
        }
        if (cons.getParameterCount() == 2 &&
            FxComponent.class.isAssignableFrom(cons.getParameters()[0].getType()) &&
            cons.getParameters()[1].getType() == Class.class) {
          return (ValueTranslator<V, M>) cons.newInstance(component, modelClass);
        }
      }
      throw new ClassNotFoundException("no matching constructor found for " + clazz);
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new FxRuntimeException("could not create value translator for view " + viewClass + " to model " + modelClass, ex);
    }
  }

  @Override
  public Stage createStage(StageStyle stageStyle, Modality modality) {
    Stage stage = new Stage(stageStyle);
    stage.initModality(modality);
    getConfigurator(Window.class).configure(stage);
    return stage;
  }

  @Override
  public Scene createScene(Parent root) {
    Scene scene = new Scene(root);
    FxUtilities.getInstance().applyStylesheets(scene);
    return scene;
  }

  @Override
  public Alert createAlert(Alert.AlertType alertType) {
    Alert alert = new Alert(alertType);
    FxUtilities.getInstance().applyStylesheets(alert.getDialogPane().getScene());
    return alert;
  }

  @Override
  public synchronized <T extends FxController> T createController(
          Class<T> controllerClass, URL fxmlUrl, ResourceBundle resources, URL cssUrl) {

    FxControllerService service = null;
    Class<? super T> annotatedClass = Objects.requireNonNull(controllerClass, "controllerClass");
    while (annotatedClass != null) {
      service = annotatedClass.getDeclaredAnnotation(FxControllerService.class);
      if (service != null) {
        break;
      }
      annotatedClass = annotatedClass.getSuperclass();
    }

    if (service == null) {
      throw new FxRuntimeException("missing annotation @FxControllerService for controller " + controllerClass.getName());
    }

    if (fxmlUrl == null) {
      String urlStr = service.url();
      if (!FxControllerService.FXML_NONE.equals(urlStr)) {
        if (!urlStr.isEmpty()) {
          fxmlUrl = annotatedClass.getResource(urlStr);
          if (fxmlUrl == null) {
            throw new FxRuntimeException("no such URL '" + urlStr +
                                         "' -> check @FxControllerService of " + annotatedClass.getName());
          }
        }
        else {
          urlStr = annotatedClass.getSimpleName() + ".fxml";
          fxmlUrl = annotatedClass.getResource(urlStr);
          if (fxmlUrl == null) {
            throw new FxRuntimeException("no such default URL '" + urlStr + "'");
          }
        }
      }
    }

    if (resources == null) {
      String resourcesStr = service.resources();
      if (resourcesStr.isEmpty()) {
        resourcesStr = annotatedClass.getName();
      }

      if (!FxControllerService.RESOURCES_NONE.equals(resourcesStr)) {
        resources = BundleFactory.getBundle(resourcesStr, LocaleProvider.getInstance().getLocale());
      }
    }

    T controller;

    try {
      Parent view;
      if (fxmlUrl == null) {
        controller = resources == null ?
                     controllerClass.getConstructor().newInstance() :
                     controllerClass.getConstructor(ResourceBundle.class).newInstance(resources);
        view = controller.getView();
      }
      else {
        FXMLLoader loader = new FXMLLoader(fxmlUrl, resources, getBuilderFactory());
        if (annotatedClass != controllerClass) {
          loader.setControllerFactory(cls -> {
            try {
              return controllerClass.getConstructor().newInstance();
            }
            catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException cx) {
              throw new FxRuntimeException("cannot create controller for " + controllerClass, cx);
            }
          });
        }
        view = loader.load();
        controller = loader.getController();
        controller.setView(view);
      }

      if (view instanceof FxContainer) {
        ((FxContainer) view).setController(controller);
      }

      if (view.getId() == null) {
        // set the FX-ID to allow CSS styling
        view.setId(controller.getClass().getSimpleName());
      }

      if (cssUrl == null) {
        String cssName = service.css();
        if (cssName.isEmpty()) {
          cssName = controllerClass.getSimpleName() + ".css";
        }
        cssUrl = controllerClass.getResource(cssName);
      }
      if (cssUrl != null) {
        view.getStylesheets().add(cssUrl.toExternalForm());
      }

      switch (service.binding()) {
        case YES -> controller.getBinder().bind();
        case COMPONENT_INHERITED -> controller.getBinder().bindWithInheritedComponents();
        case BINDABLE_INHERITED -> controller.getBinder().bindWithInheritedBindables();
        case ALL_INHERITED -> controller.getBinder().bindAllInherited();
      }

      controller.configure();

      return controller;
    }
    catch (IOException iox) {
      throw new FxRuntimeException("loading controller " + controllerClass + " failed", iox);
    }
    catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
      throw new FxRuntimeException("creating controller instance " + controllerClass + " failed", ex);
    }
    catch (Throwable t) {
      LOGGER.severe("loading controller failed", t);
      throw t;
    }
  }

  @Override
  public Collection<Class<FxController>> getControllerClasses() {
    return controllerClasses;
  }

  @Override
  public Node createGraphic(String realm, String name) {
    if (realm == null) {
      realm = "";
    }
    GraphicProvider provider = graphicProviders.get(realm);
    if (provider == null) {
      throw new IllegalArgumentException("no graphic provider for realm '" + realm + "'");
    }
    return provider.createGraphic(name);
  }

  @Override
  public <S> TableConfiguration<S> createTableConfiguration(S template, String name) {
    return new DefaultTableConfiguration<>(template, name);
  }

  @Override
  public <S> TableConfiguration<S> createTableConfiguration(Class<S> objectClass, String name) {
    return new DefaultTableConfiguration<>(objectClass, name);
  }

  /**
   * Creates the builder factory.
   *
   * @return the factory
   */
  protected BuilderFactory createBuilderFactory() {
    return new FxBuilderFactory();
  }

}
