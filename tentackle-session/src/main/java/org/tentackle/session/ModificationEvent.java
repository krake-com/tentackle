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
import java.util.HashMap;
import java.util.Iterator;
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

  /** the modification serial, master, or first of details. */
  private final long serial;

  /** the first modification name, null if master. */
  private final String name;


  /**
   * Creates a modification event.
   *
   * @param session the session, never null
   * @param details the modification details, one for each name
   */
  public ModificationEvent(Session session, Collection<ModificationEventDetail> details) {
    this.session = Objects.requireNonNull(session, "session");
    if (details != null && !details.isEmpty()) {
      this.details = new HashMap<>();
      Iterator<ModificationEventDetail> iter = details.iterator();
      ModificationEventDetail detail = iter.next();
      this.serial = detail.getSerial();
      this.name = detail.getName();
      do {
        this.details.put(detail.getName(), detail);
      }
      while (iter.hasNext() && (detail = iter.next()) != null);
    }
    else  {
      throw new IllegalArgumentException("missing details for named modification event");
    }
  }


  /**
   * Creates a modification master event.
   *
   * @param session the session, never null
   * @param serial the master modification serial
   */
  public ModificationEvent(Session session, long serial) {
    this.session = session;
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
   * Returns whether this a multi-named event.
   *
   * @return true if a multi-named event
   */
  public boolean isMultiEvent() {
    return !isMasterEvent() && details.size() > 1;
  }

  /**
   * Gets the modification serial of the first detail or master.
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
   * Gets the modification details.
   *
   * @return the details, null if master event
   */
  public Collection<ModificationEventDetail> getDetails() {
    return details.values();
  }

  /**
   * Gets the modification detail for a name.
   *
   * @param name the name
   * @return the detail, null if no such detail
   */
  public ModificationEventDetail getDetail(String name) {
    return details.get(name);
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
