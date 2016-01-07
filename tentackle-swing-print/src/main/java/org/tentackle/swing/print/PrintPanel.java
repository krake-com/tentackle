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


package org.tentackle.swing.print;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import javax.swing.JPanel;


/**
 * A printing panel.
 * <p>
 *
 * PrintPanel is a JPanel with some methods overridden to make it printable.
 * Furthermore, a PrintPanel is also a Printable with a minimalistic
 * print-implementation (prints a single page).
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class PrintPanel extends JPanel implements Printable {

  /**
   * Creates a print panel.
   */
  public PrintPanel() {
    super(false);     // turn off double-buffering
    setup();
  }


  /**
   * Creates a print panel with a Layout-Manager (default is FlowLayout)
   * @param layout the layout manager
   */
  public PrintPanel(LayoutManager layout) {
    super(layout, false);
    setup();
  }
  
  
  private void setup()  {
    setOpaque(false);             // don't draw any background
    setBackground(Color.white);   // if opaque is turned on, draw white background by default
  }

 
  /**
   * Prints the panel with all its components.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public void print (Graphics g)  {

    super.print(g);     // prints the container only (i.e. the background)

    // print components
    Component[] comps = getComponents();

    for (int i=0; i < comps.length; i++)  {
      Component c = comps[i];
      Point loc = c.getLocation();
      g.translate(loc.x, loc.y);
      if (c.isVisible())  {
        c.print(g);       // if this a PrintPanel, go recursively down
      }
      g.translate(-loc.x, -loc.y);
    }
  }


  /**
   * Calculates the dynamic size.<br>
   * The dynamic size may vary from the current size as some components
   * (usually {@link PrintTextArea}s) may change their size according to
   * their contents.
   * @return the dynamic size
   */
  public Dimension getDynamicSize() {
    Component[] comps = getComponents();
    Rectangle bounds = new Rectangle();
    for (int i=0; i < comps.length; i++)  {
      bounds.add(comps[i].getBounds());
    }
    return bounds.getSize();
  }
  

  /**
   * Gets the preferred size.<br>
   * If the prefererredSize has already been set (and its witdh and height is not zero)
   * that size will be returned.<br>
   * Otherwise the method calculates the preferred size as the 
   * smallest bounding box around all components.
   */
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (size == null || (size.width <= 1 && size.height <= 1)) {
      size = getDynamicSize();
    }
    return size;
  }
  
  
  /**
   * Packs the components and this panel.
   */
  public void pack()  {
    if (getLayout() == null)  {
      doNullLayout();
    }
    setSize(getPreferredSize());
    doLayout();
  }


  /**
   * Same as pack(), but pack only vertically and set the width to a fixed size
   * @param width the fixed width
   */
  public void packHeightForWidth(int width) {
    if (getLayout() == null)  {
      doNullLayout();
    }
    setSize(width, getPreferredSize().height);
    doLayout();
  }

  /**
   * Same as pack(), but pack only horizontally and set the width to a fixed size
   * @param height the fixed height
   */
  public void packWidthForHeight(int height) {
    if (getLayout() == null)  {
      doNullLayout();
    }
    setSize(getPreferredSize().width, height);
    doLayout();
  }



  /**
   * Overridden to do nothing
   */
  @Override
  public void revalidate() {
    // no repaint events need to be triggered (see doLayout!)
  }
  
  /**
   * Overridden to do nothing
   */
  @Override
  public void repaint() {
    // no repaint events need to be triggered!!!
  }
  

  
  /**
   * Prints this panel on a single page.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {

    if (pageIndex > 0) {
      return(NO_SUCH_PAGE);
    }
    else {
      graphics.translate((int)pageFormat.getImageableX(), (int)pageFormat.getImageableY());
      print(graphics);
      return(PAGE_EXISTS);
    }
  }
  
  

  /**
   * {@inheritDoc}
   * <p>
   * For some reasons the validate()-method does
   * not layout the container properly if it hasn't been displayed
   * on the screen. So we re-implement doLayout() here.
   * Because PrintPanels usually have a fixed size according to the
   * papers Layout we run doLayout top-down (not bottom-up).
   */
  @Override
  public void doLayout()  {
    super.doLayout();
    Component[] comps = getComponents();

    for (int i=0; i < comps.length; i++)  {
      Component c = comps[i];
      if (c instanceof Container) {
        ((Container)c).doLayout();
      }
    }
  }
  
  
  
  /**
   * move all components to the leftmost-position
   */
  private void doNullLayout()  {
    Component[] comps = getComponents();
    // get left-uppermost corner
    Dimension size = getPreferredSize();
    int minX = size.width;
    int minY = size.height;
    for (int i=0; i < comps.length; i++)  {
      Component c = comps[i];
      if (c.getX() < minX) {
        minX = c.getX();
      }
      if (c.getY() < minY) {
        minY = c.getY();
      }
    }
    // move to 0.0
    for (int i=0; i < comps.length; i++)  {
      Component c = comps[i];
      c.setLocation(c.getX() - minX, c.getY() - minY);
    }
  }

}
