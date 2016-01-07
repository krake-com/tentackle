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

import java.awt.AWTEvent;


/**
 * Value event to implement the data binding between 
 * {@link FormComponent}s and the application's data.
 * 
 * @author harald
 */
public class ValueEvent extends AWTEvent {
  
  private int action;     // what to do: SET, GET

  /** default event id **/
  public final static int FORMCN_EVENT = AWTEvent.RESERVED_ID_MAX + 7619;

  /** action code to set values (update the view) **/
  public final static int SET = 1;
  /** action code to get values (update the data) **/
  public final static int GET = 2;

  
  
  /**
   * Creates a ValueEvent.
   *
   * @param source the source object
   * @param id the event ID
   * @param action the action code
   */
  public ValueEvent (Object source, int id, int action) {
      super (source, id);
      this.action = action;
  }

  /**
   * Creates a ValueEvent with the
   * default event ID {@link #FORMCN_EVENT}.
   *
   * @param source the source object
   * @param action the action code
   */
  public ValueEvent (Object source, int action)  {
      this (source, FORMCN_EVENT, action);
  }

  
  /**
   * Gets the action code.
   * @return one of SET or GET
   */
  public int getAction()  {
    return action;
  }

}
