/**
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


package org.tentackle.swing.bind;

import java.util.*;

import org.tentackle.bind.AbstractBinder;
import org.tentackle.bind.BindableElement;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.swing.BindableTableModel;
import org.tentackle.swing.FormUtilities;


/**
 * Binding Workhorse.
 *
 * @author harald
 */
public class DefaultFormTableBinder extends AbstractBinder implements FormTableBinder {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFormTableBinder.class);

  private final BindableTableModel<?> model;                    // the table to bind
  private final Map<Integer,FormTableBinding> boundColumns;     // the bindings mapped by column number
  private final Map<String,FormTableBinding> boundPaths;        // the bindings mapped by binding path


  /**
   * Creates a binder for a table.
   *
   * @param model the table model to bind
   */
  public DefaultFormTableBinder(BindableTableModel<?> model) {
    super();
    this.model = model;
    boundColumns = new HashMap<>();
    boundPaths = new TreeMap<>();
  }


  @Override
  public BindableTableModel<?> getModel() {
    return model;
  }


  /**
   * Binds objects to a form.
   * <p>
   * The form and all components in the form's parent classes
   * and all members or submembers
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited members will be included as well.
   * <p>
   * Notice that the object references are <em>not</em> kept in the binding.
   * <p>
   * CAUTION: expensive operation!
   *
   * @return number of members bound
   */
  @Override
  public int bindAllInherited() {
    return doBind(null, null, getModel().getBindingRootClass(), false);
  }


  /**
   * Binds objects to a form.
   * <p>
   * The form will be scanned for members or submembers
   * annotated with the {@link org.tentackle.bind.Bindable}-annotation.
   * Inherited members will not be included.
   * <p>
   * Notice that the object references are <em>not</em> kept in the binding.
   *
   * @return number of members bound
   */
  @Override
  public int bind() {
    return doBind(null, null, getModel().getBindingRootClass(), true);
  }


  /**
   * Removes all bindings of the form.
   */
  @Override
  public void unbind() {
    boundColumns.clear();
    boundPaths.clear();
  }


  /**
   * Gets the list of all bindings for this form.
   *
   * @return the bindings, never null
   */
  @Override
  public Collection<? extends Binding> getBindings() {
    return boundColumns.values();
  }



  /**
   * Gets the list of all bound components of this form.
   *
   * @return the list of bound components
   */
  @Override
  public Integer[] getBoundColumns() {
    Set<Integer> integers = boundColumns.keySet();
    return integers.toArray(new Integer[integers.size()]);
  }

  /**
   * Gets the list of all unbound components of this form.
   * @return the list of unbound components.
   */
  @Override
  public Integer[] getUnboundColumns() {
    int columnCount = model.getColumnCount();
    List<Integer> unboundColumns = new ArrayList<>();
    for (int mColumn = 0; mColumn < columnCount; mColumn++) {
      if (!boundColumns.containsKey(mColumn)) {
        unboundColumns.add(mColumn);
      }
    }
    return unboundColumns.toArray(new Integer[unboundColumns.size()]);
  }


  /**
   * Tests the binding.<br>
   * The method verifies that all components of the form are bound
   * and throws a {@link BindingException} if it finds at least one
   * unbound component.<br>
   * All unbound components are logged.
   * @throws BindingException if at least one unbound component found
   */
  @Override
  public void assertAllBound() throws BindingException {
    Integer[] unboundColumns = getUnboundColumns();
    if (unboundColumns.length > 0) {
      for (Integer mColumn: unboundColumns) {
        LOGGER.severe("unbound column " + mColumn + " in table " + model);
      }
      throw new BindingException("unbound columns in " + model);
    }
  }


  /**
   * Programmatically adds a binding to this form.
   *
   * @param binding the binding to add
   */
  @Override
  public void addBinding(Binding binding) {
    if (binding instanceof FormTableBinding) {
      FormTableBinding oldBinding = boundPaths.put(binding.getMember().getMemberPath(), (FormTableBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": binding path '" + binding.getMember().getMemberPath() +
                                   "' already bound to column " + oldBinding.getColumn());
      }
      oldBinding = boundColumns.put(((FormTableBinding) binding).getColumn(), (FormTableBinding) binding);
      if (oldBinding != null) {
        throw new BindingException(binding + ": column '" + ((FormTableBinding) binding).getColumn() +
                                   "' already bound to " + oldBinding.getMember().getMemberPath());
      }
    }
  }


  @Override
  public FormTableBinding getBinding(int mColumn) {
    return boundColumns.get(mColumn);
  }


  @Override
  public FormTableBinding getBinding(String bindingPath) {
    return boundPaths.get(bindingPath);
  }


  @Override
  public FormTableBinding removeBinding(int mColumn) {
    FormTableBinding binding = boundColumns.remove(mColumn);
    if (binding != null) {
      if (boundPaths.remove(binding.getMember().getMemberPath()) != binding) {
        throw new BindingException("Binding " + binding + " missing in path map");
      }
    }
    return binding;
  }


  /**
   * Programmatically removes a binding from this form.
   *
   * @param bindingPath the object's binding path
   *
   * @return the removed binding, null if no such binding found
   */
  @Override
  public FormTableBinding removeBinding(String bindingPath) {
    FormTableBinding binding = boundPaths.remove(bindingPath);
    if (binding != null) {
      if (boundColumns.remove(binding.getColumn()) != binding) {
        throw new BindingException("Binding " + binding + " missing in component map");
      }
    }
    return binding;
  }



  @Override
  protected int doBind(BindingMember[] parents, String parentMemberPath, Class<?> parentClass, boolean declaredOnly) {

    /**
     * To detect recursion loops:
     * we don't need to recursively walk down the binding path more
     * than the max binding path length of all components in the container.
     */
    if (parentMemberPath != null) {
      boolean found = false;
      for (int i=0; i < getModel().getColumnCount(); i++) {
        // check if the binding path is leading part of any component
        String bindingPath = getModel().getBindingPath(i);
        if (bindingPath != null && bindingPath.startsWith(parentMemberPath)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return 0;
      }
    }


    int count = 0;

    // all fields and/or setter/getters annotated with the @Bindable annotation
    for (BindableElement element: FormUtilities.getInstance().getBindingFactory().getBindableCache().getBindableMap(parentClass, declaredOnly).values()) {

      String fieldMemberName = StringHelper.firstToLower(element.getCamelName());
      String fieldMemberPath = (parentMemberPath == null ? "" : (parentMemberPath + ".")) + fieldMemberName;

      BindingMember[] fieldParents = new BindingMember[parents == null ? 1 : (parents.length + 1)];
      BindingMember   fieldMember  = FormUtilities.getInstance().getBindingFactory().createBindingMember(
              parentClass, (parents == null ? null : parents[parents.length-1]),
              fieldMemberName, fieldMemberPath, element);

      if (parents != null) {
        System.arraycopy(parents, 0, fieldParents, 0, parents.length);
        fieldParents[parents.length] = fieldMember;
      }
      else  {
        fieldParents[0] = fieldMember;
      }

      try {

        if (element.getField() != null && !element.getField().isAccessible()) {
          element.getField().setAccessible(true);  // make this directly accessible
        }

        // try to bind this member
        for (int i=0; i < getModel().getColumnCount(); i++) {
          String bindingPath = getModel().getBindingPath(i);
          if (bindingPath != null && bindingPath.equals(fieldMemberPath)) {
            addBinding(FormUtilities.getInstance().getBindingFactory().createFormTableBinding(this, parents, fieldMember, i, element.getBindingOptions()));
            count++;
          }
        }

        // recursively try to bind sub-members
        count += doBind(fieldParents, fieldMemberPath, fieldMember.getType(), declaredOnly);
      }
      catch (Exception ex) {
        throw new BindingException("binding " + fieldMemberPath + " failed", ex);
      }
    }

    return count;
  }

}
