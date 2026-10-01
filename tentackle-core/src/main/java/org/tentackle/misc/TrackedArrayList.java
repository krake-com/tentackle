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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.io.Serial;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;


/**
 * Implementation of a {@link TrackedList} based on an {@link ArrayList}.
 * <p>
 * Notice that the views returned by {@link #subList(int, int)} are tracked as well.
 *
 * @param <E> the element type
 * @author harald
 */
public class TrackedArrayList<E> extends ImmutableArrayList<E> implements TrackedList<E> {

  private static final String MSG_NOT_MY_SNAPSHOT = "not my snapshot";

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(TrackedArrayList.class);

  // CAUTION: the non-transient fields are transferred by TRIP via reflection
  // (see org.tentackle.trip.types.CollectionTripType), the transient ones are not!
  private boolean noNulls;                          // true if adding null-values throws an NPE
  private boolean modified;                         // initial modification parameter or from setModified(true)
  private boolean removed;                          // some object has been removed
  private boolean added;                            // some object has been added
  @SuppressWarnings("serial")
  private Set<E> removedObjects;                    // set of removed objects
  private boolean removedObjectsFiltered;           // true if removed objects filtered

  private transient List<WeakReference<TrackedArrayList<E>>> snapshots; // snapshots of this list
  private transient List<E> elementSnapshots;       // snapshots for the elements of this list (if this is a snapshot)
  private transient boolean copied;                 // true if this list is a copy

  private transient List<TrackedListListener<E>> listeners;   // the listeners


  /**
   * Creates a tracked ArrayList.
   *
   * @param initialCapacity the initial capacity of the list
   * @param modified true if the list is marked modified initially
   */
  public TrackedArrayList(int initialCapacity, boolean modified) {
    super(initialCapacity);
    setModified(modified);
  }

  /**
   * Creates a tracked ArrayList.<br>
   * Notice that the list is initially marked 'modified'!
   *
   * @param initialCapacity the initial capacity of the list
   */
  public TrackedArrayList(int initialCapacity) {
    this(initialCapacity, true);
  }

  /**
   * Creates a tracked ArrayList.
   *
   * @param modified true if the list is marked modified initially
   */
  public TrackedArrayList(boolean modified) {
    super();
    setModified(modified);
  }

  /**
   * Creates a tracked ArrayList.<br>
   * Notice that the list is initially marked 'modified'!
   * Thus, replacing a list with an empty one will still be treated as a modification.
   */
  public TrackedArrayList() {
    this(true);
  }

  /**
   * Creates a list from a collection.<br>
   * The list is <em>not</em> marked modified.
   *
   * @param collection the collection
   */
  public TrackedArrayList(Collection<E> collection) {
    super(collection);
  }


  @Override
  public boolean isNullAllowed() {
    return !noNulls;
  }

  @Override
  public void setNullAllowed(boolean nullAllowed) {
    noNulls = !nullAllowed;
  }

  /**
   * Clones the list.<br>
   * A cloned list is always not modified, mutable, and does not contain any removed objects or snapshots.
   */
  @Override
  @SuppressWarnings("unchecked")
  public TrackedArrayList<E> clone() {
    TrackedArrayList<E> nlist = (TrackedArrayList<E>) super.clone();
    nlist.init();
    nlist.copied = false;
    return nlist;
  }


  /**
   * Creates a new snapshot.<br>
   * The snapshot is a copy of the list. If the objects maintained by the list
   * are {@link Snapshotable}, they will be snapshot'd as well. If not, their reference
   * will be copied.<br>
   * Creating a snapshot is useful, for example, to temporarily add an object
   * and return to the state before the addition without changing the
   * modification state of the list.<br>
   * Notice that snapshots cannot be taken from copies or snapshots!
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
    snapshots.add(new WeakReference<>(snapshot));
    snapshot.removedObjects = removedObjects == null ? null : new LinkedHashSet<>(removedObjects);
    snapshot.snapshots = new ArrayList<>(snapshots);
    // create snapshots for elements in the new list.
    // there is a 1:1 relation between the snapshot list and the element snapshots,
    // and this relation must not be modified anymore! Hence, snapshots are immutable.
    snapshot.elementSnapshots = new ArrayList<>(size());
    boolean fineLoggable = LOGGER.isFineLoggable(); // faster than lambda because only one check if fine is not loggable
    for (E obj: snapshot) {
      if (obj instanceof Snapshotable) {
        E elementSnapshot = ((Snapshotable<E>) obj).createSnapshot();
        if (fineLoggable) {
          LOGGER.fine("creating snapshot for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        snapshot.elementSnapshots.add(elementSnapshot);
      }
      else {
        if (fineLoggable) {
          LOGGER.fine("caution: not a Snapshotable! using reference for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        snapshot.elementSnapshots.add(obj);
      }
    }
    snapshot.copied = false;
    return snapshot;
  }


  /**
   * Reverts to the given snapshot.<br>
   * If this is a copy, the copies of the objects in the snapshot will be stored in this list.
   * Otherwise, the state of the list will be reverted to the snapshot's state.
   *
   * @param snapshot the snapshot created by {@link #createSnapshot()}, null if noop
   * @throws TentackleRuntimeException if not a snapshot or not my snapshot, and this is not a copy
   */
  @Override
  @SuppressWarnings("unchecked")
  public void revertToSnapshot(TrackedList<E> snapshot) {
    if (snapshot != null) {
      if (!snapshot.isSnapshot()) {
        throw new TentackleRuntimeException("not a snapshot");
      }
      boolean fineLoggable = LOGGER.isFineLoggable(); // faster than lambda because only one check if fine is not loggable
      if (isCopy()) {
        // reverting a snapshot to a copy just creates copies from the objects in the snapshot
        super.clear();
        for (E obj: ((TrackedArrayList<E>) snapshot).elementSnapshots) {
          if (obj instanceof Snapshotable) {
            if (fineLoggable) {
              LOGGER.fine("reverting to copy for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
            }
            super.add(((Snapshotable<E>) obj).copy());
          }
          else {
            if (fineLoggable) {
              LOGGER.fine("caution: not a Snapshotable! reverting to reference for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
            }
            super.add(obj);
          }
        }
        init();
      }
      else {
        if (getSnapshotIndex(snapshot) >= 0) {
          TrackedArrayList<E> mySnapshot = (TrackedArrayList<E>) snapshot;
          super.clear();
          // restore objects from the snapshot.
          // if the object is a Snapshotable restore from elementSnapshots.
          int i = 0;
          for (E obj : snapshot) {
            if (obj instanceof Snapshotable) {
              if (fineLoggable) {
                LOGGER.fine("reverting to snapshot for " +
                        (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
              }
              ((Snapshotable<E>) obj).revertToSnapshot(mySnapshot.elementSnapshots.get(i));
            }
            else if (fineLoggable) {
              LOGGER.fine("caution: not a Snapshotable! reverting to reference for " +
                      (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
            }
            super.add(obj);
            ++i;
          }
          noNulls = mySnapshot.noNulls;
          removedObjects = mySnapshot.removedObjects;
          removedObjectsFiltered = mySnapshot.removedObjectsFiltered;
          modified = mySnapshot.modified;
          removed = mySnapshot.removed;
          added = mySnapshot.added;
          immutable = mySnapshot.immutable;
          snapshots = mySnapshot.snapshots;
          simpleEqualsAndHashCode = mySnapshot.simpleEqualsAndHashCode;
        }
        else {
          throw new TentackleRuntimeException(MSG_NOT_MY_SNAPSHOT);
        }
      }
    }
  }

  @Override
  public void discardSnapshot(TrackedList<E> snapshot) {
    int index = getSnapshotIndex(snapshot);
    if (index >= 0) {
      snapshots.remove(index);
    }
    else {
      throw new TentackleRuntimeException(MSG_NOT_MY_SNAPSHOT);
    }
  }

  @Override
  public void discardSnapshots() {
    snapshots = null;
  }

  @Override
  public boolean isCopy() {
    return copied;
  }


  @Override
  public void setCopy(boolean copy) {
    copied = copy;
    if (copy) {
      init();
    }
  }


  @Override
  @SuppressWarnings("unchecked")
  public TrackedList<E> copy() {
    TrackedArrayList<E> list = new TrackedArrayList<>(size(), false);
    boolean fineLoggable = LOGGER.isFineLoggable();  // faster than lambda because only one check if fine is not loggable
    for (E obj: this) {
      if (obj instanceof Snapshotable) {
        if (fineLoggable) {
          LOGGER.fine("creating deep copy for " +
                  (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        list.add(((Snapshotable<E>) obj).copy());
      }
      else {
        if (fineLoggable) {
          LOGGER.fine("caution: not a Snapshotable! copying reference for " +
                  (obj instanceof Identifiable ? ((Identifiable) obj).toGenericString() : obj.toString()));
        }
        list.add(obj);
      }
    }
    list.setCopy(true);
    list.setModified(true);
    return list;
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
   * @return the list of snapshots for this object, never null
   */
  @Override
  public List<TrackedArrayList<E>> getSnapshots() {
    List<TrackedArrayList<E>> shots = new ArrayList<>();
    if (snapshots != null) {
      for (Iterator<WeakReference<TrackedArrayList<E>>> iterator = snapshots.iterator(); iterator.hasNext(); ) {
        TrackedArrayList<E> snapshot = iterator.next().get();
        if (snapshot == null) {
          iterator.remove();
        }
        else {
          shots.add(snapshot);
        }
      }
    }
    return shots;
  }



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
    if (immutable) {
      assertNotSomeRemoved();
    }
    super.setImmutable(immutable, withElements);
  }

  @Override
  public void setFinallyImmutable(boolean withElements) {
    assertNotSomeRemoved();
    super.setFinallyImmutable(withElements);
  }

  private void assertNotSomeRemoved() {
    if (removedObjects != null && !removedObjects.isEmpty()) {
      throw new ImmutableException("list already contains removed objects, cannot make it immutable");
    }
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


  @Override
  public Collection<E> getRemovedObjects() {
    filterRemovedObjects();
    return removedObjects();
  }



  /**
   * Adds an object to the list of removed objects.<br>
   * The method is invoked whenever an object is removed
   * from the list. Null values are ignored.
   *
   * @param object the removed object
   * @throws ImmutableException if the list is immutable or a snapshot
   */
  protected void addRemovedObject(E object) {
    if (object != null) {
      assertMutable();
      if (removedObjects().add(object)) {
        fireRemoved(object);
      }
      removed = true;
      removedObjectsFiltered = false;
    }
  }


  /**
   * Adds a collection of objects to the list of removed objects.<br>
   * The method is invoked whenever objects are removed
   * from the list.
   *
   * @param objects the removed objects
   * @throws ImmutableException if the list is immutable or a snapshot
   */
  protected void addRemovedObjects(Collection<? extends E> objects) {
    for (E o: objects) {
      addRemovedObject(o);
    }
  }


  /**
   * Removes an object from the list of removed objects.<br>
   * The method is invoked whenever an object is added
   * to the list.
   *
   * @param object the added object
   */
  protected void removeRemovedObject(E object) {
    if (removedObjects != null && object != null) {
      removedObjects.remove(object);
    }
  }


  /**
   * Removes a collection of objects from the list of removed objects.<br>
   * The method is invoked whenever objects are added
   * to the list.
   *
   * @param objects the added objects
   */
  protected void removeRemovedObjects(Collection<? extends E> objects) {
    for (E obj: objects) {
      removeRemovedObject(obj);
    }
  }


  /**
   * Asserts that the object is mutable and not a snapshot.
   *
   * @throws ImmutableException if the list is immutable or a snapshot
   */
  @Override
  protected void assertMutable() {
    super.assertMutable();
    if (isSnapshot()) {
      throw new ImmutableException("list is a snapshot");
    }
  }


  /**
   * Asserts that the element is not null, if null-values are not allowed.
   *
   * @param element the element to add
   * @throws NullPointerException if the element is null and null-values are not allowed
   */
  protected void assertNullAllowed(E element) {
    if (noNulls && element == null) {
      throw new NullPointerException("adding null-values is not allowed");
    }
  }

  /**
   * Asserts that the collection contains no nulls, if null-values are not allowed.
   *
   * @param collection the collection containing elements to be added to this list
   * @throws NullPointerException if the collection contains a null and null-values are not allowed
   */
  protected void assertNullAllowed(Collection<? extends E> collection) {
    if (noNulls) {
      for (E element : collection) {
        if (element == null) {
          throw new NullPointerException("adding collections with null-values is not allowed");
        }
      }
    }
  }


  /**
   * Appends the specified element to the end of this list.
   *
   * @param element the element to be appended to this list
   * @return <code>true</code> (as specified by {@link Collection#add})
   */
  @Override
  public boolean add(E element) {
    assertNullAllowed(element);
    if (super.add(element)) {
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
   * any following elements to the right (adds one to their indices).
   *
   * @param index index at which the specified element is to be inserted
   * @param element element to be inserted
   */
  @Override
  public void add(int index, E element) {
    assertNullAllowed(element);
    super.add(index, element);
    removeRemovedObject(element);
    fireAdded(element);
  }


  /**
   * Appends all the elements in the specified collection to the end of
   * this list, in the order that they are returned by the
   * specified collection's Iterator.<br>
   * The behavior of this operation is
   * undefined if the specified collection is modified while the operation
   * is in progress.  (This implies that the behavior of this call is
   * undefined if the specified collection is this list, and this
   * list is nonempty.)
   *
   * @param collection the collection containing elements to be added to this list
   * @return <code>true</code> if this list changed as a result of the call
   */
  @Override
  public boolean addAll(Collection<? extends E> collection) {
    assertNullAllowed(collection);
    if (super.addAll(collection))  {
      removeRemovedObjects(collection);
      if (listeners != null) {
        for (E element: collection) {
          fireAdded(element);
        }
      }
      else {
        added = true;
      }
      return true;
    }
    return false;
  }


  /**
   * Inserts all the elements in the specified collection into this
   * list, starting at the specified position.<br>
   * Shifts the element currently at that position (if any) and any following elements to
   * the right (increases their indices).  The new elements will appear
   * in the list in the order that they are returned by the
   * specified collection's iterator.
   *
   * @param index index at which to insert the first element from the
   *              specified collection
   * @param collection the collection containing elements to be added to this list
   * @return <code>true</code> if this list changed as a result of the call
   */
  @Override
  public boolean addAll(int index, Collection<? extends E> collection) {
    assertNullAllowed(collection);
    if (super.addAll(index, collection))  {
      removeRemovedObjects(collection);
      if (listeners != null) {
        for (E element: collection) {
          fireAdded(element);
        }
      }
      else {
        added = true;
      }
      return true;
    }
    return false;
  }


  /**
   * Removes all elements from this list.<br>
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
   * Shifts any following elements to the left (subtracts one from their
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
   * Removes the first occurrence of the specified element from this list
   * if it is present.<br>
   * If the list does not contain the element, it is
   * unchanged.  More formally, removes the element with the lowest index
   * <code>i</code> such that
   * <code>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</code>
   * (if such an element exists).  Returns <code>true</code> if this list
   * contained the specified element (or equivalently, if this list
   * changed as a result of the call).
   * The removed object will be added to the list of removed objects.
   *
   * @param obj element to be removed from this list, if present
   * @return <code>true</code> if this list contained the specified element
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
   * All removed objects will be added to the list of removed objects.
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
   * All removed objects will be added to the list of removed objects.
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


  @Override
  public boolean removeIf(Predicate<? super E> filter) {
    // catch removed objects
    stream().filter(filter).forEach(this::addRemovedObject);
    return super.removeIf(filter);
  }


  /**
   * Removes from this list all elements whose index is between
   * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, exclusive.<br>
   * Shifts any succeeding elements to the left (reduces their index).
   * This call shortens the list by <code>(toIndex - fromIndex)</code> elements.
   * (If <code>toIndex==fromIndex</code>, this operation has no effect.)
   * All removed object will be added to the list of removed objects.
   *
   * @param fromIndex index of the first element to be removed
   * @param toIndex index after the last element to be removed
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
   * The old element will be added to the list of removed objects.
   * Thus, replacing an element will result in isObjectAdded() and isObjectRemoved() to return true.
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   * @return the element previously at the specified position
   * @see #replaceSilently(int, Object)
   */
  @Override
  public E set(int index, E element) {
    assertNullAllowed(element);
    E obj = super.set(index, element);
    if (obj != element)  {    // if not exactly the same instance
      addRemovedObject(obj);
      removeRemovedObject(element);
      fireAdded(element);
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
    assertNullAllowed(element);
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
   * The new element will replace the existing one <em>without</em> affecting the modification state
   * of the list!
   * Furthermore, no event will be fired.<br>
   * Useful if parts of an object tree are reloaded (for example, from the
   * database backend) and should not change the modification state
   * of the whole object tree.
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   * @return the element previously at the specified position
   * @see #set(int, Object)
   */
  @Override
  public E replaceSilently(int index, E element) {
    assertNullAllowed(element);
    filterRemovedObjects();   // in case the replaced object is in the list of removed objects
    return super.set(index, element);
  }


  @Override
  public void replaceAll(UnaryOperator<E> operator) {
    Objects.requireNonNull(operator, "operator");
    ListIterator<E> iter = listIterator();
    while (iter.hasNext()) {
      iter.set(operator.apply(noNulls ?
                              Objects.requireNonNull(iter.next(), "operator must not return a null-value") :
                              iter.next()));
    }
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


  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    return createSubListWrapper(super.subList(fromIndex, toIndex));
  }



  /**
   * Initializes the state.
   */
  private void init() {
    removedObjects = null;
    snapshots = null;
    elementSnapshots = null;
    modified = false;
    removed = false;
    removedObjectsFiltered = false;
    added = false;
    immutable = false;
  }


  /**
   * Fires an object added event.
   *
   * @param object the added object
   */
  private void fireAdded(E object) {
    added = true;
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
   * in removedObjects, although they are still contained in the list.
   * (see {@link java.util.Collections#sort(java.util.List)}).
   */
  private void filterRemovedObjects() {
    if (!removedObjectsFiltered) {
      if (removedObjects != null && !removedObjects.isEmpty()) {
        forEach(removedObjects::remove);
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
   * @return the index >= 0 if my snapshot, -1 if not my snapshot
   */
  private int getSnapshotIndex(TrackedList<E> snapshot) {
    if (snapshots != null) {
      int index = 0;
      for (Iterator<WeakReference<TrackedArrayList<E>>> iterator = snapshots.iterator(); iterator.hasNext(); ) {
        TrackedArrayList<E> shot = iterator.next().get();
        if (shot == null) {
          iterator.remove();
        }
        else {
          if (shot == snapshot) {
            return index;
          }
          ++index;
        }
      }
    }
    return -1;
  }

  /**
   * Gets the removed object set.
   *
   * @return the set, never null
   */
  private Set<E> removedObjects() {
    if (removedObjects == null) {
      removedObjects = new LinkedHashSet<>();
    }
    return removedObjects;
  }


  private List<E> createSubListWrapper(List<E> subList) {
    return new SubListWrapper(subList);
  }


  /**
   * Wrapper class necessary since some methods of {@link ArrayList#subList(int, int)} manipulate the
   * underlying element array directly, which would bypass the tracking capabilities of the {@code TrackedArrayList}.
   */
  private class SubListWrapper implements List<E> {

    private final List<E> subList;

    public SubListWrapper(List<E> subList) {
      this.subList = subList;
    }

    @Override
    public int size() {
      return subList.size();
    }

    @Override
    public boolean isEmpty() {
      return subList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return subList.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
      return subList.iterator();
    }

    @Override
    public Object[] toArray() {
      return subList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return subList.toArray(a);
    }

    @Override
    public boolean add(E e) {
      return subList.add(e);    // properly delegated to TAL#add (see ArrayList.SubList#add)
    }

    @Override
    public boolean remove(Object o) {
      return subList.remove(o);   // properly delegated to TAL#remove (see ArrayList.SubList#add)
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return subList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
      return subList.addAll(c);   // properly delegated to TAL#addAll (see ArrayList.SubList#add)
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
      return subList.addAll(index, c);    // properly delegated to TAL#addAll(see ArrayList.SubList#add)
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      // removeAll is delegated to ArrayList.SubList#batchremove, which directly operates on the element array
      Objects.requireNonNull(c);
      boolean modified = false;
      Iterator<?> it = iterator();
      while (it.hasNext()) {
        if (c.contains(it.next())) {
          it.remove();    // ArrayList.SubList#iterator#remove is properly delegated to TAL#remove
          modified = true;
        }
      }
      return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      // removeAll is delegated to ArrayList.SubList#batchremove, which directly operates on the element array
      Objects.requireNonNull(c);
      boolean modified = false;
      Iterator<E> it = iterator();
      while (it.hasNext()) {
        if (!c.contains(it.next())) {
          it.remove();    // ArrayList.SubList#iterator#remove is properly delegated to TAL#remove
          modified = true;
        }
      }
      return modified;
    }

    @Override
    public void clear() {
      subList.clear();    // properly delegated to removeRange which is overridden in TAL
    }

    @Override
    public E get(int index) {
      return subList.get(index);
    }

    @Override
    public E set(int index, E element) {
      E obj = subList.set(index, element);    // ArrayList.SubList#set directly operates on the element array
      if (obj != element)  {    // if not exactly the same instance
        addRemovedObject(obj);
        removeRemovedObject(element);
        fireAdded(element);
      }
      return obj;
    }

    @Override
    public void add(int index, E element) {
      subList.add(index, element);    // properly delegated to TAL#add (see ArrayList.SubList#add)
    }

    @Override
    public E remove(int index) {
      return subList.remove(index);   // properly delegated to TAL#remove (see ArrayList.SubList#remove)
    }

    @Override
    public int indexOf(Object o) {
      return subList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
      return subList.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
      return subList.listIterator();    // sublist's iterator properly delegates set/add/remove to TAL
    }

    @Override
    public ListIterator<E> listIterator(int index) {
      return subList.listIterator(index);   // sublist's iterator properly delegates set/add/remove to TAL
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
      return createSubListWrapper(subList.subList(fromIndex, toIndex));
    }
  }

}
