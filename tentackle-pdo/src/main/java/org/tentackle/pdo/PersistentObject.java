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

package org.tentackle.pdo;

import org.tentackle.bind.Bindable;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.Immutable;
import org.tentackle.misc.Modifiable;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.misc.Snapshotable;
import org.tentackle.reflect.NotInterceptable;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Persistent;
import org.tentackle.session.SessionDependable;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationFailedException;

import java.util.Collection;
import java.util.List;

/**
 * Interface for the persistent part of a PDO.
 *
 * @param <T> the PDO
 * @author harald
 */
public interface PersistentObject<T extends PersistentDomainObject<T>>
       extends PersistenceDelegate<T>, PdoProvider<T>, Snapshotable<T>, Modifiable, SerialNumbered,
               Validateable, Immutable, ScopeConfigurator, DomainContextDependable, SessionDependable {

  /**
   * Gets the unique identification number.
   *
   * @return the ID, 0 if the object is virgin, negative if the object is deleted
   */
  @Persistent(comment = "object id")
  @Override
  @Bindable
  @NotInterceptable
  long getId();

  /**
   * Gets the serial number.<br>
   * Whenever the object is persisted, the serial is incremented by one.
   *
   * @return the serial number, 0 if not persisted yet
   */
  @Persistent(comment = "object version")
  @Override
  @Bindable
  long getSerial();

  /**
   * Indicates whether this entity's model provides a tableserial column.
   *
   * @return true if pdo is using the tableSerial column, false if not
   */
  boolean isTableSerialProvided();

  /**
   * Gets the table serial.
   *
   * @return the table serial
   */
  @Persistent(comment = "table version")
  @Bindable
  long getTableSerial();

  /**
   * Gets the ID string: "classId:id".<br>
   * The ID string describes the PDO by its classId and object-ID.
   * <p>
   * Example: {@code "1022:1258474"}
   *
   * @return the id string
   */
  String toIdString();

  /**
   * Returns whether this is an abstract PDO.<br>
   * Abstract PDOs are real objects but cannot be persisted.
   * They may be used to retrieve data for the concrete implementations, however.
   *
   * @return true if abstract PDO
   */
  @NotInterceptable
  boolean isAbstract();

  /**
   * Returns whether this PDO is a composite.<br>
   * Composite PDOs consist of other component PDOs, defined via composite relations.<br>
   * Notice that a <em>composite root entity</em> is synonym to the <em>aggregate root</em> in DDD lingo.
   *
   * @return true if the object is a composite, false if not.
   */
  boolean isComposite();

  /**
   * Returns whether this object is a root entity.<br>
   * All entities that are not a component of another entity are considered as a root entity.
   * They correspond to the so-called <em>aggregate root</em> in DDD,
   * with the subtle difference that a root entity may have no components at all.<br>
   * Only root entities can be persisted!
   * <p>
   * Root entities maintain their own entity context in {@link DomainContext}.
   *
   * @return true if root entity
   */
  @NotInterceptable
  boolean isRootEntity();

  /**
   * Returns whether this is an embedded PDO.<br>
   * Embedded PDOs are components with their attributes embedded in the parent object's table.
   *
   * @return true if embedded entity
   */
  @NotInterceptable
  boolean isEmbedded();

  /**
   * Gets the parent PDO if this is an embedded PDO.<br>
   * Throws a {@link PersistenceException} if this is not an embedded PDO.
   *
   * @return the parent PDO, never null
   * @param <E> the parent's type
   */
  <E extends PersistentDomainObject<E>> E getEmbeddingParent();

  /**
   * Returns whether this is a root entity of the given component.
   *
   * @param <C> the type of the component
   * @param component the component pdo
   * @return true if this is the root entity of the given component
   */
  <C extends PersistentDomainObject<C>> boolean isRootEntityOf(C component);

  /**
   * Indicates whether this entity provides a root-ID column.<br>
   * The column is added in components without a direct parent ID relation
   * to provide the ID of the root entity without accessing the database.
   *
   * @return true if column provided, false if not
   * @see #getRootId()
   */
  boolean isRootIdProvided();

  /**
   * Gets the ID of the root entity this component belongs to.
   *
   * @return the root id
   */
  @Persistent(comment = "root id")
  @NotInterceptable
  @Bindable
  long getRootId();

  /**
   * Indicates whether this entity's model provides a root-class-ID column.<br>
   * The column is added in components that belong to different root entity classes.
   *
   * @return true if column provided, false if not
   */
  boolean isRootClassIdProvided();

  /**
   * Gets the class ID of the root entity this component belongs to.
   *
   * @return the root class id
   */
  @Persistent(comment = "root class id")
  @NotInterceptable
  @Bindable
  int getRootClassId();

  /**
   * Gets the database table name for the class of this object.
   * <p>
   * Notice: for MULTI-inheritance entities, the tablename is the
   * last in the inheritance chain.
   *
   * @return the table name, null if the class does not map to a database table
   */
  @NotInterceptable
  String getTableName();

  /**
   * Gets the basename of the class of this object.<br>
   * The basename is the class name without the package name.
   *
   * @return the basename of the Objects class
   */
  String getClassBaseName();

  /**
   * Gets the unique ID of the class.<br>
   * Class IDs are provided as a replacement for FQCNs for 2 reasons:
   * <ol>
   *   <li>Refactoring: Package- and classnames may be changed (the class ID is fixed).</li>
   *   <li>Performance: Comparing integers is faster than comparing strings (in Java <em>and</em> the database).</li>
   * </ol>
   * Notice that abstract entities (all except the leaves within an inheritance hierarchy) don't have a class id.
   *
   * @return the class id, 0 if abstract
   */
  @Persistent(comment = "class id")
  @Bindable
  @NotInterceptable
  int getClassId();

  /**
   * Creates the {@link SecurityResult} for a given permission.
   *
   * @param permission the requested permission
   * @return the security result (never null)
   */
  SecurityResult getSecurityResult(Permission permission);

  /**
   * Checks whether the given permission is accepted.
   *
   * @param permission the requested permission
   * @return true if permission is accepted
   */
  boolean isPermissionAccepted(Permission permission);

  /**
   * Determines whether the application is allowed to write this PDO.
   * <p>
   * Notice that there is no {@code isReadAllowed()}, because PDOs without read permission
   * won't be loaded at all.
   *
   * @return true if allowed
   */
  boolean isWriteAllowed();

  /**
   * Determines whether the user is allowed to view this PDO.<br>
   * Can be used to determine whether the object is shown to the user.
   *
   * @return true if allowed
   */
  boolean isViewAllowed();

  /**
   * Determines whether the user is allowed to edit this PDO.<br>
   * Can be used to determine whether the object is editable by the user.
   *
   * @return true if allowed
   */
  boolean isEditAllowed();

  /**
   * Checks whether this object is already persistent in the database or only residing in memory.<br>
   * If an object {@code isNew()}, it means that it can be persisted (inserted).
   * This does not mean that the object has never been stored in the database.
   * It is possible that the object just has been deleted.
   *
   * @return true if the object is not in the database, i.e., new (or deleted)
   */
  boolean isNew();

  /**
   * Reserves an ID.<p>
   * Reserved IDs are negative.
   * A new object with a reserved ID can be distinguished from
   * a deleted object by its serial. See also {@link #isVirgin}.
   * If the object already has an ID or is deleted,
   * the ID will <em>not</em> change.
   */
  void reserveId();

  /**
   * Reserves a given ID.<br>
   * It doesn't matter whether the ID is negative or positive.
   * Reserving 0 clears the reservation.
   * <p>
   * Throws a {@link PersistenceException} if the object is not new.
   *
   * @param id the ID to reserve, 0 to clear the reservation
   */
  void reserveId(long id);

  /**
   * Checks whether the object is deleted.
   *
   * @return true if the object has been removed from the database
   */
  boolean isDeleted();

  /**
   * Returns whether the modification state is available.<br>
   * By default, PDOs are not tracked.<br>
   * This is a quality measure to ensure that isModified() returns
   * false if and only if it hasn't been modified, i.e., the (generated) code
   * checks for modification properly.
   *
   * @return true if tracked, false otherwise (default)
   */
  boolean isTracked();

  /**
   * Determines whether this object got some of its attributes modified.<br>
   * It does not check whether some of its components are modified!
   * This method can also be used for non-tracked entities.
   *
   * @return true if at least one attribute is modified
   */
  boolean attributesModified();

  /**
   * Returns whether any of the attributes differs from the values persisted in the database.<br>
   * This method is only applicable to full-tracked entities and returns false if not full-tracked.
   *
   * @return true if differs, false if no change or not full-tracked
   */
  boolean differsPersisted();

  /**
   * Determines whether this object is allowed to be stored in the database.<br>
   * Notice that once a PDO is persisted, the same object reference is no longer persistable
   * and must be reloaded. Rule: if the reference is no longer used, use {@link #save()},
   * otherwise use {@link #persist()} and replace the reference with the return value.
   *
   * @return true if persistable (default)
   * @see #persist()
   * @see #save()
   */
  boolean isPersistable();

  /**
   * Checks whether this object is referenced by other objects.
   * <p>
   * The method can be invoked before operations that may have an impact on the
   * referential integrity, for example, to enable a delete button in the UI.<br>
   * The default implementation returns false.
   *
   * @return true if referenced
   */
  boolean isReferenced();

  /**
   * Checks whether this object can be removed.
   * <p>
   * Should be invoked before operations that may have an impact on the
   * referential integrity, for example, to enable a delete button in the UI.<br>
   * The default implementation returns {@code !isNew() && !isReferenced()}.
   *
   * @return true if removable
   */
  boolean isRemovable();

  /**
   * Removes this object from the database.
   */
  void delete();

  /**
   * Sets this object's expiration flag.<br>
   * Expired objects are considered as <em>invalid</em> and must be reloaded before being used any further.
   * Used in caches.
   *
   * @param expired true if the object is expired
   */
  @NotInterceptable
  void setExpired(boolean expired);

  /**
   * Checks whether the object has been marked expired.<br>
   * The cache will reload expired objects from the database
   * when the objects are retrieved again.
   *
   * @return true if the object is expired (in cache)
   */
  @NotInterceptable
  boolean isExpired();

  /**
   * Gets the current modification count for the whole class.<br>
   * The count corresponds to the table serial.
   *
   * @return the modification count
   * @see org.tentackle.session.ModificationTracker
   */
  long getModificationCount();

  /**
   * Gets the expirations for a given range of tableserials.<br>
   * The expirations are returned as a list of {@link IdSerialTuple}s with
   * the serial being the tableserial of the corresponding PDO.<br>
   * A negative tableserial indicates a deletion. Tuples with an id of 0
   * indicate a rolled back insert or update or an object that was updated more than once.
   * <p>
   * A physical database query is only done if the requested range is not yet cached in the
   * backlog.
   * <p>
   * The returned tableserials should not have any gaps.
   * If there are gaps, the caller cannot derive which PDOs were deleted,
   * updated or inserted in a rolled back transaction or updated more than once.
   * Caches, for example, must be invalidated completely in such a case.
   * <p>
   * Please notice, for serverless applications running in local client mode,
   * there <em>will</em> be gaps for deletions, rollbacks, and multiple updates.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial), 0 up to the end
   * @return the id/tableserial tuples sorted by abs(tableserial), never null
   * @see ModificationTracker#isLocalClientMode()
   */
  Collection<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial);

  /**
   * Determines the id/tableserial tuples for PDOs in the database with a higher
   * tableserial than the given {@code oldSerial}.<br>
   * Since this queries the database, deletions, rollbacks, and multiple updates
   * of the same PDO will create gaps within the tableserials.
   *
   * @param oldSerial exclusive lower bound for tableSerial (&gt; oldSerial)
   * @return the expiration tuples, sorted by tableserial+id, never null
   */
  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial);

  /**
   * Determines the id/tableserial tuples for PDOs in the database within a
   * given range of tableserials.<br>
   * Since this queries the database, deletions, rollbacks, and multiple updates
   * of the same PDO will create gaps within the tableserials.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial)
   * @return the expiration tuples, sorted by tableserial+id, never null
   */
  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial);

  /**
   * Selects all PDOs with a higher tableserial than the given {@code oldSerial}.<br>
   * Since this queries the database, deletions, rollbacks, and multiple updates
   * of the same PDO will create gaps within the tableserials.
   *
   * @param oldSerial exclusive lower bound for tableSerial (&gt; oldSerial)
   * @return the list of objects, never null
   */
  List<T> selectAllWithExpiredTableSerials(long oldSerial);

  /**
   * Selects all PDOs within a given range of tableserials.<br>
   * Since this queries the database, deletions, rollbacks, and multiple updates
   * of the same PDO will create gaps within the tableserials.
   *
   * @param oldSerial exclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&lt;= maxSerial)
   * @return the list of objects, never null
   */
  List<T> selectAllWithExpiredTableSerials(long oldSerial, long maxSerial);

  /**
   * Selects all id/serial pairs of this class as a list of {@link IdSerialTuple}s.
   *
   * @return the list of tuples
   */
  List<IdSerialTuple> selectAllIdSerial();

  /**
   * Selects the serial-number for a given object id from the database.
   *
   * @param id the object id
   * @return the serial for that id, -1 if no such object
   */
  long selectSerial(long id);

  /**
   * Selects the highest id.
   *
   * @return the highest id, -1 if the table is empty
   */
  long selectMaxId();

  /**
   * Selects the highest table serial.
   *
   * @return the highest table serial, -1 if the table is empty
   */
  long selectMaxTableSerial();

  /**
   * Gets the cache.
   * <p>
   * Notice that abstract classes don't provide their own cache but
   * instead use the caches of their concrete classes.
   *
   * @return the cache for this PDO, null if none.
   */
  @NotInterceptable
  PdoCache<T> getCache();

  /**
   * Gets the last cache access time.
   *
   * @return the last cache access time
   */
  long getCacheAccessTime();

  /**
   * Gets the cache access count.
   *
   * @return the access count
   */
  long getCacheAccessCount();

  /**
   * Marks the cache access.<br>
   * Increments the access counter and sets the last access time.
   */
  @NotInterceptable
  void markCacheAccess();

  /**
   * Returns whether the object is cached.
   *
   * @return true if cached
   */
  boolean isCached();

  /**
   * Determines whether the object is cacheable or not.<br>
   * The default implementation always returns true, but apps
   * can use this as a filter.
   *
   * @return true if the object is cacheable
   */
  @NotInterceptable
  boolean isCacheable();

  /**
   * Searches for a "pattern" in this object.<br>
   * The default implementation looks for the pattern in the normtext.
   *
   * @param pattern the pattern to search for
   * @return true if this object "contains" the pattern
   */
  boolean containsPattern (String pattern);

  /**
   * Checks whether this object (if saved) would violate any unique constraints.
   * <p>
   * The method is usually used by the presentation layer to check for duplicates.
   * The default implementation invokes {@link DomainObject#findByUniqueDomainKey(Object)} with
   * {@link DomainObject#getUniqueDomainKey()}, both of which throw a {@link DomainException} by default,
   * i.e., if the entity provides no unique domain key.
   *
   * @return the duplicate object, null if no duplicate
   */
  T findDuplicate();

  /**
   * Indicates whether this entity provides the token lock columns.
   *
   * @return true if token lock columns present, false if not.
   */
  boolean isTokenLockProvided();

  /**
   * Gets the expiration in milliseconds of the token lock.<br>
   * Returns a value greater than 0 if {@link #isTokenLockProvided()}, else 0.
   *
   * @return the timespan in ms, 0 if no token lock
   */
  long getTokenLockTimeout();

  /**
   * Requests a token lock.
   * <p>
   * Throws a {@link LockException} if locked by another user
   * or a {@link org.tentackle.session.NotFoundException} if removed or updated in the meantime.
   */
  @NotInterceptable
  void requestTokenLock();

  /**
   * Releases a token lock.<br>
   * Use this method if a PDO needs to be unlocked without being persisted.
   * <p>
   * Throws a {@link LockException} if locked by another user
   * or a {@link org.tentackle.session.NotFoundException} if removed or updated in the meantime.
   */
  void releaseTokenLock();

  /**
   * Checks whether this object is token locked and the lock is not expired.
   *
   * @return true if locked by <em>any</em> user
   * @see #isTokenLockedByMe()
   */
  boolean isTokenLocked();

  /**
   * Checks whether this object is token locked by the given user.
   *
   * @param userId the user's ID
   * @return true if locked by that user
   */
  boolean isTokenLockedBy(long userId);

  /**
   * Checks whether this object is token locked by the current user.
   *
   * @return true if locked
   */
  boolean isTokenLockedByMe();

  /**
   * Checks whether requesting a token lock for the current user is possible.
   *
   * @return true if the object is not locked at all or locked by the current user
   */
  boolean isTokenLockableByMe();

  /**
   * Transfers the token lock to another user.<br>
   * Necessary to hand over a token without releasing it.<br>
   * Eliminates the time-gap when the object is token-free.<br>
   * Allows taking over the token WITHOUT possessing it!
   * This method should be used with great care as it does not check
   * who owns the token and if there is a token at all.
   * <p>
   * CAUTION: the method is provided for rare corner cases like administration tools.
   * Don't use it for regular locking/unlocking!
   *
   * @param userId is the new user-ID. (special 0 = free token, even if we are not the owner!)
   * @return the PDO with the updated token
   */
  T transferTokenLock(long userId);

  /**
   * Loads a PDO from the database by its unique ID.
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  T select(long id);

  /**
   * Loads a PDO from the database by its unique ID and applies a token lock.<br>
   * Same as {@link #select(long)} followed by {@link #requestTokenLock()}, but requires only a single roundtrip if remote.
   * Runs within its own transaction if none is currently running.
   * <p>
   * Throws a {@link LockException}, if locked by another user.
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  @NotInterceptable
  T selectTokenLocked(long id);

  /**
   * Loads a PDO from the database by its unique ID with a database write-lock.<br>
   * Requires that a transaction is running.
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  T selectForUpdate(long id);

  /**
   * Reloads the object.
   *
   * @return the object if reloaded, null if vanished from the database
   */
  T reload();

  /**
   * Reloads the object and applies a token lock.<br>
   * Same as {@link #reload()} followed by {@link #requestTokenLock()}, but requires only a single roundtrip if remote.
   * Runs within its own transaction if none is currently running.
   * <p>
   * Throws a {@link LockException}, if locked by another user.
   *
   * @return the object if reloaded, null if vanished from the database
   */
  T reloadTokenLocked();

  /**
   * Reloads the object with a database write-lock.<br>
   * Requires that a transaction is running.
   *
   * @return the object if reloaded, else null
   */
  T reloadForUpdate();

  /**
   * Selects all objects of this class.<br>
   * Eager relations are loaded as well, cascading as deep as the model declares
   * ({@code select = eager:<depth>}). The default depth of 1 joins the related entity only, which
   * keeps the generic selects from turning into join hogs. If a deeper or filtered graph is needed
   * for one access path only, use an explicit finder with load joins instead.
   *
   * @return the list of objects
   */
  List<T> selectAll();

  /**
   * Selects any objects of given IDs.
   *
   * @param ids the object IDs
   * @return the list of objects
   */
  List<T> selectAny(Collection<Long> ids);

  /**
   * Selects all records in the current context as a cursor.
   *
   * @return the cursor
   */
  ScrollableResource<T> selectAllAsCursor();

  /**
   * Selects the latest PDOs.<br>
   * Returns an optionally limited list of PDOs sorted by ID descending.
   *
   * @param greaterId the lower excluding ID
   * @param limit the optional limit, 0 for unlimited
   * @return the list of objects, never null
   */
  List<T> selectLatest(long greaterId, int limit);

  /**
   * Gets the object via cache.<br>
   * If there is no cache, the default implementation invokes {@link #select(long)}.
   *
   * @param id the unique object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  T selectCached(long id);

  /**
   * Gets the object via cache only.<br>
   * If there is no cache, the default implementation invokes {@link #select(long)}.
   *
   * @param id the unique object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  T selectCachedOnly(long id);

  /**
   * Gets all objects in context via cache.<br>
   * If there is no cache, the default implementation invokes {@link #selectAll()}.
   *
   * @return the list, never null
   * @see #selectAll()
   */
  List<T> selectAllCached();

  /**
   * Selects all objects to be loaded into the cache.
   * <p>
   * The method is used mainly by {@link PdoCache} as an optimization to retrieve
   * the list of PDOs from the remote server cache.
   * For local sessions, it is the same as {@link #selectAll()}.
   *
   * @return the list, never null
   */
  List<T> selectAllForCache();

  /**
   * Selects any objects of given IDs from the cache.
   * <br>
   * If there is no cache, the default implementation just loads from the database.
   *
   * @param ids the object IDs
   * @return the list of objects
   */
  List<T> selectAnyCached(Collection<Long> ids);

  /**
   * Selects any objects of given IDs.
   * <p>
   * The method is used mainly by {@link PdoCache} as an optimization to retrieve
   * multiple PDOs at once from the remote server cache.
   *
   * @param ids the object IDs
   * @return the list of objects
   */
  List<T> selectAnyForCache(Collection<Long> ids);

  /**
   * Selects a PDO to be loaded into the cache.
   * <p>
   * The method is used mainly by {@link PdoCache} as an optimization to retrieve
   * a PDO from the remote server cache.
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  @NotInterceptable
  T selectForCache(long id);

  /**
   * Validates this PDO.<br>
   * Invokes all field- and object validators.<br>
   * If the PDO is a composite, all components will be validated as well.
   * Root entities are automatically validated when save()d or persist()ed.
   *
   * @throws ValidationFailedException if validation fails
   */
  void validate();

  /**
   * Returns whether PDO has been successfully validated.<br>
   * A PDO is validated if not modified since the last validation
   * or not modified since loaded from the backend.
   *
   * @return true if validated, false if PDO needs validation before being persisted
   */
  boolean isValidated();

  /**
   * Save this PDO.<br>
   * If the object is new, a new ID is obtained and the object inserted.
   * Otherwise, the object is updated.
   * <p>
   * A saved object is no longer persistable.
   * It must be {@link #reload()}-ed to make it persistable again.
   * <p>
   * Any token lock will be removed.
   * @see #persist()
   */
  void save();

  /**
   * Persist this PDO.<br>
   * Same as {@link #save()} but returns the persisted object.<br>
   * The returned object is persistable.<br>
   * If the session is local, {@code this} is returned. For remote sessions,
   * the returned reference is always another instance.
   *
   * @return the persisted object, never null
   * @see #save()
   */
  T persist();

  /**
   * Persist this PDO and renew the token lock.<br>
   * Use this method if the application continues to work with the locked PDO
   * after being persisted.
   *
   * @return the persisted object, never null
   */
  T persistTokenLocked();

  /**
   * Gets the id of the user currently editing this object.
   *
   * @return the id or 0 if not being edited currently
   */
  @Persistent(comment = "userId of token holder")
  @Bindable
  long getEditedBy();

  /**
   * Gets the time since when this object is being edited.
   *
   * @return the timestamp, null if not being edited
   */
  @Persistent(comment = "time since token given to user")
  @Bindable
  Timestamp getEditedSince();

  /**
   * Gets the time when the token lock expires.
   *
   * @return the timestamp, null if not being edited
   */
  @Persistent(comment = "time when token expires")
  @Bindable
  Timestamp getEditedExpiry();

  /**
   * Indicates whether this entity's model provides at least one normtext column.
   *
   * @return true if pdo is using the normtext column, false if not.
   */
  boolean isNormTextProvided();

  /**
   * Gets the normtext.<br>
   * The <em>normtext</em> holds the normalized text of columns and relations.
   *
   * @return the normtext
   * @see org.tentackle.common.StringNormalizer#normalize(String)
   */
  @Persistent(comment = "normalized text")
  String getNormText();

  /**
   * Selects all objects containing a normalized text.<br>
   * The given text will be normalized via {@link org.tentackle.common.StringNormalizer#normalize(String)}
   * and selected via a sql {@code LIKE} expression.
   * If the text begins with an exclamation mark or a minus-sign (! or -), {@code NOT LIKE} will be used.
   *
   * @param text the text to search for
   * @return the list of objects, never null
   */
  List<T> selectByNormText(String text);

  /**
   * Same as {@link #selectByNormText(String)} but returns a cursor.
   *
   * @param text the text to search for
   * @return the cursor
   */
  ScrollableResource<T> selectByNormTextAsCursor(String text);

  /**
   * Loads all components.<br>
   * The method is used to transfer a copy of an object between tiers including all
   * composite object relations recursively.
   * Furthermore, it can be used to get a map of all components.
   *
   * @param onlyLoaded true to return only already loaded components (lazy composite relations)
   * @return the map of all components, including this object.
   */
  IdentifiableMap<? extends PersistentDomainObject<?>> loadComponents(boolean onlyLoaded);

}
