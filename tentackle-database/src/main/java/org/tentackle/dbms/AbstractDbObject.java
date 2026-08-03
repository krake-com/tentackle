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

package org.tentackle.dbms;

import org.tentackle.dbms.trip.AbstractDbObjectRemoteDelegate;
import org.tentackle.dbms.trip.DbObjectResult;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.Immutable;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.PropertyListener;
import org.tentackle.misc.PropertySupport;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.NotFoundException;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionHolder;
import org.tentackle.sql.Backend;
import org.tentackle.trip.TripSerializable;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import static org.tentackle.sql.Backend.SQL_ALLSTAR;
import static org.tentackle.sql.Backend.SQL_AND;
import static org.tentackle.sql.Backend.SQL_COMMA;
import static org.tentackle.sql.Backend.SQL_DELETE_FROM;
import static org.tentackle.sql.Backend.SQL_EQUAL;
import static org.tentackle.sql.Backend.SQL_EQUAL_PAR;
import static org.tentackle.sql.Backend.SQL_FROM;
import static org.tentackle.sql.Backend.SQL_ORDERBY;
import static org.tentackle.sql.Backend.SQL_SELECT;
import static org.tentackle.sql.Backend.SQL_SET;
import static org.tentackle.sql.Backend.SQL_UPDATE;
import static org.tentackle.sql.Backend.SQL_WHERE;
import static org.tentackle.sql.Backend.SQL_WHEREALL;

/**
 * A persistent low-level database object.
 * <p>
 * All persistence implementations must extend {@code AbstractDbObject}, which provides the generic
 * functionality of persistent objects. Every {@code AbstractDbObject} is associated
 * with a logical {@link Session}, which can be either local or remote.
 * The application-specific configuration is achieved by implementing and/or
 * overriding methods (pure OO-approach). These methods are usually generated
 * by wurblets (see <a href="https://wurbelizer.org">wurbelizer.org</a>), according to a model.
 *
 * @param <P> the persistent class type
 */
@TripSerializable(validate = "clearFromThisJVM")
public abstract class AbstractDbObject<P extends AbstractDbObject<P>>
       implements Immutable, ModificationLoggable, Comparable<P> {

  /**
   * Instantiates a new db object for a given class without a session.
   *
   * @param <P> the class type
   * @param clazz the class
   * @return the object
   */
  public static <P extends AbstractDbObject<?>> P newInstance(Class<P> clazz) {
    try {
      // load the class
      return clazz.getDeclaredConstructor().newInstance();
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new PersistenceException("creating object for " + clazz + " failed", e);
    }
  }

  /**
   * Instantiates a new object for a given class and session.
   *
   * @param <P> the class type
   * @param session the session
   * @param clazz the class
   * @return the object
   */
  public static <P extends AbstractDbObject<?>> P newInstance(Session session, Class<P> clazz) {
    // load the class
    P obj = newInstance(clazz);
    obj.setSession(session);
    return obj;
  }


  /** name of ID column. */
  public static final String CN_ID = org.tentackle.common.Constants.CN_ID;

  /** name of ID attribute. */
  public static final String AN_ID = org.tentackle.common.Constants.AN_ID;

  /** name of serial column. */
  public static final String CN_SERIAL = org.tentackle.common.Constants.CN_SERIAL;

  /** name of serial attribute. */
  public static final String AN_SERIAL = org.tentackle.common.Constants.AN_SERIAL;

  /** name of tableserial column. */
  public static final String CN_TABLESERIAL = org.tentackle.common.Constants.CN_TABLESERIAL;

  /** name of tableserial attribute. */
  public static final String AN_TABLESERIAL = org.tentackle.common.Constants.AN_TABLESERIAL;

  /** name of class-ID column. */
  public static final String CN_CLASSID = org.tentackle.common.Constants.CN_CLASSID;

  /** name of class-ID attribute. */
  public static final String AN_CLASSID = org.tentackle.common.Constants.AN_CLASSID;


  /** transaction name: insert plain **/
  public static final String TX_INSERT_PLAIN                  = "insert plain";
  /** transaction name: insert object **/
  public static final String TX_INSERT_OBJECT                 = "insert object";
  /** transaction name: update plain **/
  public static final String TX_UPDATE_PLAIN                  = "update plain";
  /** transaction name: dummy update **/
  public static final String TX_DUMMY_UPDATE                  = "dummy update";
  /** transaction name: update serial **/
  public static final String TX_UPDATE_SERIAL                 = "update serial";
  /** transaction name: update serial and tableserial **/
  public static final String TX_UPDATE_SERIAL_AND_TABLESERIAL = "update serial and tableserial";
  /** transaction name: update tableserial **/
  public static final String TX_UPDATE_TABLESERIAL            = "update tableserial";
  /** transaction name: update object **/
  public static final String TX_UPDATE_OBJECT                 = "update object";
  /** transaction name: saveObject **/
  public static final String TX_SAVE                          = "save";
  /** transaction name: sync **/
  public static final String TX_SYNC                          = "sync";
  /** transaction name: delete object **/
  public static final String TX_DELETE_OBJECT                 = "delete object";
  /** transaction name: saveObject list **/
  public static final String TX_SAVE_LIST                     = "save list";
  /** transaction name: delete a list of objects **/
  public static final String TX_DELETE_LIST                   = "delete list";
  /** transaction name: delete missing in a list of objects **/
  public static final String TX_DELETE_MISSING_IN_LIST        = "delete missing in list";


  private static final Logger LOGGER = Logger.get(AbstractDbObject.class);

  @Serial
  private static final long serialVersionUID = 1L;


  // data object attributes are always private
  private long id;                                    // unique Object ID
  private long serial;                                // serial-nummer (version to detect simultaneous updates)
  private int classId;                                // the class id (overrides classvariables classid)
  private long tableSerial;                           // last table serial from countModification (only if isTableSerialProvided() == true)

  // not persistable (but not transient due to TRIP)
  private boolean modified;                           // true if the object is modified and not written to db yet

  private boolean immutable;                          // true if the object is immutable
  private boolean finallyImmutable;                   // true if the object is finally immutable
  private Level immutableLoggingLevel;                // optional logging for immutable violations instead of exception

  // transient (not transferred to/from the application server)
  private transient Db session;                       // database session (transient because it shouldn't be serialized)
  private transient boolean sessionImmutable;         // true if Db is immutable
  private SessionHolder sessionHolder;                // a redirector to retrieve the session
  private transient boolean fromThisJVM = true;       // true if the object created in this JVM, else remote (works both with JOS and TRIP)
  private transient boolean overloadable;             // true if the object can be loaded more than once from the database
  private transient volatile PropertySupport propertySupport; // for PropertyListeners


  /**
   * Creates a database object.
   *
   * @param db the session
   */
  public AbstractDbObject(Db db) {
    setSession(db);
  }

  /**
   * Creates a database object not associated with a session.<br>
   * The session must be set via {@link #setSession} in order to use it.
   */
  public AbstractDbObject() {
    // nothing to do
  }


  /**
   * Gets the default string value.<br>
   * The default implementation invokes {@link #toGenericString}.
   *
   * @return the string value of this AbstractDbObject
   */
  @Override
  public String toString() {
    return toGenericString();
  }

  /**
   * Gets the string value: "&lt;className&gt;[id/serial]".
   * <p>
   * Example: {@code "de.krake.plsbl.Product[344/2]"}
   *
   * @return the string value of this object
   */
  @Override
  public String toGenericString() {
    return  getClass().getName() +
            '[' +
            id +
            '/' +
            serial +
            ']';
  }

  /**
   * Gets the ID string: "classId:id".<br>
   * The ID string describes the PDO by its classId and object-ID.
   * <p>
   * Example: {@code "1022:1258474"}
   *
   * @return the id string
   */
  public final String toIdString() {
    return getClassId() + ":" + getId();
  }

  /**
   * Gets some attributes and variables common to all objects of the same class.
   * Class variables for classes derived from AbstractDbObject are kept in an
   * instance of {@link DbObjectClassVariables}.
   *
   * @return the class variables
   */
  public DbObjectClassVariables<P> getClassVariables() {
    throw new PersistenceException(this, "classvariables not initialized for " + getClass());
  }

  /**
   * Gets the basename of the class of this object.<br>
   * The basename is the class name without the package name.
   *
   * @return the basename of the Objects class
   */
  public String getClassBaseName ()  {
    return getClassVariables().classBaseName;
  }

  /**
   * Gets the unique class id.
   *
   * @return the class id
   */
  public int getClassId() {
    return classId != 0 ? classId : getClassVariables().classId;
  }

  /**
   * Sets the class id for this po.
   *
   * @param classId the class id, 0 to use id from class-variables
   */
  public void setClassId(int classId) {
    this.classId = classId;
  }

  /**
   * Returns whether this object has been created in this JVM.
   *
   * @return true if this JVM, else from remote JVM
   */
  public boolean isFromThisJVM() {
    return fromThisJVM;
  }

  /**
   * Invoked after TRIP deserialization.
   */
  public void clearFromThisJVM() {
    fromThisJVM = false;
  }

  /**
   * Returns whether this is a deep copy.
   *
   * @return true if this object is a copy
   */
  public boolean isCopy() {
    return false;   // implemented in AbstractPersistentObject
  }

  /**
   * Accepts a persistence operation visitor.<br>
   *
   * @param visitor the visitor
   * @param modType the modification type
   */
  public void acceptPersistenceVisitor(PersistenceVisitor visitor, ModificationType modType) {
    try {
      visitor.visit(this, new Class<?>[] {Character.TYPE}, modType);
    }
    catch (NoSuchMethodException nsm) {
      throw new PersistenceException(getSession(), "no visit method for " + getClass().getName() + " in " + visitor.getClass().getName(), nsm);
    }
  }

  /**
   * Adds a {@link PropertyListener} to the listener list.<br>
   * The listener is registered for all bound properties of this class.
   * <p>
   * Please notice that all listeners are automatically removed after setModified(false)!
   *
   * @param  listener  the property change listener to be added
   *
   * @see #setModified(boolean)
   */
  public void addPropertyListener(PropertyListener listener) {
    if (listener != null) {
      getPropertySupport().addPropertyListener(listener);
    }
  }

  /**
   * Removes a {@link PropertyListener} from the listener list.
   *
   * @param listener the PropertyChangeListener to be removed
   */
  public void removePropertyListener(PropertyListener listener) {
    if (listener != null) {
      PropertySupport localSupport = propertySupport;
      if (localSupport != null) {
        localSupport.removePropertyListener(listener);
      }
    }
  }

  /**
   * Adds a {@link PropertyListener} to the listener list for a specific
   * property.
   * <p>
   * Please notice that all listeners are automatically removed after setModified(false)!
   *
   * @param propertyName one of the property names listed above
   * @param listener the property change listener to be added
   *
   * @see #setModified(boolean)
   */
  public void addPropertyListener(String propertyName, PropertyListener listener) {
    if (listener != null) {
      getPropertySupport().addPropertyListener(propertyName, listener);
    }
  }

  /**
   * Removes a {@link PropertyListener} from the listener
   * list for a specific property. This method should be used to remove
   * <code>PropertyChangeListener</code>s
   * that were registered for a specific bound property.
   *
   * @param propertyName a valid property name
   * @param listener the PropertyChangeListener to be removed
   */
  public void removePropertyListener(String propertyName, PropertyListener listener) {
    if (listener != null) {
      PropertySupport localSupport = propertySupport;
      if (localSupport != null) {
        localSupport.removePropertyListener(propertyName, listener);
      }
    }
  }

  /**
   * Removes all {@link PropertyListener}s.
   * <p>
   * Using <code>PropertyListeners</code> implies the risk to "forget"
   * about such listeners for long living objects as it is the case in
   * desktop client applications. This method safely removes all listeners.
   * It is invoked from setModified(false).
   *
   * @see #setModified(boolean)
   */
  public void removeAllPropertyListeners() {
    propertySupport = null;
  }

  /**
   * Defines whether the object may be loaded more than once from storage.<br>
   * By default, overloading is not allowed.
   *
   * @param overloadable true if overloading is allowed
   */
  public void setOverloadable(boolean overloadable) {
    this.overloadable = overloadable;
  }

  /**
   * Gets the overloadable flag.
   *
   * @return true if overloading is allowed (default is false)
   */
  public boolean isOverloadable() {
    return overloadable;
  }

  /**
   * Returns whether instances of this class exist as database entities.
   * The default is true. An example of a non-entity object is PartialDbObject.
   *
   * @return true if entity
   */
  public boolean isEntity() {
    return true;
  }

  /**
   * Creates a new object of the same class.<br>
   * The new object belongs to the same session.
   *
   * @return the new object
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public P newInstance() {
    try {
      AbstractDbObject obj = getClass().getDeclaredConstructor().newInstance();
      obj.session = session;
      obj.sessionHolder = sessionHolder;
      return (P) obj;
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new PersistenceException(this, "creating new object failed", e);
    }
  }

  /**
   * Sets the session holder for this object.
   * <p>
   * If a holder is set, getSession() will return the session from the holder.
   *
   * @param sessionHolder the session holder
   */
  public void setSessionHolder(SessionHolder sessionHolder) {
    this.sessionHolder = sessionHolder;
  }

  /**
   * Gets the session holder.
   *
   * @return the holder, null if none
   */
  public SessionHolder getSessionHolder() {
    return sessionHolder;
  }

  /**
   * Sets the db to immutable.
   *
   * @param sessionImmutable true if db cannot be changed anymore
   */
  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  /**
   * Returns whether the db is immutable.
   *
   * @return true if immutable
   */
  @Override
  public boolean isSessionImmutable() {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      return holder.isSessionImmutable();
    }
    return sessionImmutable;
  }

  /**
   * Sets the session for this object.
   *
   * @param session the session
   */
  @Override
  public void setSession(Session session) {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      holder.setSession(session);
    }
    else {
      if (isSessionImmutable() && this.session != session) {
        throw new PersistenceException(this.session, "illegal attempt to change the immutable Db of " + this +
                                                " from " + this.session + " to " + session);
      }
      this.session = (Db) session;
    }
  }

  /**
   * Get the session for this object.
   *
   * @return the session
   */
  @Override
  public Db getSession() {
    SessionHolder holder = getSessionHolder();
    if (holder != null) {
      return (Db) holder.getSession();
    }
    return session;
  }

  /**
   * Gets the backend.
   *
   * @return the backend
   */
  public Backend getBackend() {
    return getSession().getBackend();
  }

  /**
   * Sets the unique ID of this object.
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param id the object id
   */
  public void setId (long id) {
    assertMutable();
    this.id = id;
  }

  /**
   * Gets the object ID.<br>
   * If the object is deleted (negated ID), the returned ID is still positive!
   *
   * @return the object id
   */
  @Override
  public long getId ()  {
    return id < 0 ? -id : id;
  }

  /**
   * Sets the serial number (modification count).<br>
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param serial the serial number
   */
  public void setSerial (long serial) {
    assertMutable();
    this.serial = serial;
  }

  /**
   * Gets the serial number.
   *
   * @return the serial number.
   */
  @Override
  public long getSerial ()  {
    return serial;
  }

  /**
   * Sets the table serial number (table modification count).
   * Does <em>not</em> set this object to be modified, see {@link #isModified}.
   *
   * @param tableSerial the new table serial
   */
  public void setTableSerial (long tableSerial) {
    assertMutable();
    this.tableSerial = tableSerial;
  }

  /**
   * Gets the table serial.
   *
   * @return the table serial
   */
  public long getTableSerial ()  {
    return tableSerial;
  }

  /**
   * Gets a new ID for this object.<p>
   * If the object already has an ID or is deleted (negative ID),
   * the ID will <em>not</em> change.
   */
  public void newId() {
    assertNotRemote();
    if (id == 0)  {
      assertMutable();
      id = getIdSource().nextId(getSession());
    }
  }

  /**
   * Reserves an ID.<p>
   * Reserved IDs are negative.
   * A new object with a reserved ID can be distinguished from
   * a deleted object by its serial. See also {@link #isVirgin()}.
   * If the object already has an ID or is deleted (negative ID),
   * the ID will _not_ change.
   */
  public void reserveId() {
    if (id == 0)  {
      if (getSession().isRemote())  {
        id = getRemoteDelegate().obtainReservedId();
      }
      else  {
        newId();
        id = -id;
      }
    }
  }

  /**
   * Reserves a given ID.<br>
   * It doesn't matter whether the ID is negative or positive.
   * Reserving 0 clears the reservation.
   * <p>
   * Throws a {@link PersistenceException} if the object is not new.
   *
   * @param id the ID to reserve, 0 to clear the reservation
   */
  public void reserveId(long id) {
    assertNew();
    this.id = id < 0 ? id : -id;
  }

  /**
   * Checks whether this object is already persistent in the database
   * or only residing in memory.<br>
   * If an object {@code isNew()}, it means that it can be inserted.
   * This does not mean that the object has never been stored in the db,
   * i.e., it is possible that the object just has been deleted.
   *
   * @return true if the object is not in the database, i.e., new (or deleted)
   */
  public boolean isNew() {
    // deleted objects get the id negated but keep their serial,
    // i.e., it's not enough to check only the serial
    return id <= 0;
  }

  /**
   * Checks whether the object has a valid ID.<br>
   * Please keep in mind that deleted objects keep their ID negated,
   * so it is still valid.
   *
   * @return true if the ID is not 0
   */
  public boolean isIdValid()  {
    return id != 0;
  }

  /**
   * Checks whether the object is deleted.
   *
   * @return true if the object has been deleted
   */
  public boolean isDeleted()  {
    return id < 0 && serial > 0;
  }

  /**
   * Checks whether this object ever was stored in the database.<br>
   * Virgin objects have a serial of zero.
   * Notice that an object is still virgin if it got a valid id via {@link #reserveId()}
   * but has not been saved so far.
   *
   * @return true if the object is virgin, false if it is or was stored in the database
   */
  @Override
  public boolean isVirgin() {
    return serial == 0;
  }

  /**
   * Sets the modified flag.<p>
   * For optimizations, it is possible to skip objects that have not
   * been modified. The modified-attribute is cleared whenever the
   * object is saved (inserted or updated -- NOT insertPlain and updatePlain!).
   * The application is responsible to set the modified flag!
   * This is usually done in the setter-methods of the attributes.
   * <p>
   * If invoked with {@code modified=false}, which is the case after being loaded
   * from storage or after a {@code delete/update/insert}, all property
   * change listeners will also be removed.
   *
   * @param modified is true if the object is flagged modified, false if not
   */
  public void setModified(boolean modified) {
    assertMutable();
    this.modified = modified;
    if (!modified) {
      // loaded from storage or persisted: remove property change listeners
      removeAllPropertyListeners();
    }
  }

  /**
   * Defines whether attributes of the object may be changed.
   * <p>
   * Any attempt to invoke a setter on an immutable object
   * results in a {@link PersistenceException}.
   * All generated methods check this. Notice that lazy loading
   * is still allowed.
   *
   * @param immutable true if the object is immutable
   */
  @Override
  public void setImmutable(boolean immutable) {
    if (immutable) {
      if (attributesModified()) {
        // wrapped in PersistenceException due to "this" argument
        throw new PersistenceException(this, new ImmutableException("object is already modified"));
      }
    }
    else if (isFinallyImmutable()) {
      throw new PersistenceException(this, new ImmutableException("object is finally immutable"));
    }
    this.immutable = immutable;
  }

  @Override
  public void setFinallyImmutable() {
    setImmutable(true);
    finallyImmutable = true;
  }

  /**
   * Returns whether the object is immutable.
   * <p>
   * By default, objects are mutable.
   *
   * @return true if immutable
   */
  @Override
  public boolean isImmutable() {
    return immutable;
  }

  @Override
  public boolean isFinallyImmutable() {
    return finallyImmutable;
  }

  @Override
  public void setImmutableLoggingLevel(Level immutableLoggingLevel) {
    this.immutableLoggingLevel = immutableLoggingLevel;
  }

  @Override
  public Level getImmutableLoggingLevel() {
    return immutableLoggingLevel;
  }

  /**
   * Checks if the modifications of this object are tracked.<br>
   * By default, {@code AbstractDbObjects} are <em>not</em>> tracked!
   * This is a quality measure to ensure that {@link #isModified()} returns
   * false if and only if it hasn't been modified, i.e., the setters
   * check for modification. See the wurblet DbMethods.
   *
   * @return true if tracked, false otherwise (default)
   */
  public boolean isTracked()  {
    return false;
  }

  /**
   * Determines whether the object should be written to persistent
   * storage because it has been modified.<p>
   * By definition, an object is 'modified' if the object OR ANY
   * of its components are modified. See {@code PdoRelations.wrbl} on how
   * and when to override isModified().<p>
   * New objects are "modified" by definition!<br>
   * Furthermore, isModified() will invoke the errorhandler if
   * isTracked() != true.
   *
   * @return true if the object is modified and should be saved()
   */
  public boolean isModified() {
    if (!isTracked()) {
      throw new PersistenceException(this, "isModified() invoked on untracked object");
    }
    return attributesModified() || isNew();
  }

  /**
   * Determines whether this object got some of its attributes modified.<br>
   * It does not check whether some of its components are modified!
   * This method can also be used for non-tracked entities.
   *
   * @return true if this object
   */
  public boolean attributesModified() {
    return modified;
  }

  /**
   * Returns the modified flag.<br>
   * Necessary for fulltracked PDOs only, since attributedModified will be overridden with differsPersisted
   * and setModified(true) invoked by the application would not have any effect.
   *
   * @return the modified flag
   */
  public boolean isForcedModified() {
    return modified;
  }

  /**
   * Returns whether any of the attributes differs from the values persisted in the database.<br>
   * This method is only applicable to fulltracked entities and returns false if not fulltracked.
   *
   * @return true if differs, false if unchanged or the entity isn't fulltracked
   */
  public boolean differsPersisted() {
    return false;
  }

  /**
   * Determines whether this object is allowed to be stored in DB.<br>
   * By default, all mutable objects are allowed to be saved.
   * Objects not allowed to be saved will force saveObject(), insert() and update()
   * to return 'false' and silently skipped in saveCollection().
   *
   * @return true if savable
   */
  public boolean isPersistable() {
    return !isImmutable();
  }

  /**
   * Gets a prepared statement.
   *
   * @param stmtId the statement id
   * @param resultSetType the result set type
   * @param resultSetConcurrency the result set concurrency
   * @param sqlSupplier the SQL code supplier
   * @return the statement
   */
  public PreparedStatementWrapper getPreparedStatement(
          StatementId stmtId, int resultSetType, int resultSetConcurrency, SqlSupplier sqlSupplier) {
    return getSession().getPreparedStatement(createStatementKey(stmtId), isStatementAlwaysPrepared(),
                                             resultSetType, resultSetConcurrency, sqlSupplier);
  }

  /**
   * Gets a prepared statement.<br>
   * Uses {@link java.sql.ResultSet#TYPE_FORWARD_ONLY} and {@link java.sql.ResultSet#CONCUR_READ_ONLY}.
   *
   * @param stmtId the statement id
   * @param sqlSupplier the SQL code supplier
   * @return the statement
   */
  public PreparedStatementWrapper getPreparedStatement(StatementId stmtId, SqlSupplier sqlSupplier) {
    return getSession().getPreparedStatement(createStatementKey(stmtId), isStatementAlwaysPrepared(), sqlSupplier);
  }

  /**
   * Creates a one-shot prepared statement.<br>
   *
   * @param sqlSupplier the SQL code supplier
   * @param resultSetType the result set type
   * @param resultSetConcurrency the result set concurrency
   * @return the statement
   */
  public PreparedStatementWrapper createPreparedStatement(SqlSupplier sqlSupplier, int resultSetType, int resultSetConcurrency) {
    return getSession().createPreparedStatement(sqlSupplier, resultSetType, resultSetConcurrency);
  }

  /**
   * Creates a one-shot prepared statement.<br>
   * Uses {@link java.sql.ResultSet#TYPE_FORWARD_ONLY} and {@link java.sql.ResultSet#CONCUR_READ_ONLY}.
   *
   * @param sqlSupplier the SQL code supplier
   * @return the statement
   */
  public PreparedStatementWrapper createPreparedStatement(SqlSupplier sqlSupplier) {
    return getSession().createPreparedStatement(sqlSupplier);
  }

  /**
   * Saves all composite relations that reference this object.
   *
   * @param update true if this is an update operation, else insert
   */
  public void saveReferencingRelations(boolean update) {
    // default does nothing
  }

  /**
   * Saves all composite relations referenced by this object.
   *
   * @param update true if this is an update operation, else insert
   */
  public void saveReferencedRelations(boolean update) {
    // default does nothing
  }

  /**
   * Deletes all composite relations that reference this object.
   */
  public void deleteReferencingRelations() {
    // default does nothing
  }

  /**
   * Deletes all composite relations referenced by this object.
   */
  public void deleteReferencedRelations() {
    // default does nothing
  }

  /**
   * Loads lazy references.
   * <p>
   * The method is used to load any lazy references (not composite) before an object
   * is being transferred to another tier. Some lazy references may be necessary
   * to persist the object on the other side (due to some eccentric business logic or
   * replicated environments such as PoolKeeper). If those referenced objects are
   * not available yet on the remote side (because of an inappropriately ordered stream),
   * they must be preloaded on the local side before Serialization.<br>
   * Please keep in mind that those references must not be transient!
   * <p>
   * The default implementation does nothing.
   */
  public void loadLazyReferences() {
    // default does nothing
  }

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
  public boolean isReferenced() {
    Db db = getSession();
    if (db.isRemote()) {
      if (isNew())  {
        // new objects are never referenced because they simply don't exist in the db!
        // so we can saveObject a roundtrip here
        return false;
      }
      else  {
        return getRemoteDelegate().isReferenced(id);
      }
    }
    else  {
      // local
      return getClassVariables().isReferenced(db, getId());
    }
  }

  /**
   * Checks whether this object can be removed.
   * <p>
   * It is invoked before operations that may have an impact on the
   * referential integrity.
   * The default implementation returns true if !isNew and !isReferenced.
   * Does not refer to the SecurityManager!
   * <p>
   * The application can assume a lazy context (invoked from is...Lazy)
   * if invoked outside a transaction. This is just an optimization hint.
   * <p>
   * Notice that isRemovable() for performance reasons is not covered
   * by its own delegate method in remote connections.
   * Hence, if classes in your application require a different implementation than
   * {@code (!isNew && !isReferenced)}, you must provide such a remote method.
   *
   * @return true if removable
   */
  public boolean isRemovable() {
    return !isNew() && !isReferenced();
  }

  /**
   * Determines whether modifications of this object are counted in the modification table.<br>
   * Counting is also turned on if the PDO provides a tableserial.
   *
   * @param modType the modification type
   * @return true if count modification, false if not
   */
  public boolean isCountingModification(ModificationType modType)  {
    return isTableSerialProvided();
  }

  /**
   * By default, objects don't need to include the tableSerial in the
   * database table.<br>
   * Override this method if the object contains a TABLESERIAL-column.
   *
   * @return true if the object is using the tableSerial column, false if not
   */
  public boolean isTableSerialProvided()  {
    return false;
  }

  /**
   * Determines whether each database modification of this object should be logged.
   *
   * @param modType the modification type
   * @return true if log modification, false if not
   * @see ModificationLog
   */
  public boolean isLoggingModification(ModificationType modType)  {
    return false;
  }

  /**
   * Determines whether the modification of this object should be replayed in lenient mode.
   *
   * @param modType the modification type
   * @return true if leniently
   * @see ModificationLog
   */
  public boolean isReplayedLeniently(ModificationType modType) {
    return false;
  }

  /**
   * Indicates whether some other object is "equal to" this one.<br>
   * Method is final!<br>
   * <p>
   * Objects are identical if their IDs and classes are identical.<br>
   * IDs are *NOT* necessarily unique among all database tables!
   * If this or the passed object has an ID of 0, there is no domain identity
   * and {@link Object#equals(java.lang.Object)}
   * will be invoked.
   *
   * @param object the object to test for equality
   */
  @Override
  public final boolean equals (Object object) {
    if (object != null && getClass() == object.getClass()) {
      long objectId = ((AbstractDbObject<?>) object).getId();
      if (objectId == 0 || getId() == 0) {
        // no ID -> no PDO identity -> use object address
        return super.equals(object);
      }
      return objectId == getId();
    }
    return false;
  }

  /**
   * Compare two objects.<br>
   * Comparison makes only sense between objects of the same type.
   * Method is final!<br>
   *
   * @param obj the object to compare this object with
   * @return 0 if objects are equal, &lt; 0 if this is logically less than obj, &gt; 0 if this is logically greater than obj
   */
  @Override
  public final int compareTo(P obj) {
    if (obj != null) {
      int rv = Long.compare(getId(), obj.getId());
      if (rv == 0 &&
          // should not happen, but one never knows: check the class
          getClass() != obj.getClass()) {   // != is okay here

        rv = getClass().getName().compareTo(obj.getClass().getName());
      }
      return rv;
    }
    return 1;
  }

  /**
   * The hashcode is the ID.<br>
   * Method is final!<br>
   * It is ok -- according to the contract of {@code hashCode()} -- that objects
   * in different tables with the same id may return the same hashcode.<br>
   * If the id is 0, i.e., the object has no domain-identity,
   * so {@link Object#hashCode()} is returned.
   *
   * @return  a hash code value for this object.
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public final int hashCode() {
    long identity = getId();
    return identity == 0 ?
             super.hashCode() : // no identity: use the object's address
             (int) identity;
  }

  /**
   * Get the IdSource.<br>
   *
   * @return the id source
   */
  public IdSource getIdSource() {
    return getClassVariables().getIdSource(getSession());
  }

  /**
   * Reads the values from a result-set into this object.
   *
   * @param rs is the result set (-wrapper)
   * @return the persistent object, never null
   */
  @SuppressWarnings("unchecked")
  public P readFromResultSetWrapper(ResultSetWrapper rs)  {
    assertNotOverloaded();
    Db db = getSession();
    db.setAlive(true);   // keep the (local) db alive for long-running retrievals
    try {
      getFields(rs);
      setModified(false);
      return (P) this;
    }
    catch (PersistenceException px) {
      throw px;   // no cleanup necessary: done in PersistenceException
    }
    catch (RuntimeException re) {
      if (!db.isTxRunning()) {
        // no transaction running: application cannot invoke rollback -> force cleanup
        db.forceDetached();
      }
      throw re;
    }
  }

  /**
   * Loads an object from the database by its unique ID.<p>
   * For local sessions the database values replace the current object's attributes,
   * i.e., <em>this</em> object is returned.
   * For remote connections, a copy of the object in the server is returned.
   * Hence, applications should always create a new object and invoke
   * select and don't make any further assumptions. This applies to
   * all select methods returning an object! Example:
   * <pre>
   *    Customer customer = new Customer(db).select(customerId);
   * </pre>
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  public P selectObject (long id) {
    P obj = null;
    if (id > 0) {
      Db db = getSession();
      if (db.isRemote()) {
        obj = getRemoteDelegate().selectObject(id);
        if (obj != null) {
          obj.setSession(db);
        }
      }
      else {
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectObjectStatementId,
                                                           b -> createSelectSql(b, false));
        st.setLong(1, id);
        try (ResultSetWrapper rs = st.executeQuery()) {
          if (rs.next()) {
            obj = readFromResultSetWrapper(rs);
          }
        }
      }
    }
    return obj;
  }

  /**
   * Load the object from the database with an exclusive lock (aka write lock).<br>
   * This is implemented via "SELECT FOR UPDATE".
   *
   * @param id is the object id
   *
   * @return object if loaded, null if no such object
   */
  public P selectObjectForUpdate(long id) {
    P obj = null;
    if (id > 0) {
      Db db = getSession();
      if (db.isRemote()) {
        obj = getRemoteDelegate().selectObjectForUpdate(id);
        if (obj != null) {
          obj.setSession(db);
        }
      }
      else  {
        /*
         * else local.
         *
         * Notice that some dbms don't provide a select for update. In this case,
         * the dummyUpdate()-method should be automatically used by tentackle
         * (although it's not really the same, but better than nothing).
         * We did not implement it so far, because all tentackle-supported dbms
         * provide a SELECT FOR UPDATE.
         *
         * However, in Oracle (version 8 at the time of writing) the SELECT FOR UPDATE
         * is broken under the following circumstances:
         * If an object is selected without "FOR UPDATE" and IMMEDIATELY after that
         * a transaction is started AND the same object is read with
         * SELECT FOR UPDATE within that transaction, a "fetch out of sequence"
         * ORA-01002 exception is raised.
         * Furthermore, if a transaction that contains a SELECT FOR UPDATE for a given
         * object and the same object is selected IMMEDIATELY after the commit
         * of the transaction, the JDBC connection hangs!
         * @todo: verify whether that's still the case with newer versions of Oracle
         */
        getSession().assertTxRunning();
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectForUpdateStatementId,
                                                           b -> createSelectSql(b, true));
        st.setLong(1, id);
        try (ResultSetWrapper rs = st.executeQuery()) {
          if (rs.next()) {
            obj = readFromResultSetWrapper(rs);
          }
        }
      }
    }
    return obj;
  }

  /**
   * Reloads the object.<p>
   * Note: to make sure that any lazy inits are cleared,
   * the returned object is always a new object.
   *
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  public P reloadObject() {
    return newInstance().selectObject(id);
  }

  /**
   * Reloads the object with a write-lock.
   *
   * @return the object if reloaded, else null (never <em>this</em>)
   */
  public P reloadObjectForUpdate() {
    return newInstance().selectObjectForUpdate(id);
  }

  /**
   * Selects all objects of this class and returns the ResultSetWrapper.
   *
   * @return the result set
   */
  public ResultSetWrapper resultAllObjects() {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllObjectsStatementId,
      b -> {
        StringBuilder sql = createSelectAllInnerSql(b);
        b.buildSelectSql(sql, false, 0, 0);
        return sql.toString();
      }
    );
    return st.executeQuery();
  }

  /**
   * Selects all objects of this class with a higher tableserial and returns the ResultSetWrapper.
   *
   * @param oldSerial the last known serial
   * @return the result set
   */
  public ResultSetWrapper resultObjectsWithExpiredTableSerials(long oldSerial) {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectObjectsWithExpiredTableSerialsStatementId,
      b -> {
        StringBuilder sql = createSelectObjectsWithExpiredTableSerialsSql(b);
        b.buildSelectSql(sql, false, 0, 0);
        return sql.toString();
      }
    );
    st.setLong(1, oldSerial);
    return st.executeQuery();
  }

  /**
   * Selects all id, serial-pairs of this class and returns the ResultSetWrapper.
   *
   * @return the result set
   */
  public ResultSetWrapper resultAllIdSerial() {
    getSession().assertNotRemote();
    PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectAllIdSerialStatementId,
       b -> {
        StringBuilder sql = createSelectAllIdSerialInnerSql();
        b.buildSelectSql(sql, false, 0, 0);
        return sql.toString();
      }
    );
    return st.executeQuery();
  }

  /**
   * Selects the next object from a result-set.<br>
   * AbstractApplications should close the result-set if null is returned.
   *
   * @param rs the result set
   * @return the next object, null if the end of the set is reached
   */
  public P selectNextObject(ResultSetWrapper rs)  {
    getSession().assertNotRemote();
    if (rs.next()) {
      return readFromResultSetWrapper(rs);
    }
    return null;
  }

  /**
   * Selects all objects of this class as a {@link java.util.List}.
   *
   * @return the list of objects, never null
   */
  public List<P> selectAllObjects() {
    Db db = getSession();
    if (db.isRemote()) {
      List<P> list = getRemoteDelegate().selectAllObjects();
      for (P obj : list) {
        obj.setSession(db);
      }
      return list;
    }
    else  {
      // local
      List<P> list = new ArrayList<>();
      try (ResultSetWrapper rs = resultAllObjects()) {
        P obj;
        while ((obj = newInstance().selectNextObject(rs)) != null)  {
          list.add(obj);
        }
        return list;
      }
    }
  }

  /**
   * Selects all objects with a tableSerial starting at a given serial.<br>
   * Useful to update expired objects in a batch.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @return the list of objects, never null
   */
  public List<P> selectObjectsWithExpiredTableSerials(long oldSerial) {
    Db db = getSession();
    if (db.isRemote()) {
      List<P> list = getRemoteDelegate().selectObjectsWithExpiredTableSerials(oldSerial);
      for (P obj : list) {
        obj.setSession(db);
      }
      return list;
    }
    else  {
      // local
      List<P> list = new ArrayList<>();
      try (ResultSetWrapper rs = resultObjectsWithExpiredTableSerials(oldSerial)) {
        P obj;
        while ((obj = newInstance().selectNextObject(rs)) != null)  {
          list.add(obj);
        }
        return list;
      }
    }
  }

  /**
   * Selects all id,serial-pairs of this class as a list of {@link IdSerialTuple}.
   *
   * @return the list of objects
   */
  public List<IdSerialTuple> selectAllIdSerial() {
    if (getSession().isRemote())  {
      return getRemoteDelegate().selectAllIdSerial();
    }
    else  {
      // local
      List<IdSerialTuple> list = new ArrayList<>();
      try (ResultSetWrapper rs = resultAllIdSerial()) {
        while (rs.next())  {
          IdSerialTuple idSerial = new IdSerialTuple(rs.getLong(1), rs.getLong(2));
          list.add(idSerial);
        }
        return list;
      }
    }
  }

  /**
   * Selects the serial-number for a given object id.
   *
   * @param id the object id
   * @return the serial for that id, -1 if no such object
   */
  public long selectSerial(long id) {
    if (getSession().isRemote()) {
      return getRemoteDelegate().selectSerial(id);
    }
    else  {
      // local
      PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectSerialStatementId,
                                                         _ -> createSelectSerialSql());
      st.setLong(1, id);
      try (ResultSetWrapper rs = st.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        else  {
          return -1;
        }
      }
    }
  }

  /**
   * Selects the highest id.
   *
   * @return the highest id, -1 if the table is empty
   */
  public long selectMaxId() {
    if (getSession().isRemote()) {
      return getRemoteDelegate().selectMaxId();
    }
    else  {
      // local
      PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectMaxIdStatementId,
                                                         _ -> createSelectMaxIdSql());
      try (ResultSetWrapper rs = st.executeQuery()) {
        long maxId = -1;
        if (rs.next()) {
          Long val = rs.getALong(1);
          if (val != null) {
            maxId = val;
          }
        }
        return maxId;
      }
    }
  }

  /**
   * Selects the highest table serial.
   *
   * @return the highest table serial, -1 if the table is empty
   */
  public long selectMaxTableSerial() {
    if (getSession().isRemote()) {
      return getRemoteDelegate().selectMaxTableSerial();
    }
    else  {
      // local
      PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectMaxTableSerialStatementId,
                                                         _ -> createSelectMaxTableSerialSql());
      try (ResultSetWrapper rs = st.executeQuery()) {
        long maxTableSerial = -1;
        if (rs.next()) {
          Long val = rs.getALong(1);
          if (val != null) {
            maxTableSerial = val;
          }
        }
        return maxTableSerial;
      }
    }
  }

  /**
   * Gets the number of columns for this entity class.<br>
   * The method does a "fake-select" if not known so far.
   *
   * @return the number of columns
   */
  public int getColumnCount()  {
    assertNotRemote();
    DbObjectClassVariables<P> classVariables = getClassVariables();
    if (classVariables.columnCount <= 0) {
      // run fake-select (this happens only once per class, hence no prepared statement)
      Backend backend = getBackend();
      String sql = backend.optimizeSql(Backend.SQL_SELECT + createSelectAllInnerSql(backend) + SQL_AND + "1=0");
      StatementWrapper stmt = getSession().createStatement();
      stmt.setParallelOk(true);   // don't count this as a parallel query
      try (ResultSetWrapper rs = stmt.executeQuery(sql)) {
        classVariables.columnCount = rs.getColumnCount();
      }
    }
    return classVariables.columnCount;
  }

  /**
   * Insert this object into the database without any further processing.<br>
   * No prepareSetFields, linked objects, mod counting, logging, etc...
   */
  @SuppressWarnings("unchecked")
  public void insertPlain()  {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().insertPlain((P) this);
    }
    else  {
      // local
      if (db.isPersistenceOperationAllowed(this, DbModificationType.INSERT)) {
        insertImpl(getClassVariables(), this::createInsertSql);
      }
    }
  }

  /**
   * Deletes this object from the database without any further processing.<br>
   * No linked objects, mod counting, logging, etc...
   */
  public void deletePlain()  {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().deletePlain(id, serial);
    }
    else  {
      // local
      if (db.isPersistenceOperationAllowed(this, DbModificationType.DELETE)) {
        deleteImpl(getClassVariables(), this::createDeleteSql);
      }
    }
  }

  /**
   * Updates this object to the database without any further processing.<br>
   * No prepareSetFields, linked objects, mod counting, logging, etc...
   */
  @SuppressWarnings("unchecked")
  public void updatePlain ()  {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().updatePlain((P) this);
    }
    else  {
      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        updateImpl(getClassVariables(), this::createUpdateSql);
        serial++;
      }
    }
  }

  /**
   * Performs a fake update.<br>
   * The method is provided as an alternative to {@link #reloadObjectForUpdate} or {@link #selectObjectForUpdate}
   * to lock the object during a transaction by updating the ID without changing it.
   */
  @SuppressWarnings("unchecked")
  public void dummyUpdate ()  {
    if (getSession().isRemote())  {
      getRemoteDelegate().dummyUpdate((P) this);
    }
    else  {
      // for Oracle notes see selectLocked().
      PreparedStatementWrapper st = getPreparedStatement(getClassVariables().dummyUpdateStatementId,
                                                         _ -> createDummyUpdateSql());
      st.setLong(1, id);
      assertThisRowAffected(st.executeUpdate());
    }
  }

  /**
   * Updates and increments the serial number of this object.<br>
   * The method is provided to update an object with isModified() == true
   * and 'modified' == false, i.e., an object that itself is not modified
   * but some of its components. In such a case it is not necessary
   * to update the whole object. However, it is sometimes necessary to
   * update the serial to indicate 'some modification' and to make
   * sure that this object is part of the transaction.
   * Whether it is necessary or not depends on the application.
   *
   * @see #isUpdatingSerialEvenIfNotModified
   */
  public void updateSerial ()  {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().updateSerial(id, serial);
    }
    else  {
      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateSerialStatementId,
                                                           _ -> createUpdateSerialSql());
        st.setLong(1, id);
        st.setLong(2, serial);
        assertThisRowAffected(st.executeUpdate());
      }
    }
    serial++;   // SQL statement incremented serial successfully
  }

  /**
   * Updates and sets the serial number of this object.<br>
   * Caution: this is a low-level method provided to fix the serial
   * if it is wrong for whatever reason.
   *
   * @param serial the new serial
   */
  public void updateSerial(long serial)  {
    Db db = getSession();
    if (db.isRemote())  {
      getRemoteDelegate().updateAndSetSerial(id, serial);
    }
    else  {
      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateAndSetSerialStatementId,
                                                           _ -> createUpdateAndSetSerialSql());
        st.setLong(1, serial);
        st.setLong(2, id);
        assertThisRowAffected(st.executeUpdate());
      }
    }
    this.serial = serial;
  }

  /**
   * Same as {@link #updateSerial} but updates tableSerial as well.
   * Notice: the tableSerial is NOT modified in the current object,
   * but only in the database!
   */
  public void updateSerialAndTableSerial ()  {
    Db db = getSession();
    if (db.isRemote()) {
      getRemoteDelegate().updateSerialAndTableSerial(id, serial, tableSerial);
      serial++;
    }
    else  {
      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateSerialAndTableSerialStatementId,
                                                           _ -> createUpdateSerialAndTableSerialSql());
        st.setLong(1, tableSerial);
        st.setLong(2, id);
        st.setLong(3, serial);
        assertThisRowAffected(st.executeUpdate());
        serial++;   // SQL statement incremented serial successfully
      }
    }
  }

  /**
   * Determines whether in updates of composite objects unmodified objects in the
   * update path get at least the serial updated or are not touched at all.
   * The default is to leave unmodified objects untouched.
   * However, in some applications it is necessary to update the master object if some of its children are updated (usually
   * to trigger something, for example, a cache-update).<p>
   * The default implementation returns false.
   * Override this method to change to 'true'.
   *
   * @return true if update serial even if the object is unchanged
   * @see #updateObject
   */
  public boolean isUpdatingSerialEvenIfNotModified()  {
    return false;
  }

  /**
   * Inserts this (new) object into the database.<p>
   * Note: this method does *NOT* set the ID and should be used
   * by the application with great care! Use {@link #saveObject} instead!
   */
  @SuppressWarnings("unchecked")
  public void insertObject()  {

    assertPersistable();

    Db db = getSession();
    if (db.isRemote()) {
      applyDbObjectResult(getRemoteDelegate().insertObject((P) this));
      setModified(false);
    }

    else  {

      prepareSetFields();
      alignComponents();

      if (db.isPersistenceOperationAllowed(this, DbModificationType.INSERT)) {

        long oldId = id;            // remember old id and serial
        long oldSerial = serial;

        long txVoucher = db.begin(TX_INSERT_OBJECT);

        try {

          initModification(DbModificationType.INSERT);

          if (id < 0) {
            id = -id;             // could have been "deleted" before
          }
          serial++;

          saveReferencedRelations(false);
          insertImpl(getClassVariables(), this::createInsertSql);
          saveReferencingRelations(false);
          finishModification(DbModificationType.INSERT);
          db.commit(txVoucher);
          setModified(false);
        }
        catch (RuntimeException ex) {
          // The application has thrown an exception.
          // If tx was begun, the tx will be rolled back, and all pending
          // statements (marked ready) will be consumed.
          // If no rollback, the exception must be caught elsewhere in the path
          // until a valid rollback can be performed.
          db.rollback(txVoucher);  // rollback if tx was begun
          serial = oldSerial;
          id = oldId;
          if (ex instanceof PersistenceException) {
            ((PersistenceException) ex).updateDbObject(this);
            throw ex;
          }
          throw new PersistenceException(this, ex);
        }
      }
    }
  }

  /**
   * Updates this object to the database.<br>
   * The modified attribute gets cleared if the insert was successful.
   * Note: this method should be used by the application with great care!
   * Use {@link #saveObject} instead!
   */
  @SuppressWarnings("unchecked")
  public void updateObject()  {

    assertPersistable();

    Db db = getSession();
    if (db.isRemote()) {
      applyDbObjectResult(getRemoteDelegate().updateObject((P) this));
      setModified(false);
    }

    else  {

      prepareSetFields();

      if (db.isPersistenceOperationAllowed(this, DbModificationType.UPDATE)) {

        long oldId = id;            // remember old id and serial
        long oldSerial = serial;

        long txVoucher = db.begin(TX_UPDATE_OBJECT);  // start transaction

        try {
          boolean updated = true; // cleared to false if this object was NOT updated, only components

          if (!isUpdateNecessary()) {   // DON'T USE isModified() here because its generated/overridden
            // the object itself is not modified, but some of its components
            if (isUpdatingSerialEvenIfNotModified()) {
              initModification(DbModificationType.UPDATE);
              // DON'T USE updateSerial() here! We need the old serial unchanged
              PreparedStatementWrapper st = getPreparedStatement(getClassVariables().updateSerialStatementId,
                                                                 _ -> isTableSerialProvided() ?
                                                                      createUpdateSerialAndTableSerialSql() :
                                                                      createUpdateSerialSql());
              saveReferencedRelations(true);
              // id can't be negative cause if isNew() above
              int ndx = 0;
              if (isTableSerialProvided()) {
                st.setLong(++ndx, tableSerial);
              }
              st.setLong(++ndx, id);
              st.setLong(++ndx, serial);
              assertThisRowAffected(st.executeUpdate());
              saveReferencingRelations(true);
              finishModification(DbModificationType.UPDATE);
            }
            else {
              // saveObject linked objects without updating the serial of this object
              saveReferencedRelations(true);
              saveReferencingRelations(true);
              finishNotUpdated(DbModificationType.UPDATE);
              updated = false;    // this object was not updated! -> no modlog as well, no serial++!
            }
          }
          else {
            // normal update
            initModification(DbModificationType.UPDATE);

            if (id < 0) {
              id = -id; // was deleted: reuse ID
            }

            saveReferencedRelations(true);
            updateImpl(getClassVariables(), this::createUpdateSql);
            saveReferencingRelations(true);
            finishModification(DbModificationType.UPDATE);
          }

          db.commit(txVoucher);

          if (updated) {
            serial++; // serial is already incremented in the SQL statement!
          }

          setModified(false);   // clear modified flag
        }
        catch (RuntimeException ex) {
          // The application has thrown an exception.
          // If tx was begun, the tx will be rolled back, and all pending
          // statements (marked ready) will be consumed.
          // If no rollback, the exception must be caught elsewhere in the path
          // until a valid rollback can be performed.
          db.rollback(txVoucher);  // rollback if tx was begun
          id = oldId;
          serial = oldSerial;
          if (ex instanceof PersistenceException) {
            ((PersistenceException) ex).updateDbObject(this);
            throw ex;
          }
          throw new PersistenceException(this, ex);
        }
      }
    }
  }

  /**
   * Prepares this object for saveObject.<br>
   * The method is invoked at the client-side only, i.e., 3-tier or 2-tier,
   * but never at the server-side in 3-tier mode.<br>
   * Applications may override the method to perform any modifications to the object
   * before it is sent to the server (instead of {@link #prepareSetFields()} which is
   * invoked at the JDBC side only).
   * <p>
   * Notice that the method is invoked outside the save-transaction.
   *
   * @see #prepareSetFields()
   */
  public void prepareSave() {
  }

  /**
   * Clears some references to reduce bandwidth.<br>
   * Invoked for remote objects only.
   */
  public void clearOnRemoteSave() {
    // default does nothing
  }

  /**
   * Saves this object.<br>
   * This is the standard method applications should use to insert or update
   * objects.<br>
   * If the ID is 0, a new ID is obtained and this object inserted.
   * Otherwise, this object is updated.
   * The modified attribute gets cleared if save() was successful.
   */
  @SuppressWarnings("unchecked")
  public void saveObject ()  {
    Db db = getSession();

    if (db.isRemote()) {
      clearOnRemoteSave();
      prepareSave();
      applyDbObjectResult(getRemoteDelegate().saveObject((P) this));
      setModified(false);
    }
    else  {
      if (isFromThisJVM()) {
        prepareSave();
      }

      long oldId = id;    // saveObject in case of error
      long txVoucher = db.begin(TX_SAVE);   // getting the ID should be used in transaction with insert()

      try {
        if (id <= 0) {
          // the object is new or contains a reserved ID (e.g., is deleted)
          if (id == 0) {
            // object is new: get a new ID
            newId();
          }
          // if id has been reserved, it will be made positive in insert or update
          // don't use insertObject() bec. insert() OR insertObject()
          insertObject();
        }
        else {
          // object already exists in the database
          updateObject();
        }
        db.commit(txVoucher);
      }
      catch (RuntimeException ex) {
        db.rollback(txVoucher);
        id = oldId; // set back old ID in case it was changed
        if (ex instanceof PersistenceException) {
          ((PersistenceException) ex).updateDbObject(this);
          throw ex;
        }
        throw new PersistenceException(this, ex);
      }
    }
  }

  /**
   * Persists this object and returns it.
   *
   * @return the persisted object, null if saveObject failed
   */
  @SuppressWarnings("unchecked")
  public P persistObject() {
    Db db = getSession();

    if (db.isRemote()) {
      clearOnRemoteSave();
      prepareSave();
      P obj = getRemoteDelegate().persistObject((P) this);
      if (obj != null) {
        obj.setSession(db);
      }
      return obj;     // obj != this !!!
    }

    saveObject();
    return (P) this;
  }

  /**
   * Prepares this object for delete.<br>
   * The method is invoked at the client-side only, i.e., but never at the server-side in 3-tier mode.
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
   * Notice that the method is invoked outside the transaction and that it
   * is not invoked if the object is new or not savable.
   *
   * @see #deleteObject()
   * @see #isNew()
   * @see #isPersistable()
   */
  public void prepareDelete() {
  }

  /**
   * Removes this object from the database.<br>
   * This includes all components, if any.<br>
   * A removed object will also get the modified attribute set by definition,
   * because it {@link #isNew} again.<br>
   * It is also verified that the object {@link #isPersistable} and is not new.
   *
   * @throws NotFoundException if the object is new or not savable or not in the database
   */
  @SuppressWarnings("unchecked")
  public void deleteObject()  {

    assertNotNew();
    assertPersistable();

    Db db = getSession();
    if (db.isRemote()) {
      prepareDelete();
      applyDbObjectResult(getRemoteDelegate().deleteObject((P) this));
      setModified(false);
    }
    else {
      if (isFromThisJVM()) {
        prepareDelete();
      }
      if (db.isPersistenceOperationAllowed(this, DbModificationType.DELETE)) {

        long txVoucher = db.begin(TX_DELETE_OBJECT);

        try {
          initModification(DbModificationType.DELETE);
          deleteReferencingRelations();
          deleteImpl(getClassVariables(), this::createDeleteSql);
          deleteReferencedRelations();
          finishModification(DbModificationType.DELETE);
          db.commit(txVoucher);
          id = -id;     // make ID reserved again, i.e., mark the object as being deleted
          setModified(false);
        }
        catch (RuntimeException ex) {
          // The application has thrown an exception.
          // If tx was begun, the tx will be rolled back, and all pending
          // statements (marked ready) will be consumed.
          // If no rollback, the exception must be caught elsewhere in the path
          // until a valid rollback can be performed.
          db.rollback(txVoucher);  // rollback if tx was begun
          if (ex instanceof PersistenceException) {
            ((PersistenceException) ex).updateDbObject(this);
            throw ex;
          }
          throw new PersistenceException(this, ex);
        }
      }
    }
  }

  /**
   * Marks an object to be deleted.<br>
   * This is done by negating its id.
   * If the object is already marked deleted, the method does nothing.
   * Must be overridden if the object is composite, i.e.,
   * all its components must be markDeleted as well.
   * Note: an object with a negative ID is always {@link #isModified}.
   *
   * @see #unmarkDeleted()
   */
  public void markDeleted() {
    // getId() always returns >= 0!
    setId(-getId());
  }

  /**
   * Removes the deleted mark.
   *
   * @see #markDeleted()
   */
  public void unmarkDeleted() {
    setId(getId());
  }

  /**
   * Counts a modification for the class of this object.
   *
   * @return the table serial,
   *          -1 if isCountModificationAllowed() == false
   */
  public long countModification ()  {
    Db db = getSession();
    if (db.isRemote()) {
      return getRemoteDelegate().countModification();
    }
    else {
      return db.isCountModificationAllowed() ?
               ModificationTracker.getInstance().countModification(db, getTableName()) : -1;
    }
  }

  /**
   * Selects the current modification counter for the class of this object.
   *
   * @return the modification counter
   */
  public long getModificationCount () {
    return ModificationTracker.getInstance().getSerial(getTableName());
  }

  /**
   * Determines the objects with a tableSerial starting at a given serial.<br>
   * Useful to clean up the caches, for example.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @return pairs of longs, the first being the ID, the second the tableserial, never null
   */
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) {
    if (getSession().isRemote()) {
      return getRemoteDelegate().selectExpiredTableSerials(oldSerial);
    }
    else {
      // local
      PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectExpiredTableSerials1StatementId,
                                                         _ -> createSelectExpiredTableSerials1Sql());
      st.setLong(1, oldSerial);
      try (ResultSetWrapper rs = st.executeQuery()) {
        List<IdSerialTuple> expireList = new ArrayList<>();
        while (rs.next()) {
          expireList.add(new IdSerialTuple(rs.getLong(1), rs.getLong(2)));
        }
        return expireList;
      }
    }
  }

  /**
   * Determines the objects with their tableSerial within a given range.
   * Useful to clean up caches.
   *
   * @param oldSerial non-inclusive lower bound for tableSerial (&gt; oldSerial)
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial)
   * @return list of id/tableserial tuples, sorted by tableserial + id, never null
   */
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) {
    if (maxSerial > oldSerial) {
      if (getSession().isRemote()) {
        return getRemoteDelegate().selectExpiredTableSerials(oldSerial, maxSerial);
      }
      else {
        // local
        PreparedStatementWrapper st = getPreparedStatement(getClassVariables().selectExpiredTableSerials2StatementId,
                                                           _ -> createSelectExpiredTableSerials2Sql());
        st.setLong(1, oldSerial);
        st.setLong(2, maxSerial);
        List<IdSerialTuple> expireList = new ArrayList<>();
        try (ResultSetWrapper rs = st.executeQuery()) {
          while (rs.next()) {
            expireList.add(new IdSerialTuple(rs.getLong(1), rs.getLong(2)));
          }
          return expireList;
        }
      }
    }
    else {
      return new ArrayList<>();
    }
  }

  /**
   * Gets the expirations for a given range of tableserials.<br>
   * The expirations are returned as a list of {@link IdSerialTuple}s with
   * the serial being the tableserial of the corresponding PDO.<br>
   * A negative tableserial indicates a deletion. Tuples with an id of 0
   * indicate a rolled back insert or update or an object that was updated more than once.
   * <p>
   * A physical database query is only done if the requested range is not yet cached in the
   * {@link TableSerialExpirationBacklog}.
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
   * @param maxSerial inclusive upper bound for tableSerial (&le; maxSerial)
   * @return the id/tableserial tuples sorted by abs(tableserial), never null
   * @see ModificationTracker#isLocalClientMode()
   */
  public Collection<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) {
    Collection<IdSerialTuple> exp;
    if (maxSerial > oldSerial) {
      if (getSession().isRemote()) {
        // remote apps always use the backlog
        TreeSet<IdSerialTuple> expSet = getClassVariables().expirationBacklog.getExpiration(oldSerial, maxSerial);
        if (expSet == null || expSet.isEmpty() || Math.abs(expSet.last().getId()) < maxSerial) {
          // no backlog info or backlog info is outdated (or there is a gap...)
          exp = getRemoteDelegate().getExpiredTableSerials(oldSerial, maxSerial);
          // exp is always != null and already contains the merged history from the local server
          getClassVariables().expirationBacklog.addExpiration(oldSerial, maxSerial, exp, false);
          // Adding to the expiration backlog is only necessary for remote servers.
          // In remote clients it's used only once by the PdoCache to determine the updates.
          // However, this feature may also be used for application-specific purposes.
        }
        else {
          exp = expSet;
        }
      }
      else {
        boolean useBacklog = !ModificationTracker.getInstance().isLocalClientMode();
        TreeSet<IdSerialTuple> expSet = useBacklog ? getClassVariables().expirationBacklog.getExpiration(oldSerial, maxSerial) : null;
        if (expSet == null || expSet.isEmpty() || Math.abs(expSet.last().getId()) < maxSerial) {
          // no backlog info or backlog info is outdated (or there is a gap...)
          exp = selectExpiredTableSerials(oldSerial, maxSerial);
          if (useBacklog) {
            // merge expired table serials with table serial history
            getClassVariables().expirationBacklog.addExpiration(oldSerial, maxSerial, exp, true);
            expSet = getClassVariables().expirationBacklog.getExpiration(oldSerial, maxSerial);
            if (expSet != null) {
              exp = expSet;
            }
          }
        }
        else {
          exp = expSet;
        }
      }
    }
    else {
      exp = new ArrayList<>();
    }
    return exp;
  }

  /**
   * Logs a modification for this object to the modlog
   * (not to be mixed up with the modification-counter!)
   *
   * @param modType the modification type
   */
  public void logModification(ModificationType modType)  {
    Db db = getSession();
    db.assertNotRemote();   // don't log locally if db is remote
    if (db.isLogModificationAllowed()) {
      db.logBeginTx();      // optionally create the BEGIN-log
      createModificationLog(modType).saveObject();
    }
  }

  /**
   * Gets the delegate for remote connections.<br>
   * Each class has its own delegate.
   *
   * @return the delegate for this object
   */
  public AbstractDbObjectRemoteDelegate<P> getRemoteDelegate()  {
    return getClassVariables().getRemoteDelegate(getSession());
  }

  /**
   * Determines whether prepared statements of this class should always
   * be prepared each time when the statement used.
   * @return true if always prepare
   */
  public boolean isStatementAlwaysPrepared() {
    return getClassVariables().alwaysPrepare;
  }

  /**
   * Sets the always prepare flag.
   *
   * @param alwaysPrepare true if always prepare
   */
  public void setStatementAlwaysPrepared(boolean alwaysPrepare) {
    getClassVariables().alwaysPrepare = alwaysPrepare;
  }

  /**
   * Gets the database table name for the class of this object.
   * @return the table name
   */
  public String getTableName() {
    return getClassVariables().tableName;
  }

  /**
   * Retrieves the values of all fields.<br>
   *
   * @param rs the result set
   */
  public void getFields(ResultSetWrapper rs) {
    // default does nothing
  }

  /**
   * Prepares the object's attributes before the object is saved to the database.<br>
   * The default implementation does nothing.
   * Used to set up, check, and align values.
   * <p>
   * Notice that this method is invoked on the JDBC side only, i.e., local sessions,
   * not in remote clients.
   *
   * @see #prepareSave()
   */
  public void prepareSetFields()  {
  }

  /**
   * Sets the values of all fields (all columns of the database table)
   * in the given {@link PreparedStatementWrapper} from the object's attributes.
   *
   * @param st the statement
   * @return the number of fields set
   */
  public int setFields (PreparedStatementWrapper st) {
    return 0;   // no fields processed so far
  }

  /**
   * Creates the inner SQL text to select all fields.
   * <p>
   * Returns something like:
   * <pre>
   *  "* FROM xytable WHERE 1=1"
   * </pre>
   *
   * @param backend the backend
   * @return the sql text
   */
  public StringBuilder createSelectAllInnerSql(Backend backend) {
    StringBuilder sql = new StringBuilder();
    sql.append(SQL_ALLSTAR);
    sql.append(SQL_FROM);
    sql.append(getTableName());
    sql.append(SQL_WHEREALL);
    return sql;
  }

  /**
   * Creates the inner SQL text to select the id and serial fields.
   * <p>
   * Returns something like:
   * <pre>
   *  "id,serial FROM xytable WHERE 1=1"
   * </pre>
   *
   * @return the sql text
   */
  public StringBuilder createSelectAllIdSerialInnerSql() {
    StringBuilder sql = new StringBuilder();
    sql.append(CN_ID);
    sql.append(SQL_COMMA);
    sql.append(CN_SERIAL);
    sql.append(SQL_FROM);
    sql.append(getTableName());
    sql.append(SQL_WHEREALL);
    return sql;
  }

  /**
   * Creates the inner SQL text to select all fields by ID.
   * <p>
   * Returns something like:
   * <pre>
   *  "* FROM xytable WHERE id=?"
   * </pre>
   *
   * @param backend the backend
   * @return the sql text
   */
  public StringBuilder createSelectAllByIdInnerSql(Backend backend) {
    StringBuilder sql = createSelectAllInnerSql(backend);
    sql.append(SQL_AND);
    sql.append(CN_ID);
    sql.append(SQL_EQUAL_PAR);
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
   * @return the sql text
   */
  public StringBuilder createSelectIdInnerSql() {
    StringBuilder sql = new StringBuilder();
    sql.append(CN_ID);
    sql.append(SQL_FROM);
    sql.append(getTableName());
    sql.append(SQL_WHEREALL);
    return sql;
  }

  /**
   * Creates the SQL text to delete objects.
   * <p>
   * Returns something like:
   * <pre>
   *  "DELETE FROM xytable WHERE 1=1"
   * </pre>
   *
   * @return the sql text
   */
  public StringBuilder createDeleteAllSql() {
    StringBuilder sql = new StringBuilder(SQL_DELETE_FROM);
    sql.append(getTableName());
    sql.append(SQL_WHEREALL);
    return sql;
  }

  /**
   * Creates the SQL intro text to update objects.
   * <p>
   * Returns something like:
   * <pre>
   *  "UPDATE xytable SET "
   * </pre>
   *
   * @return the sql text
   */
  public StringBuilder createSqlUpdate() {
    StringBuilder sql = new StringBuilder(SQL_UPDATE);
    sql.append(getTableName());
    sql.append(SQL_SET);
    return sql;
  }

  /**
   * Creates the SQL code for the update statement.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createUpdateSql(Backend backend) {
    throw new PersistenceException(this, "method createUpdateSql not implemented in " + getClass());
  }

  /**
   * Creates the SQL code for the insert statement.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createInsertSql(Backend backend) {
    throw new PersistenceException(this, "method createInsertSql not implemented in " + getClass());
  }

  /**
   * Creates the SQL code for the select by id statement.
   *
   * @param backend the backend
   * @param locked true if select locked (FOR UPDATE)
   * @return the SQL code
   */
  public String createSelectSql(Backend backend, boolean locked) {
    StringBuilder sql = createSelectAllByIdInnerSql(backend);
    backend.buildSelectSql(sql, locked, 0, 0);
    return sql.toString();
  }

  /**
   * Creates the SQL code for the selectSerial statement.
   *
   * @return the SQL code
   */
  public String createSelectSerialSql() {
    return SQL_SELECT + CN_SERIAL + SQL_FROM + getTableName() + SQL_WHERE + CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the selectMaxId statement.
   *
   * @return the SQL code
   */
  public String createSelectMaxIdSql() {
    return SQL_SELECT + "MAX(" + CN_ID + ")" + SQL_FROM + getTableName();
  }

  /**
   * Creates the SQL code for the selectMaxTableSerial statement.
   *
   * @return the SQL code
   */
  public String createSelectMaxTableSerialSql() {
    return SQL_SELECT + "MAX(" + CN_TABLESERIAL + ")" + SQL_FROM + getTableName();
  }

  /**
   * Creates the SQL code for the delete statement.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public String createDeleteSql(Backend backend) {
    return SQL_DELETE_FROM + getTableName() + SQL_WHERE + CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the fake update statement.<br>
   * Useful to get an exclusive lock within a transaction.
   *
   * @return the SQL code
   */
  public String createDummyUpdateSql() {
    return SQL_UPDATE + getTableName() + SQL_SET + CN_ID + SQL_EQUAL + CN_ID + SQL_WHERE + CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the serial update statement.
   *
   * @return the SQL code
   */
  public String createUpdateSerialSql() {
    return SQL_UPDATE + getTableName() + SQL_SET + CN_SERIAL + "=" + CN_SERIAL + "+1" + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the serial set and update statement.
   *
   * @return the SQL code
   */
  public String createUpdateAndSetSerialSql() {
    return SQL_UPDATE + getTableName() + SQL_SET + CN_SERIAL + SQL_EQUAL_PAR + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the serial + tableSerial update statement.
   *
   * @return the SQL code
   */
  public String createUpdateSerialAndTableSerialSql() {
    return SQL_UPDATE + getTableName() + SQL_SET + CN_SERIAL + "=" + CN_SERIAL + "+1, " +
           CN_TABLESERIAL + SQL_EQUAL_PAR + SQL_WHERE +
           CN_ID + SQL_EQUAL_PAR + SQL_AND + CN_SERIAL + SQL_EQUAL_PAR;
  }

  /**
   * Creates the SQL code for the first statement to select expired table serials.
   *
   * @return the SQL code
   */
  public String createSelectExpiredTableSerials1Sql() {
    // sort by tableserial+id to return pairs in deterministic order for same tableserials
    return SQL_SELECT + CN_ID + SQL_COMMA + CN_TABLESERIAL + SQL_FROM + getTableName() + SQL_WHERE +
           CN_TABLESERIAL + ">?" + SQL_ORDERBY + CN_TABLESERIAL + SQL_COMMA + CN_ID;
  }

  /**
   * Creates the SQL code for the second statement to select expired table serials.
   *
   * @return the SQL code
   */
  public String createSelectExpiredTableSerials2Sql() {
    // sort by tableserial+id to return pairs in deterministic order for same tableserials
    return SQL_SELECT + CN_ID + SQL_COMMA + CN_TABLESERIAL + SQL_FROM + getTableName() + SQL_WHERE +
           CN_TABLESERIAL + ">?" + SQL_AND + CN_TABLESERIAL + "<=?" +
           SQL_ORDERBY + CN_TABLESERIAL + SQL_COMMA + CN_ID;
  }

  /**
   * Creates the SQL code for the first statement to select expired table serials.
   *
   * @param backend the backend
   * @return the SQL code
   */
  public StringBuilder createSelectObjectsWithExpiredTableSerialsSql(Backend backend) {
    StringBuilder sql = createSelectAllInnerSql(backend);
    sql.append(SQL_AND).
        append(CN_TABLESERIAL).append(">?").
        append(SQL_ORDERBY).append(CN_TABLESERIAL).append(SQL_COMMA).append(CN_ID);
    return sql;
  }


  /**
   * Insert implementation.
   * <p>
   * Should not be used by applications. Will be overridden for multi-inheritance.
   *
   * @param classVariables the classvariables
   * @param sqlSupplier the SQL code supplier
   */
  protected void insertImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getPreparedStatement(classVariables.insertStatementId, sqlSupplier);
    setFields(st);
    assertThisRowAffected(st.executeUpdate());
  }

  /**
   * Delete implementation.
   * <p>
   * Should not be used by applications. Will be overridden for multi-inheritance.
   *
   * @param classVariables the classvariables
   * @param sqlSupplier the SQL code supplier
   */
  protected void deleteImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getPreparedStatement(classVariables.deleteStatementId, sqlSupplier);
    st.setLong(1, id);
    st.setLong(2, serial);
    assertThisRowAffected(st.executeUpdate());
  }

  /**
   * Update implementation.
   * <p>
   * Should not be used by applications. Will be overridden for multi-inheritance.
   *
   * @param classVariables the classvariables
   * @param sqlSupplier the SQL code supplier
   */
  protected void updateImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
    PreparedStatementWrapper st = getPreparedStatement(classVariables.updateStatementId, sqlSupplier);
    setFields(st);
    assertThisRowAffected(st.executeUpdate());
  }

  /**
   * Creates a statement key for the corresponding persistence class from a statement id.
   *
   * @param stmtId the statement id
   * @return the statement key
   */
  protected StatementKey createStatementKey(StatementId stmtId) {
    return new StatementKey(stmtId, getClass());
  }

  /**
   * Copies all attributes from a snapshot object back to this object.
   * <p>
   * There is no createAttributesInSnapshot, since the snapshot is cloned and all attributes are either immutable or frozen.
   *
   * @param snapshot the snapshot object
   */
  @SuppressWarnings("rawtypes")
  protected void revertAttributesToSnapshot(AbstractDbObject snapshot) {
    // id, serial, tableSerial are not reverted because they can only be changed by a persistence operation
    if (!isCopy()) {    // copies are always mutable and new
      modified = snapshot.modified;
      immutable = snapshot.immutable;
      finallyImmutable = snapshot.finallyImmutable;
      overloadable = snapshot.overloadable;
      propertySupport = snapshot.propertySupport;
      sessionImmutable = snapshot.sessionImmutable;
    }
    session = snapshot.session;
    sessionHolder = snapshot.sessionHolder;
  }

  /**
   * Does any preprocessing before delete, insert, or update.
   *
   * @param modType the modification type
   */
  protected void initModification(ModificationType modType) {
    if (isCountingModification(modType))  {
      setTableSerial(countModification());
      if (modType == DbModificationType.DELETE) {
        ModificationTracker.getInstance().addDeletion(getSession(), getTableName(), getId(), getTableSerial());
      }
    }
  }

  /**
   * Does any postprocessing after the object has been updated, inserted, or deleted.
   *
   * @param modType the modification type
   */
  protected void finishModification(ModificationType modType) {
    if (isLoggingModification(modType))  {
      logModification(modType);
    }
  }

  /**
   * Update postprocessing for objects not being updated.<br>
   * Necessary, because not modified for some reason, e.g., only components modified.<br>
   * The default implementation does nothing.
   *
   * @param modType the modification type
   */
  protected void finishNotUpdated(ModificationType modType) {
  }

  /**
   * Prepares the attributes concerning composite relations.<br>
   * Overridden, for example, to update list counts.
   */
  protected void alignComponents() {
    // default does nothing
  }

  /**
   * Returns whether the update of this object is necessary.
   *
   * @return true if update necessary
   */
  protected boolean isUpdateNecessary() {
    alignComponents();
    return !isTracked() || attributesModified() || isNew();
  }

  /**
   * Support for reporting bound property changes for Object properties.
   * This method can be called when a bound property has changed, and it will
   * send the appropriate PropertyChangeEvent to any registered
   * PropertyChangeListeners.
   *
   * @param propertyName the property whose value has changed
   * @param oldValue the property's previous value
   * @param newValue the property's new value
   */
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    PropertySupport localSupport = propertySupport;
    if (localSupport != null) {
      localSupport.firePropertyChanged(propertyName, oldValue, newValue);
    }
  }

  /**
   * Creates a property support object for this persistent object.
   *
   * @return the support object
   */
  protected PropertySupport createPropertySupport() {
    return new PropertySupport(this);
  }

  /**
   * Gets the property support.<br>
   * Creates it atomically if it doesn't exist.
   *
   * @return the property support, never null
   */
  protected PropertySupport getPropertySupport() {
    PropertySupport localSupport = propertySupport;
    if (localSupport == null) {
      synchronized (this) {
        localSupport = propertySupport;
        if (localSupport == null) {
          localSupport = propertySupport = createPropertySupport();
        }
      }
    }
    return localSupport;
  }

  /**
   * Applies a {@link DbObjectResult} to this persistence object.
   *
   * @param result the remote result
   */
  protected void applyDbObjectResult(DbObjectResult result) {
    id = result.id();
    serial = result.serial();
    tableSerial = result.tableSerial();
  }

  /**
   * asserts that this object does not belong to a remote session.
   */
  protected void assertNotRemote() {
    if (getSession().isRemote()) {
      throw new PersistenceException(this, "operation not allowed for objects belonging to a remote session");
    }
  }

  /**
   * asserts that this object belongs to a remote session.
   */
  protected void assertRemote() {
    if (!getSession().isRemote()) {
      throw new PersistenceException(this, "operation only allowed for objects belonging to a remote session");
    }
  }

  /**
   * Checks the correct number of rows affected.
   * <p>
   * Use whenever an executeUpdate is not related to this object.
   *
   * @param count the effective number of rows affected
   * @param expected the expected number of rows affected
   *
   * @throws NotFoundException if count &lt; 1
   */
  protected void assertNumberOfRowsAffected(int count, int expected) {
    if (count != expected) {
      String message = "unexpected number of rows affected: " + count + ", expected: " + expected;
      if (count < 1) {
        throw new NotFoundException(getSession(), message);
      }
      throw new PersistenceException(getSession(), message);
    }
  }

  /**
   * Checks that exactly one row is affected.
   * <p>
   * Use whenever an executeUpdate is related to this object.
   *
   * @param count the effective number of rows affected
   */
  protected void assertThisRowAffected(int count) {
    if (count != 1) {
      if (count < 1) {
        String message = "no rows affected";
        long persistedSerial = 0;
        boolean temporary = false;
        if (!isVirgin()) {
          // read the serial to figure out whether the object exists in the database
          persistedSerial = selectSerial(getId());
          if (persistedSerial > 0) {
            // object exists, but probably with the wrong serial (depends on the statement)
            message += " (persisted serial=" + persistedSerial + ")";
            temporary = true;   // optimistic locking failure? may disappear if the whole tx is retried (see @Transaction)
          }
          else  {
            message += " (object with id=" + id + " doesn't exist in table " + getTableName() + ")";
          }
        }
        PersistenceException nfx = new NotFoundException(this, message, persistedSerial);
        nfx.setTemporary(temporary);
        throw nfx;
      }
      else {
        throw new PersistenceException(this, "more than one row affected: " + count);
      }
    }
  }

  /**
   * Asserts that this object is savable.
   */
  protected void assertPersistable() {
    if (!isPersistable()) {
      throw new PersistenceException(
              this, (isImmutable() ? "immutable " : "") + "object is not persistable");
    }
  }

  /**
   * Asserts that the object is not new.
   */
  protected void assertNotNew() {
    if (isNew()) {
      throw new PersistenceException(this, id == 0 ? "object is new" : "object is deleted");
    }
  }

  /**
   * Asserts that the object is new.
   */
  protected void assertNew() {
    if (!isNew()) {
      throw new PersistenceException(this, "object is not new");
    }
  }

  /**
   * Asserts that this object is not overloaded.<br>
   */
  protected void assertNotOverloaded() {
    if (!isOverloadable() && !isVirgin()) {
      // overloading disabled and the object is not virgin:
      throw new PersistenceException(this, "object is already loaded");
    }
  }

  /**
   * Asserts that the object is mutable.
   */
  protected void assertMutable() {
    if (isImmutable()) {
      PersistenceException ex = new PersistenceException(this, new ImmutableException("object is immutable"));
      if (immutableLoggingLevel == null) {
        throw ex;
      }
      LOGGER.log(immutableLoggingLevel, ex.getMessage(), ex);
    }
  }

}
