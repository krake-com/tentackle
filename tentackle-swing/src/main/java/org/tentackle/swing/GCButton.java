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


package org.tentackle.swing;

import java.awt.event.ActionEvent;

/**
 * Garbage Collector and Memorystatus Button.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class GCButton extends FormButton {

  /**
   * Creates a GC Button
   */
  public GCButton() {
    super();
  }

  /**
   * Refreshes the view of the button showing the
   * allocated and used memory.
   */
  public void refresh() {
    setText((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1048576 +
            "M / " + Runtime.getRuntime().totalMemory()/1048576 + "M");
  }

  @Override
  protected void fireActionPerformed(final ActionEvent event) {
    super.fireActionPerformed(event);
    Runtime.getRuntime().gc();
    refresh();
  }


}
