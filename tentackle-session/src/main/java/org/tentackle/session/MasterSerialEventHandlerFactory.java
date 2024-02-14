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

import org.tentackle.common.ServiceFactory;

import java.util.function.Consumer;

interface MasterSerialEventHandlerFactoryHolder {
  MasterSerialEventHandlerFactory INSTANCE =
    ServiceFactory.createService(MasterSerialEventHandlerFactory.class, DefaultMasterSerialEventHandlerFactory.class);
}

/**
 * Factory for {@link MasterSerialEvent}-handlers.
 *
 * @author harald
 */
public interface MasterSerialEventHandlerFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static MasterSerialEventHandlerFactory getInstance() {
    return MasterSerialEventHandlerFactoryHolder.INSTANCE;
  }

  /**
   * Creates an event handler for a master serial event class.<br>
   * The handler is simply a {@link Consumer} of a {@link MasterSerialEvent}.
   *
   * @param <T> the event type
   * @param eventClass the event class
   * @return the event handler, null if there is no handler registered for this remote client
   */
  <T extends MasterSerialEvent> Consumer<T> createEventHandler(Class<T> eventClass);

}
