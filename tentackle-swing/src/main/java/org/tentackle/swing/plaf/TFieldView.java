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


package org.tentackle.swing.plaf;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.JLabel;
import javax.swing.text.Element;
import javax.swing.text.FieldView;
import org.tentackle.swing.FormFieldComponent;


/**
 * Extended {@code FieldView}.
 * 
 * @author harald
 */
public class TFieldView extends FieldView {
  
  /**
   * Constructs a new FieldView wrapped on an element.
   *
   * @param elem the element
   */
  public TFieldView(Element elem) {
    super(elem);
  }
    
    
  /**
   * {@inheritDoc}
   * <p>
   * Overridden to allow alignment TOP and BOTTOM.
   * This is used in FormTables if the rowsize is larger than one Font-Height.
   */
  @Override
  protected Shape adjustAllocation(Shape a) {
    if (a != null) {
      Rectangle bounds = a.getBounds();
      int vspan = (int) getPreferredSpan(Y_AXIS);
      if (bounds.height > vspan) {
        Component c = getContainer();
        if (c instanceof FormFieldComponent) {
          int align = ((FormFieldComponent)c).getVerticalAlignment();
          if (align == JLabel.TOP)  {
            bounds.height = vspan;
            return super.adjustAllocation(bounds);
          }
          else if (align == JLabel.BOTTOM)  {
            bounds.y += bounds.height - vspan;
            return super.adjustAllocation(bounds);
          }
        }
      }
    }
    return super.adjustAllocation(a);
  }
    
}
