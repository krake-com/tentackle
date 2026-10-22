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

package org.tentackle.misc;

import org.tentackle.reflect.NotInterceptable;

import java.util.List;

/**
 * Interface for objects providing the snapshot functionality.
 * <p>
 * The number of snapshots is not limited. Objects may be reverted
 * to the state of a snapshot. If reverted, the snapshot itself
 * and all later snapshots are discarded.
 * <p>
 * Important: snapshots cannot be used as copies of an object at a given time. They just
 * contain the information to revert to the object's state when the snapshot was taken!<br>
 * However, {@code Snapshotable}s can be copied using the {@link #copy()} method.
 *
 * @param <T> the type
 * @author harald
 */
public interface Snapshotable<T> {

  /**
   * Creates a snapshot of this object.
   *
   * @return the snapshot
   */
  @NotInterceptable
  T createSnapshot();

  /**
   * Reverts the state of this object to the given snapshot.<br>
   * The snapshot itself and all snapshots taken after it are discarded.
   *
   * @param snapshot the snapshot to revert to
   */
  @NotInterceptable
  void revertToSnapshot(T snapshot);

  /**
   * Returns whether this is a snapshot.
   *
   * @return true if this is a snapshot
   */
  boolean isSnapshot();

  /**
   * Gets all snapshots.<br>
   * The list contains the snapshots in the order they were taken,
   * i.e., the first snapshot comes first.
   *
   * @return the list of snapshots for this object, empty list if none taken so far
   */
  List<? extends T> getSnapshots();

  /**
   * Discards the given snapshot.
   * <p>
   * Notice that depending on the implementation, it's usually not necessary to explicitly
   * discard a snapshot, since {@link java.lang.ref.WeakReference}s are normally used.
   *
   * @param snapshot the snapshot
   */
  void discardSnapshot(T snapshot);

  /**
   * Discards all snapshots.
   *
   * @see #discardSnapshot(Object)
   */
  void discardSnapshots();

  /**
   * Creates a copy of this object.<br>
   * This is done by creating a snapshot and then reverting it into a new object.
   * <p>
   * The copy is semantically equivalent to this object, but not necessarily
   * in all aspects. For example, a copied PDO and all of its components
   * contain the same data, but they are all <em>new</em>, i.e., with their id
   * and serial reset to 0.<br>
   * For a technically identical deep copy, consider {@link org.tentackle.common.ObjectSerializer#copy(Object)}.
   *
   * @return the copied object
   */
  T copy();

  /**
   * Returns whether this is a deep copy.
   *
   * @return true if this object is a copy
   */
  boolean isCopy();

  /**
   * Marks this object as a copy.
   *
   * @param copy true if change to a copy, else change to the non-copied object
   */
  @NotInterceptable
  void setCopy(boolean copy);

}
