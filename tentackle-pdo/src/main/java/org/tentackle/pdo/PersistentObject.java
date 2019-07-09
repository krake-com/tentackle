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

import java.util.List;
import org.tentackle.bind.Bindable;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.Immutable;
import org.tentackle.misc.Modifiable;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.misc.Snapshotable;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Persistent;
import org.tentackle.session.SessionDependable;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationFailedException;

/**
 * Interface for the persistent part of a PDO.
 *
 * @param <T> the PDO
 * @author harald
 */
public interface PersistentObject<T extends PersistentDomainObject<T>>
       extends PersistenceDelegate<T>, PdoHolder<T>, Snapshotable<T>, Identifiable, Modifiable, SerialNumbered,
               Validateable, Immutable, ScopeConfigurator, DomainContextDependable, SessionDependable {

  /**
   * Gets the unique identification number.
   *
   * @return the ID, 0 if object is virgin, negative if object is deleted
   */
  @Persistent(comment = "object id")
  @Override
  @Bindable
  long getId();

  /**
   * Sets the unique identification number.<br>
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param id the ID
   */
  @Override
  void setId(long id);

  /**
   * Sets the serial number.<br>
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param serial the serial number
   */
  @Override
  void setSerial(long serial);

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
   * Sets the table serial number.<br>
   * The table serial is the modification count of the whole table.<br>
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param tableSerial the new table serial
   */
  void setTableSerial(long tableSerial);

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
   * Returns whether this object is a root entity.<br>
   * All entities that are not a component of another entity, are considered as a root entity.
   * They correspond to the so-called <em>aggregate root</em> in DDD,
   * with the subtle difference that a root entity may have no components at all.<br>
   * Only root entities can be persisted!
   * <p>
   * Root entities maintain their own entity context in {@link DomainContext}.
   *
   * @return true if root entity
   */
  boolean isRootEntity();

  /**
   * Returns whether this is a root entity of given component.
   *
   * @param <C> the type of the component
   * @param component the component pdo
   * @return true if this is the root entity of given component
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
  @Bindable
  long getRootId();

  /**
   * Sets the ID of the root entity this component belongs to.
   *
   * @param rootId the root id
   */
  void setRootId(long rootId);

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
  @Bindable
  int getRootClassId();

  /**
   * Sets the class ID of the root entity this component belongs to.
   *
   * @param rootClassId the root class id
   */
  void setRootClassId(int rootClassId);

  /**
   * Gets the database table name for the class of this object.
   * <p>
   * Notice: for MULTI-inheritance entities the tablename is the
   * last in the inheritance chain.
   *
   * @return the table name, null if class does not map to a database table
   */
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
   *   <li>refactoring: package- and classnames may be changed. The class ID is fixed.</li>
   *   <li>performance: comparing integers is faster than comparing strings (in Java <em>and</em> the database)</li>
   * </ol>
   * Notice that abstract entities (all except the leaves within an inheritance hierarchy) don't have a classid.
   *
   * @return the class id, 0 if abstract
   */
  @Persistent(comment = "class id")
  @Bindable
  int getClassId();

  /**
   * Creates the {@link SecurityResult} for a given permission.
   *
   * @param permission the requested permission
   * @return the security result (never null)
   */
  SecurityResult getSecurityResult(Permission permission);

  /**
   * Checks if the permission for a given permission is accepted.
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
   * Can be used to determine whether object is shown to the user.
   *
   * @return true if allowed
   */
  boolean isViewAllowed();

  /**
   * Determines whether the user is allowed to edit this PDO.<br>
   * Can be used to determine whether object is editable by the user.
   *
   * @return true if allowed
   */
  boolean isEditAllowed();

  /**
   * Checks whether this object is already persistent in the database or only residing in memory.<br>
   * If an object {@code isNew()}, it means that it can be persisted (inserted).
   * This does not mean, that the object never has been stored in the database,
   * i.e. it is possible that the object just has been deleted.
   *
   * @return true if object is not in database, i.e. new (or deleted)
   */
  boolean isNew();

  /**
   * Reserves an ID.<p>
   * Reserved IDs are negative.
   * A new object with a reserved ID can be distinguished from
   * a deleted object by its serial. See also {@link #isVirgin}.
   * If the object already has an ID or is deleted (negative ID)
   * the ID will _not_ change.
   */
  void reserveId();

  /**
   * Checks whether object is deleted.
   *
   * @return true if object has been removed from the database
   */
  boolean isDeleted();

  /**
   * Sets the modified flag.<p>
   * For optimizations it is possible to skip objects that have not
   * been modified. The modified-attribute is cleared whenever the
   * object is persisted.
   *
   * @param modified is true if object is flagged modified, false if not.
   */
  void setModified(boolean modified);

  /**
   * Returns whether the modification state is available.<br>
   * By default, PDOs are not tracked.<br>
   * This is quality measure to ensure that isModified() returns
   * false if and only if it hasn't been modified, i.e. the (generated) code
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
   * @return true if this object
   */
  boolean attributesModified();

  /**
   * Returns whether any of the attributes differs from the values persisted in the database.<br>
   * This method is only applicable to fulltracked entities and returns false if not fulltracked.
   *
   * @return true if differs, false if no change or entity isn't fulltracked
   */
  boolean differsPersisted();

  /**
   * Determines whether this object is allowed to be stored in database.<br>
   * Notice that once a PDO is persisted, the same object reference is no more persistable
   * and must be reloaded. Rule: if the reference is no more used, use {@link #save()},
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
   * referential integrity, for example to enable a delete button in the UI.<br>
   * The default implementation returns false.
   *
   * @return true if referenced
   */
  boolean isReferenced();

  /**
   * Sets this object's expiration flag.<br>
   * Expired objects are considered as <em>invalid</em> and must be reloaded before being used any further.
   * Used in caches.
   *
   * @param expired true if object is expired
   */
  void setExpired(boolean expired);

  /**
   * Checks whether object has been marked expired.<br>
   * Expired objects will be reloaded from the database by
   * the cache when the object is retrieved again.
   *
   * @return true if object is expired (in cache)
   */
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
   * Gets the expired table serials.<br>
   * A physical database query is only done if the requested range is not in the backlog.
   * Used to reduce roundtrips for remote sessions.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial)
   * @return pairs of longs, the first being the ID, the second the tableserial, never null
   */
  List<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial);

  /**
   * Determines the objects with a tableSerial starting at a given serial.
   * Useful to cleanup caches for example.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @return pairs of longs, the first being the ID, the second the tableserial, never null
   */
  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial);

  /**
   * Determines the objects with their tableSerial within a given range.
   * Useful to cleanup caches.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial)
   * @return pairs of longs, the first being the ID, the second the tableserial, never null
   */
  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial);

  /**
   * Selects all pdos with a tableSerial starting at a given serial.<br>
   * Useful to update expired objects in a batch.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @return the list of objects, never null
   */
  List<T> selectAllWithExpiredTableSerials(long oldSerial);

  /**
   * Selects all id,serial-pairs of this class as a list of {@link IdSerialTuple}.
   *
   * @return the list of objects
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
   * @return the highest id, -1 if table is empty
   */
  long selectMaxId();

  /**
   * Selects the highest table serial.
   *
   * @return the highest table serial, -1 if table is empty
   */
  long selectMaxTableSerial();

  /**
   * Gets the cache.
   * <p>
   * Notice that abstract classes don't provide their own cache, but
   * instead use the caches of their concrete classes.
   *
   * @return the cache for this PDO, null if none.
   */
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
  void markCacheAccess();

  /**
   * Returns whether object is cached.
   *
   * @return true if cached
   */
  boolean isCached();

  /**
   * Determines whether object is cacheable or not.<br>
   * The default implementation always returns true, but apps
   * can use this as a filter.
   *
   * @return true if object is cacheable
   */
  boolean isCacheable();

  /**
   * Returns whether this an abstract PDO.<br>
   * Abstract PDOs are real objects but cannot be persisted.
   * They may be used to retrieve data for the concrete implementations, however.
   *
   * @return true if abstract PDO
   */
  boolean isAbstract();

  /**
   * Checks whether this object can be removed.
   * <p>
   * Should be invoked before operations that may have an impact on the
   * referential integrity, for example to enable a delete button in the UI.<br>
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
   * The default implementation invokes findByUniqueDomainKey(getUniqueDomainKey())
   * and throws {@link UnsupportedOperationException} if one of those methods are not implemented
   * (which is the default).
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
   * Returns a value greater 0 if {@link #isTokenLockProvided()}, else 0.
   *
   * @return the timespan in ms, 0 if no token lock
   */
  long getTokenLockTimeout();

  /**
   * Requests a token lock.
   */
  void requestTokenLock();

  /**
   * Releases a token lock.<br>
   * Use this method if a PDO needs to be unlocked without being persisted.
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
   * Checks whether this object is token locked by given user.
   *
   * @param userId the user's ID
   * @return true locked
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
   * @return true if object is not locked at all or locked by the current user
   */
  boolean isTokenLockableByMe();

  /**
   * Gets the object associated to the token lock.<br>
   * This is usually a PDO implementing the concept of a user, group, role or whatever.
   *
   * @param <U> the locking object type
   * @return the locking object, null if not locked
   */
  <U extends PersistentDomainObject<U>> U getTokenLockObject();

  /**
   * Sets the locking object.
   * <p>
   * CAUTION: the method is provided for rare corner cases like administration tools.
   * Don't use it for regular locking/unlocking!
   *
   * @param <U> the locking object type
   * @param obj the locking object, null to clear.
   */
  <U extends PersistentDomainObject<U>> void setTokenLockObject(U obj);

  /**
   * Transfers the token lock to another user.<br>
   * Necessary to hand-over a token without releasing it.<br>
   * Eliminates the time-gap when object is token-free.<br>
   * Allows taking over the token WITHOUT possessing it!
   * This method should be used with great care as it does not check
   * who owns the token and if there is a token at all.
   * The serial will be increased, so the old user gets an error
   * if trying to update.<br>
   * Furthermore, the current object does *NOT* get the serial increased
   * thus preventing any update without reloading the object again!
   * You have to use the returned PDO!
   * <br>
   * The modtable is updated because the serial is increased and thus all caches must be invalidated.
   * <p>
   * CAUTION: the method is provided for rare corner cases like administration tools.
   * Don't use it for regular locking/unlocking!
   *
   * @param userId is the new user Id. (special 0 = free token, even if we are not the owner!)
   * @return the updated PDO
   */
  T transferTokenLock(long userId);

  /**
   * Loads a PDO from the database by its unique ID.<br>
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  T select(long id);

  /**
   * Loads and write-locks a PDO from the database by its unique ID.<br>
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
   * @return the object if reloaded, null if vanished from the database (never <em>this</em>)
   */
  T reload();

  /**
   * Reloads the object with a write lock.<br>
   * Requires that a transaction is running.
   *
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  T reloadForUpdate();

  /**
   * Selects all objects of this class.<br>
   * Eager relations are loaded as well, but limited to the first level to prevent join hogs.
   * If more than one level must be eagerly loaded, use explicit finders.<br>
   * Notice that tracked objects are returned as a {@link org.tentackle.misc.TrackedList}.
   *
   * @return the list of objects
   */
  List<T> selectAll();

  /**
   * Selects all records in current context as a cursor.
   *
   * @return the cursor
   */
  ScrollableResource<T> selectAllAsCursor();

  /**
   * Gets the object via cache.<br>
   * If there is no cache, the default implementation just loads from the database.
   *
   * @param id the uniue object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  T selectCached(long id);

  /**
   * Gets the object via cache only.<br>
   * If there is no cache, the default implementation just loads from the database.
   *
   * @param id the uniue object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  T selectCachedOnly(long id);

  /**
   * Gets all objects in context via cache.<br>
   * If there is no cache, the default implementation load from the database.
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
   *
   * @return the list, never null
   * @throws PersistenceException if the session is not remote
   */
  List<T> selectAllForCache();

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
   * @return true if validated, false if needs validation before being persisted
   */
  boolean isValidated();

  /**
   * Saves this object.<br>
   * If this object is new, a new ID is obtained and this object inserted.
   * Otherwise this object is updated.
   * <p>
   * A saved object is no more persistable.
   * It must be reload()ed to persist it again.
   * Use persist() if the object needs to be persisted again.
   * <p>
   * Notice that the token lock will be cleared.
   */
  void save();

  /**
   * Persists this object.<br>
   * Same as save() but returns the persisted object.<br>
   * If the session is local, {@code this} is returned. For remote sessions,
   * the returned reference is always another instance.
   * <p>
   * Notice that the token lock will be cleared.
   *
   * @return the persisted object, never null
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
   * Gets the optional transient data object.<br>
   * The default implementation does nothing.
   *
   * @return the transient data, null if none.
   */
  Object getTransientData();

  /**
   * Sets the optional transient data object.<br>
   * When objects need to be reloaded from storage, all non-persistent
   * data attached to the object would be lost. In such cases the application may use
   * getTransientData() and setTransientData() to keep the non-persistent attributes.<br>
   * The default implementation does nothing.
   *
   * @param data the transient data
   */
  void setTransientData(Object data);

  /**
   * Gets the id of the user currently editing this object.
   *
   * @return the id or 0 if not being edited currently.
   */
  @Persistent(comment = "userId of token holder")
  @Bindable
  long getEditedBy();

  /**
   * Sets the user editing this object.
   * Does *NOT* alter isModified().
   *
   * @param editedBy the id of the user, 0 to clear.
   *
   */
  void setEditedBy(long editedBy);

  /**
   * Gets the time since when this object is being edited.
   *
   * @return the time, null if not being edited.
   */
  @Persistent(comment = "time since token given to user")
  @Bindable
  Timestamp getEditedSince();

  /**
   * Sets the time since when this object is being edited.
   * Does *NOT* alter isModified().
   *
   * @param editedSince the time, null to clear.
   */
  void setEditedSince(Timestamp editedSince);

  /**
   * Gets the time since when this object is being edited.
   *
   * @return the time, null if not being edited.
   */
  @Persistent(comment = "time when token expires")
  @Bindable
  Timestamp getEditedExpiry();

  /**
   * Sets the time when the token should expire.
   * Does *NOT* alter isModified().
   *
   * @param editedExpiry the expiration time, null to clear.
   */
  void setEditedExpiry(Timestamp editedExpiry);

  /**
   * Returns whether this PDO is a composite.<br>
   * Composite PDOs consist of other component PDOs, defined via composite relations.<br>
   * Notice that a <em>composite root entity</em> is synonym to the <em>aggregate root</em> in DDD lingo.
   *
   * @return true if object is a composite, false if not.
   */
  boolean isComposite();

  /**
   * Indicates whether this entity's model provides a normtext column.
   *
   * @return true if pdo is using the normtext column, false if not.
   */
  boolean isNormTextProvided();

  /**
   * Sets the normtext.
   *
   * @param normText the normtext
   */
  void setNormText(String normText);

  /**
   * Gets the normtext.<br>
   * The <em>normtext</em> holds the normalized text of columns and relations.
   *
   * @return the normtext
   * @see org.tentackle.common.StringHelper#normalize(String)
   */
  @Persistent(comment = "normalized text")
  String getNormText();

  /**
   * Selects all objects containing a normalized text.<br>
   * The given text will be normalized via {@link org.tentackle.common.StringHelper#normalize(String)}
   * and selected via an sql {@code LIKE} expression.
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
   * @param onlyLoaded true if return only already loaded components (lazy composite relations)
   * @return the map of all components, including this object.
   */
  IdentifiableMap<? extends PersistentDomainObject<?>> loadComponents(boolean onlyLoaded);

}
