/*
 * Tentackle - https://tentackle.org
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

import org.tentackle.bind.Bindable;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.translate.ValueStringTranslator;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainKey;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.application.Platform;
import javafx.scene.Node;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Function;

/**
 * PDO to String translator.
 *
 * @author harald
 * @param <T> the PDO type
 */
@ValueTranslatorService(modelClass = PersistentDomainObject.class, viewClass = String.class)
public class PdoStringTranslator<T extends PersistentDomainObject<T>> extends ValueStringTranslator<T>
       implements PdoTranslator<T, String > {

  private static final Logger LOGGER = Logger.get(PdoStringTranslator.class);

  private PdoComponentAddon<T> pdoAddon;
  private ValueStringTranslator<?> udkTranslator;
  private String viewValue;

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
      // create translator for the unique domain key and configure the component (TextFormatter, for ex.)
      udkTranslator = (ValueStringTranslator<?>) FxFactory.getInstance()
                                                          .createValueTranslator(udkClass, String.class, component);
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

    setPdoAddon(createPdoAddon());
  }

  @Override
  public PdoComponentAddon<T> getPdoAddon() {
    return pdoAddon;
  }

  /**
   * Sets the PDO addon.
   *
   * @param pdoAddon the addon
   */
  public void setPdoAddon(PdoComponentAddon<T> pdoAddon) {
    this.pdoAddon = pdoAddon;
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public Function<T, String> toViewFunction() {
    return m -> {
      if (m != null) {
        Object udk = m.getUniqueDomainKey();
        if (udkTranslator != null) {
          viewValue = (String) ((Function) udkTranslator.toViewFunction()).apply(udk);
        }
        else {
          viewValue = udk.toString();
        }
      }
      else {
        if (udkTranslator != null) {
          // invoke the udk translator, even if null, to allow detecting the to-view action
          viewValue = udkTranslator.toViewFunction().apply(null);
        }
        else {
          viewValue = null;
        }
      }
      pdoAddon.setPdo(m);
      return viewValue;
    };
  }

  @Override
  public Function<String, T> toModelFunction() {
    return v -> {
      if (!pdoAddon.isInSearchOrEdit() && !Objects.equals(v, viewValue)) {
        // view changed
        if (v == null) {
          pdoAddon.setPdo(null);
        }
        else {
          T proxy = pdoAddon.createPdo();
          Class<?> udkClass = proxy.getUniqueDomainKeyType();
          if (udkTranslator != null) {
            Object udk = udkTranslator.toModelFunction().apply(v);
            pdoAddon.setPdo(proxy.findByUniqueDomainKey(udk));
            if (pdoAddon.getPdo() == null) {
              // no such pdo: try interactive search
              proxy.setUniqueDomainKey(udk);
              runSearch(proxy);
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
                  runSearch(proxy);
                  return pdoAddon.getPdo();
                }
                catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                        IllegalArgumentException e) {
                  throw new RdcRuntimeException("could not create domain key: " + udkClass.getName() +
                                                "(" + v.getClass().getName() + ")", e);
                }
              }
              pdoAddon.setPdo(proxy.findByUniqueDomainKey(udk));
              if (pdoAddon.getPdo() == null &&
                  GuiProviderFactory.getInstance().isGuiProviderAvailable(proxy.getEffectiveClass())) {
                // no such pdo: try search (if gui provider available)
                presetSearchCriteria(proxy, udkClass, v);    // try to preset the proxy, if possible
                runSearch(proxy);
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
      return pdoAddon.getPdo();
    };
  }

  /**
   * Runs the interactive search.
   *
   * @param proxy the proxy object
   */
  protected void runSearch(T proxy) {
    try {
      pdoAddon.searchPdo(proxy, found -> {
        pdoAddon.setPdo(found);
        getComponent().setViewValue(found);
        getComponent().updateModel();
        Platform.runLater(() -> ((Node) getComponent()).requestFocus());
      });
    }
    catch (RdcRuntimeException rx) {
      LOGGER.warning(rx.getMessage());    // probably no GuiController or no finder -> just log
    }
  }

  /**
   * Creates the PDO style implementation.
   *
   * @return the implementation
   */
  protected PdoComponentAddon<T> createPdoAddon() {
    return new PdoComponentAddon<>(getComponent(), this::getPdo, this::setPdo);
  }

  /**
   * Gets the PDO via binding.<br>
   * For non-editable combo-boxes this translator isn't used, but registered,
   * because it could be changed to editable.
   *
   * @return the model value
   */
  @SuppressWarnings("unchecked")
  protected T getPdo() {
    return (T) getComponent().getBinding().getModelValue();
  }

  /**
   * Sets the PDO via binding.<br>
   * Mainly used by DnD.
   *
   * @param pdo the PDO, null to clear
   */
  protected void setPdo(T pdo) {
    getComponent().getBinding().setModelValue(pdo);
    pdoAddon.setPdo(pdo);
    getComponent().updateView();
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
      /*
       * Check if there is a factory method:
       *
       * <UdkClass>.lenientValueOf(String)
       *
       * If so, create a unique domain key and set it into the proxy.
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
      throw new RdcRuntimeException("cannot create lenient domain key", e1);
    }
    catch (NoSuchMethodException nm) {
      // no such method -> do nothing
    }
  }

  /**
   * Gets the {@link Bindable} annotation of the unique domain key.
   * <p>
   * If the UDK consists of more than one member, the annotation of the member whose getter
   * comes first alphabetically is returned. {@link Class#getMethods()} delivers the methods
   * in no particular order, so picking whatever comes first would apply different options
   * from one JVM to the next.
   *
   * @param proxy the PDO class to inspect
   * @return the annotation, null if not annotated with Bindable
   */
  protected Bindable getBindableAnnotation(T proxy) {
    Method firstMethod = null;
    for (Method method: proxy.getEffectiveClass().getMethods()) {
      if (method.isAnnotationPresent(DomainKey.class) && method.isAnnotationPresent(Bindable.class) &&
          (firstMethod == null || method.getName().compareTo(firstMethod.getName()) < 0)) {
        firstMethod = method;
      }
    }
    return firstMethod == null ? null : firstMethod.getAnnotation(Bindable.class);
  }

}
