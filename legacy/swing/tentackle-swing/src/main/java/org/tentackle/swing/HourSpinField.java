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
 * SpinField for hours.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class HourSpinField extends FormSpinField implements SpinListener {

  private final IntegerFormField  hourField = new IntegerFormField();


  /**
   * Creates an hour spin field.
   *
   * @param hour the initial hour (0-23)
   */
  public HourSpinField(int hour) {
    hourField.setColumns(2);
    hourField.setFormat("00");
    setFormComponent(hourField);
    setHour(hour);
    addSpinListener(this);
  }

  /**
   * Creates an hour spin field.
   */
  public HourSpinField()  {
    this(0);
  }



  @Override
  public void valueEntered (ValueEvent e)  {
    super.valueEntered(e);
    setHour(getHour());
  }


  /**
   * Sets the hour.
   * Will be aligned by modulo 24.
   *
   * @param hour the hour
   */
  public void setHour(int hour)  {
    hour %= 24; // align
    if (hour < 0) {
      hour += 24;
    }
    hourField.setIntValue(hour);
  }


  /**
   * Gets the hour
   * @return the hour 0-23
   */
  public int getHour()  {
    return hourField.getIntValue();
  }


  @Override
  public void increment (SpinEvent e, FormComponent c)  {
    setHour(getHour() + 1);
  }

  @Override
  public void decrement (SpinEvent e, FormComponent c)  {
    setHour(getHour() - 1);
  }

}