/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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

// Created on December 12, 2004, 4:17 PM

package org.tentackle.swing;

/**
 * The TooltipDisplay displays the tooltip of a FormComponent
 * usually in a StringFormField in a FormPanel.
 * FormComponents thus can display the tooltip as soon as
 * they receive focus.
 * See {@link org.tentackle.appworx.TooltipAndErrorPanel} for an example.
 *
 * @author harald
 */
public interface TooltipDisplay {
  
  /**
   * Sets the text for the tooltip.
   * 
   * @param text the tooltip text
   */
  public void setTooltip(String text);
  
}
