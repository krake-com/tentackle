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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.border.LineBorder;

/**
 * A LineBorder with the ability to enable/disable each of its four sides.
 * 
 * @author harald
 */
public class EnhancedLineBorder extends LineBorder {
  
  
  private boolean topEnabled;       // true if draw top border
  private boolean leftEnabled;      // true if draw left border
  private boolean bottomEnabled;    // true if draw bottom border
  private boolean rightEnabled;     // true if draw right border

  
  
  /**
   * Creates a line border with the specified color, thickness and
   * all four sides enabled separately.
   * 
   * @param color the color of the border
   * @param thickness the thickness of the border
   * @param topEnabled true if top border is drawn
   * @param leftEnabled true if left border is drawn
   * @param bottomEnabled true if bottom border is drawn
   * @param rightEnabled true if right border is drawn
   * 
   */
  public EnhancedLineBorder(Color color, int thickness, 
                            boolean topEnabled, boolean leftEnabled, 
                            boolean bottomEnabled, boolean rightEnabled)  {
    super(color, thickness);
    this.topEnabled     = topEnabled;
    this.leftEnabled    = leftEnabled;
    this.bottomEnabled  = bottomEnabled;
    this.rightEnabled   = rightEnabled;
  }
  
  /**
   * Creates a line border with the black color, thickness 1 and
   * all four sides enabled separately.
   * 
   * @param topEnabled true if top border is drawn
   * @param leftEnabled true if left border is drawn
   * @param bottomEnabled true if bottom border is drawn
   * @param rightEnabled true if right border is drawn
   * 
   */
  public EnhancedLineBorder(boolean topEnabled, boolean leftEnabled, 
                            boolean bottomEnabled, boolean rightEnabled)  {
    this(Color.BLACK, 1, topEnabled, leftEnabled, bottomEnabled, rightEnabled);
  }

  /**
   * Creates a line border with the specified color, thickness
   * and all four sides enabled.
   * 
   * @param color the color of the border
   * @param thickness the thickness of the border
   */
  public EnhancedLineBorder(Color color, int thickness)  {
    this(color, thickness, true, true, true, true);
  }

  /** 
   * Creates a line border with the specified color, a thickness of 1
   * and all four sides enabled.
   * 
   * @param color the color for the border
   */
  public EnhancedLineBorder(Color color) {
    this(color, 1);
  }
  
  /** 
   * Creates a line border with black color, a thickness of 1
   * and all four sides enabled.
   */
  public EnhancedLineBorder() {
    this(Color.BLACK);
  }
  

  
  /**
   * Enables the top border.
   * 
   * @param enabled true if draw top border 
   */
  public void setTopEnabled(boolean enabled) {
    topEnabled = enabled;
  }
  
  /**
   * Returns whether top border is enabled.
   * 
   * @return true if top border is drawn
   */
  public boolean isTopEnabled() {
    return topEnabled;
  }
  
  
  /**
   * Enables the bottom border.
   * 
   * @param enabled true if draw bottom border 
   */
  public void setBottomEnabled(boolean enabled) {
    bottomEnabled = enabled;
  }
  
  /**
   * Returns whether bottom border is enabled.
   * 
   * @return true if bottom border is drawn
   */
  public boolean isBottomEnabled() {
    return bottomEnabled;
  }
  
  
  /**
   * Enables the left border.
   * 
   * @param enabled true if draw left border 
   */
  public void setLeftEnabled(boolean enabled) {
    leftEnabled = enabled;
  }
  
  /**
   * Returns whether left border is enabled.
   * 
   * @return true if left border is drawn
   */
  public boolean isLeftEnabled() {
    return leftEnabled;
  }
  
  
  /**
   * Enables the right border.
   * 
   * @param enabled true if draw right border 
   */
  public void setRightEnabled(boolean enabled) {
    rightEnabled = enabled;
  }
  
  /**
   * Returns whether right border is enabled.
   * 
   * @return true if right border is drawn
   */
  public boolean isRightEnabled() {
    return rightEnabled;
  }
  
  
  /**
   * Sets the color of the border.
   * 
   * @param lineColor the border color
   */
  public void setLineColor(Color lineColor)     {
    this.lineColor = lineColor;
  }
  
  /**
   * Returns the color of the border.
   * @return the border color
   */
  @Override
  public Color getLineColor()     {
    return lineColor;
  }
  
  
  /**
   * Sets the thickness of the border.
   * @param thickness the thickness
   */
  public void setThickness(int thickness)       {
    this.thickness = thickness;
  }

  /**
   * Returns the thickness of the border.
   * @return the thickness
   */
  @Override
  public int getThickness()       {
    return thickness;
  }
  
  
  /**
   * Paints the border for the specified component with the 
   * specified position and size.
   * @param c the component for which this border is being painted
   * @param g the paint graphics
   * @param x the x position of the painted border
   * @param y the y position of the painted border
   * @param width the width of the painted border
   * @param height the height of the painted border
   */
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    
    Color oldColor = g.getColor();
    g.setColor(lineColor);

    if (topEnabled) {
      g.fillRect(x, y, width - 1, thickness);
    }
    if (bottomEnabled) {
      g.fillRect(x, y + height - thickness, width - 1, thickness);
    }
    if (leftEnabled) {
      g.fillRect(x, y, thickness, height - 1);
    }
    if (rightEnabled) {
      g.fillRect(x + width - thickness, y, thickness, height - 1);
    }

    g.setColor(oldColor);
  }

  
  /**
   * Returns the insets of the border.
   * @param c the component for which this border insets value applies
   */
  @Override
  public Insets getBorderInsets(Component c) {
    return new Insets(topEnabled ?    thickness : 0, 
                      leftEnabled ?   thickness : 0, 
                      bottomEnabled ? thickness : 0, 
                      rightEnabled ?  thickness : 0);
  }

  /** 
   * Reinitialize the insets parameter with this Border's current Insets. 
   * @param c the component for which this border insets value applies
   * @param insets the object to be reinitialized
   */
  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    insets.top    = topEnabled ?    thickness : 0;
    insets.left   = leftEnabled ?   thickness : 0;
    insets.bottom = bottomEnabled ? thickness : 0;
    insets.right  = rightEnabled ?  thickness : 0;
    return insets;
  }

}
