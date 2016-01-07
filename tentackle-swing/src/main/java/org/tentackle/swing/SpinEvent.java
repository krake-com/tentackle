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
 * A spinning event.<br>
 * 
 * Used to spin up or down the value in a {@link FormSpinField}.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class SpinEvent extends AWTEvent {


  /** default spin event id **/
  public final static int  SPIN_EVENT_ID = AWTEvent.RESERVED_ID_MAX + 7620;

  /** increment action **/
  public final static int  INCREMENT = 1;
  /** decrement action **/
  public final static int  DECREMENT = 2;


  
  private int action;     // what to do: INCREMENT or DECREMENT


  
  
  /**
   * Creates a spin event
   *
   * @param source    source object (usually this)
   * @param id        event ID
   * @param action    the action code
   */
  public SpinEvent (Object source, int id, int action) {
    super (source, id);
    this.action = action;
  }

  /**
   * Creates a spin event with the default event id.
   *
   * @param source    source object (usually this)
   * @param action    the action code
   */
  public SpinEvent (Object source, int action)  {
    this (source, SPIN_EVENT_ID, action);
  }

  
  /**
   * Gets the action code.
   * 
   * @return the action code (INCREMENT or DECREMENT)
   */
  public int getAction()  {
    return action;
  }

}
