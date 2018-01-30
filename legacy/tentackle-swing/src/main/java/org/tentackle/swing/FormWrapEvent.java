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

// Created on January 28, 2003, 10:14 AM

package org.tentackle.swing;

import java.awt.AWTEvent;
import java.awt.event.ActionEvent;



/**
 * An event fired whenever the keyboard focus wraps within a form or window.
 * 
 * @author  harald
 */
@SuppressWarnings("serial")
public class FormWrapEvent extends ActionEvent {
  
  /** the wrap event ID **/
  public static final int FORMWRAP_EVENT = AWTEvent.RESERVED_ID_MAX + 4;
  
  /** the default command **/
  public static final String FORMWRAP_COMMAND = "wrap";
  

  /**
   * Constructs an <code>FormWrapEvent</code> object.
   * <p>Note that passing in an invalid <code>id</code> results in
   * unspecified behavior.
   *
   * @param source  the object that originated the event
   * @param id      an integer that identifies the event
   * @param command a string that may specify a command (possibly one 
   *                of several) associated with the event
   */
  public FormWrapEvent(Object source, int id, String command) {
    super(source, id, command, 0);
  }

  /**
   * Constructs an <code>FormWrapEvent</code> object with the
   * default id {@link #FORMWRAP_EVENT}.
   *
   * @param source  the object that originated the event
   * @param command a string that may specify a command (possibly one 
   *                of several) associated with the event
   */
  public FormWrapEvent(Object source, String command){
    this(source, FORMWRAP_EVENT, command);
  }

  /**
   * Constructs an <code>FormWrapEvent</code> object with the
   * default id {@link #FORMWRAP_EVENT} and the default command {@link #FORMWRAP_COMMAND}.
   *
   * @param source  the object that originated the event
   */
  public FormWrapEvent(Object source) {
    this (source, FORMWRAP_COMMAND);
  }
  
}
