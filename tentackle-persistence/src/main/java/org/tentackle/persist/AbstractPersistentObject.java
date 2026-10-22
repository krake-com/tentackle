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

package org.tentackle.persist;

import org.tentackle.common.Constants;
import org.tentackle.common.DateHelper;
import org.tentackle.common.StringNormalizer;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.common.Timestamp;
import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbModificationType;
import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.ModificationType;
import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.Query;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.dbms.SqlSupplier;
import org.tentackle.dbms.StatementId;
import org.tentackle.dbms.StatementKey;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.PropertySupport;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.misc.TrackedArrayList;
import org.tentackle.misc.TrackedList;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainDelegate;
import org.tentackle.pdo.LockException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PdoMethodCache;
import org.tentackle.pdo.PdoMethodCacheProvider;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.persist.trip.AbstractPersistentObjectRemoteDelegate;
import org.tentackle.persist.trip.RemoteResultSetCursor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityException;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.NotFoundException;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;
import org.tentackle.sql.Backend;
import org.tentackle.trip.TripReferencable;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.scope.PersistenceScope;

import java.io.Serial;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.tentackle.sql.Backend.SQL_ALLSTAR;
import static org.tentackle.sql.Backend.SQL_AND;
import static org.tentackle.sql.Backend.SQL_ARRAY_ANY;
import static org.tentackle.sql.Backend.SQL_COMMA;
import static org.tentackle.sql.Backend.SQL_EQUAL;
import static org.tentackle.sql.Backend.SQL_EQUAL_PAR;
import static org.tentackle.sql.Backend.SQL_EQUAL_PAR_COMMA;
import static org.tentackle.sql.Backend.SQL_EQUAL_ZERO;
import static org.tentackle.sql.Backend.SQL_FROM;
import static org.tentackle.sql.Backend.SQL_GREATER_PAR;
import static org.tentackle.sql.Backend.SQL_ISNULL;
import static org.tentackle.sql.Backend.SQL_LEFT_PARENTHESIS;
import static org.tentackle.sql.Backend.SQL_LESS_PAR;
import static org.tentackle.sql.Backend.SQL_LIKE_PAR;
import static org.tentackle.sql.Backend.SQL_NOTLIKE_PAR;
import static org.tentackle.sql.Backend.SQL_OR;
import static org.tentackle.sql.Backend.SQL_ORDERBY;
import static org.tentackle.sql.Backend.SQL_PLUS_ONE;
import static org.tentackle.sql.Backend.SQL_RIGHT_PARENTHESIS;
import static org.tentackle.sql.Backend.SQL_SELECT;
import static org.tentackle.sql.Backend.SQL_SET;
import static org.tentackle.sql.Backend.SQL_UPDATE;
import static org.tentackle.sql.Backend.SQL_WHERE;
import static org.tentackle.sql.Backend.SQL_WHEREALL;

/**
 * Base persistence implementation of a PDO.
 * <p>
 * The application's persistence classes extend this class and implement the persistence interface
 * of the PDO. The entity-specific mapping code is generated from the model by the wurblets.
 * Besides the CRUD and query methods, the class provides:
 * <ul>
 *   <li>root and component handling of aggregates, including the root ID and root class ID of components,</li>
 *   <li>embedded PDOs, which share the table of their embedding parent,</li>
 *   <li>snapshots and deep copies,</li>
 *   <li>the optional token lock (editedBy, editedSince and editedExpiry),</li>
 *   <li>the optional normtext, a phonetically normalized search text,</li>
 *   <li>PDO caching, security checks and eager joins.</li>
 * </ul>
 * If the session is remote, the methods are forwarded to the {@link AbstractPersistentObjectRemoteDelegate}
 * and executed on the server.
 *
 * @param <T> the PDO class (interface)
 * @param <P> the persistence implementation class
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)    // only proxies are referenced, not implementations
public abstract class AbstractPersistentObject<T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>
       extends AbstractDbObject<P>
       implements PersistentObject<T>, EffectiveClassProvider<T>, PdoMethodCacheProvider<T>, Cloneable {

  @Serial
  private static final long serialVersionUID = 1L;

  private transient boolean contextImmutable;     // true if domain context cannot be changed anymore
  private T pdo;                                  // the pdo this is a delegate for
  private boolean persistable = true;             // true if persistable (default)

  private long editedBy;                          // if != 0: holds the userId of token holder
  private Timestamp editedSince;                  // if editedBy!=0: time since token given to user, if 0: time since token released
  private Timestamp editedExpiry;                 // time when token expires

  /**
   * The optional "NormText", i.e., a phonetically normalized text which can be searched.
   */
  private String normText;

  /**
   * Optional ID of the root entity.<br>
   * Improves the performance of SQL selects in composite entities.
   */
  private long rootId;

  /**
   * Optional class ID of the root entity.<br>
   * Improves performance in SELECTs for composite entities
   * if the components belong to different kinds of root entities.
   */
  private int rootClassId;

  private transient boolean validated;            // true if validated ok (transient because we don't trust remote clients)
  private boolean renewTokenLock;                 // true if renew token lock once(!) upon next persistent operation

  // if embedded
  private AbstractPersistentObject<?, ?> embeddingParent;  // != null if this is an embedded PDO
  private String columnPrefix;                        // prefix for embedded columns

  // for PdoCache
  private transient long cacheAccessCount;        // access counter (if the caching strategy is LFU)
  private transient long cacheAccessTime;         // last access time (if the caching strategy is LRU)
  private transient boolean expired;              // true = the object is expired in all caches it belongs to

  // snapshot
  private transient List<WeakReference<T>> snapshots;   // taken snapshots, null if none
  private transient boolean objectIsCopy;               // true if this is a deep copy
  private transient boolean objectIsSnapshot;           // true if this is a snapshot


  /** name of the editedBy column. */
  public static final String CN_EDITEDBY = org.tentackle.common.Constants.CN_EDITEDBY;

  /** name of the editedBy attribute. */
  public static final String AN_EDITEDBY = org.tentackle.common.Constants.AN_EDITEDBY;

  /** name of the editedExpiry column. */
  public static final String CN_EDITEDEXPIRY = org.tentackle.common.Constants.CN_EDITEDEXPIRY;

  /** name of the editedExpiry attribute. */
  public static final String AN_EDITEDEXPIRY = org.tentackle.common.Constants.AN_EDITEDEXPIRY;

  /** name of the editedSince column. */
  public static final String CN_EDITEDSINCE = org.tentackle.common.Constants.CN_EDITEDSINCE;

  /** name of the editedSince attribute. */
  public static final String AN_EDITEDSINCE = org.tentackle.common.Constants.AN_EDITEDSINCE;

  /** name of the normText column. */
  public static final String CN_NORMTEXT = org.tentackle.common.Constants.CN_NORMTEXT;

  /** name of the normText attribute. */
  public static final String AN_NORMTEXT = org.tentackle.common.Constants.AN_NORMTEXT;

  /** name of root-ID column. */
  public static final String CN_ROOTID = org.tentackle.common.Constants.CN_ROOTID;

  /** name of root-ID attribute. */
  public static final String AN_ROOTID = org.tentackle.common.Constants.AN_ROOTID;

  /** name of root class-ID column. */
  public static final String CN_ROOTCLASSID = org.tentackle.common.Constants.CN_ROOTCLASSID;

  /** name of root class-ID attribute. */
  public static final String AN_ROOTCLASSID = org.tentackle.common.Constants.AN_ROOTCLASSID;

  // transaction names
  /** save copy in context. */
  public static final String TX_SAVE_COPY_IN_CONTEXT    = "save copy in context";
  /** delete all in context. */
  public static final String TX_DELETE_ALL_IN_CONTEXT   = "delete all in context";
  /** transfer token lock. */
  public static final String TX_TRANSFER_TOKENLOCK      = "transfer lock";
  /** update token lock. */
  public static final String TX_UPDATE_TOKENLOCK        = "update lock";
  /** update token lock only. */
  public static final String TX_UPDATE_TOKENLOCK_ONLY   = "update lock only";
  /** select token-locked. */
  public static final String TX_SELECT_TOKENLOCKED      = "select locked";
  /** insert a token-locked object. */
  public static final String TX_INSERT_TOKENLOCKED      = "insert locked object";
  /** update a token-locked object. */
  public static final String TX_UPDATE_TOKENLOCKED      = "update locked object";
  /** delete a token-locked object. */
  public static final String TX_DELETE_TOKENLOCKED      = "delete locked object";



  /**
   * Creates a persistent object for the given PDO.
   *
   * @param pdo the persistent domain object this is a delegate for
   * @param context the domain context
   */
  public AbstractPersistentObject(T pdo, DomainContext context) {
    this.pdo = pdo;
    setDomainContext(context);
  }

  /**
   * Creates a persistent object for the given PDO without a domain context
   * for a given session.<p>
   * Note: the application must set the context.
   *
   * @param pdo the persistent domain object this is a delegate for
   * @param session the session (must be an instance of {@link Db})
   */
  public AbstractPersistentObject(T pdo, Session session) {
    super((Db) session);
    this.pdo = pdo;
  }

  /**
   * Creates a persistent object for the given PDO without a domain context.<p>
   * Note: the application must set the context.
   *
   * @param pdo the persistent domain object this is a delegate for
   */
  public AbstractPersistentObject(T pdo) {
    super();
    this.pdo = pdo;
  }

  /**
   * Creates a persistent object without PDO and domain context.<br>
   * Note: the application must set the PDO and the context.
   */
  public AbstractPersistentObject() {
    super();
  }


  @Override
  public DomainDelegate<T> getDomainDelegate() {
    return pdo.getDomainDelegate();
  }

  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public T me() {
    return pdo;
  }

  @Override
  public final String toGenericString() {
    if (isEmbedded()) {
      return getClass().getName() + "[embedded]";
    }
    return super.toGenericString();
  }

  /**
   * Sets the PDO.
   *
   * @param pdo the PDO
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  @Override
  public Class<T> getEffectiveClass() {
    return pdo.getEffectiveClass();
  }

  @Override
  public List<Class<? super T>> getEffectiveSuperClasses() {
    return pdo.getEffectiveSuperClasses();
  }

  @Override
  public PdoMethodCache<T> getPdoMethodCache() {
    return getClassVariables().methodCache;
  }

  /**
   * Clones this persistent object.<br>
   * The method is final to prevent applications from overriding it and
   * spoiling the contract with {@link #createSnapshot()}.
   * <p>
   * Notice that the proxy PDO is cleared to null in the cloned PO.
   *
   * @return the cloned persistent object
   */
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected final P clone() {
    try {
      P clonedPo = (P) super.clone();
      ((AbstractPersistentObject) clonedPo).pdo = null;
      return clonedPo;
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
  }

  /**
   * Creates a snapshot of this object.
   * <p>
   * The snapshot is a clone of this object in the same domain context.
   * The components are copied via the most specific {@code createComponentsInSnapshot} method
   * of the persistence class.
   *
   * @return the snapshot
   * @throws PersistenceException if the snapshot methods are not implemented correctly
   */
  @Override
  public T createSnapshot() {
    T snapshot = PdoFactory.getInstance().create(getPdoClass(), clone());
    PersistentObject<T> persistentSnapshot = snapshot.getPersistenceDelegate();
    persistentSnapshot.setDomainContext(getDomainContext());
    try {
      getClassVariables().getCreateComponentsInSnapshotMethod().invoke(this, persistentSnapshot);
      addSnapshot(snapshot);
      snapshot.setCopy(false);
      return snapshot;
    }
    catch (IllegalAccessException | IllegalArgumentException | NoSuchMethodException | InvocationTargetException ex) {
      throw new PersistenceException(this, "creating snapshot incorrectly implemented", ex);
    }
  }


  /**
   * Creates the components in the snapshot object.<br>
   * The snapshot object is assumed to be a clone of this object.
   * The default implementation marks the clone as a snapshot.
   * Persistence classes with components override this method to deep-copy the components.
   * <p>
   * There is no createAttributesInSnapshot, since the snapshot is cloned and all attributes are either immutable or frozen.
   *
   * @param snapshot the snapshot
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void createComponentsInSnapshot(AbstractPersistentObject snapshot) {
    snapshot.objectIsSnapshot = true;
    snapshot.snapshots = null;    // the clone must not share the list of snapshots with this object
  }


  /**
   * Reverts the state of this object to the given snapshot.
   * <p>
   * The snapshot and all snapshots taken after it are discarded.
   *
   * @param snapshot the snapshot to revert to, null to do nothing
   * @throws PersistenceException if not a snapshot or not a snapshot of this object
   */
  @Override
  public void revertToSnapshot(T snapshot) {
    if (snapshot != null) {
      assertValidSnapshot(snapshot);
      int index = getSnapshotIndex(snapshot);   // -1 if this is a copy
      applySnapshot(snapshot);
      if (index >= 0) {
        snapshots.subList(index, snapshots.size()).clear();   // truncate the timeline
      }
    }
  }


  /**
   * Creates a deep copy of this PDO.<br>
   * The copied PDO and its components, if any, are <em>new</em> (<code>id=0</code>).
   * <p>
   * Since only loaded components are copied (i.e., the object <em>as is</em>, which
   * is the contract for {@link org.tentackle.misc.Snapshotable}),
   * consider invoking {@link #loadComponents(boolean)} before {@code copy()}
   * to get a logically complete copy of the aggregate.
   *
   * @return the new copied object
   */
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public T copy() {
    T copiedPdo = on();
    AbstractPersistentObject copiedPo = (P) copiedPdo.getPersistenceDelegate();
    DomainContext ctx = copiedPo.getDomainContext();    // could be another root-context
    copiedPo.objectIsCopy = true;                       // controls how the snapshot is applied!
    copiedPo.applySnapshot(pdo.createSnapshot());
    copiedPo.setModified(true);
    copiedPo.setDomainContext(ctx);                     // restore the root context if reverted from the snapshot
    return copiedPdo;
  }


  /**
   * Applies the given snapshot.
   *
   * @param snapshot the snapshot to apply
   */
  private void applySnapshot(T snapshot) {
    PersistentObject<T> persistentSnapshot = snapshot.getPersistenceDelegate();
    try {
      getClassVariables().getRevertAttributesToSnapshotMethod().invoke(this, persistentSnapshot);
      getClassVariables().getRevertComponentsToSnapshotMethod().invoke(this, persistentSnapshot);
    }
    catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
      throw new PersistenceException(this, "reverting snapshot incorrectly implemented", ex);
    }
  }


  /**
   * Copies all attributes from a snapshot object back to this object.
   *
   * @param snapshot the snapshot object
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void revertAttributesToSnapshot(AbstractPersistentObject snapshot) {
    // Important: no "snapshot<T, P>" because of inheritance problems
    super.revertAttributesToSnapshot(snapshot);

    contextImmutable = snapshot.contextImmutable;
    // the token lock fields edited... are not restored because they are only changed
    // via a persistence operation
    cacheAccessCount = snapshot.cacheAccessCount;
    cacheAccessTime = snapshot.cacheAccessTime;
    expired = snapshot.expired;
    // the snapshots are not reverted: revertToSnapshot truncates them instead
    normText = snapshot.normText;
    validated = snapshot.validated;
    persistable = snapshot.persistable;
  }


  /**
   * Reverts all components of this object to a given snapshot.
   *
   * @param snapshot the snapshot object
   */
  @SuppressWarnings("rawtypes")
  protected void revertComponentsToSnapshot(AbstractPersistentObject snapshot) {
    // Important: no "snapshot<T, P>" because of inheritance problems
    // default implementation does nothing
  }


  @Override
  public boolean isSnapshot() {
    return objectIsSnapshot;
  }

  @Override
  public boolean isCopy() {
    return objectIsCopy;
  }

  @Override
  public void setCopy(boolean copy) {
    objectIsCopy = copy;
    if (copy) {
      snapshots = null;
      objectIsSnapshot = false;
    }
  }

  @Override
  public List<T> getSnapshots() {
    List<T> shots = new ArrayList<>();
    if (snapshots != null) {
      for (Iterator<WeakReference<T>> iterator = snapshots.iterator(); iterator.hasNext(); ) {
        T snapshot = iterator.next().get();
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
  public void discardSnapshot(T snapshot) {
    int index = getSnapshotIndex(snapshot);
    if (index >= 0) {
      snapshots.remove(index);
    }
    else {
      throw new PersistenceException(this, "not my snapshot");
    }
  }

  @Override
  public void discardSnapshots() {
    snapshots = null;
  }

  /**
   * Adds a snapshot to the list of snapshots.
   *
   * @param snapshot the snapshot to add
   * @throws PersistenceException if the snapshot has already been added
   */
  protected void addSnapshot(T snapshot) {
    if (snapshots == null) {
      snapshots = new ArrayList<>();
    }
    else if (getSnapshotIndex(snapshot) >= 0) {
      throw new PersistenceException(this, "snapshot already added");
    }
    snapshots.add(new WeakReference<>(snapshot));
  }


  /**
   * Asserts that the given snapshot is valid for this object.
   *
   * @param snapshot the snapshot
   * @throws PersistenceException if not a snapshot or not a snapshot of this object
   */
  protected void assertValidSnapshot(T snapshot) {
    if (!snapshot.isSnapshot()) {
      throw new PersistenceException(this, "not a snapshot");
    }
    if (!isCopy() && getSnapshotIndex(snapshot) < 0) {
      throw new PersistenceException(this, "not my snapshot");
    }
  }

  /**
   * Gets the index of a snapshot in the list of snapshots.<br>
   * Snapshots are compared by identity. Snapshots garbage-collected in the meantime are removed from the list.
   *
   * @param snapshot the snapshot
   * @return the index, -1 if not found
   */
  private int getSnapshotIndex(T snapshot) {
    if (snapshots != null) {
      int index = 0;
      for (Iterator<WeakReference<T>> iterator = snapshots.iterator(); iterator.hasNext(); ) {
        T shot = iterator.next().get();
        if (shot == null) {
          iterator.remove();
        }
        else {
          // we cannot use contains() because all snapshots have the same object ID
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
   * Asserts that the PDO is not cached.
   *
   * @throws PersistenceException if the PDO is cached
   */
  protected void assertNotCached() {
    if (isCached()) {
      throw new PersistenceException(this, "object is cached");
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden because snapshots are immutable.
   */
  @Override
  protected void assertMutable() {
    super.assertMutable();
    if (isSnapshot()) {
      throw new PersistenceException(this, new ImmutableException("object is a snapshot"));
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to require a domain context and to exclude abstract and embedded PDOs.
   * Furthermore, persistability can be disabled via {@link #setPersistable(boolean)}.
   */
  @Override
  public boolean isPersistable() {
    return persistable && super.isPersistable() &&
           getDomainContext() != null &&   // context may be used in security checks!
           !isAbstract() && !isEmbedded();
  }

  /**
   * Sets the local persistable flag.
   * <p>
   * The implementation maintains an additional flag to disable persistability.
   *
   * @param persistable true if persistable, false if not persistable
   */
  public void setPersistable(boolean persistable) {
    this.persistable = persistable;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to provide a detailed exception message.
   */
  @Override
  protected void assertPersistable() {
    if (!isPersistable()) {
      StringBuilder buf = new StringBuilder();
      if (isImmutable()) {
        buf.append("immutable ");
      }
      if (isAbstract()) {
        buf.append("abstract ");
      }
      if (getDomainContext() == null) {
        buf.append("contextless ");
      }
      buf.append("object is not persistable");
      throw new PersistenceException(this, buf.toString());
    }
  }


  @Override
  protected PropertySupport createPropertySupport() {
    // overridden to return the PDO as the source of the event
    return new PropertySupport(pdo);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default is false. Override if this is a root entity.
   */
  @Override
  public boolean isRootEntity() {
    return false;
  }


  @Override
  public boolean isEmbedded() {
    return false;
  }

  /**
   * Asserts that this is an embedded PDO.
   *
   * @throws PersistenceException if not embedded
   */
  protected void assertEmbedded() {
    if (!isEmbedded()) {
      throw new PersistenceException(this, "not an embedded PDO");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E extends PersistentDomainObject<E>> E  getEmbeddingParent() {
    assertEmbedded();
    return (E) embeddingParent.me();
  }

  /**
   * Sets the embedding PO for this embedded PDO.
   * <p>
   * Notice that the parent is set through its persistence object, not the PDO proxy!
   * This is necessary because the proxy may be unknown while reading from the database result set.
   *
   * @param embeddingParent the parent PO, never null
   * @param columnPrefix the column prefix, empty string if none, never null
   * @throws PersistenceException if this is not an embedded PDO
   */
  public void setEmbeddingPersistentObject(AbstractPersistentObject<?,?> embeddingParent, String columnPrefix) {
    assertEmbedded();
    this.embeddingParent = Objects.requireNonNull(embeddingParent, "embedding parent missing");
    this.columnPrefix = Objects.requireNonNull(columnPrefix, "column prefix missing");
  }

  /**
   * Gets the column prefix for this embedded PDO.
   *
   * @return the column prefix
   */
  public String getColumnPrefix() {
    assertEmbedded();
    return columnPrefix;
  }

  /**
   * Configures the columns of an embedded PDO.<br>
   * Must be overridden by embedded PDOs (usually generated).
   *
   * @param rs the result set
   * @throws PersistenceException if not overridden
   */
  public void configureEmbeddedColumns(ResultSetWrapper rs) {
    throw new PersistenceException(this, "configureEmbeddedColumns not implemented");
  }

  /**
   * Sets the fields of an embedded PDO.<br>
   * Must be overridden by embedded PDOs (usually generated).
   *
   * @param ndx the last used column index of the prepared statement
   * @param st the prepared statement
   * @return the last used column index
   * @throws PersistenceException if not overridden
   */
  public int setEmbeddedFields(int ndx, PreparedStatementWrapper st) {
    throw new PersistenceException(this, "setEmbeddedFields not implemented");
  }


  @Override
  public boolean isRootIdProvided() {
    return false;
  }

  @Override
  public long getRootId() {
    return rootId;
  }

  /**
   * Sets the ID of the root entity this component belongs to.
   *
   * @param rootId the root ID
   */
  public void setRootId(long rootId) {
    this.rootId = rootId;
  }

  @Override
  public boolean isRootClassIdProvided() {
    return false;
  }

  @Override
  public int getRootClassId() {
    return rootClassId;
  }

  /**
   * Sets the class ID of the root entity this component belongs to.
   *
   * @param rootClassId the root class ID
   */
  public void setRootClassId(int rootClassId) {
    this.rootClassId = rootClassId;
  }

  @Override
  public <C extends PersistentDomainObject<C>> boolean isRootEntityOf(C component) {
    return isRootEntity() &&
           component != null &&
           component.getRootId() == getId() &&
           component.getRootClassId() == getClassId();
  }

  /**
   * Presets the attributes with initial values if the PDO is persisted the first time.<br>
   * The method is invoked <em>before</em> the PDO is validated.
   */
  protected void presetVirgin() {
    // default does nothing
  }

  @Override
  public boolean isNormTextProvided() {
    return false;
  }

  /**
   * Asserts that the entity provides a normtext.
   *
   * @throws PersistenceException if no normtext provided
   */
  protected void assertNormTextProvided() {
    if (!isNormTextProvided()) {
      throw new PersistenceException(this, "entity does not provide a normtext column");
    }
  }

  /**
   * Sets the normtext.
   *
   * @param normText the normtext
   */
  public void setNormText (String normText) {
    assertMutable();
    if (!Objects.equals(this.normText, normText)) {
      setModified(true);
    }
    this.normText = normText;
  }

  /**
   * Gets the normtext.
   *
   * @return the normtext
   */
  @Override
  public String getNormText() {
    return normText;
  }

  /**
   * Creates the normalized text of all attributes with the NORMTEXT-option.<br>
   * The method is usually generated by the MethodsImpl wurblet.
   *
   * @return the builder holding the normalized text, null if no normtext attributes
   */
  public StringBuilder createAttributesNormText() {
    return null;
  }

  /**
   * Creates the normalized text of all relations with the NORMTEXT-option.<br>
   * The method is usually generated by the PdoRelations wurblet.
   *
   * @return the builder holding the normalized text, null if no normtext relations
   */
  public StringBuilder createRelationsNormText() {
    return null;
  }

  /**
   * Updates the normtext attribute if normtext is provided by this PDO.
   */
  public void updateNormText() {
    if (isNormTextProvided()) {
      StringBuilder ntBuf = createAttributesNormText();
      StringBuilder relBuf = createRelationsNormText();
      if (ntBuf != null) {
        if (relBuf != null) {
          ntBuf.append(relBuf);
        }
      }
      else {
        ntBuf = relBuf;
      }
      if (ntBuf != null) {
        ntBuf.insert(0, ',');
        setNormText(StringNormalizer.getInstance().normalize(ntBuf.toString()));
      }
      else {
        setNormText(null);
      }
    }
  }


  // ---------------- implements DomainContextDependable -------------------------


  /**
   * Returns whether the domain context is immutable.
   *
   * @return true if context cannot be changed
   */
  @Override
  public boolean isDomainContextImmutable() {
    return contextImmutable;
  }

  /**
   * Sets the immutable flag of the domain context.
   *
   * @param contextImmutable true if context cannot be changed
   */
  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    this.contextImmutable = contextImmutable;
  }


  /**
   * Asserts that the domain context is mutable.
   *
   * @throws PersistenceException if the domain context is immutable
   */
  protected void assertDomainContextMutable() {
    if (isDomainContextImmutable()) {
      throw new PersistenceException(this, "domain context is immutable");
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Setting the context will also set the session and context ID.
   * For root entities, the root context is set.
   *
   * @throws PersistenceException if the domain context is immutable
   */
  @Override
  public void setDomainContext(DomainContext context)  {
    if (getDomainContext() != context) {
      Objects.requireNonNull(context, "context");
      assertDomainContextMutable();
      if (isRootEntity()) {
        // if this is a root entity: replace holder with root context
        setSessionHolder(context.getRootContext(pdo));
      }
      else  {
        setSessionHolder(context);
      }
      determineContextId();
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just returns the context.
   * Subclasses may override this with a covariant method.
   */
  @Override
  public DomainContext getDomainContext() {
    return (DomainContext) getSessionHolder();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing (object living in a context
   * not depending on another object).
   */
  @Override
  public void determineContextId() {
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns -1.
   */
  @Override
  public long getContextId() {
    return -1;
  }

  /**
   * Gets the SQL code for the context condition.
   *
   * @return empty if no condition (default)
   */
  public String getSqlContextCondition() {
    return "";
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns the PDO's domain context.
   */
  @Override
  public DomainContext getBaseContext()  {
    return getDomainContext();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns a new {@link DomainContext}.
   * If there is a thread-local session, the new context uses it, otherwise the session of this object.
   */
  @Override
  public DomainContext createValidContext() {
    Session session = Session.getCurrentSession();
    if (session != null) {
      session = null;   // use the thread-local session for the new context
    }
    else  {
      session = getSession();
    }
    return Pdo.createDomainContext(session);
  }


  // ---------------- end DomainContextDependable ---------------------------------


  /**
   * Gets the application-oriented class variables for this object.<br>
   * Class variables for classes derived from AbstractPersistentObject are kept in an
   * instance of {@link PersistentObjectClassVariables}.
   * Embedded PDOs return the class variables of their embedding parent.
   * All other persistence classes must override this method (usually generated).
   *
   * @return the class variables
   * @throws PersistenceException if not overridden or embedding parent not set
   * @see AbstractDbObject#getClassVariables
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public PersistentObjectClassVariables<T,P> getClassVariables() {
    if (isEmbedded()) {
      if (embeddingParent == null) {
        throw new PersistenceException(this, "parent not set for embedded " + getClass());
      }
      // generic type does not match for embedded PDOs, but that's ok here
      return (PersistentObjectClassVariables) embeddingParent.getClassVariables();
    }
    throw new PersistenceException(this, "getClassVariables() not implemented for " + getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * For embedded PDOs, the key is built from the class of the embedding parent and the column prefix.
   */
  @Override
  protected StatementKey createStatementKey(StatementId stmtId) {
    return isEmbedded() ?
           new StatementKey(stmtId, embeddingParent.getClass(), columnPrefix) :
           super.createStatementKey(stmtId);
  }

  @Override
  public String getTableName () {
    return getClassVariables().getTableName();
  }

  /**
   * Gets the table alias.
   *
   * @return the alias, null if the class does not map to a database table
   */
  public String getTableAlias () {
    return getClassVariables().getTableAlias();
  }

  /**
   * Gets the full column name with table alias.
   *
   * @param name the short name
   * @return the full name
   */
  public String getColumnName(String name) {
    return isEmbedded() ?
           getClassVariables().getColumnName(columnPrefix, name) :
           getClassVariables().getColumnName(name);
  }

  /**
   * Gets the comma-separated list of column names with table alias.
   *
   * @param columnNamesSupplier the supplier for the list of column names
   * @return the partial SQL string
   */
  public String getColumnNames(Supplier<List<String>> columnNamesSupplier) {
    return getColumnNames(columnNamesSupplier.get());
  }

  /**
   * Gets the comma-separated list of column names with table alias.
   *
   * @param columnNames the list of column names
   * @return the partial SQL string
   */
  public String getColumnNames(List<String> columnNames) {
    return getClassVariables().getColumnNames(columnPrefix, columnNames);
  }

  /**
   * Gets the valid class IDs for the concrete types of this type.<br>
   * Applies only to entities of single- or multi-table inheritance hierarchies, except the topmost entity.
   *
   * @return the class IDs, null if no restrictions
   */
  public List<Integer> getValidClassIds() {
    return null;
  }

  /**
   * Gets the SQL string used in WHERE clauses for single table inheritance entities.
   *
   * @return the SQL string, empty if no such condition necessary
   */
  public String getSqlClassIdCondition() {
    return getClassVariables().getSqlClassIdCondition(getValidClassIds());
  }

  /**
   * Gets the alias for the topmost super class.
   *
   * @return the alias
   */
  public String getTopSuperTableAlias() {
    return getClassVariables().getTopSuperClassVariables().getTableAlias();
  }

  /**
   * Gets the table name for the topmost super class.
   *
   * @return the table name
   */
  public String getTopSuperTableName() {
    return getClassVariables().getTopSuperClassVariables().getTableName();
  }

  /**
   * Gets the PDO class.
   *
   * @return the class of the associated PDO
   */
  public Class<T> getPdoClass() {
    return getClassVariables().pdoClass;
  }


  /**
   * Returns whether this is an abstract PDO.<br>
   * Abstract PDOs are real objects but cannot be persisted.
   * They may be used to retrieve data for the concrete implementations, however.
   *
   * @return true if abstract PDO
   */
  @Override
  public boolean isAbstract() {
    return getClassVariables().isAbstract();
  }

  /**
   * Asserts that this is not an abstract entity class.
   *
   * @throws PersistenceException if abstract
   */
  public void assertNotAbstract() {
    if (isAbstract()) {
      throw new PersistenceException(this, "operation not allowed for abstract entities");
    }
  }

  /**
   * Returns whether an explicit ID alias is necessary in joins.<br>
   * This applies to MULTI-table inherited PDOs only (abstracts and leaves).
   *
   * @return true if we need an explicit ID alias
   */
  public boolean isExplicitIdAliasRequiredInJoins() {
    return false;
  }

  /**
   * Tells whether this object is composite (i.e., has composite relations).<br>
   * The method is overridden by the PdoRelations wurblet if at least one
   * relation is flagged as "composite".
   * The default implementation returns false.
   *
   * @return true if the object is a composite, false if not
   */
  @Override
  public boolean isComposite() {
    return false;
  }


  /**
   * {@inheritDoc}
   * <p>
   * By default, the method throws a PersistenceException telling that it is not implemented
   * if isComposite() returns true.
   *
   * @param onlyLoaded true to return only already loaded components (lazy composite relations)
   * @return the map of all components, including this object
   */
  @Override
  public IdentifiableMap<? extends PersistentDomainObject<?>> loadComponents(boolean onlyLoaded) {
    if (isComposite()) {
      throw new PersistenceException("method not implemented");
    }
    // else: this is not a composite (or an embedded object) -> return me
    return new IdentifiableMap<>(this.getPdo());
  }


  /**
   * Adds the components of this object to a map.
   *
   * @param components the component map
   * @param onlyLoaded true to return only already loaded components (lazy composite relations)
   * @return the number of components added
   */
  public int addComponents(IdentifiableMap<PersistentDomainObject<?>> components, boolean onlyLoaded)  {
    return components.add(this.getPdo()) == null ? 1 : 0;
  }

  /**
   * Adds the components of a collection to a map.
   *
   * @param components the component map
   * @param objects the collection of objects to add along with their components
   * @param onlyLoaded true to return only already loaded components (lazy composite relations)
   * @return the number of components added
   */
  public int addComponents(IdentifiableMap<PersistentDomainObject<?>> components, Collection<? extends PersistentDomainObject<?>> objects, boolean onlyLoaded)  {
    int count = 0;
    for (PersistentDomainObject<?> object: objects) {
      count += ((AbstractPersistentObject<?,?>) object.getPersistenceDelegate()).addComponents(components, onlyLoaded);
    }
    return count;
  }


  /**
   * Deletes this object and all its components without any further processing.<br>
   * Same as {@link #deletePlain()} but with components.
   */
  public void deletePlainWithComponents() {
    assertNotRemote();
    if (isComposite()) {
      throw new PersistenceException(this, "local part of the method not implemented");
    }
    else  {
      deletePlain();
    }
  }


  /**
   * Deletes all objects of a collection, including their components, without any further processing.<br>
   * All objects must belong to the same session. The deletion runs within a transaction.
   *
   * @param objects the collection of PDOs
   * @return the number of objects processed
   * @throws PersistenceException if the objects belong to different sessions or deletion failed
   */
  public int deletePlainWithComponents(Collection<? extends PersistentDomainObject<?>> objects) {
    int count = 0;
    if (objects != null && !objects.isEmpty()) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj : objects) {
          if (obj != null) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PersistenceException(obj, "session is null");
              }
              txVoucher = session.begin("delete plain with components collection");
            }
            else if (obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PersistenceException(obj, "unexpected session: " + obj.getSession() + ", expected: " + session);
            }
            ((AbstractPersistentObject<?, ?>) obj.getPersistenceDelegate()).deletePlainWithComponents();
            count++;
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }

    return count;
  }


  /**
   * Inserts this object and all its components without any further processing.<br>
   * Same as {@link #insertPlain()} but with components.
   */
  public void insertPlainWithComponents() {
    assertNotRemote();
    if (isComposite()) {
      throw new PersistenceException(this, "local part of the method not implemented");
    }
    else  {
      insertPlain();
    }
  }


  /**
   * Inserts all objects of a collection, including their components, without any further processing.<br>
   * All objects must belong to the same session. The insertion runs within a transaction.
   *
   * @param objects the collection of PDOs
   * @return the number of objects processed
   * @throws PersistenceException if the objects belong to different sessions or insertion failed
   */
  public int insertPlainWithComponents(Collection<? extends PersistentDomainObject<?>> objects) {
    int count = 0;
    if (objects != null && !objects.isEmpty()) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj: objects) {
          if (obj != null) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PersistenceException(obj, "session is null");
              }
              txVoucher = session.begin("insert plain with components collection");
            }
            else if(obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PersistenceException(obj, "unexpected session: " + obj.getSession() + ", expected: " + session);
            }
            ((AbstractPersistentObject<?,?>) obj.getPersistenceDelegate()).insertPlainWithComponents();
            count++;
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return count;
  }


  /**
   * Checks whether this object (if saved) would violate any
   * unique constraints.
   * <p>
   * The method is usually used by the presentation layer
   * to check for duplicates.
   * The default implementation invokes findByUniqueDomainKey(getUniqueDomainKey()) of the domain delegate,
   * both of which throw a {@link org.tentackle.pdo.DomainException} by default,
   * i.e., if the entity provides no unique domain key.
   *
   * @return the duplicate object, null if no duplicate
   */
  @Override
  public T findDuplicate() {
    T thisPdo = getPdo();
    Object key = thisPdo.getDomainDelegate().getUniqueDomainKey();
    if (key != null) {
      T otherPdo = thisPdo.getDomainDelegate().findByUniqueDomainKey(key);
      if (otherPdo != null && !otherPdo.equals(thisPdo)) {
        return otherPdo;
      }
    }
    return null;
  }


  /**
   * Determines whether the object is cacheable or not.<br>
   * The default implementation always returns true, but apps
   * can use this as a filter.
   *
   * @return true if the object is cacheable
   */
  @Override
  public boolean isCacheable()  {
    return true;
  }


  /**
   * Checks whether the object has been marked expired.<br>
   * When an expired object is retrieved from the cache,
   * it is reloaded from the database.
   *
   * @return true if the object is expired in cache
   */
  @Override
  public boolean isExpired() {
    return expired;
  }

  /**
   * Sets this object's expiration flag.
   *
   * @param expired true if the object is expired
   */
  @Override
  public void setExpired(boolean expired) {
    this.expired = expired;
  }

  /**
   * Gets the last cache access time.
   *
   * @return the last cache access time
   */
  @Override
  public long getCacheAccessTime()  {
    return cacheAccessTime;
  }

  /**
   * Gets the cache access count.
   *
   * @return the access count
   */
  @Override
  public long getCacheAccessCount() {
    return cacheAccessCount;
  }

  /**
   * Marks a cache access (increments the count and sets the current system time).<br>
   * Clears the token lock holder, since token locks do not apply to cached objects.
   */
  @Override
  public void markCacheAccess() {
    cacheAccessCount++;
    cacheAccessTime = System.currentTimeMillis();
    editedBy = 0;     // token lock does not apply to cached objects!
  }

  /**
   * Returns whether the object is located in a cache.
   *
   * @return true if the object is cached
   */
  @Override
  public boolean isCached() {
    return cacheAccessCount > 0;
  }


  @Override
  public SecurityResult getSecurityResult(Permission permission) {
    return SecurityFactory.getInstance().getSecurityManager().evaluate(getBaseContext(), permission, getClassId(), getId());
  }

  @Override
  public boolean isPermissionAccepted(Permission permission) {
    return getSecurityResult(permission).isAccepted();
  }


  /**
   * Selects an object according to a template object.
   * Useful to find corresponding objects in another context.
   * The default implementation loads the same object by its ID.
   * Should be overridden to select by some other unique key.
   *
   * @param template the template object
   * @return the object if found, else null.
   */
  public T selectByTemplate(AbstractPersistentObject<T,P> template) {
    return select(template.getId());
  }


  /**
   * Creates a joined select for the eager joins of this PDO.
   *
   * @return the joined select, null if this PDO has no eager joins
   */
  public JoinedSelect<T> getEagerJoinedSelect() {
    List<Join<? super T,?>> eagerJoins = getEagerJoins();
    return eagerJoins != null && !eagerJoins.isEmpty() ? new JoinedSelect<>(eagerJoins) : null;
  }

  /**
   * Gets a prepared statement and marks it as batchable.<br>
   * If there is a transaction running in batched mode, the statement will be executed via JDBC batching.
   * Otherwise, the method is equivalent to {@link #getPreparedStatement(StatementId, SqlSupplier)}.
   * <p>
   * The method must only be used for inserts, updates, and deletes of a single row!<br>
   * Requires a domain context, since the batch is grouped by the root class ID.
   *
   * @param modType the modification type
   * @param stmtId the statement ID
   * @param sqlSupplier the SQL code supplier
   * @return the statement
   */
  protected PreparedStatementWrapper getBatchablePreparedStatement(ModificationType modType, StatementId stmtId, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getPreparedStatement(stmtId, sqlSupplier);
    getSession().addToBatch(modType, st, this, getDomainContext().getRootClassId());
    return st;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to support JDBC batching.
   */
  @Override
  protected void insertImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getBatchablePreparedStatement(DbModificationType.INSERT, classVariables.insertStatementId, sqlSupplier);
    setFields(st);
    assertThisRowAffected(st.executeUpdate());
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to support JDBC batching.
   */
  @Override
  protected void updateImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getBatchablePreparedStatement(DbModificationType.UPDATE, classVariables.updateStatementId, sqlSupplier);
    setFields(st);
    assertThisRowAffected(st.executeUpdate());
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to support JDBC batching.
   */
  @Override
  protected void deleteImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getBatchablePreparedStatement(DbModificationType.DELETE, classVariables.deleteStatementId, sqlSupplier);
    st.setLong(1, getId());
    st.setLong(2, getSerial());
    assertThisRowAffected(st.executeUpdate());
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to implement inheritance and adjust the domain context.
   * For abstract PDOs, the returned persistent object is a new instance of the concrete class,
   * determined by the class ID read from the result set.
   *
   * @return the persistent object, this or a new instance of the concrete class
   */
  @Override
  @SuppressWarnings("unchecked")
  public P readFromResultSetWrapper(ResultSetWrapper rs)  {
    P po = super.readFromResultSetWrapper(rs);
    if (po.isAbstract()) {
      // create the concrete persistence implementation according to the class id
      int classId = po.getClassId();
      if (classId == 0) {
        throw new PersistenceException(po, "class id is 0 in " + po.toGenericString());
      }
      String className = SessionUtilities.getInstance().getClassName(classId);
      if (className == null) {
        throw new PersistenceException(po, "no such PDO class for classId " + classId + " in " + po.toGenericString());
      }
      // no PdoFactory#createPersistenceDelegate, since we need a complete PDO
      po = (P) PdoFactory.getInstance().create(className, getDomainContext()).getPersistenceDelegate();
      po = po.readFromResultSetWrapper(rs);   // invoke the concrete implementation
    }
    if (po.getContextId() >= 0 || po.getDomainContext() == null) {
      // pdo depends on a valid context id or no context at all
      long domainContextId = po.getDomainContext() == null ? -1 : po.getDomainContext().getContextId();
      if (domainContextId != po.getContextId()) {
        // create valid context if context-IDs don't match or there is no domain context at all
        po.setDomainContext(po.createValidContext());
      }
    }
    return po;
  }


  /**
   * Executes the query for a prepared statement and adds the results to a list.
   *
   * @param st the query statement
   * @param js the joined select configuration, null if no joins
   * @param list the list
   */
  public void executeQueryToList(PreparedStatementWrapper st, JoinedSelect<T> js, List<T> list) {
    assertRootContextIsAccepted();
    try (ResultSetWrapper rs = st.executeQuery()) {
      executeQueryToList(rs, js, list);
    }
  }


  /**
   * Reads all rows of an already executed query and adds the PDOs to a list.
   *
   * @param rs the result set wrapper
   * @param js the joined select configuration, null if no joins
   * @param list the list
   */
  public void executeQueryToList(ResultSetWrapper rs, JoinedSelect<T> js, List<T> list) {
    RootInfo rootInfo = new RootInfo();
    if (js == null) {
      while (rs.next()) {
        P nextPo = newInstance();
        nextPo = nextPo.readFromResultSetWrapper(rs);
        T nextPdo = Pdo.create(nextPo.getPdoClass(), nextPo);
        nextPdo = derivePdoFromPo(nextPdo, nextPo, rootInfo);
        if (nextPdo != null) {
          list.add(nextPdo);
        }
      }
    }
    else  {
      js.initialize(list);
      while (rs.next()) {
        readJoinedRow(rs, js, rootInfo);
      }
    }
  }

  /**
   * Executes the query for a prepared statement and returns the results in a list.
   *
   * @param st the query statement
   * @param js the joined select configuration, null if no joins
   * @return the list
   */
  public List<T> executeListQuery(PreparedStatementWrapper st, JoinedSelect<T> js) {
    List<T> list = new ArrayList<>();
    executeQueryToList(st, js, list);
    return list;
  }

  /**
   * Executes the query for a prepared statement and returns the results in a list.
   *
   * @param st the query statement
   * @return the list
   */
  public List<T> executeListQuery(PreparedStatementWrapper st) {
    return executeListQuery(st, null);
  }

  /**
   * Executes the query for a prepared statement and returns the results in a tracked list.
   *
   * @param st the query statement
   * @param js the joined select configuration, null if no joins
   * @return the tracked list
   */
  public TrackedList<T> executeTrackedListQuery(PreparedStatementWrapper st, JoinedSelect<T> js) {
    TrackedList<T> list = new TrackedArrayList<>();
    executeQueryToList(st, js, list);
    list.setModified(false);
    return list;
  }

  /**
   * Executes the query for a prepared statement and returns the results in a tracked list.
   *
   * @param st the query statement
   * @return the tracked list
   */
  public TrackedList<T> executeTrackedListQuery(PreparedStatementWrapper st) {
    return executeTrackedListQuery(st, null);
  }


  /**
   * Creates a {@link Query} to retrieve a list of this entity type.
   *
   * @param js the joined select configuration, null if no joins
   * @return the initialized query
   */
  public Query createQuery(JoinedSelect<T> js) {
    Query query;
    if (js != null) {
      query = new Query() {
        @Override
        public StringBuilder createSql(Backend backend) {
          StringBuilder sql = super.createSql(backend);
          js.createJoinedSql(me(), sql);
          return sql;
        }
      };
    }
    else {
     query = new Query();
    }

    query.add(this::createSelectAllInnerSql);
    return query;
  }

  /**
   * Creates a {@link Query} to retrieve a list of this entity type.
   *
   * @return the initialized query
   */
  public Query createQuery() {
    return createQuery(null);
  }

  /**
   * Executes a {@link Query} built to retrieve PDOs of this entity type and returns them in a tracked list.
   *
   * @param query the query object
   * @param js the joined select configuration, null if no joins
   * @return the tracked list
   * @see #createQuery(JoinedSelect)
   */
  public TrackedList<T> executeQuery(Query query, JoinedSelect<T> js) {
    assertRootContextIsAccepted();
    TrackedList<T> list = new TrackedArrayList<>();
    try (ResultSetWrapper rs = query.execute(getSession())) {
      executeQueryToList(rs, js, list);
    }
    list.setModified(false);
    return list;
  }

  /**
   * Executes a {@link Query} built to retrieve PDOs of this entity type and returns them in a tracked list.
   *
   * @param query the query object
   * @return the tracked list
   * @see #createQuery()
   */
  public TrackedList<T> executeQuery(Query query) {
    return executeQuery(query, null);
  }

  /**
   * Executes a {@link Query} built to retrieve PDOs of this entity type and returns them as a scrollable cursor.
   *
   * @param query the query object
   * @param js the joined select configuration, null if no joins (requires the query to be sorted by ID)
   * @return the scrollable cursor
   */
  public ResultSetCursor<T> executeScrollableQuery(Query query, JoinedSelect<T> js) {
    return new ResultSetCursor<>(me(), query.execute(getSession()), js);
  }

  /**
   * Executes a {@link Query} built to retrieve PDOs of this entity type and returns them as a scrollable cursor.
   *
   * @param query the query object
   * @return the scrollable cursor
   */
  public ResultSetCursor<T> executeScrollableQuery(Query query) {
    return executeScrollableQuery(query, null);
  }


  /**
   * Returns the eager joins for this PDO.
   *
   * @return the list of eager joins, null if none
   */
  public List<Join<? super T,?>> getEagerJoins() {
    return getClassVariables().getEagerJoins();
  }

  /**
   * Reads the next row from a result set within a "joined select".<br>
   * The results may come in any order. Thus, it is possible to sort the results
   * according to the domain's requirements.
   *
   * @param rs the result set
   * @param js the joined select
   */
  public void readJoinedRow(ResultSetWrapper rs, JoinedSelect<T> js) {
    readJoinedRow(rs, js, null);
  }

  /**
   * Reads the next row from a result set within a "joined select".<br>
   * The results may come in any order. Thus, it is possible to sort the results
   * according to the domain's requirements.
   *
   * @param rs the result set
   * @param js the joined select
   * @param lastCheckedRoot the last security-checked root, null if no optimization for lists
   */
  @SuppressWarnings("unchecked")
  private void readJoinedRow(ResultSetWrapper rs, JoinedSelect<T> js, RootInfo lastCheckedRoot) {
    rs.startSkip();
    P nextPo = newInstance();
    nextPo = nextPo.readFromResultSetWrapper(rs);
    T nextPdo;
    if (js.currentPdo() == null || js.currentPdo().getPersistenceDelegate().getId() != nextPo.getId()) {
      // create a new PDO
      nextPdo = Pdo.create(nextPo.getPdoClass(), nextPo);
      nextPdo = derivePdoFromPo(nextPdo, nextPo, lastCheckedRoot);
      T knownPdo = js.nextPdo(nextPdo);
      if (knownPdo != nextPdo) {
        // already in the list
        nextPdo = knownPdo;
        nextPo = (P) nextPdo.getPersistenceDelegate();
      }
    }
    else  {
      // stay within same PDO
      nextPdo = js.currentPdo();
    }
    // skip to the first join
    rs.skip(nextPo.getColumnCount());

    for (Join<? super T,?> join: js.getJoins()) {
      readJoin(rs, js.getJoinState(), nextPdo, (Join<T,?>) join);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <J extends PersistentDomainObject<J>> void readJoin(ResultSetWrapper rs, JoinState joinState,
                                                              J nextPdo, Join<? super J,?> join) {
    PersistentDomainObject joinedPdo = join.createJoinedPdo(nextPdo);
    AbstractPersistentObject joinedPo = (AbstractPersistentObject) joinedPdo.getPersistenceDelegate();

    if (rs.getObject(rs.findColumn(CN_ID)) != null) {
      // Only if not all columns null.
      // This is the case if ID is null (which is not nullable)
      // -> this one is valid:
      joinedPo = joinedPo.readFromResultSetWrapper(rs);
      joinedPdo = joinedPo.derivePdoFromPo(joinedPdo, joinedPo);
      if (joinedPdo != null) {
        joinedPdo = ((Join) join).join(joinState, nextPdo, joinedPdo);
      }
    }

    // skip to the next join
    int columnCount = joinedPo != null ? joinedPo.getColumnCount() : 0;
    if (join.isExplicitIdAliasRequired()) {
      columnCount++;
    }
    rs.skip(columnCount);

    // recursively read the joined joins
    for (Join<?,?> subJoin: join.getJoins()) {
      readJoin(rs, joinState, joinedPdo, (Join) subJoin);
    }
  }


  /**
   * Executes a query for a prepared statement and returns the first PDO.
   *
   * @param st the query statement
   * @param js the joined select configuration, null if no joins
   * @return the PDO, null if no row found or not readable
   */
  public T executeFirstPdoQuery(PreparedStatementWrapper st, JoinedSelect<T> js) {
    assertRootContextIsAccepted();
    T pDo = null;
    try (ResultSetWrapper rs = st.executeQuery()) {
      if (js == null) {
        if (rs.next()) {
          P po = readFromResultSetWrapper(rs);
          pDo = derivePdoFromPo(pdo, po);
        }
      }
      else  {
        // joined: until the end of result set or first PDO completed
        js.initialize(null);
        while (pDo == null && rs.next()) {
          readJoinedRow(rs, js);
          pDo = js.getLastPdo();  // != null if js.currentPdo() already contains second PDO
        }
        if (pDo == null) {        // rs.next() returned false -> eof
          pDo = js.currentPdo();
        }
      }
      return pDo;
    }
  }

  /**
   * Executes a query for a prepared statement and returns the first PDO.
   *
   * @param st the query statement
   * @return the PDO, null if no row found or not readable
   */
  public T executeFirstPdoQuery(PreparedStatementWrapper st) {
    return executeFirstPdoQuery(st, null);
  }


  /**
   * Loads a PDO from the database by its unique ID.<br>
   * If {@code tokenLocked} is true, the token lock is requested after the PDO has been loaded.
   * <p>
   * Notice that {@code forUpdate} and {@code tokenLocked} require a running transaction.
   * Use {@link #selectTokenLocked(long)} to load a token-locked PDO within its own transaction.
   *
   * @param id the object ID
   * @param forUpdate true if select FOR UPDATE
   * @param tokenLocked true if request a token lock as well
   * @return object if loaded, null if no such object or not readable
   * @throws PersistenceException if {@code forUpdate} or {@code tokenLocked} is true and there is no transaction running
   * @throws LockException if {@code tokenLocked} is true and the PDO is token-locked by another user
   */
  public T select(long id, boolean forUpdate, boolean tokenLocked) {
    T obj = null;
    if (id > 0) {
      if (getSession().isRemote())  {
        DomainContext context = getDomainContext();
        obj = getRemoteDelegate().select(context, id, forUpdate, tokenLocked);
        configureRemoteObject(context, obj);
      }
      else {
        if (forUpdate || tokenLocked) {
          getSession().assertTxRunning();
        }
        StatementId stmtId = forUpdate ?
                               getClassVariables().selectForUpdateStatementId :
                               getClassVariables().selectObjectStatementId;
        JoinedSelect<T> js = getEagerJoinedSelect();
        PreparedStatementWrapper st = getPreparedStatement(stmtId,
                b -> {
                  StringBuilder sql;
                  String sqlClassIdCondition = getSqlClassIdCondition();
                  if (!sqlClassIdCondition.isEmpty() && forUpdate) {
                    sql = createSelectAllByIdInnerSql(b);
                    sql.append(sqlClassIdCondition);
                    b.buildSelectSql(sql, true, 0, 0);
                  }
                  else {
                    sql = new StringBuilder(createSelectSql(b, forUpdate));
                    sql.append(sqlClassIdCondition);
                  }
                  if (js != null) {
                    js.createJoinedSql(pdo, sql);
                  }
                  return sql.toString();
                }
        );
        st.setLong(1, id);
        obj = executeFirstPdoQuery(st, js);
        if (tokenLocked && obj != null) {
          obj.getPersistenceDelegate().requestTokenLock();
        }
      }
    }
    return obj;
  }

  @Override
  public T select(long id) {
    return select(id, false, false);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Runs within its own transaction, if no transaction is running.
   * For remote sessions, the token lock attributes are applied to this object if the PDO
   * is token-locked by another user.
   */
  @Override
  public T selectTokenLocked(long id) {
    Db db = getSession();
    if (db.isRemote()) {
      try {
        DomainContext context = getDomainContext();
        T obj = getRemoteDelegate().selectTokenLocked(context, id);
        configureRemoteObject(context, obj);
        return obj;
      }
      catch (RuntimeException e) {
        if (e instanceof LockException lx) {
          applyTokenLockInfo(lx.getTokenLockInfo());
        }
        throw e;
      }
    }

    long txVoucher = db.begin(TX_SELECT_TOKENLOCKED);
    try {
      T obj = select(id, false, true);
      db.commit(txVoucher);
      return obj;
    }
    catch (PersistenceException px) {
      db.rollback(txVoucher, !px.isTemporary());
      throw px;
    }
    catch (RuntimeException rx) {
      db.rollback(txVoucher);
      throw new PersistenceException(this, rx);
    }
  }

  @Override
  public T selectForUpdate(long id) {
    return select(id, true, false);
  }


  /**
   * Gets the ID to reload this object.<br>
   * If this object is a new root entity in a domain context cloned from a root context of the same class,
   * the root ID of that context is used.
   *
   * @return the ID
   */
  private long getIdForReload() {
    long reloadId = getId();
    if (reloadId == 0 && isRootEntity()) {
      // probably something like: invoice.on("anotherContext").reload() ?
      DomainContext clonedContext = getDomainContext().getClonedContext();
      if (clonedContext != null &&
          clonedContext.isRootContext() && clonedContext.getRootClassId() == getClassId()) {
        // was cloned from a valid root-entity of the same class
        reloadId = clonedContext.getRootId();   // take that id for reload operation
        // shorthand for: invoice.on("anotherContext").select(invoice.getId())
        // this behavior also avoids programming errors, since the developer would
        // expect it to work this way.
        // Important: that works only for reload...()-methods!
      }
    }
    return reloadId;
  }

  /**
   * Reloads the object.<p>
   * Restores transient data, if present.
   *
   * @param tokenLocked true to apply token lock as well
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  public T reload(boolean tokenLocked) {
    Object data = getTransientData();
    T obj = Pdo.create(getPdo());
    obj = tokenLocked ? obj.selectTokenLocked(getIdForReload()) : obj.select(getIdForReload());
    if (obj != null && data != null) {
      ((AbstractPersistentObject<?,?>) obj.getPersistenceDelegate()).setTransientData(data);
    }
    return obj;
  }

  @Override
  public T reload() {
    return reload(false);
  }

  @Override
  public T reloadTokenLocked() {
    return reload(true);
  }

  @Override
  public T reloadForUpdate() {
    Object data = getTransientData();
    T obj = Pdo.create(getPdo()).selectForUpdate(getIdForReload());
    if (obj != null && data != null) {
      ((AbstractPersistentObject<?,?>) obj.getPersistenceDelegate()).setTransientData(data);
    }
    return obj;
  }


  /**
   * Derives the concrete PDO from a given PO.<br>
   * Applies the security checks and makes PDOs loaded outside their root context immutable.
   *
   * @param pdo the original persistent domain object
   * @param po the effective persistent object from readFromResultSetWrapper
   * @return the persistent domain object, null if no read permission
   */
  public T derivePdoFromPo(T pdo, P po) {
    return derivePdoFromPo(pdo, po, null);
  }


  /**
   * Just a holder for the object- and class-ID of the last successfully security-checked root entity.
   * Used in list queries of components that don't belong to the current root context. In such cases,
   * the security manager will be asked only once if the components all belong to the same root (which
   * is often the case).
   */
  private static class RootInfo {
    private long rootId;
    private int rootClassId;
  }

  /**
   * Derives the concrete PDO from a given PO.
   *
   * @param pdo the original persistent domain object
   * @param po the effective persistent object from readFromResultSetWrapper
   * @param lastCheckedRoot the last security-checked root, null if no optimization for lists
   * @return the persistent domain object, null if no read permission
   */
  private T derivePdoFromPo(T pdo, P po, RootInfo lastCheckedRoot) {
    T derivedPdo;
    if (po == pdo.getPersistenceDelegate()) {
      derivedPdo = pdo;    // same type
    }
    else {
      if (pdo.getPersistenceDelegate().isAbstract()) {
        // some subtype
        derivedPdo = po.getPdo();
      }
      else {
        throw new PersistenceException(this, "misconfigured PDO inheritance");
      }
    }

    DomainContext context = getDomainContext();

    if (po.isRootEntity()) {
      // set the root-context for sure
      po.setSessionHolder(context.getRootContext(derivedPdo));   // no setDomainContext!
      // always check because context wasn't checked by assertRootContextIsAccepted
      // but simply created along with the PDO!
      if (!po.isReadAllowed()) {
        return null;
      }
      // The special case for PDOs being programmatically either root entity or component.
      // Those PDOs must be modeled like a normal component, but with [root] in the global options,
      // with \@wurblet MethodsImpl --noisroot option and the isRootEntity() method overridden to return
      // true or false, according to the domain logic.
      // The component will then point to the logical parent, either as a component or just a non-composite relation.
      //
      // If loaded from within such a root entity context, make it immutable, since it's loaded like a component
      // but must not be updated and therefore not modified!
      if (po.getRootId() != 0 && po.getRootClassId() != 0 &&              // related to some other entity
          po.getRootId() == context.getRootId() && po.getRootClassId() == context.getRootClassId()) {
        // loaded from within the associated root entity's context
        po.setFinallyImmutable();   // must not be persisted within the other root's context!
      }
    }
    else {    // component
      // optional security checks for components (the root context is already checked by assertRootContextIsAccepted)
      long poRootId = po.getRootId();
      int poRootClassId = po.getRootClassId();
      if (poRootId != 0 && poRootId != context.getRootId() ||
          poRootClassId != 0 && poRootClassId != context.getRootClassId()) {
        // the component is not loaded from within its root entity context: make it immutable to
        // prevent modifying the wrong object within the RE's component tree
        po.setFinallyImmutable();
        // explicitly check the root-entity's security rules (if any)
        if (poRootId != 0 && poRootClassId != 0) {
          // po provides both the rootClassId and the rootId -> check that explicitly (useful for deep links)
          if (lastCheckedRoot == null ||    // no list optimization ...
              lastCheckedRoot.rootId != poRootId || lastCheckedRoot.rootClassId != poRootClassId) { // ... or another root
            // asking the security manager is necessary
            SecurityResult sr = SecurityFactory.getInstance().getSecurityManager().
                                evaluate(context, SecurityFactory.getInstance().getReadPermission(), poRootClassId, poRootId);
            if (sr.isAccepted()) {
              if (lastCheckedRoot != null) {
                lastCheckedRoot.rootId = poRootId;
                lastCheckedRoot.rootClassId = poRootClassId;
              }
            }
            else {
              // no permission for the root-entity!
              // -> the component does not exist as the root doesn't for this user as well
              return null;
            }
          }
        }
        else {
          throw new SecurityException(po, "unexpected root context " + context.getRootClassId() + "[" +
                                          context.getRootId() + "], expected " + poRootClassId + "[" + poRootId + "]");
        }
      }
    }
    return derivedPdo;
  }


  /**
   * Checks the root context against the security rules.<br>
   * Non-root contexts are always accepted.
   *
   * @throws SecurityException if the user has no read permission for the root entity of the context
   */
  public void assertRootContextIsAccepted() {
    DomainContext context = getDomainContext();
    if (context.isRootContext()) {
      SecurityResult result = SecurityFactory.getInstance().getSecurityManager().
              evaluate(context, SecurityFactory.getInstance().getReadPermission(),
                       context.getRootClassId(), context.getRootId());
      if (!result.isAccepted()) {
        throw new SecurityException(this, result.explain(
                "no read permission for root-entity " + context.getRootClassId() + "[" + context.getRootId() + "]"));
      }
    }
    // non-root contexts are always accepted.
    // however: they must select root-entities!
    // any attempt to select non-roots will fail in derivePdoFromPo above!
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use the table alias.
   */
  @Override
  public StringBuilder createSelectAllInnerSql(Backend backend) {
    StringBuilder sql = new StringBuilder();
    String alias = getTableAlias();
    sql.append(alias).append('.').append(SQL_ALLSTAR)
       .append(SQL_FROM)
       .append(getTableName()).append(backend.sqlAsBeforeTableAlias()).append(alias)
       .append(SQL_WHEREALL);
    return sql;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use the table alias of the topmost superclass, which holds the ID and serial columns.
   */
  @Override
  public StringBuilder createSelectAllIdSerialInnerSql() {
    StringBuilder sql = new StringBuilder();
    String alias = getTopSuperTableAlias();
    sql.append(alias).append('.').append(CN_ID)
       .append(SQL_COMMA)
       .append(alias).append('.').append(CN_SERIAL)
       .append(SQL_FROM)
       .append(getTableName()).append(getBackend().sqlAsBeforeTableAlias()).append(alias)
       .append(SQL_WHEREALL);
    return sql;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to use the table alias of the topmost superclass for the ID column.
   */
  @Override
  public StringBuilder createSelectAllByIdInnerSql(Backend backend) {
    StringBuilder sql = createSelectAllInnerSql(backend);
    sql.append(SQL_AND)
       .append(getTopSuperTableAlias()).append('.').append(CN_ID)
       .append(SQL_EQUAL_PAR);
    return sql;
  }

  /**
   * Creates the inner SQL text to select the ID field.
   * <p>
   * Returns something like:
   * <pre>
   *  "id FROM xytable WHERE 1=1"
   * </pre>
   *
   * @param classVariables the class variables
   * @param withAlias true if column name with table alias, else without
   * @return the SQL text
   */
  public StringBuilder createSelectIdInnerSql(PersistentObjectClassVariables<? super T, ? super P> classVariables,
                                              boolean withAlias) {
    StringBuilder sql = new StringBuilder();
    String tName = classVariables.getTableName();
    String tAlias = classVariables.getTableAlias();
    if (withAlias) {
      sql.append(tAlias).append('.');
    }
    sql.append(CN_ID).append(SQL_FROM).append(tName);
    if (withAlias) {
      sql.append(getBackend().sqlAsBeforeTableAlias()).append(tAlias);
    }
    sql.append(SQL_WHEREALL);
    return sql;
  }


  /**
   * Creates the inner SQL text to select the ID field.
   * <p>
   * Returns something like:
   * <pre>
   *  "id FROM xytable WHERE 1=1"
   * </pre>
   *
   * @param withAlias true if column name with table alias, else without
   * @return the SQL text
   */
  public StringBuilder createSelectIdInnerSql(boolean withAlias) {
    return createSelectIdInnerSql(getClassVariables(), withAlias);
  }

  @Override
  public StringBuilder createSelectIdInnerSql() {
    return createSelectIdInnerSql(getClassVariables(), true);
  }

  /**
   * Creates the SQL code for select by normtext.
   *
   * @param backend the backend
   * @param not true to find all not matching the normtext
   * @return the SQL code
   */
  public String createSelectByNormTextSql(Backend backend, boolean not) {
    StringBuilder sql = new StringBuilder(SQL_SELECT);
    sql.append(createSelectAllInnerSql(backend));
    sql.append(getSqlClassIdCondition());
    sql.append(getSqlContextCondition());
    sql.append(SQL_AND)
       .append(getTopSuperTableAlias()).append('.').append(CN_NORMTEXT);
    if (not) {
      sql.append(SQL_NOTLIKE_PAR);
    }
    else {
      sql.append(SQL_LIKE_PAR);
    }
    String orderSuffix = orderBy();
    if (orderSuffix != null)  {
      sql.append(SQL_ORDERBY).append(orderSuffix);
    }
    return sql.toString();
  }


  /**
   * Creates the SQL code for the selectSerial statement.
   *
   * @return the SQL code
   */
  @Override
  public String createSelectSerialSql() {
    return SQL_SELECT + CN_SERIAL + SQL_FROM + getTopSuperTableName() + SQL_WHERE + CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the selectMaxId statement.
   *
   * @return the SQL code
   */
  @Override
  public String createSelectMaxIdSql() {
    return SQL_SELECT + getBackend().sqlFunction(Backend.SQL_MAX, CN_ID) + SQL_FROM + getTopSuperTableName();
  }

  /**
   * Creates the SQL code for the selectMaxTableSerial statement.
   *
   * @return the SQL code
   */
  @Override
  public String createSelectMaxTableSerialSql() {
    return SQL_SELECT + getBackend().sqlFunction(Backend.SQL_MAX, CN_TABLESERIAL) + SQL_FROM + getTopSuperTableName();
  }

  /**
   * Creates the SQL code for the fake update statement.<br>
   * Useful to get an exclusive lock within a transaction.
   *
   * @return the SQL code
   */
  @Override
  public String createDummyUpdateSql() {
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET + CN_ID + SQL_EQUAL + CN_ID + SQL_WHERE + CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the serial update statement.
   *
   * @return the SQL code
   */
  @Override
  public String createUpdateSerialSql() {
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET + CN_SERIAL + SQL_EQUAL + CN_SERIAL + SQL_PLUS_ONE + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the serial + tableSerial update statement.
   *
   * @return the SQL code
   */
  @Override
  public String createUpdateSerialAndTableSerialSql() {
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET + CN_SERIAL + SQL_EQUAL + CN_SERIAL + Backend.SQL_PLUS_ONE + Backend.SQL_COMMA +
           CN_TABLESERIAL + SQL_EQUAL_PAR + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the first statement to select expired table serials.
   *
   * @return the SQL code
   */
  @Override
  public String createSelectExpiredTableSerials1Sql() {
    // sort by tableserial+id to return pairs in deterministic order for same tableserials
    return SQL_SELECT + CN_ID + SQL_COMMA + CN_TABLESERIAL + SQL_FROM + getTopSuperTableName() + SQL_WHERE +
           CN_TABLESERIAL + SQL_GREATER_PAR +
           getClassVariables().createSqlClassIdCondition(getValidClassIds(), false) +   // for multi- and single-table inheritance
           SQL_ORDERBY + CN_TABLESERIAL + SQL_COMMA + CN_ID;
  }

  /**
   * Creates the SQL code for the second statement to select expired table serials.
   *
   * @return the SQL code
   */
  @Override
  public String createSelectExpiredTableSerials2Sql() {
    // sort by tableserial+id to return pairs in deterministic order for same tableserials
    return SQL_SELECT + CN_ID + SQL_COMMA + CN_TABLESERIAL + SQL_FROM + getTopSuperTableName() + SQL_WHERE +
           CN_TABLESERIAL + SQL_GREATER_PAR + SQL_AND + CN_TABLESERIAL + Backend.SQL_LESSOREQUAL_PAR +
           getClassVariables().createSqlClassIdCondition(getValidClassIds(), false) +   // for multi- and single-table inheritance
           SQL_ORDERBY + CN_TABLESERIAL + SQL_COMMA + CN_ID;
  }


  /**
   * Gets the result set for the normtext query.
   *
   * @param text the text to search for
   * @param js the optional join config, null if no joins
   * @return the result set
   * @see NormText
   * @see #resultByNormTextCursor
   */
  public ResultSetWrapper resultByNormText(String text, JoinedSelect<T> js) {

    NormText nt = new NormText(text);
    if (nt.isMatchingAll()) {
      return resultAll(js);
    }

    assertRootContextIsAccepted();
    assertNormTextProvided();

    getSession().assertNotRemote();

    StatementId stmtId = nt.isInverted() ?
            getClassVariables().selectByInvertedNormTextStatementId : getClassVariables().selectByNormTextStatementId;

    PreparedStatementWrapper st = getPreparedStatement(stmtId,
            b -> {
              StringBuilder sql = new StringBuilder(createSelectByNormTextSql(b, nt.isInverted()));
              if (js != null) {
                js.createJoinedSql(pdo, sql);
              }
              return sql.toString();
            }
    );

    int ndx = 1;
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx++, contextId);
    }
    st.setString(ndx, nt.getPattern());

    return st.executeQuery();
  }


  /**
   * Gets the result set for the normtext query as a cursor.<br>
   * The result set is scrollable and, if joined, sorted by ID.
   *
   * @param text the text to search for
   * @param js the optional join config, null if no joins
   * @return the result set
   * @see NormText
   * @see #resultByNormText
   */
  public ResultSetWrapper resultByNormTextCursor(String text, JoinedSelect<T> js) {

    NormText nt = new NormText(text);
    if (nt.isMatchingAll()) {
      return resultAllCursor(js);
    }

    assertRootContextIsAccepted();
    assertNormTextProvided();

    getSession().assertNotRemote();

    StatementId stmtId = nt.isInverted() ?
            getClassVariables().selectByInvertedNormTextCursorStatementId : getClassVariables().selectByNormTextCursorStatementId;

    PreparedStatementWrapper st = getPreparedStatement(stmtId,
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
            b -> {
              StringBuilder sql = new StringBuilder(createSelectByNormTextSql(b, nt.isInverted()));
              if (js != null) {
                js.createJoinedSql(pdo, sql);
                sql.append(SQL_ORDERBY).append(getColumnName(CN_ID));
              }
              return sql.toString();
            }
    );

    int ndx = 1;
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx++, contextId);
    }
    st.setString(ndx, nt.getPattern());

    return st.executeQuery();
  }


  /**
   * Selects all objects with a given normtext as a list.
   *
   * @param text the text to search for
   * @return the list of objects, never null
   * @see #resultByNormText
   */
  @Override
  public List<T> selectByNormText(String text) {
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectByNormText(context, text);
      configureRemoteObjects(context, list);
      return list;
    }

    JoinedSelect<T> js = getEagerJoinedSelect();
    List<T> list = new ArrayList<>();
    try (ResultSetWrapper rs = resultByNormText(text, js)) {
      executeQueryToList(rs, js, list);
      return list;
    }
  }


  /**
   * Selects all objects with a given normtext as a cursor.
   *
   * @param text the text to search for
   * @return the cursor
   * @see #resultByNormTextCursor
   */
  @Override
  public ScrollableResource<T> selectByNormTextAsCursor(String text) {
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      RemoteResultSetCursor<T> remoteCursor = getRemoteDelegate().selectByNormTextAsCursor(context, text);
      return new ResultSetCursor<>(context, remoteCursor);
    }
    JoinedSelect<T> js = getEagerJoinedSelect();
    return new ResultSetCursor<>(pdo, resultByNormTextCursor(text, js), js);
  }


  /**
   * Creates SQL code for select all.<br>
   * Appends context condition and order by clause if configured.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createSelectAllSql(Backend backend) {
    StringBuilder sql = new StringBuilder(SQL_SELECT);
    sql.append(createSelectAllInnerSql(backend));
    sql.append(getSqlClassIdCondition());
    sql.append(getSqlContextCondition());
    String orderSuffix = orderBy();
    if (orderSuffix != null)  {
      sql.append(SQL_ORDERBY);
      sql.append(orderSuffix);
    }
    return sql.toString();
  }

  /**
   * Creates SQL code for select any.<br>
   * Appends context condition and order by clause if configured.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createSelectAnySql(Backend backend) {
    StringBuilder sql = new StringBuilder(SQL_SELECT);
    sql.append(createSelectAllInnerSql(backend));
    sql.append(SQL_AND).append(CN_ID).
        append(Backend.SQL_EQUAL).append(Backend.SQL_ARRAY_ANY_PAR);
    sql.append(getSqlClassIdCondition());
    sql.append(getSqlContextCondition());
    String orderSuffix = orderBy();
    if (orderSuffix != null)  {
      sql.append(SQL_ORDERBY);
      sql.append(orderSuffix);
    }
    return sql.toString();
  }

  /**
   * Creates SQL code for {@link #selectAllWithExpiredTableSerials(long)}.<br>
   * Appends context condition and order by clause if configured.<br>
   * By default, the result is sorted by table serial + ID.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createSelectAllWithExpiredTableSerialsSql(Backend backend) {
    StringBuilder sql = new StringBuilder(SQL_SELECT);
    sql.append(createSelectAllInnerSql(backend));
    sql.append(SQL_AND).
        append(CN_TABLESERIAL).append(">?");
    sql.append(getSqlClassIdCondition());
    sql.append(getSqlContextCondition());
    sql.append(SQL_ORDERBY);
    String orderSuffix = orderBy();
    if (orderSuffix != null)  {
      sql.append(orderSuffix);
    }
    else {
      sql.append(CN_TABLESERIAL).append(SQL_COMMA).append(CN_ID);
    }
    return sql.toString();
  }

  /**
   * Creates SQL code for {@link #selectAllWithExpiredTableSerials(long, long)}.<br>
   * Appends context condition and order by clause if configured.<br>
   * By default, the result is sorted by table serial + ID.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createSelectAllWithExpiredTableSerialsSql2(Backend backend) {
    StringBuilder sql = new StringBuilder(SQL_SELECT);
    sql.append(createSelectAllInnerSql(backend));
    sql.append(SQL_AND).append(CN_TABLESERIAL).append(">?");
    sql.append(SQL_AND).append(CN_TABLESERIAL).append("<=?");
    sql.append(getSqlClassIdCondition());
    sql.append(getSqlContextCondition());
    sql.append(SQL_ORDERBY);
    String orderSuffix = orderBy();
    if (orderSuffix != null)  {
      sql.append(orderSuffix);
    }
    else {
      sql.append(CN_TABLESERIAL).append(SQL_COMMA).append(CN_ID);
    }
    return sql.toString();
  }

  /**
   * Gets the result set for all objects in the current context
   * for use in lists.
   *
   * @param js the optional join config, null if no joins
   * @return the result set
   * @see #resultAllCursor
   */
  public ResultSetWrapper resultAll(JoinedSelect<T> js)  {

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllStatementId,
            b -> {
              StringBuilder sql = new StringBuilder(createSelectAllSql(b));
              if (js != null) {
                js.createJoinedSql(pdo, sql);
              }
              return sql.toString();
            }
    );

    int ndx = 1;
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx, contextId);
    }

    return st.executeQuery();
  }


  /**
   * Gets the result set for any objects in the current context for given IDs.<br>
   * Requires a backend supporting the {@code ANY} array operator.
   *
   * @param js the optional join config, null if no joins
   * @param ids the object IDs
   * @return the result set
   */
  public ResultSetWrapper resultAny(JoinedSelect<T> js, Collection<Long> ids)  {

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAnyStatementId,
                                                       b -> {
                                                         StringBuilder sql = new StringBuilder(createSelectAnySql(b));
                                                         if (js != null) {
                                                           js.createJoinedSql(pdo, sql);
                                                         }
                                                         return sql.toString();
                                                       }
    );

    int ndx = 1;
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx++, contextId);
    }
    st.setArray(ndx, Long.class, 0, ids, SQL_ARRAY_ANY);

    return st.executeQuery();
  }


  /**
   * Gets the result set for all objects in the current context with a table serial greater than the given serial.
   *
   * @param js the optional join config, null if no joins
   * @param oldSerial the last known table serial
   * @return the result set
   */
  public ResultSetWrapper resultAllWithExpiredTableSerials(JoinedSelect<T> js, long oldSerial)  {

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllWithExpiredTableSerialsStatementId,
            b -> {
              StringBuilder sql = new StringBuilder(createSelectAllWithExpiredTableSerialsSql(b));
              if (js != null) {
                js.createJoinedSql(pdo, sql);
              }
              return sql.toString();
            }
    );

    st.setLong(1, oldSerial);
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(2, contextId);
    }

    return st.executeQuery();
  }


  /**
   * Gets the result set for all objects in the current context with a table serial within a given range.
   *
   * @param js the optional join config, null if no joins
   * @param oldSerial the last known table serial (exclusive)
   * @param maxSerial the highest table serial (inclusive)
   * @return the result set
   */
  public ResultSetWrapper resultAllWithExpiredTableSerials(JoinedSelect<T> js, long oldSerial, long maxSerial)  {

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllWithExpiredTableSerialsStatement2Id,
                                                       b -> {
                                                         StringBuilder sql = new StringBuilder(createSelectAllWithExpiredTableSerialsSql2(b));
                                                         if (js != null) {
                                                           js.createJoinedSql(pdo, sql);
                                                         }
                                                         return sql.toString();
                                                       }
    );

    st.setLong(1, oldSerial);
    st.setLong(2, maxSerial);
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(3, contextId);
    }

    return st.executeQuery();
  }


  /**
   * Gets the result set for all objects in the current context
   * for use in cursors.<br>
   * Cursors differ from lists because they need {@link ResultSet#TYPE_SCROLL_INSENSITIVE} set.
   * If joined, the result set is sorted by ID.
   *
   * @param js the optional join config, null if no joins
   * @return the result set
   * @see #resultAll
   */
  public ResultSetWrapper resultAllCursor(JoinedSelect<T> js)  {

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllCursorStatementId,
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
            b -> {
              StringBuilder sql = new StringBuilder(createSelectAllSql(b));
              if (js != null) {
                js.createJoinedSql(pdo, sql);
                sql.append(SQL_ORDERBY).append(getColumnName(CN_ID));
              }
              return sql.toString();
            }
    );

    int ndx = 1;
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx, contextId);
    }

    return st.executeQuery();
  }


  /**
   * Selects all records in the current context as a list.
   *
   * @return the list of objects, never null
   * @see #resultAll
   */
  @Override
  public List<T> selectAll() {
    if (getSession().isRemote())  {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectAll(context);
      configureRemoteObjects(context, list);
      return list;
    }

    JoinedSelect<T> js = getEagerJoinedSelect();
    List<T> list = new ArrayList<>();
    try (ResultSetWrapper rs = resultAll(js)) {
      executeQueryToList(rs, js, list);
      return list;
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the backend does not support the {@code ANY} array operator,
   * the PDOs are selected one by one.
   */
  @Override
  public List<T> selectAny(Collection<Long> ids) {
    if (getSession().isRemote())  {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectAny(context, ids);
      configureRemoteObjects(context, list);
      return list;
    }

    if (getBackend().isArrayOperatorSupported(SQL_ARRAY_ANY)) {
      JoinedSelect<T> js = getEagerJoinedSelect();
      List<T> list = new ArrayList<>();
      try (ResultSetWrapper rs = resultAny(js, ids)) {
        executeQueryToList(rs, js, list);
        return list;
      }
    }
    else {
      List<T> list = new ArrayList<>();
      for (long id : ids) {
        T obj = on().select(id);
        if (obj != null) {
          list.add(obj);
        }
      }
      return list;
    }
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(long oldSerial) {
    if (getSession().isRemote())  {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectAllWithExpiredTableSerials(context, oldSerial);
      configureRemoteObjects(context, list);
      return list;
    }

    JoinedSelect<T> js = getEagerJoinedSelect();
    List<T> list = new ArrayList<>();
    try (ResultSetWrapper rs = resultAllWithExpiredTableSerials(js, oldSerial)) {
      executeQueryToList(rs, js, list);
      return list;
    }
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(long oldSerial, long maxSerial) {
    if (getSession().isRemote())  {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectAllWithExpiredTableSerials(context, oldSerial, maxSerial);
      configureRemoteObjects(context, list);
      return list;
    }

    JoinedSelect<T> js = getEagerJoinedSelect();
    List<T> list = new ArrayList<>();
    try (ResultSetWrapper rs = resultAllWithExpiredTableSerials(js, oldSerial, maxSerial)) {
      executeQueryToList(rs, js, list);
      return list;
    }
  }


  /**
   * Selects all records in the current context as a cursor.
   *
   * @return the cursor
   * @see #resultAllCursor
   */
  @Override
  public ScrollableResource<T> selectAllAsCursor() {
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      return new ResultSetCursor<>(context, getRemoteDelegate().selectAllAsCursor(context));
    }
    JoinedSelect<T> js = getEagerJoinedSelect();
    return new ResultSetCursor<>(pdo, resultAllCursor(js), js);
  }


  @Override
  public List<T> selectLatest(long greaterId, int limit) {
    if (getSession().isRemote())  {
      DomainContext context = getDomainContext();
      List<T> list = getRemoteDelegate().selectLatest(context, greaterId, limit);
      configureRemoteObjects(context, list);
      return list;
    }

    getSession().assertNotRemote();
    assertRootContextIsAccepted();

    JoinedSelect<T> js = getEagerJoinedSelect();

    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectLatestStatementId,
         b -> {
           StringBuilder sql = new StringBuilder(createSelectAllInnerSql(b));
           sql.append(getSqlClassIdCondition());
           sql.append(getSqlContextCondition());
           sql.append(Backend.SQL_AND);
           sql.append(getColumnName(CN_ID));
           sql.append(Backend.SQL_GREATER_PAR);
           sql.append(Backend.SQL_ORDERBY)
              .append(getColumnName(CN_ID)).append(Backend.SQL_SORTDESC);
           b.buildSelectSql(sql, false, limit, 0);
           if (js != null) {
             js.createJoinedSql(pdo, sql);
           }
           return sql.toString();
         }
    );

    int ndx = getBackend().setLeadingSelectParameters(st, limit, 0);
    long contextId = getContextId();
    if (contextId >= 0) {
      st.setLong(ndx++, contextId);
    }
    st.setLong(ndx++, greaterId);
    getBackend().setTrailingSelectParameters(st, ndx, limit, 0);

    try (ResultSetWrapper rs = st.executeQuery()) {
      List<T> list = new ArrayList<>();
      executeQueryToList(rs, js, list);
      return list;
    }
  }


  /**
   * Gets the cache.
   * The default implementation returns null.
   * Must be overridden to enable optimization features with TRIP servers.
   *
   * @return the cache, null if uncached
   */
  @Override
  public PdoCache<T> getCache() {
    return null;
  }


  /**
   * Expires the cache according to the table serial numbers.
   * <p>
   * If objects of this class are cached, the
   * cache must be expired on updates, etc...
   * Furthermore, if there is a cache, isCountingModification() <em>must</em> return {@code true},
   * in order for {@link #countModification()} to invalidate the cache for the local JVM.
   * Classes with a cache must override this method!
   * The implementation with the PdoCache should look like this:
   * <pre>
   *    super.expireCache(maxSerial); // in case the inherited cache is overridden
   *    CacheHolder.CACHE.expire(getSession(), getTableName(), maxSerial);
   * </pre>
   * See the wurblet {@code PdoCache.wrbl}.
   *
   * @param maxSerial the new tableSerial this object will get
   * @see PdoCache
   */
  public void expireCache(long maxSerial)  {
    // default does nothing
  }

  @Override
  public T selectCached(long id)  {
    return select(id);
  }

  @Override
  public T selectCachedOnly(long id)  {
    return select(id);
  }

  @Override
  public List<T> selectAllCached()  {
    return selectAll();
  }

  @Override
  public List<T> selectAllForCache() {
    List<T> list;
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      list = getRemoteDelegate().selectAllForCache(context);
      configureRemoteObjects(context, list);
    }
    else {
      list = selectAll();
    }
    return list;
  }

  @Override
  public List<T> selectAnyCached(Collection<Long> ids) {
    return selectAny(ids);
  }

  @Override
  public List<T> selectAnyForCache(Collection<Long> ids) {
    List<T> list;
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      list = getRemoteDelegate().selectAnyForCache(context, ids);
      configureRemoteObjects(context, list);
    }
    else {
      list = selectAny(ids);
    }
    return list;
  }

  @Override
  public T selectForCache(long id) {
    T obj;
    if (getSession().isRemote()) {
      DomainContext context = getDomainContext();
      obj = getRemoteDelegate().selectForCache(context, id);
      configureRemoteObject(context, obj);
    }
    else {
      obj = select(id);
    }
    return obj;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to expire the cache if the object is using the table serial.
   */
  @Override
  public long countModification() {
    long tableSerial = super.countModification();
    if (tableSerial >= 0) {
      expireCache(tableSerial);
    }
    return tableSerial;
  }


  /**
   * Adds a PDO class referencing this PDO class.
   * <p>
   * The method name can also contain dots to denote a chain of methods that must be invoked.
   *
   * @param <R> the PDO type
   * @param clazz the class to add
   * @param methodPath the method path name, null if default <code>"isReferencing&lt;ClassBaseName&gt;"</code>
   * @return true if added, false if already in the map
   * @see #removeReferencingClass(Class, String)
   */
  public <R extends PersistentDomainObject<R>> boolean addReferencingClass(Class<R> clazz, String methodPath) {
    return getClassVariables().addReferencingClass(getPersistenceClass(clazz), methodPath);
  }

  /**
   * Adds a PDO class referencing this PDO class.
   * <p>
   * Same as {@link #addReferencingClass(Class, String)} but by class ID to avoid referencing the
   * PDO's class that can be part of another (yet unknown) module.
   *
   * @param classId the class ID
   * @param methodPath the method path name, null if default <code>"isReferencing&lt;ClassBaseName&gt;"</code>
   * @return true if added, false if already in the map
   * @throws PersistenceException if the class ID is unknown or the class cannot be loaded
   * @see #removeReferencingClass(int, String)
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public boolean addReferencingClass(int classId, String methodPath) {
    String className = SessionUtilities.getInstance().getClassName(classId);
    if (className == null) {
      throw new PersistenceException("unknown class id " + classId);
    }
    try {
      return addReferencingClass((Class) Class.forName(className), methodPath);
    }
    catch (ClassNotFoundException nfx) {
      throw new PersistenceException("class not found " + className, nfx);
    }
  }

  /**
   * Removes a PDO class referencing this PDO class.
   *
   * @param <R> the PDO type
   * @param clazz the class to remove
   * @param methodPath the method path name, null if default <code>"isReferencing&lt;ClassBaseName&gt;"</code>
   * @return true if removed, false if not in the map
   * @see #addReferencingClass(Class, String)
   */
  public <R extends PersistentDomainObject<R>> boolean removeReferencingClass(Class<R> clazz, String methodPath) {
    return getClassVariables().removeReferencingClass(getPersistenceClass(clazz), methodPath);
  }

  /**
   * Removes a PDO class referencing this PDO class.
   *
   * @param classId the class ID
   * @param methodPath the method path name, null if default <code>"isReferencing&lt;ClassBaseName&gt;"</code>
   * @return true if removed, false if not in the map
   * @throws PersistenceException if the class ID is unknown or the class cannot be loaded
   * @see #addReferencingClass(int, String)
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public boolean removeReferencingClass(int classId, String methodPath) {
    String className = SessionUtilities.getInstance().getClassName(classId);
    if (className == null) {
      throw new PersistenceException("unknown class id " + classId);
    }
    try {
      return removeReferencingClass((Class) Class.forName(className), methodPath);
    }
    catch (ClassNotFoundException nfx) {
      throw new PersistenceException("class not found " + className, nfx);
    }
  }


  /**
   * Gets the persistence class from a PDO class.
   *
   * @param <R> the PDO type
   * @param clazz the PDO class
   * @return the persistence class
   * @throws PersistenceException if the persistence class is not an {@link AbstractPersistentObject}
   */
  @SuppressWarnings("unchecked")
  public <R extends PersistentDomainObject<R>> Class<? extends AbstractPersistentObject<R,?>> getPersistenceClass(Class<R> clazz) {
    Class<?> persistenceClass = PdoFactory.getInstance().getPersistenceClass(clazz.getName());
    if (AbstractPersistentObject.class.isAssignableFrom(persistenceClass)) {
      return (Class<? extends AbstractPersistentObject<R,?>>) persistenceClass;
    }
    throw new PersistenceException(this, "persistence class not compatible: " + persistenceClass);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden because remote method requires a {@link DomainContext} instead of just the {@link Session}.
   */
  @Override
  public boolean isReferenced() {
    if (getSession().isRemote()) {
      if (isNew()) {
        // new objects are never referenced because they simply don't exist in the db!
        // so we can save a round trip here
        return false;
      }
      else {
        return getRemoteDelegate().isReferenced(getDomainContext(), getId());
      }
    }
    else {
      // local model
      return super.isReferenced();
    }
  }


  /**
   * Searches for a "pattern" in this object.<br>
   * The default implementation looks for the pattern in the normtext.
   *
   * @param pattern the pattern to search for
   * @return true if this object "contains" the pattern
   */
  @Override
  public boolean containsPattern (String pattern) {
    return getNormText() != null && pattern != null && getNormText().contains(pattern);
  }


  /**
   * Gets the natural ordering to be appended to a SELECT after "ORDER BY".
   * The wurblets will use it if the --sort option is set.
   * Example:
   * <pre>
   *  return CN_ID + "," + CN_PRINTED + " DESC";
   * </pre>
   * Returning just the field name is enough for a single field with sort ascending.
   * The default is null, i.e., no ORDER BY clause will be added.
   *
   * @return the order by appendix string, null if no "order by"
   */
  public String orderBy() {
    return null;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear or renew the token lock in memory.
   *
   * @see #clearTokenLock()
   */
  @Override
  protected void initModification(ModificationType modType) {
    super.initModification(modType);
    clearTokenLock();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to clear the renewTokenLock flag.
   */
  @Override
  protected void finishModification(ModificationType modType) {
    super.finishModification(modType);
    setRenewTokenLockRequested(false);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to update the token lock in the database, since the object itself was not updated.
   * The token lock is renewed if requested, otherwise released.
   * Does nothing if the entity provides no token lock.
   */
  @Override
  protected void finishNotUpdated(ModificationType modType) {
    if (isTokenLockProvided()) {
      if (clearTokenLock()) {
        updateTokenLock(DateHelper.now(getTokenLockTimeout()), getContextUserId(), getEditedSince());
      }
      else {
        // release token
        updateTokenLock(null);
      }
      setRenewTokenLockRequested(false);
    }
  }


  /**
   * Gets the user ID from the current domain context.<br>
   * The ID is derived from the session info of the session associated with the domain context.
   *
   * @return the user ID
   * @throws PersistenceException if the user ID is 0
   */
  public long getContextUserId() {
    long userId = getDomainContext().getSessionInfo().getUserId();
    if (userId == 0) {
      throw new PersistenceException(this, "userId is 0");
    }
    else  {
      return userId;
    }
  }


  /**
   * Clears or renews the token lock.
   * <p>
   * If {@link #isRenewTokenLockRequested()} is true, the token will be renewed (prolonged
   * if existing) or created (if not existing).
   * The operation is only done in memory, not in persistent storage.
   *
   * @return true if token lock set or updated, false if token lock cleared, or PDO does not provide a token lock
   */
  public boolean clearTokenLock() {
    if (isTokenLockProvided()) {
      if (isRenewTokenLockRequested()) {
        long userId = getContextUserId();
        setEditedExpiry(DateHelper.now(getTokenLockTimeout()));
        if (getEditedSince() == null || userId != getEditedBy() || getEditedExpiry() == null) {
          setEditedSince(DateHelper.now());
        }
        setEditedBy(userId);
        return true;
      }
      setEditedBy(0);
      setEditedExpiry(null);
    }
    return false;
  }


  /**
   * Sets whether to apply or renew the token lock on insert or update.
   * <p>
   * By default, the token lock (if set) will be cleared upon insert or update. However, sometimes it is
   * necessary to keep or renew it.<p>
   * Setting this flag to true will renew the token upon the next persistence operation. The flag will be
   * cleared afterward.
   *
   * @param renewTokenLock true to renew the token (once)
   * @throws LockException if {@code renewTokenLock} is true and the PDO is not token-lockable by the current user
   */
  public void setRenewTokenLockRequested(boolean renewTokenLock) {
    if (renewTokenLock && !isTokenLockableByMe()) {
      throw new LockException(getSession(), new TokenLockInfo(getEditedBy(), getEditedSince(), getEditedExpiry()));
    }
    this.renewTokenLock = renewTokenLock;
  }

  /**
   * Determines whether to renew the token lock on insert or update.
   *
   * @return true to renew the token (once), false to clear token (default)
   */
  public boolean isRenewTokenLockRequested() {
    return renewTokenLock;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to force an update if the token lock is held or a renewal is requested.
   */
  @Override
  protected boolean isUpdateNecessary() {
    /*
     * if editedBy or renewTokenLock is set, the token lock must be updated
     * even if the attributes were not changed.
     */
    return super.isUpdateNecessary() || getEditedBy() != 0 || isRenewTokenLockRequested();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to update the normtext and notify the {@link LockManager}.
   */
  @Override
  public void updateObject() {
    // always local session since remoting is not allowed for this method, see AbstractPersistentObjectRemoteDelegateImpl
    updateNormText();
    withLockManagerUpdate(TX_UPDATE_TOKENLOCKED, super::updateObject);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to update the normtext and notify the {@link LockManager}.
   */
  @Override
  public void insertObject() {
    // always local session since remoting is not allowed for this method, see AbstractPersistentObjectRemoteDelegateImpl
    updateNormText();
    withLockManagerUpdate(TX_INSERT_TOKENLOCKED, super::insertObject);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to notify the {@link LockManager}.
   */
  @Override
  public void deleteObject() {
    // always local session since remoting is not allowed for this method, see AbstractPersistentObjectRemoteDelegateImpl
    withLockManagerUpdate(TX_DELETE_TOKENLOCKED, super::deleteObject);
  }

  /**
   * Runs a persistence operation and notifies the {@link LockManager} about the token lock afterward.
   * <p>
   * Both must happen within the <em>same</em> transaction, since the lock table is written by the
   * lock manager while the token columns are written by the operation itself. The operations below
   * open and commit a transaction of their own, which is nested (and thus a no-op) whenever the
   * application already runs one -- as it does via {@code save()} or {@code persist()}. Called
   * directly and outside a transaction, however, the operation would already be committed by the
   * time the lock manager gets to see it, leaving the lock table permanently out of sync.
   * The extra transaction here closes that gap.
   *
   * @param txName the transaction name
   * @param persistenceOperation the operation to run
   */
  private void withLockManagerUpdate(String txName, Runnable persistenceOperation) {
    Db db = getSession();
    if (isTokenLockProvided() && !db.isRemote()) {
      long txVoucher = db.begin(txName);
      try {
        persistenceOperation.run();
        LockManager.getInstance().update(pdo);
        db.commit(txVoucher);
      }
      catch (RuntimeException ex) {
        db.rollback(txVoucher);
        throw ex;
      }
    }
    else {
      // a remote session rejects these operations anyway (see AbstractPersistentObjectRemoteDelegateImpl),
      // so don't spend a round trip on a transaction that only wraps the resulting exception
      persistenceOperation.run();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the root ID and root class ID of components from the domain context, if provided.
   */
  @Override
  public void newId() {
    super.newId();
    if (!isRootEntity()) {
      /*
       * Is a component.
       * Retrieve the root entity's ID and class ID if configured
       */
      DomainContext context = getDomainContext();
      if (isRootIdProvided()) {
        setRootId(context.getRootId());
      }
      if (isRootClassIdProvided()) {
        setRootClassId(context.getRootClassId());
      }
    }
  }

  /**
   * Updates the root context.<br>
   * Method is used after deserialization.
   *
   * @return true if root context set, false if this is not a root-entity
   */
  public boolean updateRootContext() {
    if (isRootEntity()) {
      DomainContext context = getDomainContext();
      // switch root context if not already done
      context.getRootContext(pdo);
      return true;
    }
    return false;
  }


  /**
   * Configures the remotely retrieved object.<br>
   * Root entities get their own root context. Components referring to the same root entity
   * as the given context get that context. The object is flagged as validated.
   *
   * @param context the local domain context
   * @param obj the object, null if none
   */
  public void configureRemoteObject(DomainContext context, PersistentDomainObject<?> obj) {
    if (obj != null) {
      // do that without invocation handling (faster)
      @SuppressWarnings("unchecked")
      AbstractPersistentObject<?,?> po = (AbstractPersistentObject<?,?>) obj.getPersistenceDelegate();
      if (po.isRootEntity()) {
        // root entities get their own root context
        if (!context.isSessionThreadLocal()) {
          po.setSession(context.getSession());
        }
        po.setSessionHolder(po.getDomainContext().getRootContext(obj));
      }
      else  {
        // component
        DomainContext poContext = po.getDomainContext();
        if (poContext.getRootId() == context.getRootId() &&
            poContext.getRootClassId() == context.getRootClassId()) {
          // component that refers to the same root entity: use the same context instance
          po.setDomainContext(context);
        }
        // else: use a different context instance
      }
      // loaded from the database: not modified yet and hence no validation pending
      po.validated = true;
    }
  }

  /**
   * Configures the remotely retrieved objects.
   *
   * @param context the local domain context
   * @param objects the objects to configure, null if none
   */
  public void configureRemoteObjects(DomainContext context, Collection<? extends PersistentDomainObject<?>> objects) {
    if (objects != null) {
      for (PersistentDomainObject<?> obj: objects) {
        configureRemoteObject(context, obj);
      }
    }
  }


  /**
   * Asserts that the PO's context is a root context.
   *
   * @throws PersistenceException if not a root context
   */
  protected void assertRootContext() {
    if (!getDomainContext().isRootContext()) {
      /*
       * This will also prevent components from being saved alone,
       * i.e., not from within their root entity.
       * (possible only in servers)
       */
      throw new PersistenceException(this, "unexpected non-root domain context");
    }
  }


  /**
   * Marks all objects in a list to be deleted.<br>
   * This method is provided to mark components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param <X> the PDO type
   * @param pdos the objects to mark deleted
   */
  @SuppressWarnings("rawtypes")
  protected <X extends PersistentDomainObject<X>> void markDeleted(Collection<X> pdos)  {
    if (pdos != null) {
      for (X pDo: pdos)  {
        if (pDo != null) {
          AbstractPersistentObject po = (AbstractPersistentObject) pDo.getPersistenceDelegate();
          if (!po.isNew()) {
            po.markDeleted();
          }
        }
      }
    }
  }


  /**
   * Marks an object to be deleted.<br>
   * This method is provided to mark components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param pdo the PDO to mark deleted
   */
  @SuppressWarnings("rawtypes")
  protected void markDeleted(PersistentDomainObject<?> pdo) {
    if (pdo != null) {
      ((AbstractPersistentObject) pdo.getPersistenceDelegate()).markDeleted();
    }
  }


  /**
   * Deletes a collection of objects.<br>
   * This method is provided to delete components in PDOs only.
   * This method must not be used from within the application!
   * <p>
   * New objects that were never persisted are skipped.
   * A {@link TrackedList} is marked as not modified afterward.
   *
   * @param <X> the PDO type
   * @param pdos the collection of the objects to delete
   */
  @SuppressWarnings("rawtypes")
  protected <X extends PersistentDomainObject<X>> void delete(Collection<X> pdos) {
    if (pdos != null) {
      Session session = null;
      for (X pDo: pdos) {
        if (pDo != null) {
          AbstractPersistentObject po = (AbstractPersistentObject) pDo.getPersistenceDelegate();
          if (!po.isVirgin()) {
            if (session == null) {
              session = po.getSession();
              if (session == null) {
                throw new PdoRuntimeException(pDo, "session is null");
              }
            }
            else if (po.getSession() != session) {
              // must be the same instance of Db!
              throw new PdoRuntimeException(pDo, "unexpected session: " + po.getSession() + ", expected: " + session);
            }
            po.deleteImpl();
          }
        }
      }
      if (pdos instanceof TrackedList<?>) {
        ((TrackedList<?>) pdos).setModified(false);
      }
    }
  }

  /**
   * Deletes a PDO.<br>
   * This method is provided to delete components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param pdo the PDO to delete
   */
  @SuppressWarnings("rawtypes")
  protected void delete(PersistentDomainObject<?> pdo) {
    if (pdo != null) {
      ((AbstractPersistentObject) pdo.getPersistenceDelegate()).deleteImpl();
    }
  }


  /**
   * Deletes all objects in the oldCollection that are not in the newCollection.<br>
   * This method is provided to save components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param <X> the PDO type
   * @param oldCollection the list of objects stored in db
   * @param newCollection the new list of objects
   */
  @SuppressWarnings("rawtypes")
  protected <X extends PersistentDomainObject<X>> void deleteMissingInCollection(Collection<X> oldCollection,
                                                                                 Collection<X> newCollection) {
    if (oldCollection != null && !oldCollection.isEmpty()) {
      Session session = null;
      for (X pDo: oldCollection) {
        if (pDo != null) {
          AbstractPersistentObject po = (AbstractPersistentObject) pDo.getPersistenceDelegate();
          if (!po.isVirgin() && (newCollection == null || !newCollection.contains(pDo))) {
            if (session == null) {
              session = po.getSession();
              if (session == null) {
                throw new PersistenceException(pDo, "session is null");
              }
            }
            else if(po.getSession() != session) {
              // must be the same instance of Db!
              throw new PersistenceException(pDo, "unexpected session: " + po.getSession() + ", expected: " + session);
            }
            po.deleteImpl();
          }
        }
      }
    }
  }


  /**
   * Asserts that the PO is a root entity.
   *
   * @throws PersistenceException if not a root entity
   */
  public void assertRootEntity() {
    if (!isRootEntity()) {
      throw new PersistenceException(this, "not a root-entity");
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Only root entities can be deleted. The write permission is checked only once, i.e., in the server for remote sessions.
   *
   * @throws PersistenceException if not a root entity
   */
  @Override
  public void delete() {
    assertRootEntity();
    if (!getSession().isRemote()) {
      assertWritePermission();  // check only once at server side
    }
    deleteImpl();
  }

  /**
   * Implementation of delete bypassing the invocation handler.<br>
   * For remote sessions, the object cannot be used anymore after deletion.
   */
  protected void deleteImpl() {
    if (getSession().isRemote()) {
      assertPersistable();
      prepareDelete();
      long tableSerial = getRemoteDelegate().deleteImpl(pdo);     // this will do delete() in server, i.e. all checks
      setPersistable(false);                                      // pdo cannot be used anymore
      expireCache(tableSerial);
    }
    else {
      deleteObject();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Only root entities can be saved. The write permission is checked only once, i.e., in the server for remote sessions.
   * Notice that for remote sessions, the object cannot be used anymore after saving. Use {@link #persist()} instead.
   *
   * @throws PersistenceException if not a root entity
   */
  @Override
  public void save() {
    assertRootEntity();
    if (!getSession().isRemote()) {
      assertWritePermission();  // check only once at server side
    }
    saveImpl();
  }

  /**
   * Implementation of save bypassing the invocation handler.<br>
   * For remote sessions, the object cannot be used anymore after saving.
   * Locally, the object is validated before, if not already validated.
   */
  protected void saveImpl() {
    if (getSession().isRemote()) {
      assertPersistable();
      // execute in the 3-tier client
      clearOnRemoteSave();
      prepareSave();
      long tableSerial = getRemoteDelegate().saveImpl(pdo);
      setPersistable(false);    // pdo cannot be used anymore
      expireCache(tableSerial);
    }
    else {
      if (!isValidated()) {
        validate();
      }
      super.saveObject();
    }
  }


  /**
   * Saves a collection of PDOs.<br>
   * This method is provided to save components in PDOs only.
   * Assumes that a transaction is already running.
   * This method must not be used from within the application!
   * <p>
   * PDOs that are no longer persistable are deleted, if already stored in the database.
   * A {@link TrackedList} is marked as not modified afterward.
   *
   * @param <X> the PDO type
   * @param pdos the collection to save
   * @param modifiedOnly true if only modified objects are saved
   */
  @SuppressWarnings({"rawtypes" })
  public static <X extends PersistentDomainObject<X>> void save(Collection<X> pdos, boolean modifiedOnly) {
    if (pdos != null) {
      Session session = null;
      for (X pDo: pdos) {
        if (pDo != null) {
          AbstractPersistentObject po = (AbstractPersistentObject) pDo.getPersistenceDelegate();
          if (session == null) {
            session = po.getSession();
            if (session == null) {
              throw new PdoRuntimeException(pDo, "session is null");
            }
          }
          else if (po.getSession() != session) {
            // must be the same instance!
            throw new PdoRuntimeException(pDo, "unexpected session: " + po.getSession() + ", expected: " + session);
          }
          if (po.isPersistable()) {
            if (!modifiedOnly || po.isModified())  {
              po.saveImpl();
            }
          }
          else  {
            // po not persistable anymore?
            if (!po.isNew()) {
              // already stored on disk: remove it!
              po.deleteImpl();
            }
          }
        }
      }
      if (pdos instanceof TrackedList<?>) {
        ((TrackedList<?>) pdos).setModified(false);
      }
    }
  }

  /**
   * Saves a PDO.<br>
   * This method is provided to save components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param pdo the PDO to save
   */
  @SuppressWarnings("rawtypes")
  public static void save(PersistentDomainObject<?> pdo) {
    if (pdo != null) {
      ((AbstractPersistentObject) pdo.getPersistenceDelegate()).saveImpl();
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * Only root entities can be persisted. The write permission is checked only once, i.e., in the server for remote sessions.
   * Transient data is preserved for remote sessions.
   *
   * @throws PersistenceException if not a root entity
   */
  @Override
  @SuppressWarnings("rawtypes")
  public T persist() {
    assertRootEntity();
    T obj;
    if (getSession().isRemote()) {
      Object data = getTransientData();
      obj = persistImpl();
      if (obj != null) {
        if (data != null) {
          ((AbstractPersistentObject) obj.getPersistenceDelegate()).setTransientData(data);
        }
        expireCache(obj.getTableSerial());
      }
    }
    else {
      assertWritePermission();  // check only once at server side
      obj = persistImpl();
    }
    return obj;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Same as {@link #persist()}, but renews the token lock.
   *
   * @throws LockException if the PDO is not token-lockable by the current user
   */
  @Override
  public T persistTokenLocked() {
    setRenewTokenLockRequested(true);
    return persist();
  }


  /**
   * Implementation of persist() bypassing the invocation handler.<br>
   * Locally, the object is validated before, if not already validated.
   *
   * @return the persisted PDO
   * @throws PersistenceException if the local persist does not return the same object
   */
  protected T persistImpl() {
    if (getSession().isRemote())  {
      assertPersistable();

      // execute in the 3-tier client
      clearOnRemoteSave();
      prepareSave();

      /*
       * We cannot invoke super.persistObject because we cannot
       * change the reference to the persistent object in the PDO proxy object.
       * Hence, we must transfer the whole PDO proxy and update the context/session on return.
       */
      T persistedPdo = getRemoteDelegate().persistImpl(pdo);
      configureRemoteObject(getDomainContext(), persistedPdo);
      return persistedPdo;
    }
    else {
      if (!isValidated()) {
        validate();
      }
      if (super.persistObject() != this) {
        throw new PersistenceException(this, "local persist does not return same object");
      }
      return pdo;
    }
  }


  /**
   * Determines whether in updates of composite objects unmodified objects in the
   * update path get at least the serial updated or are not touched at all.
   * The default is to leave unmodified objects untouched, except root entities.
   * However, in some applications it is necessary to update the master object if some of its children are updated (usually
   * to trigger something, e.g., a cache-update).<p>
   * The default implementation returns true if this is a root entity.
   *
   * @return true if update serial even if the object is unchanged
   * @see #updateObject
   */
  @Override
  public boolean isUpdatingSerialEvenIfNotModified() {
    return isRootEntity();
  }


  /**
   * Checks whether some objects in a collection are modified.<br>
   * A {@link TrackedList} is also modified if elements were added, replaced or removed.
   * This method is provided for components in PDOs only.
   * This method must not be used from within the application!
   *
   * @param <X> the PDO type
   * @param pdos the objects
   * @return true if modified
   */
  @SuppressWarnings("rawtypes")
  protected <X extends PersistentDomainObject<X>> boolean isModified(Collection<X> pdos) {
    // TrackedArrayLists are modified if elements added, replaced or removed
    if (pdos instanceof TrackedList<?> &&
        ((TrackedList<?>) pdos).isModified())  {
      return true;
    }
    // check attributes
    if (pdos != null) {
      for (X pDo: pdos)  {
        if (pDo != null) {
          AbstractPersistentObject po = (AbstractPersistentObject) pDo.getPersistenceDelegate();
          if (po.isModified()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * By default, objects don't need to include the editedBy/Since/Expiry columns in the
   * database table.<br>
   * Override this method if the object contains such columns, i.e., global model option [TOKENLOCK] set.
   *
   * @return true if the object is using the edited lock columns, false if not.
   */
  @Override
  public boolean isTokenLockProvided()  {
    return false;
  }

  /**
   * Asserts that the entity provides the token lock columns.
   *
   * @throws PersistenceException if no token lock provided
   */
  protected void assertTokenLockProvided() {
    if (!isTokenLockProvided()) {
      throw new PersistenceException(this, "entity does not provide the edited-token columns");
    }
  }

  /**
   * Gets the expiration in milliseconds of a token lock.<br>
   * If {@link #isTokenLockProvided()}, the default is 1 hour, else 0.
   *
   * @return the timespan in ms, 0 = no token required.
   */
  @Override
  public long getTokenLockTimeout() {
    // if token lock columns are provided, the default timeout is 1 hour
    return isTokenLockProvided() ? Constants.HOUR_MS : 0;
  }


  /**
   * Creates the SQL code for the {@link #updateTokenLock} statement.
   *
   * @return the SQL code
   */
  public String createUpdateTokenLockSql() {
    assertTokenLockProvided();
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET +
           CN_EDITEDBY + SQL_EQUAL_PAR_COMMA +
           CN_EDITEDSINCE + SQL_EQUAL_PAR_COMMA +
           CN_EDITEDEXPIRY + SQL_EQUAL_PAR + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR + SQL_AND +
           SQL_LEFT_PARENTHESIS +
           CN_EDITEDBY + SQL_EQUAL_PAR + SQL_OR +                 // the current user holds the token
           CN_EDITEDBY + SQL_EQUAL_ZERO + SQL_OR +                // no one holding the token
           CN_EDITEDEXPIRY + SQL_LESS_PAR + SQL_OR +              // token expired
           CN_EDITEDEXPIRY + SQL_ISNULL +                         // or not set at all (pathologic case)
           SQL_RIGHT_PARENTHESIS;
  }

  /**
   * Creates the SQL code for the {@link #updateTokenLockOnly} statement.
   *
   * @return the SQL code
   */
  public String createUpdateTokenLockOnlySql() {
    assertTokenLockProvided();
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET +
           CN_EDITEDBY + SQL_EQUAL_PAR_COMMA +
           CN_EDITEDSINCE + SQL_EQUAL_PAR_COMMA +
           CN_EDITEDEXPIRY + SQL_EQUAL_PAR + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the select in {@link #updateTokenLock} statement.
   *
   * @return the SQL code
   */
  public String createSelectTokenLockSql() {
    assertTokenLockProvided();
    return SQL_SELECT + CN_EDITEDBY + SQL_COMMA + CN_EDITEDSINCE + SQL_COMMA + CN_EDITEDEXPIRY +
           SQL_COMMA + CN_SERIAL +
           SQL_FROM + getTopSuperTableName() + SQL_WHERE + CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the {@link #transferTokenLock} statement without table serial.
   *
   * @return the SQL code
   */
  public String createTransferTokenLockSql() {
    assertTokenLockProvided();
    return SQL_UPDATE + getTopSuperTableName() + SQL_SET +
           CN_EDITEDBY + SQL_EQUAL_PAR +
           SQL_WHERE + CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Gets the ID of the user currently editing this object.
   *
   * @return the ID or 0 if not being edited currently
   */
  @Override
  public long getEditedBy() {
    return editedBy;
  }

  /**
   * Sets the user editing this object.<br>
   * Does *NOT* alter isModified() and does not require the object to be mutable.
   *
   * @param editedBy the ID of the user, 0 to clear
   */
  public void setEditedBy(long editedBy) {
    this.editedBy = editedBy;
  }


  /**
   * Requests a token lock for the current user.
   *
   * @throws LockException if locked by another user
   * @throws NotFoundException if the PDO was updated or removed in the meantime
   * @see #updateTokenLock(Timestamp, long, Timestamp)
   */
  @Override
  public void requestTokenLock() {
    updateTokenLock(DateHelper.now(getTokenLockTimeout()));
  }

  /**
   * Releases the token lock.<br>
   * Use this method if a PDO needs to be unlocked without being persisted.
   *
   * @throws LockException if locked by another user
   * @throws NotFoundException if the PDO was updated or removed in the meantime
   * @see #updateTokenLock(Timestamp, long, Timestamp)
   */
  @Override
  public void releaseTokenLock() {
    updateTokenLock(null);
  }

  /**
   * Checks whether this object is token locked (editedBy != 0) and
   * the lock is not expired.
   *
   * @return true if locked by any user
   */
  @Override
  public boolean isTokenLocked() {
    return isTokenLockProvided() && !isNew() && getEditedBy() != 0 && DateHelper.toMillis(getEditedExpiry()) > System.currentTimeMillis();
  }

  /**
   * Checks whether this object is token-locked by the given user.
   *
   * @param userId the user's ID
   * @return true if locked by the given user
   */
  @Override
  public boolean isTokenLockedBy(long userId) {
    return isTokenLocked() && getEditedBy() == userId;
  }

  /**
   * Checks whether this object is token-locked by the current user.
   *
   * @return true if locked
   */
  @Override
  public boolean isTokenLockedByMe() {
    return isTokenLockedBy(getContextUserId());
  }

  /**
   * Checks whether this object can be token-locked by the current user.
   *
   * @return true if the entity provides a token lock and it is not locked or already locked by the current user
   */
  @Override
  public boolean isTokenLockableByMe() {
    return isTokenLockProvided() && (!isTokenLocked() || getEditedBy() == getContextUserId());
  }

  /**
   * Gets the time since when this object is being edited.
   *
   * @return the timestamp, null if not being edited
   */
  @Override
  public Timestamp getEditedSince() {
    return editedSince;
  }

  /**
   * Sets the time since when this object is being edited.<br>
   * Does *NOT* alter isModified() and does not require the object to be mutable.
   *
   * @param editedSince the time, null to clear
   */
  public void setEditedSince(Timestamp editedSince) {
    this.editedSince = editedSince;
  }


  /**
   * Gets the time when the token lock expires.
   *
   * @return the expiration timestamp, null if not set
   */
  @Override
  public Timestamp getEditedExpiry() {
    return editedExpiry;
  }

  /**
   * Sets the time when the token should expire.<br>
   * Does *NOT* alter isModified() and does not require the object to be mutable.
   *
   * @param editedExpiry the expiration time, null to clear
   */
  public void setEditedExpiry(Timestamp editedExpiry) {
    this.editedExpiry = editedExpiry;
  }


  /**
   * Applies the given token lock info to this PDO.
   *
   * @param tokenLockInfo the token lock info
   */
  public void applyTokenLockInfo(TokenLockInfo tokenLockInfo) {
    if (tokenLockInfo != null) {
      setEditedBy(tokenLockInfo.editedBy());
      setEditedSince(tokenLockInfo.editedSince());
      setEditedExpiry(tokenLockInfo.editedExpiry());
    }
  }

  /**
   * Updates the token lock columns in the database.<br>
   * This method will <em>not</em> log modification and <em>not</em> update the serial counter.
   * The update is a single conditional statement that succeeds only if the serial is unchanged and the token
   * is held by the given user, not held at all, or expired.
   * <p>
   * If {@code tokenExpiry} is null, the lock will be released.<br>
   * If the operation was successful, {@code editedBy} is set to 0, {@code editedExpiry} is set to null
   * and {@code editedSince} is set to {@code curTime}.
   * Releasing an already released token is not considered as an error
   * and will just update the release timestamp.
   * <p>
   * If {@code tokenExpiry} is not null, a new lock for the given {@code userId} and {@code curTime} is requested.<br>
   * {@code editedBy} is set to the user's ID, {@code editedExpiry} holds the expiration timestamp
   * and {@code editedSince} the {@code curTime}.
   * Requesting an already requested token will simply renew the lock, i.e., update {@code editedExpiry} and
   * {@code editedSince} accordingly.
   * <p>
   * If the operation was successful, the values for {@code editedBy}, {@code editedSince} and {@code editedExpiry}
   * are set in this PDO accordingly. Otherwise, they are set to the values read from the database and
   * a {@link LockException} is thrown in case another user is holding the lock.
   * A {@link NotFoundException} is thrown if the PDO was updated or removed in the meantime,
   * which indicates some logic error in the application (missing {@code @Transaction}, for example).
   * <p>
   * The method does nothing if the persistence operation is not allowed by the session
   * (see {@link Db#isPersistenceOperationAllowed}).
   *
   * @param tokenExpiry holds the time the token will expire, null to release the token
   * @param userId the current user (the token must be held by this user, not held at all, or expired)
   * @param curTime the current system time
   * @throws LockException if another user is holding the lock
   * @throws NotFoundException if the PDO was updated or removed in the meantime
   */
  public void updateTokenLock(Timestamp tokenExpiry, long userId, Timestamp curTime) {
    Db db = getSession();
    if (db.isRemote())  {
      try {
        applyTokenLockInfo(getRemoteDelegate().updateTokenLock(getDomainContext(), getId(), tokenExpiry, userId, curTime));
      }
      catch (RuntimeException e) {
        if (e instanceof LockException lx) {
          applyTokenLockInfo(lx.getTokenLockInfo());
        }
        throw e;
      }
    }
    else  {
      assertTokenLockProvided();
      assertNotCached();
      assertPersistable();
      assertRootEntity();
      assertWritePermission();

      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        long txVoucher = db.begin(TX_UPDATE_TOKENLOCK);
        try {
          PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateTokenLockStatementId,
                                                             _ -> createUpdateTokenLockSql());
          long newUser = tokenExpiry != null ? userId : 0;

          st.setLong(1, newUser);
          st.setTimestamp(2, curTime);
          st.setTimestamp(3, tokenExpiry);
          st.setLong(4, getId());
          st.setLong(5, getSerial());
          st.setLong(6, userId);
          st.setTimestamp(7, curTime);

          if (st.executeUpdate() == 1) {
            // update was successful
            setEditedBy(newUser);
            setEditedSince(curTime);
            setEditedExpiry(tokenExpiry);
            LockManager.getInstance().update(pdo);
            db.commit(txVoucher);
          }
          else {
            // no success: another user is currently holding the token, or PDO was updated in the meantime
            st = getPreparedStatement(getClassVariables().selectTokenLockStatementId, _ -> createSelectTokenLockSql());
            st.setLong(1, getId());
            try (ResultSetWrapper rs = st.executeQuery()) {
              if (rs.next()) {
                setEditedBy(rs.getLong(CN_EDITEDBY));
                setEditedSince(rs.getTimestamp(CN_EDITEDSINCE));
                setEditedExpiry(rs.getTimestamp(CN_EDITEDEXPIRY));
                long curSerial = rs.getLong(CN_SERIAL);
                if (curSerial != getSerial()) {
                  throw new NotFoundException(this, "PDO was updated in the meantime with serial " + curSerial, getSerial());
                }
                throw new LockException(db, new TokenLockInfo(getEditedBy(), getEditedSince(), getEditedExpiry()));
              }
              else {
                throw new NotFoundException(this, "PDO was removed in the meantime", -1);
              }
            }
          }
        }
        catch (PersistenceException px) {
          setPersistable(false);
          db.rollback(txVoucher, !px.isTemporary());
          px.updateDbObject(this);
          throw px;
        }
        catch (RuntimeException ex) {
          setPersistable(false);
          db.rollback(txVoucher);
          throw new PersistenceException(this, ex);
        }
      }
    }
  }


  /**
   * Updates the token lock columns in the database for the current user.<br>
   * Same as {@link #updateTokenLock(Timestamp, long, Timestamp)} with the user ID of the domain context's
   * session info and the current time.
   *
   * @param tokenExpiry holds the time the token will expire, null to release the token
   * @throws LockException if another user is holding the lock
   * @throws NotFoundException if the PDO was updated or removed in the meantime
   */
  public void updateTokenLock(Timestamp tokenExpiry) {
    updateTokenLock(tokenExpiry, getDomainContext().getSessionInfo().getUserId(), DateHelper.now());
  }


  /**
   * Updates the token lock attributes to persistent storage.<br>
   * No check is done whether locked or not, and there is no serial
   * update and no modification log. Used by daemons to clean up.
   */
  public void updateTokenLockOnly() {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().updateTokenLockOnly(getDomainContext(), getId(), getEditedBy(), getEditedSince(), getEditedExpiry());
    }
    else  {
      assertNotCached();
      assertPersistable();
      assertRootEntity();
      assertWritePermission();

      long txVoucher = db.begin(TX_UPDATE_TOKENLOCK_ONLY);
      try {
        if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
          PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateTokenLockOnlyStatementId,
                                                             _ -> createUpdateTokenLockOnlySql());
          st.setLong(1, getEditedBy());
          st.setTimestamp(2, getEditedSince());
          st.setTimestamp(3, getEditedExpiry());
          st.setLong(4, getId());
          assertThisRowAffected(st.executeUpdate());
          LockManager.getInstance().update(pdo);
          db.commit(txVoucher);
        }
      }
      catch (PersistenceException px) {
        setPersistable(false);
        db.rollback(txVoucher, !px.isTemporary());
        px.updateDbObject(this);
        throw px;
      }
      catch (RuntimeException ex) {
        setPersistable(false);
        db.rollback(txVoucher);
        throw new PersistenceException(this, ex);
      }
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * The update is serial-checked, i.e., fails if the PDO was updated in the meantime.
   * If the token is transferred to another user, the expiry is refreshed as well.
   * If cleared ({@code userId == 0}), only {@code editedBy} is updated.
   * The method does nothing if the persistence operation is not allowed by the session.
   */
  @Override
  public T transferTokenLock(long userId) {
    Db db = getSession();
    if (db.isRemote()) {
      T tPdo = getRemoteDelegate().transferTokenLock(pdo, userId);
      configureRemoteObject(getDomainContext(), tPdo);
      return tPdo;
    }
    else {
      assertNotCached();
      assertPersistable();
      assertRootEntity();
      assertWritePermission();

      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        long txVoucher = db.begin(TX_TRANSFER_TOKENLOCK);
        try {
          PreparedStatementWrapper st = getPreparedStatement(getClassVariables().transferTokenLockStatementId,
                                                             _ -> createTransferTokenLockSql());
          int ndx = 0;
          st.setLong(++ndx, userId);
          setEditedBy(userId);
          st.setLong(++ndx, getId());
          st.setLong(++ndx, getSerial());
          assertThisRowAffected(st.executeUpdate());

          if (userId != 0) {
            // set/refresh the expiry
            updateTokenLock(DateHelper.now(getTokenLockTimeout()), userId, DateHelper.now());
          }
          LockManager.getInstance().update(pdo);
          db.commit(txVoucher);
        }
        catch (PersistenceException px) {
          setPersistable(false);
          db.rollback(txVoucher);
          px.updateDbObject(this);
          throw px;
        }
        catch (RuntimeException ex) {
          setPersistable(false);
          db.rollback(txVoucher);
          throw new PersistenceException(this, ex);
        }
      }
      return pdo;
    }
  }


  /**
   * Gets the optional transient data object.<br>
   * When objects need to be reloaded from storage, all non-persistent
   * data attached to the object would be lost. The methods {@link #reload()}, {@link #reloadTokenLocked()},
   * {@link #reloadForUpdate()} and, for remote sessions, {@link #persist()} preserve that data via
   * {@code getTransientData()} and {@code setTransientData(Object)}.
   * Applications may override those methods.<br>
   * The default implementation returns null.
   *
   * @return the transient data, null if none
   * @see #setTransientData(Object)
   */
  public Object getTransientData()  {
    return null;
  }

  /**
   * Sets the optional transient data object.<br>
   * The default implementation does nothing.
   *
   * @param data the transient data
   * @see #getTransientData()
   */
  public void setTransientData(Object data) {
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to covariance.
   */
  @Override
  @SuppressWarnings("unchecked")
  public AbstractPersistentObjectRemoteDelegate<T,P> getRemoteDelegate()  {
    return (AbstractPersistentObjectRemoteDelegate<T,P>) super.getRemoteDelegate();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default scopes are: {@link PersistenceScope}, {@link MandatoryScope} and {@link ChangeableScope}.
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    return new Class[] { PersistenceScope.class, MandatoryScope.class, ChangeableScope.class };
  }

  @Override
  public List<ValidationResult> validate(String validationPath, ValidationScope scope) {
    if (isVirgin()) {   // if never persisted before (isNew() would be true for deleted objects as well)
      presetVirgin();
    }
    return ValidationUtilities.getInstance().validate(getPdo(), validationPath, scope);   // not this but getPdo()!
  }


  /**
   * {@inheritDoc}
   * <p>
   * Validates within the persistence scope and sets the validated flag if successful.
   *
   * @throws ValidationFailedException if validation failed
   */
  @Override
  public void validate() {
    validated = false;
    String validationPath = ValidationUtilities.getInstance().getDefaultValidationPath(pdo);
    List<ValidationResult> results;
    try {
      results = validate(validationPath, ValidationScopeFactory.getInstance().getPersistenceScope());
    }
    catch (ValidationFailedException vfx) {
      results = vfx.getResults();
    }
    if (ValidationUtilities.getInstance().hasFailed(results)) {
      throw new ValidationFailedException(
              "validation of " + pdo.toGenericString() + " as " + validationPath + " failed:\n"
              + ValidationUtilities.getInstance().resultsToString(results), results);
    }
    validated = true;
  }

  @Override
  public boolean isValidated() {
    return validated;
  }

  /**
   * Sets the modified flag.<p>
   * For optimizations, it is possible to skip objects that have not
   * been modified. The modified attribute is cleared whenever the
   * object is persisted.
   * Modified objects need to be validated again, unmodified objects are considered validated.
   *
   * @param modified true if the object is flagged modified, false if not
   */
  @Override
  public void setModified(boolean modified) {
    super.setModified(modified);
    validated = !modified;
  }

  /**
   * Determines whether the application is allowed to read this PDO.<br>
   * Makes no sense to publish in PersistentObject because PDOs without read permissions
   * are not read from the database at all.
   *
   * @return true if allowed
   */
  public boolean isReadAllowed() {
    return getClassVariables().isReadAllowed(this);
  }

  @Override
  public boolean isWriteAllowed() {
    return getClassVariables().isWriteAllowed(this);
  }

  @Override
  public boolean isViewAllowed() {
    return getClassVariables().isViewAllowed(this);
  }

  @Override
  public boolean isEditAllowed() {
    return getClassVariables().isEditAllowed(this);
  }

  /**
   * Checks write permission for this object.
   *
   * @throws SecurityException if there is no write-permission
   */
  protected void assertWritePermission() {
    if (!isWriteAllowed()) {
      throw new SecurityException(this, "no write-permission");
    }
  }

  /**
   * Checks read permission for this object.
   *
   * @throws SecurityException if there is no read permission
   */
  protected void assertReadPermission() {
    if (!isReadAllowed()) {
      throw new SecurityException(this, "no read-permission");
    }
  }

}
