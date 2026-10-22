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

import org.tentackle.fx.bind.FxBindingFactory;
import org.tentackle.fx.bind.FxComponentBinder;
import org.tentackle.log.Logger;
import org.tentackle.misc.ImmutableArrayList;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.MandatoryScope;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.stage.Stage;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Base class for tentackle fx controllers.
 *
 * @author harald
 */
public abstract class AbstractFxController implements FxController, ScopeConfigurator {

  private static final Logger LOGGER = Logger.get(AbstractFxController.class);

  private Parent view;                                // the view managed by this controller
  private FxContainer container;                      // the container
  private FxComponentBinder binder;                   // the binder for this container
  private ImmutableArrayList<Field> fxmlFields;       // fields annotated with @FXML
  private ImmutableArrayList<Method> fxmlMethods;     // methods annotated with @FXML


  /**
   * Parent constructor.
   */
  public AbstractFxController() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Parent getView() {
    return view;
  }

  @Override
  public void setView(Parent view) {
    this.view = view;
    if (view instanceof FxContainer) {
      this.container = (FxContainer) view;
    }
    else {
      this.container = null;
    }
  }

  @Override
  public Stage getStage() {
    return FxUtilities.getInstance().getStage(getView());
  }

  @Override
  public FxContainer getContainer() {
    return container;
  }

  @Override
  public List<Field> getFXMLFields() {
    if (fxmlFields == null) {
      fxmlFields = new ImmutableArrayList<>();
      for (Field field: getClass().getDeclaredFields()) {
        if (field.isAnnotationPresent(FXML.class)) {
          fxmlFields.add(field);
          LOGGER.fine("added {0} to @FXML-fields", field);
        }
      }
      fxmlFields.setImmutable(true);
    }
    return fxmlFields;
  }

  @Override
  public List<Method> getFXMLMethods() {
    if (fxmlMethods == null) {
      fxmlMethods = new ImmutableArrayList<>();
      for (Method method: getClass().getDeclaredMethods()) {
        if (method.isAnnotationPresent(FXML.class)) {
          fxmlMethods.add(method);
          LOGGER.fine("added {0} to @FXML-methods", method);
        }
      }
      fxmlMethods.setImmutable(true);
    }
    return fxmlMethods;
  }

  @Override
  public void validateInjections() {
    for (Field field: getFXMLFields()) {
      try {
        Object comp;
        try {
          comp = field.get(this);
        }
        catch (IllegalAccessException ex) {
          field.setAccessible(true);
          comp = field.get(this);
        }
        if (comp == null) {
          throw new FxRuntimeException(field + " annotated with @FXML was not injected");
        }
      }
      catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException ex) {
        throw new FxRuntimeException("cannot verify " + field, ex);
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * For bindables defined within this controller, the default scopes are:<br>
   * {@link MandatoryScope} and {@link ChangeableScope}.
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    return new Class[] { MandatoryScope.class, ChangeableScope.class };
  }


  /**
   * Creates a binder for this controller.<br>
   * The default implementation invokes
   * {@code FxBindingFactory.getInstance().createComponentBinder(this)}.
   *
   * @return the binder
   */
  protected FxComponentBinder createBinder() {
    return FxBindingFactory.getInstance().createComponentBinder(this);
  }


  @Override
  public FxComponentBinder getBinder() {
    if (binder == null) {
      binder = createBinder();
    }
    return binder;
  }

  @Override
  public void configure() {
    // the default does nothing
  }

}
