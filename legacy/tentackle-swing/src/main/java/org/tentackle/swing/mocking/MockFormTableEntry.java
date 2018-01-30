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



package org.tentackle.swing.mocking;

import org.tentackle.swing.FormTableEntry;


/**
 * A mocking formtable entry.
 *
 * @author harald
 */
public class MockFormTableEntry extends FormTableEntry {

  /**
   * The mock object
   */
  public static class MockObject {

    /**
     * Gets the mocked column value.
     *
     * @param col the model column
     * @return the value
     */
    public Object getValueAt(int col) {
      switch (col) {
        case ONE:
          return "<1>";
        case TWO:
          return "<2>";
        case THREE:
          return "<3>";
      }
      return null;
    }
  }

  private static final String[] columnNames = {
    "One", "Two", "Three"
  };

  private static final int ONE = 0;
  private static final int TWO = 1;
  private static final int THREE = 2;

  private final MockObject object;

  /**
   * Creates a form table entry.
   */
  public MockFormTableEntry() {
    object = new MockObject();
  }



  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  @Override
  public String getColumnName(int col) {
    return columnNames[col];
  }

  @Override
  public MockObject getObject() {
    return object;
  }


  @Override
  public Object getValueAt(int col) {
    return getObject().getValueAt(col);
  }

  @Override
  public FormTableEntry newInstance(Object object) {
    return new MockFormTableEntry();
  }


}
