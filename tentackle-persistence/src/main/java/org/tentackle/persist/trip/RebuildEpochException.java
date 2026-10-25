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

package org.tentackle.persist.trip;

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;
import org.tentackle.session.PersistenceException;

import java.io.Serial;

/**
 * Thrown by the receiving {@link TripModificationLogSerializer} if the dictionary of an epoch must be rebuilt.
 * <p>
 * This happens if the receiver was restarted and lacks the entries of the modlogs it processed before, or if it
 * already discarded the dictionary of the epoch. The receiver rebuilds the dictionary via
 * {@link TripModificationLogSerializer#rebuildEpoch(long, Iterable)} and retries.
 */
public class RebuildEpochException extends PersistenceException {

  @Serial
  private static final long serialVersionUID = 1L;

  /** the epoch to rebuild. */
  private final long epoch;

  /**
   * Creates a rebuild epoch exception.
   *
   * @param epoch the epoch to rebuild
   * @param message the detail message
   */
  @CanonicalConstructor
  public RebuildEpochException(@CanonicalName("epoch") long epoch,
                               @CanonicalName("message") String message) {
    super(message);
    this.epoch = epoch;
  }

  /**
   * Gets the epoch to rebuild.
   *
   * @return the epoch
   */
  public long getEpoch() {
    return epoch;
  }

}
