/*
 * Tentackle - http://www.tentackle.org
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
 */

package org.tentackle.fx.rdc.translate;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import org.tentackle.bind.Bindable;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.rdc.RdcUtilities;
import org.tentackle.fx.translate.ValueStringTranslator;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainKey;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * PDO to String translator.
 *
 * TODO: DnD support
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = PersistentDomainObject.class, viewClass = String.class)
public class PdoStringTranslator<T extends PersistentDomainObject<T>> extends ValueStringTranslator<T> {

  private static final Logger LOGGER = Logger.get(PdoStringTranslator.class);


  private ValueStringTranslator<?> udkTranslator;
  private String viewValue;
  private T pdo;
  private boolean inPdoSearch;


  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public PdoStringTranslator(FxTextComponent component) {
    super(component);
    // figure out the correct translator for the UDK
    @SuppressWarnings("unchecked")
    Class<T> pdoClass = (Class<T>) getComponent().getType();
    try {
      T proxy = Pdo.create(pdoClass);
      Class<?> udkClass = proxy.getUniqueDomainKeyType();
      udkTranslator = (ValueStringTranslator<?>) FxFactory.getInstance().createValueTranslator(udkClass, String.class, component);
      // allow incomplete input to preset the search criteria in the finder if possible
      udkTranslator.setLenient(true);

      // apply binding options defined for the UDK in the PDO, if provided
      Bindable bindable = getBindableAnnotation(proxy);
      if (bindable != null) {
        FxUtilities.getInstance().applyBindingOptions(component, component.getBinding().getMember(), bindable.options());
      }
    }
    catch (RuntimeException rex) {
      // no translator found, try default (see below)
    }

    // register function keys:
    // F2 = edit/view current PDO
    // F3 = invoke search
    Node node = (Node) getComponent();
    node.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
              !event.isShiftDown() && !event.isShortcutDown()) {
        if (event.getCode() == KeyCode.F2) {
          event.consume();
          if (pdo != null) {
            ObservableList<T> pdoList = FXCollections.observableArrayList();    // pdo list will disable new,delete and find buttons!
            pdoList.add(pdo);
            pdo = RdcUtilities.getInstance().displayCrudStage(
                    pdo, pdoList, !pdo.isImmutable(),
                    Modality.APPLICATION_MODAL, Fx.getStage(node), null);
          }
        }
        else if (event.getCode() == KeyCode.F3) {
          event.consume();
          inPdoSearch = true;
          T found = searchPdo(createPdo());
          inPdoSearch = false;
          if (found != null) {
            pdo = found;
            component.setViewValue(pdo);
            component.updateModel();
          }
        }
      }
    });
  }

  @Override
  @SuppressWarnings("unchecked")
  public Function<T, String> toViewFunction() {
    return m -> {
      if (m != null) {
        if (udkTranslator != null) {
          viewValue = (String) ((Function) udkTranslator.toViewFunction()).apply(m.getUniqueDomainKey());
        }
        else {
          viewValue = m.toString();
        }
      }
      else {
        viewValue = null;
      }
      pdo = m;
      return viewValue;
    };
  }

  @Override
  public Function<String, T> toModelFunction() {
    return v -> {
      if (!inPdoSearch && !Objects.equals(v, viewValue)) {
        // view changed
        if (v == null) {
          pdo = null;
        }
        else {
          T proxy = createPdo();
          Class<?> udkClass = proxy.getUniqueDomainKeyType();
          if (udkTranslator != null) {
            Object udk = udkTranslator.toModelFunction().apply(v);
            pdo = proxy.findByUniqueDomainKey(udk);
            if (pdo == null) {
              // no such pdo: try interactive search
              proxy.setUniqueDomainKey(udk);
              pdo = searchPdo(proxy);
              Platform.runLater(() -> ((Node) getComponent()).requestFocus());
            }
          }
          else {
            // default: no udk translator found
            Object udk = v;
            try {
              if (!udkClass.isAssignableFrom(v.getClass())) {
                // needs conversion
                try {
                  Constructor<?> cons = udkClass.getConstructor(v.getClass());
                  udk = cons.newInstance(v);
                }
                catch (InvocationTargetException ix) {
                  // construction failed.
                  // this usually means that the input is malformed, incomplete, whatever.
                  // in such cases get the PDO from the search dialog
                  presetSearchCriteria(proxy, udkClass, v);    // try to preset the proxy, if possible
                  pdo = searchPdo(proxy);
                  Platform.runLater(() -> ((Node) getComponent()).requestFocus());
                  return pdo;
                }
                catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                        IllegalArgumentException e) {
                  throw new FxRuntimeException("could not create domain key: " + udkClass.getName() +
                                               "(" + v.getClass().getName() + ")", e);
                }
              }
              pdo = proxy.findByUniqueDomainKey(udk);
              if (pdo == null) {
                // no such pdo: try search
                presetSearchCriteria(proxy, udkClass, v);    // try to preset the proxy, if possible
                pdo = searchPdo(proxy);
                Platform.runLater(() -> ((Node) getComponent()).requestFocus());
              }
            }
            catch (RuntimeException rex) {
              LOGGER.warning("loading PDO " + proxy.getClassBaseName() + " for " +
                             udkClass + "='" + udk + "' failed", rex);
              throw rex;
            }
          }
        }
      }
      return pdo;
    };
  }



  /**
   * Creates a new PDO.
   *
   * @return the pdo
   */
  protected T createPdo() {
    @SuppressWarnings("unchecked")
    Class<T> pdoClass = (Class<T>) getComponent().getType();
    DomainContext context = getComponent().getBinding().getBinder().getBindingProperty(DomainContext.class);
    if (context == null) {
      throw new FxRuntimeException("missing binding property for DomainContext");
    }
    return Pdo.create(pdoClass, context);
  }


  /**
   * Modal search of the pdo.
   *
   * @param proxy the proxy pdo (possibly presetted with partial search criteria)
   * @return the selected pdo, null if none
   */
  protected T searchPdo(T proxy) {
    ObservableList<T> list = RdcUtilities.getInstance().displaySearchStage(
            proxy, Modality.APPLICATION_MODAL, Fx.getStage((Node) getComponent()), true);
    if (!list.isEmpty()) {
      return list.get(0);
    }
    return null;
  }


  /**
   * Preset the given proxy with the string from the component.
   *
   * @param proxy the proxy pdo
   * @param udkClass the class of the unique domain key
   * @param v the view value
   */
  protected void presetSearchCriteria(T proxy, Class<?> udkClass, String v) {
    try {
      /**
       * Check if there is a factory method:
       *
       * <UdkClass>.lenientValueOf(String)
       *
       * If so, create a udk and set it into the proxy.
       */
      Method m = udkClass.getDeclaredMethod("lenientValueOf", String.class);
      if (Modifier.isStatic(m.getModifiers()) && udkClass.isAssignableFrom(m.getReturnType())) {
        Object udk = m.invoke(null, v);
        if (udk != null) {
          proxy.setUniqueDomainKey(udk);
        }
      }
    }
    catch (SecurityException | IllegalAccessException | IllegalArgumentException |
            InvocationTargetException e1) {
      throw new FxRuntimeException("cannot create lenient domain key", e1);
    }
    catch (NoSuchMethodException nm) {
      // no such method -> do nothing
    }
  }


  /**
   * Gets the {@link Bindable} annotation of the unique domain key.
   * <p>
   * If the UDK consists of more than one member, the first Bindable annotation is returned.
   *
   * @param proxy the PDO class to inspect
   * @return the annotation, null if not annotated with Bindable
   */
  protected Bindable getBindableAnnotation(T proxy) {
    for (Method method: proxy.getEffectiveClass().getMethods()) {
      if (method.isAnnotationPresent(DomainKey.class)) {
        Bindable bindable = method.getAnnotation(Bindable.class);
        if (bindable != null) {
          return bindable;
        }
      }
    }
    return null;
  }

}
