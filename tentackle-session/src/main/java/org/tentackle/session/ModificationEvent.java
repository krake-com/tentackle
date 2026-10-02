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

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modification event.
 * <p>
 * Modification events come in three flavors, which is determined by the corresponding
 * {@link ModificationListener}.
 * <ol>
 * <li>master: master modification event for <em>any</em> modification</li>
 * <li>named event: a single named modification event (usually a table)</li>
 * <li>list of named events: modification for more than one name</li>
 * </ol>
 *
 * @author harald
 */
public class ModificationEvent implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  /** the session. */
  private final transient Session session;

  /** the modification details. */
  @SuppressWarnings("serial")
  private final Map<String,ModificationEventDetail> details;    //  null if master event

  /** the modification serial, master, or of the first detail. */
  private final long serial;

  /** the first modification name, null if master. */
  private final String name;


  /**
   * Creates a named modification event.<br>
   * The first detail determines the {@link #getName() name} and {@link #getSerial() serial} of the event.
   *
   * @param session the session, never null
   * @param details the modification details, one for each name, never null or empty
   */
  public ModificationEvent(Session session, Collection<ModificationEventDetail> details) {
    this.session = Objects.requireNonNull(session, "session");
    if (details == null || details.isEmpty()) {
      throw new IllegalArgumentException("missing details for named modification event");
    }
    this.details = new HashMap<>();
    ModificationEventDetail firstDetail = null;
    for (ModificationEventDetail detail: details) {
      Objects.requireNonNull(detail, "detail");
      if (firstDetail == null) {
        firstDetail = detail;
      }
      this.details.put(detail.getName(), detail);
    }
    this.serial = firstDetail.getSerial();
    this.name = firstDetail.getName();
  }


  /**
   * Creates a modification master event.
   *
   * @param session the session, never null
   * @param serial the master modification serial
   */
  public ModificationEvent(Session session, long serial) {
    this.session = Objects.requireNonNull(session, "session");
    this.details = null;
    this.serial = serial;
    this.name = null;
  }


  /**
   * Gets the session.
   *
   * @return the session
   */
  public Session getSession() {
    return session;
  }


  /**
   * Returns whether this is a master event.<br>
   * Master events provide no details.
   *
   * @return true if master event
   */
  public boolean isMasterEvent() {
    return details == null;
  }

  /**
   * Returns whether this is a named single event.
   *
   * @return true if a single event
   */
  public boolean isSingleEvent() {
    return !isMasterEvent() && details.size() == 1;
  }

  /**
   * Returns whether this is a multi-named event.
   *
   * @return true if a multi-named event
   */
  public boolean isMultiEvent() {
    return !isMasterEvent() && details.size() > 1;
  }

  /**
   * Gets the modification serial of the first detail, or the master serial.
   *
   * @return the serial
   */
  public long getSerial() {
    return serial;
  }

  /**
   * Gets the modification name of the first detail.
   *
   * @return the name, null if master event
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the modification details.<br>
   * Master events provide no details, so listeners can iterate the result unconditionally.
   *
   * @return the details, never null, empty if master event
   * @see #isMasterEvent()
   */
  public Collection<ModificationEventDetail> getDetails() {
    return details == null ? Collections.emptyList() : details.values();
  }

  /**
   * Gets the modification detail for a name.
   *
   * @param name the name
   * @return the detail, null if no such detail or master event
   */
  public ModificationEventDetail getDetail(String name) {
    return details == null ? null : details.get(name);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (isMasterEvent()) {
      buf.append("MASTER:").append(getSerial());
    }
    else {
      boolean first = true;
      for (ModificationEventDetail detail: getDetails()) {
        if (first) {
          first = false;
        }
        else {
          buf.append(", ");
        }
        buf.append(detail);
      }
    }
    return buf.toString();
  }

}
