/*
 * Tentackle - https://tentackle.org
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

package org.tentackle.fx.rdc;

import javafx.beans.NamedArg;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

import org.tentackle.pdo.PersistentDomainObject;

import java.io.Serial;

/**
 * Event related to persistence operations on a PDO.<br>
 * Fired by the {@link org.tentackle.fx.rdc.crud.PdoCrud} to the handlers and filters registered on it.
 *
 * @author harald
 */
public class PdoEvent extends Event {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Common supertype for all PDO event types.
   */
  public static final EventType<PdoEvent> ANY = new EventType<>(Event.ANY, "PDO");


  /**
   * A new PDO has been inserted into the database.
   */
  public static final EventType<PdoEvent> CREATE = new EventType<>(PdoEvent.ANY, "CREATE");

  /**
   * A PDO has been read from the database.
   */
  public static final EventType<PdoEvent> READ = new EventType<>(PdoEvent.ANY, "READ");

  /**
   * An existing PDO has been updated in the database.
   */
  public static final EventType<PdoEvent> UPDATE = new EventType<>(PdoEvent.ANY, "UPDATE");

  /**
   * A PDO has been deleted from the database.
   */
  public static final EventType<PdoEvent> DELETE = new EventType<>(PdoEvent.ANY, "DELETE");



  /**
   * The PDO the event refers to.
   */
  private final PersistentDomainObject<?> pdo;


  /**
   * Creates a PDO event.
   *
   * @param pdo the PDO
   * @param target the event target
   * @param eventType the event type
   */
  public PdoEvent(@NamedArg("source") PersistentDomainObject<?> pdo,
                  @NamedArg("target") EventTarget target,
                  @NamedArg("eventType") EventType<? extends PdoEvent> eventType) {
    super(target, target, eventType);
    this.pdo = pdo;
  }

  /**
   * Gets the PDO.
   *
   * @return the PDO
   */
  public PersistentDomainObject<?> getPdo() {
    return pdo;
  }

  @Override
  @SuppressWarnings("unchecked")
  public EventType<PdoEvent> getEventType() {
    return (EventType<PdoEvent>) super.getEventType();
  }

  @Override
  public PdoEvent copyFor(Object newSource, EventTarget newTarget) {
    return (PdoEvent) super.copyFor(newSource, newTarget);
  }

  @Override
  public String toString() {
    return "PdoEvent [" +
            "pdo = " + pdo.toGenericString() +
            ", source = " + getSource() +
            ", target = " + getTarget() +
            ", eventType = " + getEventType() +
            ", consumed = " + isConsumed() +
            "]";
  }

}
