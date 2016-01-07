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



/**
 * A SpinFeld to edit minutes or seconds.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class MinSecSpinField extends FormSpinField implements SpinListener {

  private final IntegerFormField  minSecField = new IntegerFormField();


  /**
   * Creates an min/sec spin field.
   *
   * @param minSec the initial minutes or seconds (0-59)
   */
  public MinSecSpinField(int minSec) {
    minSecField.setColumns(2);
    minSecField.setFormat("00");
    setFormComponent(minSecField);
    setMinSec(minSec);
    addSpinListener(this);
  }

  /**
   * Creates a min/sec spin field.
   */
  public MinSecSpinField() {
    this(0);
  }


  @Override
  public void valueEntered (ValueEvent e)  {
    super.valueEntered(e);
    setMinSec(getMinSec());
  }


  /**
   * Sets the minutes or seconds.
   * Will be aligned by modulo 60.
   *
   * @param minSec the minutes or seconds
   */
  public void setMinSec (int minSec)  {
    minSec %= 60; // align
    if (minSec < 0) {
      minSec += 60;
    }
    minSecField.setIntValue(minSec);
  }


  /**
   * Gets the minutes or seconds
   * @return the min/sec value 0-59
   */
  public int getMinSec()  {
    return minSecField.getIntValue();
  }


  @Override
  public void increment (SpinEvent e, FormComponent c)  {
    setMinSec(getMinSec() + 1);
  }

  @Override
  public void decrement (SpinEvent e, FormComponent c)  {
    setMinSec(getMinSec() - 1);
  }
}