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

package org.tentackle.trip.impl;

import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.ref.Cleaner;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of a dictionary.
 */
public class DefaultDictionary implements Dictionary {

  private static final Logger LOGGER = Logger.get(DefaultDictionary.class);

  private static final long WAIT_TIME = 1000;     // wait time in ms
  private static final int WARN_WAIT_LOOPS = 60;  // log a warning once a minute
  private static final int MAX_WAIT_LOOPS = 300;  // 5-minutes max. wait time

  /**
   * Used to set the closed flag.
   */
  private static class Resources implements Runnable {
    private final UUID id;
    private boolean closed;

    private Resources(UUID id) {
      this.id = id;
      LOGGER.info("dictionary {0} created", id);
    }

    @Override
    public void run() {
      closed = true;
      LOGGER.info("dictionary {0} closed", id);
    }
  }

  private static final Cleaner CLEANER = Cleaner.create();        // instead of deprecated finalize()

  private final UUID id;
  private final boolean server;
  private final Map<Integer, DictionaryEntry> entriesByOrdinal = new ConcurrentHashMap<>();
  private final Map<String, DictionaryEntry> entriesByName = new ConcurrentHashMap<>();
  private final AtomicInteger ordinalCounter = new AtomicInteger();
  private final AtomicInteger temporaryOrdinalCounter = new AtomicInteger();
  private final Resources resources;

  /**
   * Creates a dictionary.
   *
   * @param id the ID of the dictionary
   * @param server true if this is a server dictionary, false if client dictionary
   */
  public DefaultDictionary(UUID id, boolean server) {
    this.id = Objects.requireNonNull(id);
    this.server = server;
    resources = new Resources(id);
    CLEANER.register(this, resources);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isServer() {
    return server;
  }

  @Override
  public boolean isEmpty() {
    // entriesByOrdinal holds the permanent as well as the temporary (client-only) entries
    return entriesByOrdinal.isEmpty();
  }

  @Override
  public String toString() {
    return id.toString();
  }

  @Override
  public DictionaryEntry createEntry(Class<?> clazz) {
    return new DefaultDictionaryEntry(ordinalCounter.getAndIncrement(),
                                      ReflectionHelper.getSerializableClassName(clazz), Proxy.isProxyClass(clazz), getComponents(clazz));
  }

  @Override
  public DictionaryEntry createTemporaryEntry(Class<?> clazz) {
    if (isServer()) {
      throw new TripRuntimeException("temporary entries can be created by stream clients only");
    }
    return new DefaultDictionaryEntry(temporaryOrdinalCounter.decrementAndGet(),
                                      ReflectionHelper.getSerializableClassName(clazz), Proxy.isProxyClass(clazz), getComponents(clazz));
  }

  @Override
  public DictionaryEntry createEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components) {
    return new DefaultDictionaryEntry(ordinal, className, isProxy, components);
  }

  @Override
  public DictionaryEntryComponent createComponent(String componentName, TripType<?> type) {
    return new DefaultDictionaryEntryComponent(componentName, type);
  }

  @Override
  public synchronized void addEntry(DictionaryEntry entry) {
    // synchronized since two maps are involved and entries can be added concurrently,
    // i.e., this operation must be atomic!
    if (entry.isClientOnly()) {
      DictionaryEntry oldEntry = entriesByName.put(entry.getClassName(), entry);
      if (oldEntry != null) {
        entriesByOrdinal.remove(oldEntry.getOrdinal());
      }
      entriesByOrdinal.put(entry.getOrdinal(), entry);
    }
    else {
      DictionaryEntry oldEntry = entriesByOrdinal.get(entry.getOrdinal());
      if (oldEntry != null) {
        throw new TripRuntimeException("duplicate ordinal: " + entry + ", existing: " + oldEntry);
      }
      oldEntry = entriesByName.get(entry.getClassName());
      if (oldEntry != null) {
        if (oldEntry.isClientOnly()) {
          // don't remove temporary client entries from entriesByOrdinal in server!
          // We have to keep them in case older data is received via another connection
          // from the same client in parallel (which is rare, but theoretically possible).
          if (!server) {
            // removing from the client dictionary is ok since the server will never send client entries back
            entriesByOrdinal.remove(oldEntry.getOrdinal());
          }
        }
        else {
          throw new TripRuntimeException("duplicate ordinal and class name: " + entry + ", existing: " + oldEntry);
        }
      }
      entriesByOrdinal.put(entry.getOrdinal(), entry);
      entriesByName.put(entry.getClassName(), entry);
    }
    this.notifyAll();
  }

  @Override
  public DictionaryEntry getEntry(int ordinal) {
    DictionaryEntry entry = entriesByOrdinal.get(ordinal);
    if (entry != null) {
      return entry;
    }

    // Since the dictionary is shared among connections from the same client
    // and among connections to the same server, we must wait for the entry to be added
    // if it is missing.
    // This is necessary to avoid race conditions when a connection#1 sends a dictionary entry,
    // then connection#2 then sends the ordinal, but at the receiver's side connection#2 is
    // handled before connection#1.
    // At the receiving side the waiting thread is virtual (cheap), and when the
    // connections are closed, they are released to the GC together with their shared dictionary.
    int loops = 0;
    while (entry == null && !resources.closed && loops < MAX_WAIT_LOOPS) {
      if (++loops % WARN_WAIT_LOOPS == 0) {
        LOGGER.warning("still waiting for entry with ordinal " + ordinal + " for dictionary " + this + " ...");
      }
      synchronized (this) {
        try {
          this.wait(WAIT_TIME);
          entry = entriesByOrdinal.get(ordinal);
        }
        catch (InterruptedException e) {
          // Don't throw! Just remember that the thread was interrupted
          LOGGER.fine("interrupted while waiting for entry with ordinal {0} -> keep waiting", ordinal);
          Thread.currentThread().interrupt();
        }
      }
    }
    if (entry == null) {
      // Either the other connection failed to send the entry (which should not take that long!)
      // or there is something wrong with our implementation.
      // -> force closing the connection
      throw new TripRuntimeException("receiving entry with ordinal " + ordinal + " timed out for dictionary " + this);
    }
    return entry;
  }

  @Override
  public DictionaryEntry getEntry(String className) {
    return entriesByName.get(className);
  }

  /**
   * Gets the component names for the given class.
   *
   * @param clazz the class
   * @return the component names
   */
  protected DictionaryEntryComponent[] getComponents(Class<?> clazz) {
    TripComponent<?, ?>[] components = TripFactory.getInstance().getType(clazz).getComponents();
    DictionaryEntryComponent[] entryComponents = new DictionaryEntryComponent[components.length];
    Set<Integer> usedOrdinals = new HashSet<>();
    for (TripComponent<?, ?> component : components) {
      int ordinal = component.getOrdinal();
      if (ordinal < 0 || ordinal >= entryComponents.length) {
        throw new TripRuntimeException("ordinal " + ordinal + " of component " + component + " out of range for " + clazz);
      }
      if (!usedOrdinals.add(ordinal)) {
        throw new TripRuntimeException("duplicate ordinal " + ordinal + " of component " + component + " for " + clazz);
      }
      entryComponents[ordinal] = createComponent(component.getName(), component.getType());
    }
    return entryComponents;
  }

}
