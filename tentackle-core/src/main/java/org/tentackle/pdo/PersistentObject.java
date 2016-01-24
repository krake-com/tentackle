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


package org.tentackle.pdo;

import java.util.List;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.Modifiable;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.misc.Snapshotable;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityResult;
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
               Validateable, ScopeConfigurator, DomainContextDependable, SessionDependable {

  /**
   * Gets the pdo instance.
   * <p>
   * Same as {@link PdoHolder#getPdo()} but shorter.
   *
   * @return the pdo
   */
  T pdo();

  /**
   * Creates a clone of this PO.
   *
   * @return the cloned PO
   */
  PersistentObject<T> clonePersistentObject();

  /**
   * Gets the identification number.
   *
   * @return the ID
   */
  @Persistent("object id")
  @Override
  long getId();

  /**
   * Sets the identification number.
   *
   * @param id the ID
   */
  @Persistent("object id")
  @Override
  void setId(long id);

  /**
   * Sets the serial number (modification count).
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param serial the serial number
   */
  @Persistent("object version")
  @Override
  void setSerial(long serial);

  /**
   * Gets the serial number.
   *
   * @return the serial number.
   */
  @Persistent("object version")
  @Override
  long getSerial();


  /**
   * Indicates whether this entity's model provides a tableserial column.
   *
   * @return true if pdo is using the tableSerial column, false if not.
   */
  boolean isTableSerialProvided();

  /**
   * Sets the table serial number (table modification count).
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param tableSerial the new table serial
   */
  @Persistent("table version")
  void setTableSerial(long tableSerial);

  /**
   * Gets the table serial.
   *
   * @return the table serial
   */
  @Persistent("table version")
  long getTableSerial();


  /**
   * Defines whether attributes of the object may be changed.
   * <p>
   * Any attempt to invoke a setter on an immutable object
   * results in a {@link PersistenceException}.
   * All generated methods check this. Note that even lazy loading of
   * an immutable object isn't allowed as well because the
   * reference to the lazily loaded object must be changed.
   *
   * @param immutable true if object is immutable
   */
  void setImmutable(boolean immutable);

  /**
   * Returns whether object is immutable.
   * <p>
   * By default objects are mutable.
   *
   * @return true if immutable
   */
  boolean isImmutable();

  /**
   * Returns whether this object is a root entity.
   * <p>
   * Root entities maintain their own entity context in {@link DomainContext}.
   *
   * @return true if root entity
   */
  boolean isRootEntity();

  /**
   * Indicates whether this entity's model provides a root-ID column.
   *
   * @return true if column provided, false if not
   */
  boolean isRootIdProvided();

  /**
   * Gets the ID of the root entity this component belongs to.
   *
   * @return the root id
   */
  long getRootId();

  /**
   * Sets the ID of the root entity this component belongs to.
   *
   * @param rootId the root id
   */
  void setRootId(long rootId);

  /**
   * Indicates whether this entity's model provides a root-class-ID column.
   *
   * @return true if column provided, false if not
   */
  boolean isRootClassIdProvided();

  /**
   * Gets the class ID of the root entity this component belongs to.
   *
   * @return the root class id
   */
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
   * Gets the unique ID of the class.
   *
   * @return the class id
   */
  int getClassId();

  /**
   * Creates the {@link SecurityResult} for a given permission.
   * @param permission the requested permission
   * @return the security result (never null)
   */
  SecurityResult getSecurityResult(Permission permission);

  /**
   * Checks if the permission for a given permission is accepted.
   * @param permission the requested permission
   * @return true if permission is accepted
   */
  boolean isPermissionAccepted(Permission permission);

  /**
   * Determines whether the application is allowed to write this PDO.
   * <p>
   * Notice that there is no isReadAllowed because PDOs without read permission
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
   * Checks whether this object is already persistant in the db
   * or only residing in memory.
   * If an object isNew(), it means that it can be inserted.
   * This does not mean, that the object never has been stored in the db,
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
   * Checks whether object is deleted (or got a reserved ID).
   * A deleted object with a reserved ID can be distinguished from
   * an object with a reserved ID by its serial. See also {@link #isVirgin}.
   *
   * @return true if object has been deleted
   */
  boolean isDeleted();


  /**
   * Sets the modified flag.<p>
   * For optimizations it is possible to skip objects that have not
   * been modified. The modified-attribute is cleared whenever the
   * object is saved (inserted or updated -- NOT insertPlain and updatePlain!).
   * The application is responsible to set the modified flag!
   * This is usually done in the setter-methods of the db-attributes.
   * The wurblet 'DbMethods' does this for you with option '--tracked'
   *
   * @param modified is true if object is flagged modified, false if not.
   */
  void setModified(boolean modified);


  /**
   * Returns whether the modification state is available.<br>
   * If true, setModified() is called properly. By default, PDOs are not tracked.
   * This is quality measure to ensure that isModified() returns
   * false if and only if it hasn't been modified, i.e. the setters
   * check for modification.
   *
   * @return true if tracked, false otherwise (default)
   */
  boolean isTracked();

  /**
   * Determines whether this object got some of its attributes modified.<br>
   * It does not check whether some of its components are modified!
   * This method can also be used for non-tracked objects.
   *
   * @return true if this object
   */
  boolean attributesModified();

  /**
   * Determines whether this object is allowed to be stored in DB.<br>
   * The default is true.<br>
   * Objects not allowed to be saved will force save(), insert() and update()
   * to return 'false' and silently skipped in saveList().<br>
   * Objects save()d or delete()d are no more persistable.
   *
   * @return true if saveable
   */
  boolean isPersistable();

  /**
   * Checks whether this object is referenced by other objects.
   * <p>
   * It is invoked before operations that may have an impact on the
   * referential integrity.
   * The default implementation returns false.
   * <p>
   * The application can assume a lazy context (invoked from is...Lazy)
   * if invoked outside a transaction. This is just an optimization hint.
   * <p>
   * @return true if referenced
   */
  boolean isReferenced();

  /**
   * Sets this object's expiration flag.
   * @param expired true if object is expired
   */
  void setExpired(boolean expired);

  /**
   * Checks whether object has been marked expired.
   * Expired objects will be reloaded from the database by
   * the cache when the object is retrieved again.
   * @return true if object is expired (in cache)
   */
  boolean isExpired();

  /**
   * Selects the current modification count for the class of this object.
   *
   * @return the modification count
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
   * Selects all id,serial-pairs of this class as a list of {@link IdSerialTuple}.
   *
   * @return the list of objects
   */
  List<IdSerialTuple> selectAllIdSerial();

  /**
   * Selects the serial-number for a given object id.
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
   * mark cache access (count and set current system-time)
   */
  void markCacheAccess();

  /**
   * @return true if object is cached
   */
  boolean isCached();

  /**
   * Determines whether object is cacheable or not.
   * The default implementation always returns true, but apps
   * can use this as a filter.
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
   * It is invoked before operations that may have an impact on the
   * referential integrity.
   * The default implementation returns true if !isNew and !isReferenced.
   * Does not refer to the SecurityManager!
   * <p>
   * Notice that isRemovable() for performance reasons is not covered
   * by its own delegate method in remote connections.
   * Hence, if classes in your application require a different implementation
   * (!isNew &amp;&amp; !isReferenced) you must provide such a remote method.
   *
   * @return true if removable
   */
  boolean isRemovable();


  /**
   * Prepares this object for delete.<br>
   * The method is invoked at the client-side only, i.e. 3-tier or
   * 2-tier, but never at the server-side in 3-tier mode.
   * The default implementation clears a non-transient flag that tells
   * the server that prepareDelete() already has been invoked on the client side.
   * Applications may override the method to perform any modifications to the
   * object before it is sent to the server.
   * <p>
   * Example:
   * <pre>
   * void prepareDelete() {
   *   // do something
   *   ...
   *   super.prepareDelete();   // IMPORTANT!!!
   * }
   * </pre>
   * Notice that the method is invoked outside of the transaction and that it
   * is not invoked if the object is new or not saveable.
   *
   * @see #delete
   * @see #isNew()
   * @see #isPersistable()
   */
  void prepareDelete();

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
   * Checks whether this object (if saved) would violate any
   * unique constraints.
   * <p>
   * The method is usually used by the presentation layer
   * to check for duplicates.
   * The default implementation invokes findByUniqueDomainKey(getUniqueDomainKey())
   * and throws {@link UnsupportedOperationException} if one of those methods are not implemented
   * (which is the default).
   *
   * @return the duplicate object, null if no duplicate
   */
  T findDuplicate();


  /**
   * Indicates whether this entity's model provides the token lock columns.
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
   * Checks whether this object is token locked (editedBy != 0) and
   * the lock is not expired.
   *
   * @return true if locked by another user
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
   * Gets the object associated to the id of the editedBy-attribute.<br>
   * This is usually the id of an PDO implementing the concept
   * of a user, group, role or whatever.
   *
   * @param <U> the user type
   * @return the user object, null if no user or id was 0
   */
  <U extends PersistentDomainObject<U>> U getTokenLockObject();

  /**
   * Sets the user/group/role-object editing this object.
   *
   * @param <U> the user type
   * @param obj the object, null to clear.
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
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  T selectLocked(long id);

  /**
   * Reloads the object.<p>
   * Note: to make sure that any lazy inits are cleared,
   * the returned object is always a new object.
   *
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  T reload();

  /**
   * Reloads the object with a write lock.
   * Note: to make sure that any lazy inits are cleared,
   * the returned object is always a new object.
   *
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  T reloadLocked();


  /**
   * Selects all objects of this class as a {@link java.util.List}.
   * The linked objects are loaded as well.
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
   * If there is no cache (i.e. the method is not overridden),
   * the default implementation just loads from the session.
   *
   * @param id the uniue object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  T selectCached(long id);

  /**
   * Gets the object via cache only.<br>
   * If there is no cache (i.e. the method is not overridden),
   * the default implementation just loads from the db.
   *
   * @param id the uniue object ID
   * @return the object, null if no such object
   * @see #select(long)
   */
  public T selectCachedOnly(long id);


  /**
   * Gets all objects in context via cache.
   * If there is no cache (i.e. the method is not overridden),
   * the default implementation gets it from the session.
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
   * Creates a new object as a copy of the current object in another
   * (or the same) database context.<br>
   * The new context must be of the same class as the old one.
   * Subclasses must override this method if they contain links to
   * other objects (via IDs) that may have changed too (depending on
   * the context).<br>
   * The default implementation just clones the object, sets the context
   * and sets the original object (see {@link #getCopiedObject}).
   *
   * @param otherContext the database context
   * @return the created object
   */
  T createCopyInContext(DomainContext otherContext);


  /**
   * Gets the original object if this object was created by {@link #createCopyInContext}.
   *
   * @return the object or null if this object is not a copy
   */
  T getCopiedObject();


  /**
   * Validates this PDO.<br>
   * Invokes all field- and object validators.<br>
   * Root-entities are automatically validated when save()d or persist()ed.
   *
   * @throws ValidationFailedException if validation fails
   */
  void validate();

  /**
   * Returns whether PDO has been successfully validated.<br>
   * A PDO is validated if not modified since the last validation
   * or not modified since loaded from backend.
   *
   * @return true if validated, false if needs validation before being persisted
   */
  boolean isValidated();


  /**
   * Saves this object.<br>
   * If this object is new, a new ID is obtained and this object inserted.
   * Otherwise this object is updated.
   * <p>
   * If the session is remote, the saved object is no more persistable.
   * It must be reload()ed to persist it again.
   * Use persist() if the object needs to be persisted again.
   * <p>
   * Notice that the token lock will be cleared.
   */
  void save();


  /**
   * Persists this object.<br>
   * Same as save() but returns the persisted object.<br>
   * If the session is local, this is returned. For remote sessions,
   * the returned reference is always another instance.
   * <p>
   * Notice that the token lock will be cleared.
   *
   * @return the persisted object, never null
   */
  T persist();

  /**
   * Persist this PDO and renew the edited-by token.<br>
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
   * Sometimes, e.g. when objects need to be reloaded from storage, all non-persistent
   * data attached to the object would be lost. In such cases the framework invokes
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
  @Persistent("userId of token holder")
  long getEditedBy();

  /**
   * Sets the user editing this object.
   * Does *NOT* alter isModified().
   *
   * @param editedBy the id of the user, 0 to clear.
   *
   */
  @Persistent("userId of token holder")
  void setEditedBy(long editedBy);


  /**
   * Gets the time since when this object is being edited.
   *
   * @return the time, null if not being edited.
   */
  @Persistent("time since token given to user")
  Timestamp getEditedSince();

  /**
   * Sets the time since when this object is being edited.
   * Does *NOT* alter isModified().
   *
   * @param editedSince the time, null to clear.
   */
  @Persistent("time since token given to user")
  void setEditedSince(Timestamp editedSince);


  /**
   * Gets the time since when this object is being edited.
   *
   * @return the time, null if not being edited.
   */
  @Persistent("time when token expires")
  Timestamp getEditedExpiry();

  /**
   * Sets the time when the token should expire.
   * Does *NOT* alter isModified().
   *
   * @param editedExpiry the expiration time, null to clear.
   */
  @Persistent("time when token expires")
  void setEditedExpiry(Timestamp editedExpiry);



  /**
   * Returns whether this PDO is a composite.<br>
   * Composite PDOs consist of other PDOs via composite relations.
   *
   * @return true if object is composite, false if not.
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
   * @param normText the normtext
   */
  @Persistent("normalized text")
  void setNormText(String normText);

  /**
   * Gets the normtext.
   *
   * @return the normtext
   */
  @Persistent("normalized text")
  String getNormText();

  /**
   * Selects all objects with a given normtext as a list.
   *
   * @param normText the normtext
   * @return the list of objects, never null
   */
  List<T> selectByNormText(String normText);

  /**
   * Selects all objects with a given normtext as a cursor.
   *
   * @param normText the normtext
   * @return the cursor
   */
  ScrollableResource<T> selectByNormTextAsCursor(String normText);

}
