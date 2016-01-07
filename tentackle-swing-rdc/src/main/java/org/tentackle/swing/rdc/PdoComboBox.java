/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.tentackle.swing.rdc;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Objects;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.AbstractFormField;
import org.tentackle.swing.FormFieldComboBox;

/**
 * A combobox for PDOs.
 * <p>
 * Useful for master data PDOs.<br>
 * Works without further configuation if used with an appropriate binding.
 *
 * @author harald
 * @param <T> the PDO type
 */
public class PdoComboBox<T extends PersistentDomainObject<T>> extends FormFieldComboBox<T> implements DomainContextProvider {

  private static final long serialVersionUID = 1826592080778987508L;

  private Class<T> pdoClass;                    // the managed class
  private DomainContext domainContext;          // the current domain context
  private boolean contextChanged;               // true if context has changed


  /**
   * Creates a combobox for a given AbstractFormField.
   *
   * @param editorField the AbstractFormField used as the editor field and formatting
   *        null, if standard {@link org.tentackle.swing.StringFormField}
   *
   */
  public PdoComboBox (AbstractFormField editorField) {
    super(editorField);
  }

  /**
   * Creates a combobox.
   */
  public PdoComboBox ()  {
    super();
  }


  /**
   * Sets the domain context.<br>
   * Useful if there's no binding.
   *
   * @param domainContext the context
   */
  public void setDomainContext(DomainContext domainContext) {
    contextChanged |= !Objects.equals(this.domainContext, domainContext);
    this.domainContext = domainContext;
  }

  /**
   * Gets the domain context.
   * @return the context
   */
  @Override
  public DomainContext getDomainContext() {
    return domainContext;
  }


  /**
   * Sets the PDO-class.<br>
   * Useful if there's no binding.
   *
   * @param pdoClass the class
   */
  public void setPdoClass(Class<T> pdoClass) {
    contextChanged |= !Objects.equals(this.pdoClass, pdoClass);
    this.pdoClass = pdoClass;
  }

  /**
   * Gets the PDO-class.
   *
   * @return the class
   */
  public Class<T> getPdoClass() {
    return pdoClass;
  }


  /**
   * Loads the items.
   * <p>
   * If a binding is present, it will be used to determine
   * the PDO class and loads all PDOs in the current context.
   * If a cache is present, selectAllIntContextCached will be
   * used instead of selectAllCached.
   * The items will be reloaded only if the binding property
   * {@link DomainContext} of the {@link org.tentackle.swing.FormContainer} has changed.
   */
  @SuppressWarnings("unchecked")
  public void loadItemsIfContextChanged() {
    Binding binding = getBinding();
    if (binding != null) {    // if bound ...
      try {
        Class<?> clazz = binding.getMember().getType();
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {  // ... to a PDO
          // check whether context has changed
          setDomainContext(binding.getBinder().getBindingProperty(DomainContext.class));
          setPdoClass((Class<T>) clazz);
        }
      }
      catch (Exception ex) {
        throw new BindingException("could not determine type for " + binding, ex);
      }
    }

    // reload if context has changed (or initially)
    if (contextChanged && domainContext != null && pdoClass != null) {
      contextChanged = false;
      // figure out whether a cache is configured (see PdoCache-wurblet)
      try {
        try {
          setAllItems((Collection<T>)
                      pdoClass.getMethod("selectAllPdoCached", DomainContext.class).invoke(null, domainContext));
        }
        catch (NoSuchMethodException nme) {
          // fallback to real select
          setAllItems(on(pdoClass).selectAll());
        }
      }
      catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        throw new PdoRuntimeException(domainContext.getSession(),
                                     "cannot load items into combobox for " + binding, ex);
      }
    }
    // else: no binding, behaves like a FormFieldComboBox.
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to load all items if domain context has changed.
   */
  @Override
  public void setFormValue(Object item) {
    loadItemsIfContextChanged();
    super.setFormValue(item);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to get the object and not the string.
   * Furthermore, if the combobox is editable and allowDeselect == false
   * input text not corresponding to a PDO will be refused.
   */
  @Override
  @SuppressWarnings("unchecked")
  public T getFormValue() {
    if (getBinding() != null) {
      // return the object and not the string if editable
      Object item = getSelectedItem();
      if (item == null && isEditable() && !isAllowDeselect()) {
        String valueText = getText();
        if (valueText != null && valueText.length() > 0) {
          // some weird text without an object
          requestFocusLater();
        }
      }
      return (T) item;
    }
    else  {
      return super.getFormValue();
    }
  }

}
