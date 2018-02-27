/**
 * Tentackle - http://www.tentackle.org
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;



/**
 * Implementation of a {@link TrackedList}.
 *
 * @param <E> the element type
 * @author harald
 */
public class TrackedArrayList<E> extends ImmutableArrayList<E> implements TrackedList<E> {

  private static final long serialVersionUID = 5820724718467532237L;

  private static final Logger LOGGER = Logger.get(TrackedArrayList.class);

  private boolean modified;                         // initial modification parameter or from setModified(true)
  private boolean removed;                          // some object has been removed
  private boolean added;                            // some object has been added
  private Set<E> removedObjects;                    // set of removed objects
  private boolean removedObjectsFiltered;           // true if removed objects filtered

  private transient List<TrackedArrayList<E>> snapshots; // snapshots of this list
  private transient List<E> elementSnapshots;       // snapshots for the elements of this list (if this is a snapshot)

  private transient List<TrackedListListener<E>> listeners;   // the listeners


  /**
   * Creates a tracked ArrayList.
   *
   * @param initialCapacity is the initial size of the list
   * @param modified is true if list is marked modified initially
   */
  public TrackedArrayList(int initialCapacity, boolean modified) {
    super(initialCapacity);
    setModified(modified);
  }


  /**
   * Creates a tracked ArrayList.
   *
   * @param modified is true if list is marked modified initially
   */
  public TrackedArrayList(boolean modified) {
    super();
    setModified(modified);
  }


  /**
   * Creates a tracked ArrayList.<br>
   * Notice that the list is initially marked 'modified'!
   * Thus replacing a list with an empty one will still treated as
   * a modification.
   */
  public TrackedArrayList() {
    this(true);
  }


  /**
   * Creates a list from a collection.<br>
   * The list is NOT marked modified.
   *
   * @param collection the collection
   */
  public TrackedArrayList(Collection<E> collection) {
    super(collection);
  }


  /**
   * Clones the list.<br>
   * A cloned list is always not modified, mutable and does not contain any removed objects or snapshots.
   */
  @Override
  @SuppressWarnings("unchecked")
  public TrackedArrayList<E> clone() {
    TrackedArrayList<E> nlist = (TrackedArrayList<E>) super.clone();
    nlist.removedObjects = null;
    nlist.snapshots = null;
    nlist.elementSnapshots = null;
    nlist.modified = false;
    nlist.removed = false;
    nlist.removedObjectsFiltered = false;
    nlist.added = false;
    nlist.immutable = false;
    return nlist;
  }


  /**
   * Creates a new snapshot.<br>
   * The snapshot is a copy of the list. It is not a snapshot
   * for the objects and/or its referenced objects in the list!
   * Creating a snapshot is useful, for example, to temporarily add an object
   * and return to the state before the addition without changing the
   * modification state of the list.
   *
   * @return the new snapshot
   */
  @Override
  @SuppressWarnings("unchecked")
  public TrackedArrayList<E> createSnapshot() {
    if (snapshots == null) {
      snapshots = new ArrayList<>();
    }
    TrackedArrayList<E> snapshot = (TrackedArrayList<E>) super.clone();
    snapshots.add(snapshot);
    snapshot.removedObjects = removedObjects == null ? null : new LinkedHashSet<>(removedObjects);
    snapshot.snapshots = new ArrayList<>(snapshots);
    // create snapshots for elements in new list.
    // there is a 1:1 relation between the snapshot list and the element snapshots
    // and this relation must not be modified anymore! Hence, snapshots are immutable.
    snapshot.elementSnapshots = new ArrayList<>(size());
    for (E obj: snapshot) {
      if (obj instanceof Snapshotable) {
        E elementsnapshot = ((Snapshotable<E>) obj).createSnapshot();
        if (LOGGER.isFineLoggable()) {
          LOGGER.fine("creating snapshot for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        snapshot.elementSnapshots.add(elementsnapshot);
      }
      else {
        if (LOGGER.isFineLoggable()) {
          LOGGER.fine("caution: not a Snapshotable! using reference for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        snapshot.elementSnapshots.add(obj);
      }
    }
    return snapshot;
  }


  /**
   * Reverts to the given snapshot.
   *
   * @param snapshot the snapshot created by {@link #createSnapshot()}, null if noop
   * @throws TentackleRuntimeException if no such snapshot
   */
  @Override
  @SuppressWarnings("unchecked")
  public void revertToSnapshot(TrackedList<E> snapshot) {
    if (snapshot != null) {
      if (!snapshot.isSnapshot()) {
        throw new TentackleRuntimeException("not a snapshot");
      }
      if (isMySnapshot(snapshot)) {
        TrackedArrayList<E> mySnapshot = (TrackedArrayList<E>) snapshot;
        super.clear();
        // restore objects from snapshot.
        // if object is a Snapshotable restore from elementsnapshots.
        int i = 0;
        for (E obj: snapshot) {
          if (obj instanceof Snapshotable) {
            if (LOGGER.isFineLoggable()) {
              LOGGER.fine("reverting to snapshot for " +
                          (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
            }
            ((Snapshotable<E>) obj).revertToSnapshot(mySnapshot.elementSnapshots.get(i));
          }
          else  {
            if (LOGGER.isFineLoggable()) {
              LOGGER.fine("caution: not a snapshotCapable! reverting to reference for " +
                          (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
            }
          }
          super.add(obj);
          ++i;
        }
        removedObjects = mySnapshot.removedObjects;
        removedObjectsFiltered = mySnapshot.removedObjectsFiltered;
        modified = mySnapshot.modified;
        removed = mySnapshot.removed;
        added = mySnapshot.added;
        immutable = mySnapshot.immutable;
        snapshots = mySnapshot.snapshots;
        simpleEqualsAndHashCode = mySnapshot.simpleEqualsAndHashCode;
      }
      else  {
        throw new TentackleRuntimeException("no such snapshot");
      }
    }
  }


  /**
   * Returns whether this list is a snapshot.
   * <p>
   * Notice: snapshots are immutable!
   *
   * @return true if this is a snapshot
   */
  @Override
  public boolean isSnapshot() {
    return elementSnapshots != null;
  }


  /**
   * Gets all snapshots.
   *
   * @return the list of snapshots for this object, null if none taken so far
   */
  @Override
  public List<TrackedArrayList<E>> getSnapshots() {
    return snapshots;
  }



  /**
   * Sets the modified state.
   *
   * @param modified is true to set modified to true,
   *        false to clear all other flags including the list of removed Objects.
   */
  @Override
  public void setModified(boolean modified) {
    this.modified = modified;
    if (!modified) {
      // applies to all
      added = false;
      removed = false;
      removedObjectsFiltered = false;
      removedObjects = null;
    }
  }


  /**
   * Gets the modification state.<br>
   * Returns whether objects have been added, removed or replaced or
   * setModified(true) was invoked.
   *
   * @return true if modified.
   */
  @Override
  public boolean isModified() {
    return modified || isSomeAdded() || isSomeRemoved();
  }

  @Override
  public boolean isElementModified() {
    for (E element: this) {
      if (element instanceof Modifiable && ((Modifiable) element).isModified()) {
        return true;
      }
    }
    return false;
  }


  @Override
  public void setImmutable(boolean immutable, boolean withElements) {
    if (immutable && removedObjects != null && !removedObjects.isEmpty()) {
      throw new ImmutableException("list already contains removed objects, cannot make it immutable");
    }
    super.setImmutable(immutable, withElements);
  }


  /**
   * Returns whether some object has been removed.
   *
   * @return true if removed
   */
  @Override
  public boolean isSomeRemoved() {
    filterRemovedObjects();
    return removed;
  }

  /**
   * Returns whether some object has been added.
   *
   * @return true if added
   */
  @Override
  public boolean isSomeAdded() {
    return added;
  }


  /**
   * Gets the removed objects in the order of their removal.
   *
   * @return the list of removed objects, null if no object has been removed so far.
   */
  @Override
  public Collection<E> getRemovedObjects() {
    filterRemovedObjects();
    return removedObjects;
  }



  /**
   * Adds an object to the list of removed objects.<br>
   * Only objects of type &lt;E&gt; are added.
   * The method is invoked whenever an object is removed
   * from the list.
   *
   * @param object the object to be added.
   */
  protected void addRemovedObject(E object) {
    if (object != null) {
      assertMutable();
      if (removedObjects == null) {
        removedObjects = new LinkedHashSet<>();
      }
      if (removedObjects.add(object)) {
        fireRemoved(object);
      }
      removed = true;
      removedObjectsFiltered = false;
    }
  }


  /**
   * Adds a list of objects to the list of removed objects.<br>
   * Only objects of type &lt;E&gt; are added.
   * The method is invoked whenever an object is removed
   * from the list.
   *
   * @param objects the list of objects
   */
  protected void addRemovedObjects(Collection<? extends E> objects) {
    for (E o: objects) {
      addRemovedObject(o);
    }
  }


  /**
   * Removes an object from the list of removed objects.<br>
   * Only objects of type &lt;E&gt; are added.
   * The method is invoked whenever an object is removed
   * from the list.
   *
   * @param object the object to be added.
   */
  protected void removeRemovedObject(E object) {
    if (removedObjects != null && object != null) {
      removedObjects.remove(object);
    }
  }


  /**
   * Removes a list of objects to the list of removed objects.<br>
   * Only objects of type &lt;E&gt; are added.
   * The method is invoked whenever an object is removed
   * from the list.
   *
   * @param objects the list of objects
   */
  protected void removeRemovedObjects(Collection<? extends E> objects) {
    for (E obj: objects) {
      removeRemovedObject(obj);
    }
  }


  /**
   * Asserts that object is mutable and not a snapshot.
   */
  @Override
  protected void assertMutable() {
    super.assertMutable();
    if (isSnapshot()) {
      throw new ImmutableException("list is a snapshot");
    }
  }



  /**
   * Appends the specified element to the end of this list.
   *
   * @param element the element to be appended to this list
   * @return <tt>true</tt> (as specified by {@link Collection#add})
   */
  @Override
  public boolean add(E element) {
    if (super.add(element)) {
      added = true;
      removeRemovedObject(element);
      fireAdded(element);
      return true;
    }
    return false;
  }


  /**
   * Inserts the specified element at the specified position in this
   * list.<br>
   * Shifts the element currently at that position (if any) and
   * any subsequent elements to the right (adds one to their indices).
   *
   * @param index index at which the specified element is to be inserted
   * @param element element to be inserted
   */
  @Override
  public void add(int index, E element) {
    super.add(index, element);
    removeRemovedObject(element);
    added = true;
    fireAdded(element);
  }


  /**
   * Appends all of the elements in the specified collection to the end of
   * this list, in the order that they are returned by the
   * specified collection's Iterator.<br>
   * The behavior of this operation is
   * undefined if the specified collection is modified while the operation
   * is in progress.  (This implies that the behavior of this call is
   * undefined if the specified collection is this list, and this
   * list is nonempty.)
   *
   * @param collection the collection containing elements to be added to this list
   * @return <tt>true</tt> if this list changed as a result of the call
   * @throws NullPointerException if the specified collection is null
   */
  @Override
  public boolean addAll(Collection<? extends E> collection) {
    if (super.addAll(collection))  {
      added = true;
      removeRemovedObjects(collection);
      if (listeners != null) {
        for (E element: collection) {
          fireAdded(element);
        }
      }
      return true;
    }
    return false;
  }


  /**
   * Inserts all of the elements in the specified collection into this
   * list, starting at the specified position.<br>
   * Shifts the element currently at that position (if any) and any subsequent elements to
   * the right (increases their indices).  The new elements will appear
   * in the list in the order that they are returned by the
   * specified collection's iterator.
   *
   * @param index index at which to insert the first element from the
   *              specified collection
   * @param collection the collection containing elements to be added to this list
   * @return <tt>true</tt> if this list changed as a result of the call
   */
  @Override
  public boolean addAll(int index, Collection<? extends E> collection) {
    if (super.addAll(index, collection))  {
      added = true;
      removeRemovedObjects(collection);
      if (listeners != null) {
        for (E element: collection) {
          fireAdded(element);
        }
      }
      return true;
    }
    return false;
  }


  /**
   * Removes all of the elements from this list.<br>
   * The list will be empty after this call returns. All objects in the list
   * will be added to the list of removed objects.
   */
  @Override
  public void clear() {
    addRemovedObjects(this);
    super.clear();
  }

  /**
   * Removes the element at the specified position in this list.<br>
   * Shifts any subsequent elements to the left (subtracts one from their
   * indices). The removed object will be added to the list of removed
   * objects.
   *
   * @param index the index of the element to be removed
   * @return the element that was removed from the list
   */
  @Override
  public E remove(int index) {
    E obj = super.remove(index);
    addRemovedObject(obj);
    return obj;
  }

  /**
   * Removes the first occurrence of the specified element from this list,
   * if it is present.<br>
   * If the list does not contain the element, it is
   * unchanged.  More formally, removes the element with the lowest index
   * <tt>i</tt> such that
   * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
   * (if such an element exists).  Returns <tt>true</tt> if this list
   * contained the specified element (or equivalently, if this list
   * changed as a result of the call).
   * The removed object will be added to the list of removed objects.
   *
   * @param obj element to be removed from this list, if present
   * @return <tt>true</tt> if this list contained the specified element
   */
  @Override
  public boolean remove(Object obj) {
    // because of Object.equals in super.remove(obj) we cannot be
    // sure that the reference is correct (for addRemovedObject).
    // Hence, we must grab the object "equal" to obj from the list.
    // This is achieved by using remove(int) instead of remove(obj).
    int ndx = indexOf(obj);
    if (ndx >= 0) {
      remove(ndx);
      return true;
    }
    return false;
  }

  /**
   * Removes all objects that are contained in the given collection.<br>
   * All removed object will be added to the list of removed objects.
   *
   * @param collection the collection containing the objects to be removed from this list.
   * @return true if at least one object has been removed.
   */
  @Override
  public boolean removeAll(Collection<?> collection) {
    // re-implemented here to catch removed objects
    boolean someRemoved = false;
    Iterator<E> iter = iterator();
    while (iter.hasNext()) {
      E obj = iter.next();
      if (collection.contains(obj)) {
        iter.remove();
        someRemoved = true;
      }
    }
    return someRemoved;
  }


  /**
   * Removes all objects that are not contained in the given collection.<br>
   * All removed object will be added to the list of removed objects.
   *
   * @param collection the collection containing the objects not to be removed from this list.
   * @return true if at least one object has been removed.
   */
  @Override
  public boolean retainAll(Collection<?> collection) {
    // re-implemented here to catch removed objects
    boolean someRemoved = false;
    Iterator<E> iter = iterator();
    while (iter.hasNext()) {
      E obj = iter.next();
      if (!collection.contains(obj)) {
        iter.remove();
        addRemovedObject(obj);
        someRemoved = true;
      }
    }
    return someRemoved;
  }


  /**
   * Removes from this list all of the elements whose index is between
   * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.<br>
   * Shifts any succeeding elements to the left (reduces their index).
   * This call shortens the list by <tt>(toIndex - fromIndex)</tt> elements.
   * (If <tt>toIndex==fromIndex</tt>, this operation has no effect.)
   * All removed object will be added to the list of removed objects.
   *
   * @param fromIndex index of first element to be removed
   * @param toIndex index after last element to be removed
   */
  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    int max = size();
    // we need to align before because we have to catch the removed objects
    if (fromIndex < max) {
      if (toIndex > max)  {
        toIndex = max;
      }
      if (toIndex - fromIndex > 0) {
        addRemovedObjects(subList(fromIndex, toIndex));
        super.removeRange(fromIndex, toIndex);
      }
    }
  }


  /**
   * Replaces the element at the specified position in this list with
   * the specified element.<br>
   * The old element will be added to the list
   * of removed objects. Thus, replacing an element will result in
   * isObjectAdded() and isObjectRemoved() to return true.
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   * @return the element previously at the specified position
   */
  @Override
  public E set(int index, E element) {
    E obj = super.set(index, element);
    if (obj != element)  {    // if not exactly the same instance
      addRemovedObject(obj);
      removeRemovedObject(element);
      fireAdded(element);
      added = true;
    }
    return obj;
  }

  @Override
  public boolean addIfAbsent(E element) {
    if (!contains(element)) {
      add(element);
      return true;
    }
    return false;
  }

  @Override
  public void addBlunt(E element) {
    super.add(element);
  }

  @Override
  public boolean removeBlunt(E element) {
    return super.remove(element);
  }

  @Override
  public E removeBlunt(int index) {
    return super.remove(index);
  }


  /**
   * Replaces the element at the specified position in this list with
   * the specified element.<br>
   * The old element will replace the existing one <em>without</em> affecting the modification state
   * of the list.<br>
   * Futhermore, no event will be fired.<br>
   * Useful if parts of an object tree are reloaded (for example from the
   * database backend) and should not change the modification state
   * of the whole object tree.
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   * @return the element previously at the specified position
   */
  @Override
  public E replace(int index, E element) {
    filterRemovedObjects();   // in case replaced object is in list of removed objects
    return super.set(index, element);
  }

  @Override
  public void addListener(TrackedListListener<E> listener) {
    getListeners().add(listener);
  }

  @Override
  public boolean removeListener(TrackedListListener<E> listener) {
    boolean listenerRemoved = false;
    for (Iterator<TrackedListListener<E>> iter = getListeners().iterator(); iter.hasNext(); ) {
      TrackedListListener<E> l = iter.next();
      if (listener.equals(l)) {
        iter.remove();
        listenerRemoved = true;
      }
    }
    return listenerRemoved;
  }


  /**
   * Fires an object added event.
   *
   * @param object the added object
   */
  private void fireAdded(E object) {
    if (listeners != null && !listeners.isEmpty()) {
      for (TrackedListListener<E> listener: getListeners()) {
        listener.added(object);
      }
    }
  }

  /**
   * Fires an object removed event.
   *
   * @param object the removed object
   */
  private void fireRemoved(E object) {
    if (listeners != null && !listeners.isEmpty()) {
      for (TrackedListListener<E> listener: getListeners()) {
        listener.removed(object);
      }
    }
  }

  /**
   * Gets the listeners.
   *
   * @return the listeners, never null
   */
  private List<TrackedListListener<E>> getListeners() {
    if (listeners == null) {
      listeners = new ArrayList<>();
    }
    return listeners;
  }


  /**
   * Filter removed objects.<br>
   * set and add/remove operations may leave removed objects
   * in removedObjects although they are still contained in the list.
   * (see {@link java.util.Collections#sort(java.util.List)}).
   */
  private void filterRemovedObjects() {
    if (!removedObjectsFiltered) {
      if (removedObjects != null && !removedObjects.isEmpty()) {
        removedObjects.removeAll(this);
        removed = !removedObjects.isEmpty();
      }
      removedObjectsFiltered = true;
    }
  }


  /**
   * Linear scan of snapshots.<br>
   * We cannot use .contains() because equals and hashcode check elements as well.
   *
   * @param snapshot the snapshot to test
   * @return true if snapshot belongs to me
   */
  private boolean isMySnapshot(TrackedList<E> snapshot) {
    if (snapshots != null) {
      for (TrackedList<E> snap: snapshots) {
        if (snap == snapshot) {
          return true;
        }
      }
    }
    return false;
  }
}
