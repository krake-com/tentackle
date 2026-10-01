/*
 * Tentackle - https://tentackle.org
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


package org.tentackle.bind;

import java.io.Serial;
import java.util.EventObject;

/**
 * A binding event.<br>
 * Fired to the {@link ToViewListener}s and {@link ToModelListener}s before the value is transferred
 * between model and view.
 *
 * @author harald
 */
public class BindingEvent extends EventObject {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The kind of transfer.
   */
  public enum Type {

    /**
     * from model via getter to view.
     */
    TO_VIEW_VIA_GETTER,

    /**
     * from model via field to view.
     */
    TO_VIEW_VIA_FIELD,

    /**
     * from view to model via setter.
     */
    TO_MODEL_VIA_SETTER,

    /**
     * from view to model via from-method.
     */
    TO_MODEL_VIA_FROM_METHOD,

    /**
     * from view to model via field.
     */
    TO_MODEL_VIA_FIELD

  }


  private final Type type;                // the event type
  private final transient Object parent;  // parent (holder) of the value
  private final transient Object value;   // the data value


  /**
   * Creates a binding event.
   *
   * @param source the binding source
   * @param type the action type
   * @param parent the parent object containing the value
   * @param value the value to be transferred
   */
  public BindingEvent(Binding source, Type type, Object parent, Object value) {
    super(source);
    this.type = type;
    this.parent = parent;
    this.value = value;
  }


  @Override
  public Binding getSource() {
    return (Binding) source;
  }

  /**
   * Gets the event type.
   *
   * @return the type
   */
  public Type getType() {
    return type;
  }

  /**
   * Gets the parent object containing the value.
   *
   * @return the parent object
   */
  public Object getParent() {
    return parent;
  }

  /**
   * Gets the value to be transferred.
   *
   * @return the value
   */
  public Object getValue() {
    return value;
  }

  @Override
  public String toString() {
    return super.toString() + "[" + type + ": " + value + "]";
  }

}
