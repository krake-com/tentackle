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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.BuilderFactory;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.fx.table.DefaultTableConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.reflect.DefaultClassMapper;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Default implementation of an fx factory.
 *
 * @author harald
 */
@Service(FxFactory.class)
public class DefaultFxFactory implements FxFactory {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFxFactory.class);


  /**
   * The builder factory.
   */
  private final BuilderFactory builderFactory;

  /**
   * All configurators mapped by the classname served.
   */
  private final Map<String, Configurator<?>> configurators;

  /**
   * Map of view's class to classmapper of value-translators.
   */
  private final Map<Class<?>, DefaultClassMapper> viewTranslatorMap;

  /**
   * Map of FX control class to evaluated configurator.
   */
  private final Map<Class<?>, Optional<Configurator<?>>> fxToConfiguratorMap;

  /**
   * all FX controller classes.
   */
  private final Collection<Class<FxController>> controllerClasses;

  /**
   * FX controller singletons.
   */
  private final Map<Class<? extends FxController>, FxController> controllers;

  /**
   * Map of realm to image providers.
   */
  private final Map<String, ImageProvider> imageProviders;



  /**
   * Creates the default factory.
   */
  public DefaultFxFactory() {

    builderFactory = createBuilderFactory();

    try {
      controllerClasses = ServiceFactory.getServiceFinder().findServiceProviders(FxController.class);
    }
    catch (ClassNotFoundException ex) {
      throw new FxRuntimeException("loading FX controller classes failed", ex);
    }
    controllers = new HashMap<>();

    configurators = new HashMap<>();
    Map<String,String> serviceMap = ServiceFactory.getServiceFinder().createNameMap(Configurator.class.getName());
    for (Map.Entry<String,String> entry: serviceMap.entrySet()) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends Configurator<?>> configuratorClass = (Class<? extends Configurator<?>>) Class.forName(entry.getValue());
        configurators.put(entry.getKey(), configuratorClass.newInstance());
      }
      catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
        throw new FxRuntimeException(ex);
      }
    }
    fxToConfiguratorMap = new HashMap<>();

    imageProviders = new HashMap<>();
    try {
      for (Class<ImageProvider> clazz: ServiceFactory.getServiceFinder().findServiceProviders(ImageProvider.class)) {
        ImageProviderService svc = clazz.getAnnotation(ImageProviderService.class);
        if (svc != null) {
          imageProviders.putIfAbsent(svc.value(), clazz.newInstance());
        }
        else {
          LOGGER.severe("{0} not annotated with @ImageProviderService", clazz);
        }
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
      throw new FxRuntimeException("loading image providers failed", ex);
    }

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
      Class<?> viewClass = entry.getKey().getViewClass();
      DefaultClassMapper translators = viewTranslatorMap.get(viewClass);
      if (translators == null) {
        translators = new DefaultClassMapper(ReflectionHelper.getClassBaseName(viewClass) + "-translator", new HashMap<>(), null);
        viewTranslatorMap.put(viewClass, translators);
      }
      translators.getNameMap().put(entry.getKey().getModelClass().getName(), entry.getValue().getName());
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
      configurator = optional.isPresent() ? optional.get() : null;
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
  public synchronized <T extends FxController> T createController(Class<T> controllerClass) {

    @SuppressWarnings("unchecked")
    T controller = (T) controllers.get(controllerClass);
    if (controller != null) {
      if (controller.getView().isVisible()) {
        throw new FxRuntimeException(controllerClass + " is a singleton and already visible");
      }
      return controller;
    }

    URL url = null;
    ResourceBundle resources = null;
    String basename = ReflectionHelper.getClassBaseName(controllerClass);

    FxControllerService service = controllerClass.getAnnotation(FxControllerService.class);
    if (service == null) {
      throw new FxRuntimeException("missing annotation @FxControllerService for controller " + controllerClass.getName());
    }

    boolean singleton = service.caching() != FxControllerService.CACHING.NO;

    String urlStr = service.url();
    if (!urlStr.isEmpty()) {
      url = controllerClass.getResource(urlStr);
      if (url == null) {
        throw new FxRuntimeException("no such URL '" + urlStr +
                                     "' -> check @FxControllerService of " + controllerClass.getName());
      }
    }

    String resourcesStr = service.resources();
    if (resourcesStr.isEmpty()) {
      resourcesStr = controllerClass.getName();
    }

    if (!FxControllerService.RESOURCES_NONE.equals(resourcesStr)) {
      resources = ResourceBundle.getBundle(resourcesStr, LocaleProvider.getInstance().getLocale());
    }

    if (url == null) {
      urlStr = basename + ".fxml";
      url = controllerClass.getResource(urlStr);
      if (url == null) {
        throw new FxRuntimeException("no such default URL '" + urlStr + "'");
      }
    }

    FXMLLoader loader = new FXMLLoader(url, resources, getBuilderFactory());
    try {
      Parent view = loader.load();
      controller = loader.getController();
      controller.setView(view);
      if (view instanceof FxContainer) {
        ((FxContainer) view).setController(controller);
      }

      if (singleton) {
        controllers.put(controllerClass, controller);
        LOGGER.info("controller {0} added to singletons", ReflectionHelper.getClassBaseName(controllerClass));
      }

      String cssName = service.css();
      if (cssName.isEmpty()) {
        cssName = ReflectionHelper.getClassBaseName(controllerClass) + ".css";
      }
      URL cssUrl = controllerClass.getResource(cssName);
      if (cssUrl != null) {
        view.getStylesheets().add(cssUrl.toExternalForm());
      }

      switch (service.binding()) {

        case YES:
          controller.getBinder().bind();
          break;

        case COMPONENT_INHERITED:
          controller.getBinder().bindWithInheritedComponents();
          break;

        case BINDABLE_INHERITED:
          controller.getBinder().bindWithInheritedBindables();
          break;

        case ALL_INHERITED:
          controller.getBinder().bindAllInherited();
          break;

        default:
          // NO

      }

      controller.configure();

      return controller;
    }
    catch (IOException iox) {
      throw new FxRuntimeException("loading controller " + controllerClass + " failed", iox);
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
  public void preloadControllers() {
    Collection<Class<FxController>> preloadClasses = new ArrayList<>();
    for (Class<FxController> clazz: getControllerClasses()) {
      FxControllerService service = clazz.getAnnotation(FxControllerService.class);   // cannot be null!
      if (service.caching() == FxControllerService.CACHING.PRELOAD) {
        preloadClasses.add(clazz);
      }
    }
    javafx.concurrent.Service<Void> preloadSvc = new javafx.concurrent.Service<Void>() {

      @Override
      protected Task<Void> createTask() {
        return new Task<Void>() {

          @Override
          protected Void call() throws Exception {
            for (Class<FxController> clazz: preloadClasses) {
              createController(clazz);
            }
            return null;
          }
        };
      }
    };

    preloadSvc.start();
  }

  @Override
  public Image getImage(String realm, String name) {
    if (realm == null) {
      realm = "";
    }
    ImageProvider provider = imageProviders.get(realm);
    if (provider == null) {
      throw new IllegalArgumentException("no image provider for realm '" + realm + "'");
    }
    return provider.getImage(name);
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
