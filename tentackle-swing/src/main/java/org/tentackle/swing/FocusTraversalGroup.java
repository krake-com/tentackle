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


package org.tentackle.swing;

import java.awt.Component;
import java.util.Arrays;

/**
 * A component group sharing tha same focus traversal.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
public class FocusTraversalGroup extends ComponentGroup {
  
  private FormLayoutComparator layoutComparator;    // to sort the components
  private Component[] sortedComponents;             // sorted components


  /**
   * Creates a focus traversal group.
   *
   * @param layoutComparator the layout comparator
   */
  public FocusTraversalGroup(FormLayoutComparator layoutComparator) {
    if (layoutComparator == null) {
      throw new IllegalArgumentException("layout comparator must not be null");
    }
    this.layoutComparator = layoutComparator;
  }

  /**
   * Creates a focus traversal group with the default layout comparator.
   */
  public FocusTraversalGroup() {
    this(new FormLayoutComparator());
  }



  @Override
  public boolean addComponent(Component component) {
    invalidateSortedComponents();
    return super.addComponent(component);
  }

  @Override
  public boolean removeComponent(Component component) {
    invalidateSortedComponents();
    return super.removeComponent(component);
  }



  /**
   * Gets the horizontal traversal flag.
   *
   * @return true if traversal is horizontal
   */
  public boolean isHorizontal() {
    return layoutComparator.isHorizontal();
  }

  /**
   * Sets the horizontal traversal flag.
   *
   * @param horizontal true if traversal is horizontal
   */
  public void setHorizontal(boolean horizontal) {
    invalidateSortedComponents();
    layoutComparator.setHorizontal(horizontal);
  }

  /**
   * Gets the left-to-right traversal flag.
   *
   * @return true if traversal is left to right
   */
  public boolean isLeftToRight() {
    return layoutComparator.isLeftToRight();
  }

  /**
   * Sets the left-to-right traversal flag.
   *
   * @param leftToRight true if traversal is left to right
   */
  public void setLeftToRight(boolean leftToRight) {
    invalidateSortedComponents();
    layoutComparator.setLeftToRight(leftToRight);
  }

  /**
   * Gets the row/column tolerance.
   *
   * @return the tolerance
   */
  public int getTolerance() {
    return layoutComparator.getTolerance();
  }

  /**
   * Sets the row/column tolerance.
   *
   * @param tolerance the tolerance
   */
  public void setTolerance(int tolerance) {
    invalidateSortedComponents();
    layoutComparator.setTolerance(tolerance);
  }



  /**
   * Gets the next component within this group.
   *
   * @param component the current component
   * @return the component next to given component or null if last component or group is empty
   */
  public Component getComponentAfter(Component component) {
    Component[] comps = getSortedComponents();
    for(int i=0; i < comps.length - 1; i++) {
      if (comps[i] == component) {
        return comps[i+1];
      }
    }
    return null;
  }


  /**
   * Gets the previous component within this group.
   *
   * @param component the current component
   * @return the component before given component or null if first component or group is empty
   */
  public Component getComponentBefore(Component component) {
    Component[] comps = getSortedComponents();
    for(int i = comps.length - 1; i > 0; i--) {
      if (comps[i] == component) {
        return comps[i-1];
      }
    }
    return null;
  }


  /**
   * Gets the last component
   * @return the last component or null if group is empty
   */
  public Component getLastComponent() {
    Component[] comps = getSortedComponents();
    return comps.length > 0 ? comps[comps.length-1] : null;
  }


  /**
   * Gets the first component
   * @return the first component or null if group is empty
   */
  public Component getFirstComponent() {
    Component[] comps = getSortedComponents();
    return comps.length > 0 ? comps[0] : null;
  }



  /**
   * Invalidate sorted components.
   * Invoked whenever a component is added or removed or the
   * sorting criteria have changed.
   */
  private void invalidateSortedComponents() {
    sortedComponents = null;
  }

  /**
   * Gets the (cached) sorted components.
   *
   * @return the sorted components
   */
  private Component[] getSortedComponents() {
    if (sortedComponents == null) {
      sortedComponents = getComponents();
      Arrays.sort(sortedComponents, layoutComparator);
    }
    return sortedComponents;
  }

}
