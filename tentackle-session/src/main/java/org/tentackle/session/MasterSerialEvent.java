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

package org.tentackle.session;

/**
 * Interface for a master serial event.<br>
 * Such events can be sent from the server to the client via the {@link ModificationTracker}.
 * <p>
 * Implementing such an application-specific event involves the following steps:
 * <ul>
 *   <li>
 *   create a class implementing {@link MasterSerialEvent}.<br>
 *   Example:
 *   <pre>
 *    public class MyEvent implements MasterSerialEvent {
 *
 *      private long serial;
 *
 *      &#64;Override
 *      public void setSerial(long serial) {
 *        this.serial = serial;
 *      }
 *
 *      &#64;Override
 *      public long serial() {
 *        return serial;
 *      }
 *
 *      // plus extra payload specific to this event
 *    }
 *   </pre>
 *   </li>
 *   <li>
 *   create a {@link java.util.function.Consumer} for this event which will serve as its event handler.
 *   The handler must be annotated with {@link MasterSerialEventService}. This is a {@link org.tentackle.common.MappedService}
 *   and provides the mapping of the event to its handler, which allows providing different handlers for different
 *   clients connecting to the same server. Furthermore, the server does not need to know the handlers at all.<br>
 *   Example:
 *   <pre>
 *     &#64;MasterSerialEventService(MyEvent.class)
 *     public class MyEventHandler implements Consumer&lt;MyEvent&gt; {
 *
 *       &#64;Override
 *       public void accept(MyEvent event) {
 *         // handle event
 *       }
 *     }
 *   </pre>
 *   </li>
 *   <li>
 *   to send an event from the server to the client, use the method
 *   <code>org.tentackle.dbms.trip.RemoteDbSessionImpl#addMasterSerialEvent</code>, which is part of the
 *   module <code>tentackle-database</code>.
 *   </li>
 * </ul>
 * <p>
 * If the event is a {@link java.util.Collection}, the elements of the event are
 * processed instead of the event itself, see {@code org.tentackle.dbms.DbModificationTracker#extractMasterSerial}.
 */
public interface MasterSerialEvent extends MasterSerial {

  /**
   * Sets the serial value.
   *
   * @param serial the serial
   */
  void setSerial(long serial);

  /**
   * Returns whether only the latest event counts.<br>
   * Useful for events that contain status information or alike.
   *
   * @return true if replace older event of this type, false to deliver each event
   */
  default boolean isOverridingOlderEvents() {
    return false;
  }

  /**
   * Returns whether this event overrides the given older event.<br>
   * The method is only invoked if {@link #isOverridingOlderEvents()} returns true.
   *
   * @param olderEvent the older event
   * @return true if this event should replace the older event
   */
  default boolean isOverridingEvent(MasterSerialEvent olderEvent) {
    return getClass() == olderEvent.getClass();
  }

}
