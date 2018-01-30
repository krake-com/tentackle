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

import java.awt.Component;
import java.beans.PropertyEditorSupport;


/**
 * PropertyEditor for cell traversal.
 *
 * @author harald
 */
public class CellTraversalPropertyEditor extends PropertyEditorSupport {

  private final CellTraversalPropertyEditorPanel panel;
  private boolean superPropertyChange;


  public CellTraversalPropertyEditor() {
    panel = new CellTraversalPropertyEditorPanel(this);
  }

  @Override
  public boolean supportsCustomEditor() {
    return true;
  }

  @Override
  public Component getCustomEditor() {
    return panel;
  }

  @Override
  public void firePropertyChange() {
    if (superPropertyChange) {
      super.firePropertyChange();
    }
    else {
      super.setValue(panel.getCellTraversal());
    }
  }

  @Override
  public void setValue(Object value) {
    superPropertyChange = true;
    super.setValue(value);
    panel.setCellTraversal(value instanceof Integer ? (Integer) value : 0);
    superPropertyChange = false;
  }

  @Override
  public void setAsText(String text) throws IllegalArgumentException {
    try {
      setValue(Integer.parseInt(text));
    }
    catch (Exception e) {
      setValue(0);
    }
  }

}
