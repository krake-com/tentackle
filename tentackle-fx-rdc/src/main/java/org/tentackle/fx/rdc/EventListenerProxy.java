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

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event listener proxy for event handlers and filters.<br>
 * Registers the listeners at a node and keeps track of them.
 * Because all listeners are kept in internal lists, they can be removed
 * all at once explicitly, which works even for lambdas.
 * FX nodes don't provide such a feature.
 *
 * @param <T> the event type
 * @author harald
 */
public class EventListenerProxy<T extends Event> {

  private final Node node;                                    // the node to register listeners for
  private Map<EventType<T>, List<EventHandler<T>>> filters;   // event filters
  private Map<EventType<T>, List<EventHandler<T>>> handlers;  // event handlers

  /**
   * Creates a registration proxy.
   *
   * @param node the target node
   */
  public EventListenerProxy(Node node) {
    this.node = node;
  }


  /**
   * Fires an event at the node.
   *
   * @param event the event
   */
  public void fireEvent(T event) {
    node.fireEvent(event);
  }

  /**
   * Adds an event filter.
   *
   * @param eventType the event type
   * @param filter the filter
   */
  public void addEventFilter(EventType<T> eventType, EventHandler<T> filter) {
    node.addEventFilter(eventType, filter);
    if (filters == null) {
      filters = new HashMap<>();
    }
    addListener(filters, eventType, filter);
  }

  /**
   * Removes an event filter.
   *
   * @param eventType the event type
   * @param filter the filter
   */
  public void removeEventFilter(EventType<T> eventType, EventHandler<T> filter) {
    node.removeEventFilter(eventType, filter);
    if (filters != null) {
      removeListener(filters, eventType, filter);
    }
  }


  /**
   * Adds an event handler.
   *
   * @param eventType the event type
   * @param handler the handler
   */
  public void addEventHandler(EventType<T> eventType, EventHandler<T> handler) {
    node.addEventHandler(eventType, handler);
    if (handlers == null) {
      handlers = new HashMap<>();
    }
    addListener(handlers, eventType, handler);
  }

  /**
   * Removes an event handler.
   *
   * @param eventType the event type
   * @param handler the handler
   */
  public void removeEventHandler(EventType<T> eventType, EventHandler<T> handler) {
    node.removeEventHandler(eventType, handler);
    if (handlers != null) {
      removeListener(handlers, eventType, handler);
    }
  }


  /**
   * Removes all event filters.
   */
  public void removeAllEventFilters() {
    if (filters != null) {
      for (Map.Entry<EventType<T>, List<EventHandler<T>>> entry: filters.entrySet()) {
        EventType<T> eventType = entry.getKey();
        for (EventHandler<T> listener: entry.getValue()) {
          node.removeEventFilter(eventType, listener);
        }
      }
      filters.clear();
    }
  }

  /**
   * Removes all event handlers.
   */
  public void removeAllEventHandlers() {
    if (handlers != null) {
      for (Map.Entry<EventType<T>, List<EventHandler<T>>> entry: handlers.entrySet()) {
        EventType<T> eventType = entry.getKey();
        for (EventHandler<T> listener: entry.getValue()) {
          node.removeEventHandler(eventType, listener);
        }
      }
      handlers.clear();
    }
  }



  private void addListener(Map<EventType<T>, List<EventHandler<T>>> map, EventType<T> eventType, EventHandler<T> handler) {
    List<EventHandler<T>> list = map.computeIfAbsent(eventType, k -> new ArrayList<>());
    list.add(handler);
  }

  private void removeListener(Map<EventType<T>, List<EventHandler<T>>> map, EventType<T> eventType, EventHandler<T> handler) {
    List<EventHandler<T>> list = map.get(eventType);
    if (list != null) {
      list.remove(handler);
    }
  }

}
