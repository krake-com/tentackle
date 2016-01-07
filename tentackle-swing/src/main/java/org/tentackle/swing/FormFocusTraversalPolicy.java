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

// Created on August 21, 2002, 3:57 PM

package org.tentackle.swing;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SortingFocusTraversalPolicy;



/**
 * Keyboard focus traversal policy for Tentackle forms.
 * <p>
 * Code mostly copied from {@link javax.swing.LayoutFocusTraversalPolicy} because of package-scope constructor.
 * Extended to focus only on Tentackle-components, provide form wrap events and support for {@link FocusTraversalGroup}s.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFocusTraversalPolicy extends SortingFocusTraversalPolicy implements Serializable {

  private static final SwingDefaultFocusTraversalPolicy fitnessTestPolicy = new SwingDefaultFocusTraversalPolicy();
  
  private Component lastComp;   // last triggered component


  
  /**
   * Creates a traversal policy for a given comparator.
   *
   * @param layoutComparator the layout comparator
   */
  public FormFocusTraversalPolicy(Comparator<? super Component> layoutComparator) {
    super(layoutComparator);
  }


  /**
   * Creates the default layout policy.
   *
   * @see FormLayoutComparator
   */
  public FormFocusTraversalPolicy() {
    this(new FormLayoutComparator());
  }


  
  /**
   * Determines whether the specified <code>Component</code>
   * is an acceptable choice as the new focus owner.
   * This method performs the following sequence of operations:
   * <ol>
   * <li>Checks whether <code>component</code> is visible, displayable,
   *     enabled, and focusable.  If any of these properties is
   *     <code>false</code>, this method returns <code>false</code>.
   * <li>If <code>component</code> is an instance of <code>JTable</code>,
   *     returns <code>true</code>.
   * <li>If <code>component</code> is an instance of <code>JComboBox</code>,
   *     then returns the value of
   *     <code>component.getUI().isFocusTraversable(component)</code>.
   * <li>If <code>component</code> is a <code>JComponent</code>
   *     with a <code>JComponent.WHEN_FOCUSED</code>
   *     <code>InputMap</code> that is neither <code>null</code>
   *     nor empty, returns <code>true</code>.
   * <li>Returns the value of
   *     <code>DefaultFocusTraversalPolicy.accept(component)</code>.
   * </ol>
   *
   * @param component the <code>Component</code> whose fitness
   *                   as a focus owner is to be tested
   * @see java.awt.Component#isVisible
   * @see java.awt.Component#isDisplayable
   * @see java.awt.Component#isEnabled
   * @see java.awt.Component#isFocusable
   * @see javax.swing.plaf.ComboBoxUI#isFocusTraversable
   * @see javax.swing.JComponent#getInputMap
   * @see java.awt.DefaultFocusTraversalPolicy#accept
   * @return <code>true</code> if <code>component</code> is a valid choice
   *         for a focus owner;
   *         otherwise <code>false</code>
   * <p>
   * Overridden to accept only FormComponents, FormButtons, FormTables and JPasswordFields
   */
  @Override
  protected boolean accept(Component component) {

    if (!super.accept(component) ||
        (component instanceof FormFieldComponent && !((FormFieldComponent)component).isChangeable()))  {
      // don't focus non-editable formfields (the default accept() would do)
      return false;
    }
    
    if ((component instanceof FormComponent && ((FormComponent) component).isFormTraversable()) ||
        (component instanceof FormButton && component.isEnabled() && ((FormButton) component).isFormTraversable()) ||
        (component instanceof FormTable && component.isEnabled() && ((FormTable) component).isFormTraversable()) ||
        component instanceof javax.swing.JPasswordField) {

      if (component instanceof JTable) {
        // JTable only has ancestor focus bindings, we thus force it
        // to be focusable by returning true here.
        return true;
      }

      else if (component instanceof JComboBox) {
        JComboBox box = (JComboBox) component;
        return box.getUI().isFocusTraversable(box);
      }

      else if (component instanceof JComponent) {
        // note: getInputMap(condition, create) is package scope :(
        InputMap inputMap = ((JComponent) component).getInputMap(JComponent.WHEN_FOCUSED);
        while (inputMap != null && inputMap.size() == 0) {
          inputMap = inputMap.getParent();
        }
        if (inputMap != null) {
          return true;
        }

        // Delegate to the fitnessTestPolicy, this will test for the
        // case where the developer has overriden isFocusTraversable to
        // return true.
      }
      return fitnessTestPolicy.accept(component);
    }

    return false;
  }
  
  
  /**
   * Returns the Component that should receive the focus after component.
   * container must be a focus cycle root of component.
   * <p>
   * By default, FormFocusTraversalPolicy implicitly transfers focus down-
   * cycle. That is, during normal focus traversal, the Component
   * traversed after a focus cycle root will be the focus-cycle-root's
   * default Component to focus. This behavior can be disabled using the
   * <code>setImplicitDownCycleTraversal</code> method.
   * <p>
   * If container is <a href="../../java/awt/doc-files/FocusSpec.html#FocusTraversalPolicyProviders">focus
   * traversal policy provider</a>, the focus is always transferred down-cycle.
   *
   * @param container a focus cycle root of component or a focus traversal policy provider
   * @param component a (possibly indirect) child of container, or
   *        container itself
   * @return the Component that should receive the focus after component, or
   *         null if no suitable Component can be found
   * @throws IllegalArgumentException if container is not a focus cycle
   *         root of component or a focus traversal policy provider, or if either container or
   *         component is null
   */
  @Override
  public Component getComponentAfter(Container container, Component component) {

    if (container == null || component == null) {
      throw new IllegalArgumentException("container and component cannot be null");
    }

    Component comp = null;

    if (component instanceof FormComponent) {
      FocusTraversalGroup focusGroup = ((FormComponent) component).getFocusTraversalGroup();
      if (focusGroup != null) {
        // current component is part of a focus group
        comp = focusGroup.getComponentAfter(component);
        if (comp == null) {
          // component is last in group
          comp = focusGroup.getFirstComponent();
          if (comp != null) {
            // take next component of first component according to policy
            // while skipping all components part of a group not being the first
            updateComparatorFromContainer(container);
            do {
              comp = super.getComponentAfter(container, comp);
            } while (comp != null && !isFirstComponentOrNonGrouped(comp));
          }
        }
      }
    }

    if (comp != null) {
      // if next component found according to focus group
      if (!accept(comp)) {
        return getComponentAfter(container, comp);
      }
    }
    else  {
      updateComparatorFromContainer(container);
      comp = super.getComponentAfter(container, component);
    }

    
    if (comp != null && // don't formwrap if tab changed or alike
        container instanceof FormWindow && // must be a FormWindow
        component instanceof FormComponent && // must be for FormComponent
        comp != component && lastComp != component && // don't trigger more than once if stayed in same field
        accept(component) && // don't trigger from a non-accepted field
        comp == super.getFirstComponent(container)) {
      
      // next focusLost will trigger the event
      ((FormComponent) component).setFormWrapWindow((FormWindow) container);
      lastComp = component;
    }
    else {
      lastComp = null;
    }
    
    return comp;
  }



  /**
   * Returns the Component that should receive the focus before component.
   * container must be a focus cycle root of component.
   * <p>
   * By default, LayoutFocusTraversalPolicy implicitly transfers focus down-
   * cycle. That is, during normal focus traversal, the Component
   * traversed after a focus cycle root will be the focus-cycle-root's
   * default Component to focus. This behavior can be disabled using the
   * <code>setImplicitDownCycleTraversal</code> method.
   * <p>
   * If container is <a href="../../java/awt/doc-files/FocusSpec.html#FocusTraversalPolicyProviders">focus
   * traversal policy provider</a>, the focus is always transferred down-cycle.
   *
   * @param container a focus cycle root of component or a focus traversal policy provider
   * @param component a (possibly indirect) child of container, or
   *        container itself
   * @return the Component that should receive the focus before component,
   *         or null if no suitable Component can be found
   * @throws IllegalArgumentException if container is not a focus cycle
   *         root of component or a focus traversal policy provider, or if either container or
   *         component is null
   */
  @Override
  public Component getComponentBefore(Container container, Component component) {
    if (container == null || component == null) {
      throw new IllegalArgumentException("container and component cannot be null");
    }

    Component comp = null;

    if (component instanceof FormComponent) {
      FocusTraversalGroup focusGroup = ((FormComponent) component).getFocusTraversalGroup();
      if (focusGroup != null) {
        // current component is part of a focus group
        comp = focusGroup.getComponentBefore(component);
        if (comp == null) {
          // component is first in group
          comp = focusGroup.getLastComponent();
          if (comp != null) {
            // take previous component of last component according to policy
            // while skipping all components part of a group not being the first
            updateComparatorFromContainer(container);
            do {
              comp = super.getComponentBefore(container, comp);
            } while (comp != null && !isLastComponentOrNonGrouped(comp));
          }
        }
      }
    }

    if (comp != null) {
      // if previous component found according to focus group
      if (accept(comp)) {
        return comp;
      }
      else  {
        return getComponentBefore(container, comp);
      }
    }
    else  {
      updateComparatorFromContainer(container);
      return super.getComponentBefore(container, component);
    }
  }


  /**
   * Returns the first Component in the traversal cycle. This method is used
   * to determine the next Component to focus when traversal wraps in the
   * forward direction.
   *
   * @param container a focus cycle root of component or a focus traversal policy provider whose
   *        first Component is to be returned
   * @return the first Component in the traversal cycle of container,
   *         or null if no suitable Component can be found
   * @throws IllegalArgumentException if container is null
   */
  @Override
  public Component getFirstComponent(Container container) {
    if (container == null) {
      throw new IllegalArgumentException("container cannot be null");
    }
    updateComparatorFromContainer(container);
    return super.getFirstComponent(container);
  }


  /**
   * Returns the last Component in the traversal cycle. This method is used
   * to determine the next Component to focus when traversal wraps in the
   * reverse direction.
   *
   * @param container a focus cycle root of component or a focus traversal policy provider whose
   *        last Component is to be returned
   * @return the last Component in the traversal cycle of container,
   *         or null if no suitable Component can be found
   * @throws IllegalArgumentException if container is null
   */
  @Override
  public Component getLastComponent(Container container) {
    if (container == null) {
      throw new IllegalArgumentException("container cannot be null");
    }
    updateComparatorFromContainer(container);
    return super.getLastComponent(container);
  }



  /**
   * Updates the comparator's orientation from the container.
   *
   * @param container the container
   */
  private void updateComparatorFromContainer(Container container) {
    Comparator comparator = getComparator();
    if (comparator instanceof FormLayoutComparator) {
      ((FormLayoutComparator) comparator).setComponentOrientation(container.getComponentOrientation());
    }
  }





  /**
   * Checks whether the given component is the first component of a group or not in a group at all.
   * @param comp the component
   * @return true if first component
   */
  private boolean isFirstComponentOrNonGrouped(Component comp) {
    if (comp instanceof FormComponent) {
      FocusTraversalGroup group = ((FormComponent) comp).getFocusTraversalGroup();
      if (group != null) {
        return comp == group.getFirstComponent();
      }
      else  {
        return true;  // not grouped
      }
    }
    return false;
  }

  /**
   * Checks whether the given component is the last component of a group or not in a group at all.
   * @param comp the component
   * @return true if last component
   */
  private boolean isLastComponentOrNonGrouped(Component comp) {
    if (comp instanceof FormComponent) {
      FocusTraversalGroup group = ((FormComponent) comp).getFocusTraversalGroup();
      if (group != null) {
        return comp == group.getLastComponent();
      }
      else  {
        return true;
      }
    }
    return false;
  }

  

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(getComparator());
    out.writeBoolean(getImplicitDownCycleTraversal());
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in)
          throws IOException, ClassNotFoundException {
    setComparator((Comparator<? super Component>) in.readObject());
    setImplicitDownCycleTraversal(in.readBoolean());
  }

}



// Create our own subclass and change accept to public so that we can call accept.
@SuppressWarnings("serial")
class SwingDefaultFocusTraversalPolicy extends java.awt.DefaultFocusTraversalPolicy {

  @Override
  public boolean accept(Component component) {
    return super.accept(component);
  }
}

