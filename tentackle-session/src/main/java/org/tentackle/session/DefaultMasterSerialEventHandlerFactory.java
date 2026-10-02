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

import org.tentackle.common.Service;
import org.tentackle.reflect.ClassMapper;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The default master serial event handler factory.
 *
 * @author harald
 */
@Service(MasterSerialEventHandlerFactory.class)
public class DefaultMasterSerialEventHandlerFactory implements MasterSerialEventHandlerFactory {

  /**
   * caches the handler per event class, empty if there is no handler.
   */
  @SuppressWarnings("rawtypes")
  private final ConcurrentHashMap<Class, Optional> serviceMap = new ConcurrentHashMap<>();

  /**
   * maps event classes to handler classes, according to {@link MasterSerialEventService}.
   */
  private final ClassMapper eventClassMapper;

  /**
   * Creates the factory.
   */
  public DefaultMasterSerialEventHandlerFactory() {
    eventClassMapper = ClassMapper.create("MasterSerial Events", MasterSerialEvent.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends MasterSerialEvent> Consumer<T> getEventHandler(Class<T> eventClass) {
    Optional<Consumer<T>> handler = serviceMap.computeIfAbsent(eventClass, this::getHandler);
    return handler.orElse(null);
  }


  /**
   * Creates the handler for an event class.<br>
   * Invoked only once per event class, the result is cached by {@link #getEventHandler(Class)}.
   *
   * @param <T> the event type
   * @param eventClass the event class
   * @return the handler, empty if there is no handler for this event class
   * @throws PersistenceException if the handler could not be instantiated
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected <T extends MasterSerialEvent> Optional<Consumer<T>> getHandler(Class<T> eventClass) {
    try {
      Class serviceClass = eventClassMapper.mapLenient(eventClass);
      try {
        return Optional.of((Consumer<T>) serviceClass.getConstructor().newInstance());
      }
      catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new PersistenceException("cannot instantiate " + serviceClass.getName() +
                                       " (master serial event handler for " + eventClass.getName() + ")", e);
      }
    }
    catch (ClassNotFoundException ex) {
      return Optional.empty();
    }
  }

}
