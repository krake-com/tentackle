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

import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A master serial event containing a list of events.<br>
 * This event type is used whenever there is more than one event pending for the client.
 * <p>
 * Appending an event drops the older events it overrides, see
 * {@link MasterSerialEvent#isOverridingOlderEvents()}. This holds for the constructors,
 * {@link #add(MasterSerialEvent)} and {@link #addAll(Collection)}.
 * <p>
 * Important: it does <em>not</em> hold for the positional and sequenced mutators inherited from
 * {@link ArrayList} (such as {@code add(int, event)}, {@code addFirst} or {@code set}), which are
 * not overridden. Inserting an event at a position is not meaningful for an event list anyway,
 * so append it instead.
 */
@TripReferencable(TripReferencable.NEVER)
public class MasterSerialListEvent extends ArrayList<MasterSerialEvent> implements MasterSerialEvent {

  @Serial
  private static final long serialVersionUID = 1L;

  private long serial;

  /**
   * Creates an event holding a list of events.
   *
   * @param masterSerialEvents the list of events
   */
  public MasterSerialListEvent(Collection<MasterSerialEvent> masterSerialEvents) {
    super();
    for (MasterSerialEvent event: masterSerialEvents) {
      addEvent(event);    // not addAll(): must not call an overridable method from the constructor
    }
  }

  /**
   * Creates an empty list event.
   */
  public MasterSerialListEvent() {
    super();
  }

  @Override
  public boolean add(MasterSerialEvent event) {
    return addEvent(event);
  }

  @Override
  public boolean addAll(Collection<? extends MasterSerialEvent> events) {
    boolean added = false;
    for (MasterSerialEvent event: events) {
      added |= addEvent(event);
    }
    return added;
  }

  /**
   * Appends an event, dropping the older events it overrides.
   *
   * @param event the event to append
   * @return true (as specified by {@link Collection#add})
   */
  private boolean addEvent(MasterSerialEvent event) {
    if (event.isOverridingOlderEvents()) {
      // remove older event, if any.
      // since the list contains only very few elements, we can afford sequential scanning
      removeIf(event::isOverridingEvent);
    }
    return super.add(event);
  }

  @Override
  public void setSerial(long serial) {
    this.serial = serial;
  }

  @Override
  public long serial() {
    return serial;
  }

}
