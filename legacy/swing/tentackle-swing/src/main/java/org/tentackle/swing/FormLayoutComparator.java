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
import java.awt.ComponentOrientation;
import java.awt.Window;
import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Comparator for {@link FormFocusTraversalPolicy}.
 * <p>
 * Necessary because javax.swing.LayoutComparator is package final.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormLayoutComparator implements Comparator<Component>, Serializable {

  private int tolerance;
  private boolean horizontal;
  private boolean leftToRight;


  /**
   * Creates a comparator for components.
   *
   * @param tolerance the tolerance in pixels to detect components belonging to the same row or column
   * @param horizontal true if focus traversal is horizontal
   * @param leftToRight true if focus traversal is left to right
   */
  public FormLayoutComparator(int tolerance, boolean horizontal, boolean leftToRight) {
    this.tolerance = tolerance;
    this.horizontal = horizontal;
    this.leftToRight = leftToRight;
  }


  /**
   * Creates a comparator with default values.
   * 
   * <pre>
   * rowTolerance = 10
   * horizontal = true
   * leftToRight = true
   * </pre>
   */
  public FormLayoutComparator() {
    this(10, true, true);
  }



  /**
   * Sets the component orientation of this comparator.
   *
   * @param orientation the orientation (usually from a container)
   */
  public void setComponentOrientation(ComponentOrientation orientation) {
    horizontal = orientation.isHorizontal();
    leftToRight = orientation.isLeftToRight();
  }



  /**
   * Gets the horizontal traversal flag.
   *
   * @return true if traversal is horizontal
   */
  public boolean isHorizontal() {
    return horizontal;
  }

  /**
   * Sets the horizontal traversal flag.
   *
   * @param horizontal true if traversal is horizontal
   */
  public void setHorizontal(boolean horizontal) {
    this.horizontal = horizontal;
  }

  /**
   * Gets the left-to-right traversal flag.
   *
   * @return true if traversal is left to right
   */
  public boolean isLeftToRight() {
    return leftToRight;
  }

  /**
   * Sets the left-to-right traversal flag.
   *
   * @param leftToRight true if traversal is left to right
   */
  public void setLeftToRight(boolean leftToRight) {
    this.leftToRight = leftToRight;
  }

  /**
   * Gets the row/column tolerance.
   *
   * @return the tolerance
   */
  public int getTolerance() {
    return tolerance;
  }

  /**
   * Sets the row/column tolerance.
   *
   * @param tolerance the tolerance
   */
  public void setTolerance(int tolerance) {
    this.tolerance = tolerance;
  }





  /**
   * {@inheritDoc}
   * <p>
   * Code mostly copied from javax.swing.LayoutComparator
   */
  @Override
  public int compare(Component o1, Component o2) {

    if (o1 == o2) {
      return 0;
    }

    if (o1 == null) {
      return -1;
    }

    if (o2 == null) {
      return 1;
    }


    // Row/Column algorithm only applies to siblings. If 'a' and 'b'
    // aren't siblings, then we need to find their most inferior
    // ancestors which share a parent. Compute the ancestory lists for
    // each Component and then search from the Window down until the
    // hierarchy branches.

    if (o1.getParent() != o2.getParent()) {

      LinkedList<Component> aAncestory, bAncestory;

      for (aAncestory = new LinkedList<>(); o1 != null; o1 = o1.getParent()) {
        aAncestory.add(o1);
        if (o1 instanceof Window) {
          break;
        }
      }
      if (o1 == null) {
        // 'a' is not part of a Window hierarchy. Can't cope.
        throw new ClassCastException();
      }

      for (bAncestory = new LinkedList<>(); o2 != null; o2 = o2.getParent()) {
        bAncestory.add(o2);
        if (o2 instanceof Window) {
          break;
        }
      }
      if (o2 == null) {
        // 'b' is not part of a Window hierarchy. Can't cope.
        throw new ClassCastException();
      }

      for (ListIterator<Component> aIter = aAncestory.listIterator(aAncestory.size()),
              bIter = bAncestory.listIterator(bAncestory.size());;) {
        if (aIter.hasPrevious()) {
          o1 = aIter.previous();
        }
        else {
          // a is an ancestor of b
          return -1;
        }

        if (bIter.hasPrevious()) {
          o2 = bIter.previous();
        }
        else {
          // b is an ancestor of a
          return 1;
        }

        if (o1 != o2) {
          break;
        }
      }
    }

    int ax = o1.getX(), ay = o1.getY(), bx = o2.getX(), by = o2.getY();

    int zOrder = o1.getParent().getComponentZOrder(o1) - o2.getParent().getComponentZOrder(o2);
    if (horizontal) {
      if (leftToRight) {

        // LT - Western Europe (optional for Japanese, Chinese, Korean)

        if (Math.abs(ay - by) < tolerance) {
          return (ax < bx) ? -1 : ((ax > bx) ? 1 : zOrder);
        }
        else {
          return (ay < by) ? -1 : 1;
        }
      }
      else { // !leftToRight

        // RT - Middle East (Arabic, Hebrew)

        if (Math.abs(ay - by) < tolerance) {
          return (ax > bx) ? -1 : ((ax < bx) ? 1 : zOrder);
        }
        else {
          return (ay < by) ? -1 : 1;
        }
      }
    }
    else { // !horizontal
      if (leftToRight) {

        // TL - Mongolian

        if (Math.abs(ax - bx) < tolerance) {
          return (ay < by) ? -1 : ((ay > by) ? 1 : zOrder);
        }
        else {
          return (ax < bx) ? -1 : 1;
        }
      }
      else { // !leftToRight

        // TR - Japanese, Chinese, Korean

        if (Math.abs(ax - bx) < tolerance) {
          return (ay < by) ? -1 : ((ay > by) ? 1 : zOrder);
        }
        else {
          return (ax > bx) ? -1 : 1;
        }
      }
    }
  }

}
