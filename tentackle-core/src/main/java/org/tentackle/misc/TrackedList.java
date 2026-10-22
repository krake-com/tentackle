/*
 * Tentackle - https://tentackle.org.
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

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * A {@link List} with the ability to tell whether it has been structurally modified.
 * <p>
 * {@code TrackedList} distinguishes between additions and removals.
 * A replacement is treated as a removal followed by an addition.
 * All removed objects are kept in a separate list.
 * <p>
 * The main usage is in OR-mapping to check whether a list should
 * be written back to persistent storage and which elements
 * have to be deleted and which to be inserted or updated.
 * However, the TrackedList is not restricted to PDOs.
 * <p>
 * {@code TrackedList} can be made immutable. If the objects in the list implement
 * {@link Immutable}, they will be set to immutable as well.
 * <p>
 * {@code TrackedList} supports snapshots. There may be as many snapshots
 * as needed. Reverting to a snapshot restores the complete state
 * at the time the snapshot was taken and invalidates all later snapshots.
 * Snapshots also save the state of the objects contained in the list
 * if they implement {@link Snapshotable}. Otherwise, only the references
 * to those objects are copied to the snapshot. Snapshots are immutable.
 * <p>
 * {@code TrackedList} may be configured to throw a {@link NullPointerException} when trying
 * to add a null value to the list.
 *
 * @author harald
 *
 * @param <E> the element type
 */
public interface TrackedList<E> extends List<E>, ImmutableCollection<E>, Snapshotable<TrackedList<E>>, Serializable {

  /**
   * Creates a snapshot from a list.
   *
   * @param list the list
   * @param <E> the element type
   * @return the snapshot, null if list is null
   */
  static <E> TrackedList<E> createSnapshot(TrackedList<E> list) {
    return list == null ? null : list.createSnapshot();
  }

  /**
   * Reverts a list to a snapshot'd state of the list.<br>
   * If the original list is null, a new list is created as a copy of the snapshot.
   *
   * @param list the original list to be reverted, null if copy
   * @param snapshot the snapshot
   * @param <E> the element type
   * @return the reverted list, null if snapshot was null
   */
  static <E> TrackedList<E> revertToSnapshot(TrackedList<E> list, TrackedList<E> snapshot) {
    TrackedList<E> revertedList = null;
    if (snapshot != null) {
      boolean asCopy = list == null;
      revertedList = asCopy ? new TrackedArrayList<>() : list;
      revertedList.setCopy(asCopy);
      revertedList.revertToSnapshot(snapshot);
    }
    return revertedList;
  }


  /**
   * Sets the modified state.
   *
   * @param modified true to set modified to true,
   *        false to clear all other flags including the list of removed objects
   */
  void setModified(boolean modified);

  /**
   * Gets the modification state.<br>
   * Returns whether objects have been added, removed or replaced or
   * {@code setModified(true)} was invoked.
   *
   * @return true if modified.
   */
  boolean isModified();

  /**
   * Returns whether some elements are modified.<br>
   * The elements must implement {@link Modifiable}.
   * Otherwise, the method returns false.
   *
   * @return true if at least one {@link Modifiable} element is modified
   */
  boolean isElementModified();

  /**
   * Returns whether some object has been removed.
   *
   * @return true if removed
   */
  boolean isSomeRemoved();

  /**
   * Returns whether some object has been added.
   *
   * @return true if added
   */
  boolean isSomeAdded();


  /**
   * Gets the removed objects in the order of their removal.
   *
   * @return the list of removed objects, never null
   */
  Collection<E> getRemovedObjects();

  /**
   * Replaces the element at the specified position in this list with
   * the specified element.<br>
   * The new element will replace the existing one <em>without</em> affecting the modification state
   * of the list, and no listener is notified.
   * Useful if parts of an object tree are reloaded (for example, from the
   * database backend) and should not change the modification state
   * of the whole object tree.
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   * @return the element previously at the specified position
   */
  E replaceSilently(int index, E element);

  /**
   * Adds the element to the list only if not already in the list.
   *
   * @param element the element to add
   * @return true if added, false if element already in list
   */
  boolean addIfAbsent(E element);

  /**
   * Adds the element to the list but does not mark it modified.<br>
   * No listener is notified.
   *
   * @param element the element to add
   */
  void addBlunt(E element);

  /**
   * Removes the element from the list but does not mark it modified.<br>
   * The element is not added to the removed objects and no listener is notified.
   *
   * @param element the element to remove
   * @return true if element removed
   */
  boolean removeBlunt(E element);

  /**
   * Removes the element at given index from the list but does not mark it modified.<br>
   * The element is not added to the removed objects and no listener is notified.
   *
   * @param index the index
   * @return the removed element
   */
  E removeBlunt(int index);

  /**
   * Adds a listener.
   *
   * @param listener the listener
   */
  void addListener(TrackedListListener<E> listener);

  /**
   * Removes a listener.
   *
   * @param listener the listener
   * @return true if listener(s) removed, false if no such listener
   */
  boolean removeListener(TrackedListListener<E> listener);

  /**
   * Returns whether adding null values is allowed.
   *
   * @return true if allowed (default)
   */
  boolean isNullAllowed();

  /**
   * Configures the list whether to allow adding null values.<br>
   * By default, {@code TrackedList}s can be used as a drop-in replacement for {@link java.util.ArrayList} and
   * allow nulls. Otherwise, any attempt to add a null-value will result in a {@link NullPointerException}.
   *
   * @param nullAllowed true if allowed, false if throw NPE
   */
  void setNullAllowed(boolean nullAllowed);

}
