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
 * Important: adding an event should <em>only</em> be done via {@link #add(MasterSerialEvent)} and
 * no other method, because the other methods (such as addAll, addFirst, etc...) are not overridden
 * to check for {@link MasterSerialEvent#isOverridingOlderEvents()}!
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
    super(masterSerialEvents);
  }

  /**
   * Creates an empty list event.
   */
  public MasterSerialListEvent() {
    super();
  }

  @Override
  public boolean add(MasterSerialEvent event) {
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
