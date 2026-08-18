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

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbObjectClassVariables;
import org.tentackle.dbms.StatementId;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PdoMethodCache;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.security.SecurityPersistenceImpl;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.session.SessionUtilities;
import org.tentackle.sql.Backend;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends {@link DbObjectClassVariables} for {@link AbstractPersistentObject}s.
 *
 * @param <T> the PDO class type
 * @param <P> the persistence class type
 * @author harald
 */
public class PersistentObjectClassVariables<T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>
       extends DbObjectClassVariables<P> {


  /** property key for the check security. */
  public static final String PROPERTY_CHECKSECURITY = "checksecurity";


  /**
   * Creates a PDO class-variable.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined select statements
   * @param superClassVariables the class variables of the superclass, null if not inherited
   * @param eagerJoins the optional eager joins, null of empty if none
   * @param <T> the PDO class type
   * @param <P> the persistence class type
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static <T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>> PersistentObjectClassVariables<T,P> create(
      Class<T> pdoClass, Class<P> poClass, String tableAlias,
      PersistentObjectClassVariables<? super T, ? super P> superClassVariables,
      List<Join<T,?>> eagerJoins)  {

    return PersistentClassVariablesFactory.getInstance().pdoCv(pdoClass, poClass, tableAlias,
                                                               (PersistentObjectClassVariables) superClassVariables,
                                                               eagerJoins);
  }

  /**
   * Creates a PDO class variable.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined select statements
   * @param <T> the PDO class type
   * @param <P> the persistence class type
   */
  public static <T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>  PersistentObjectClassVariables<T,P> create(
      Class<T> pdoClass, Class<P> poClass, String tableAlias)  {
    return PersistentClassVariablesFactory.getInstance().pdoCv(pdoClass, poClass, tableAlias);
  }


  /**
   * The associated PDO class (i.e., the interface)
   */
  public final Class<T> pdoClass;

  /**
   * The PDO method invocation cache.
   */
  public final PdoMethodCache<T> methodCache;

  /**
   * The optional eager join as defined in the model.
   */
  public final List<Join<T,?>> eagerJoins;

  /**
   * the pdo base-classname.
   */
  public final String pdoClassBaseName;

  /**
   * The table alias to be used in joined select statements.
   */
  public final String tableAlias;

  /**
   * The class variables of the parent class.<br>
   * null if not inherited.
   */
  public final PersistentObjectClassVariables<? super T, ? super P> superClassVariables;

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormText}.
   */
  public final StatementId selectByNormTextStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormText}.
   */
  public final StatementId selectByInvertedNormTextStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormTextAsCursor}.
   */
  public final StatementId selectByNormTextCursorStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormTextAsCursor}.
   */
  public final StatementId selectByInvertedNormTextCursorStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAll}.
   */
  public final StatementId selectAllStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAny}.
   */
  public final StatementId selectAnyStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAllWithExpiredTableSerials(long)}.
   */
  public final StatementId selectAllWithExpiredTableSerialsStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAllWithExpiredTableSerials(long, long)}.
   */
  public final StatementId selectAllWithExpiredTableSerialsStatement2Id = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectLatest(long, int)}.
   */
  public final StatementId selectLatestStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAllAsCursor()}.
   */
  public final StatementId selectAllCursorStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#updateTokenLock}.
   */
  public final StatementId updateTokenLockStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#updateTokenLockOnly}.
   */
  public final StatementId updateTokenLockOnlyStatementId = new StatementId();

  /**
   * prepared statement ID for select in {@link AbstractPersistentObject#updateTokenLock}.
   */
  public final StatementId selectTokenLockStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#transferTokenLock}.
   */
  public final StatementId transferTokenLockStatementId = new StatementId();


  /*
   * The fields below are lazily determined on first use, from arbitrary threads: class variables are
   * static and thus shared by every thread working with the entity.
   *
   * They are all volatile, because a lazily computed value and its "pending" flag are two separate
   * writes: without volatile they may be reordered or stay invisible, and a reader seeing
   * "pending == false" could still read the value as null. Since null is a legitimate result for
   * most of them (an entity may map to no table and have no eager joins), such a reader cannot even
   * tell the difference and would silently work with a wrong value.
   *
   * Writing the value *before* clearing the pending flag is therefore essential in every getter
   * below. The computations themselves are pure and idempotent, so two threads racing to compute
   * the same value is harmless.
   */

  /**
   * The class variables of the topmost parent class.<br>
   * "this" if not inherited.
   */
  private volatile PersistentObjectClassVariables<? super T, ? super P> topSuperClassVariables;
  private volatile boolean topSuperClassVariablesPending = true;

  /**
   * The effective tablename.
   */
  private volatile String effectiveTableName;
  private volatile boolean effectiveTableNamePending = true;

  /**
   * The effective table alias.
   */
  private volatile String effectiveTableAlias;
  private volatile boolean effectiveTableAliasPending = true;

  /**
   * The effective eager joins.
   */
  private volatile List<Join<? super T,?>> effectiveEagerJoins;
  private volatile boolean effectiveEagerJoinsPending = true;

  /**
   * Flag is true to check the security rules for each access to objects of this class.<br>
   * See isSecurityEnabled() below.
   */
  private volatile boolean securityEnabled;
  private volatile boolean securityEnabledPending = true;


  /*
   * Cached snapshot methods (determined only once per persistence class).
   * Volatile as well: findSnapshotMethod() calls setAccessible(true) on the method before it is
   * published here, and that mutation must be visible to every thread that picks the method up.
   */
  private volatile Method revertAttributesToSnapshotMethod;
  private volatile Method createComponentsInSnapshotMethod;
  private volatile Method revertComponentsToSnapshotMethod;


  /*
   * The two SQL fragments below are lazily built as well, but need no volatile: they are immutable
   * Strings published by a single write, so a racing thread either reads null and rebuilds the
   * identical string, or reads a reference to a fully constructed one.
   */

  /**
   * Columns for single table inheritance select-clause.
   */
  private String sqlColumnNames;

  /**
   * SQL string {@code " AND xx.classId IN (...)"} for single table inheritance.
   */
  private String sqlClassIdCondition;

  /**
   * Initializes the foreign references once if not yet done.<br>
   * null if initialized or no initialization needed.
   */
  private Runnable foreignReferencesInitializer;

  /**
   * Constructs a class variable.<p>
   * Notice: the superPoClass is necessary only in SINGLE and MULTI table inheritance configurations.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined select statements
   * @param superClassVariables the class variables of the superclass, null if not inherited
   * @param eagerJoins the optional eager joins, null of empty if none
   */
  public PersistentObjectClassVariables(Class<T> pdoClass, Class<P> poClass, String tableAlias,
                                        PersistentObjectClassVariables<? super T, ? super P> superClassVariables,
                                        List<Join<T,?>> eagerJoins)  {
    super(poClass,
          SessionUtilities.getInstance().determineClassId(pdoClass),
          SessionUtilities.getInstance().determineTablename(pdoClass));

    this.pdoClass = pdoClass;
    this.methodCache = new PdoMethodCache<>(pdoClass);
    this.tableAlias = tableAlias;
    this.superClassVariables = superClassVariables;
    this.eagerJoins = eagerJoins;

    pdoClassBaseName = pdoClass.getSimpleName();
  }

  /**
   * Constructs a class variable.
   *
   * @param pdoClass the PDO's class (i.e., interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined select statements
   */
  public PersistentObjectClassVariables(Class<T> pdoClass, Class<P> poClass, String tableAlias)  {
    this(pdoClass, poClass, tableAlias, null, null);
  }


  /**
   * Gets the comma-separated list of column names with table alias.
   *
   * @param columnPrefix the optional column name prefix (used for embedded PDOs)
   * @param columnNames the list of column names
   * @return the partial SQL string
   */
  public String getColumnNames(String columnPrefix, List<String> columnNames) {
    if (sqlColumnNames == null) {
      StringBuilder buf = new StringBuilder();
      for (String name : columnNames) {
        if (!buf.isEmpty()) {
          buf.append(',');
        }
        buf.append(getColumnName(columnPrefix, name));
      }
      sqlColumnNames = buf.toString();
    }
    return sqlColumnNames;
  }

  /**
   * Gets the comma-separated list of column names with table alias.
   *
   * @param columnNames the list of column names
   * @return the partial SQL string
   */
  public String getColumnNames(List<String> columnNames) {
    return getColumnNames(null, columnNames);
  }


  /**
   * Gets the SQL string used in WHERE-clauses for single table inheritance entities.
   *
   * @param classIds the valid class IDs, null or empty if none
   * @return the SQL string, empty if no such condition necessary
   */
  public String getSqlClassIdCondition(List<Integer> classIds) {
    if (sqlClassIdCondition == null) {
      // tableName=null for all sub-entities within single-table inheritance hierarchies.
      // multi-table inherited entities provide their own tablename.
      sqlClassIdCondition = tableName == null ? createSqlClassIdCondition(classIds, true) : "";
    }
    return sqlClassIdCondition;
  }


  /**
   * Creates the SQL string used in WHERE-clauses for inherited entities.
   *
   * @param classIds the valid class IDs, null or empty if none
   * @param withAlias prepend classID column name with table alias
   * @return the SQL string, empty if no such condition necessary
   */
  public String createSqlClassIdCondition(List<Integer> classIds, boolean withAlias) {
    StringBuilder buf = new StringBuilder();
    if (superClassVariables != null && classIds != null && !classIds.isEmpty()) {
      buf.append(Backend.SQL_AND);
      if (withAlias) {
        buf.append(getColumnName(AbstractPersistentObject.CN_CLASSID));
      }
      else {
        buf.append(AbstractPersistentObject.CN_CLASSID);
      }
      if (classIds.size() == 1) {
        buf.append('=');
      }
      else {
        buf.append(" IN(");
      }
      boolean needComma = false;
      for (int classId : classIds) {
        if (needComma) {
          buf.append(',');
        }
        else {
          needComma = true;
        }
        buf.append(classId);
      }
      if (classIds.size() > 1) {
        buf.append(')');
      }
    }
    return buf.toString();
  }


  /**
   * Gets the effective eager joins.
   *
   * @return the joins, null for none
   */
  public List<Join<? super T, ?>> getEagerJoins() {
    if (effectiveEagerJoinsPending) {
      PersistentObjectClassVariables<? super T, ? super P> cv = this;
      List<Join<? super T,?>> egList = null;
      do {
        if (cv.eagerJoins != null && !cv.eagerJoins.isEmpty()) {
          if (egList == null) {
            egList = new ArrayList<>();
          }
          egList.addAll(cv.eagerJoins);
        }
        cv = cv.superClassVariables;
      }
      while (cv != null);
      effectiveEagerJoins = egList;         // value first,
      effectiveEagerJoinsPending = false;   // ... then the flag (see the fields)
    }
    return effectiveEagerJoins;
  }


  /**
   * Returns the tablename for this class variable.
   * <p>
   * If the class has no tablename, it is derived from its superclasses.
   *
   * @return the tablename, null if the class does not map to any database table
   */
  public String getTableName () {
    if (effectiveTableNamePending) {
      String name = tableName;
      if (name == null) {
        PersistentObjectClassVariables<? super T, ? super P> superCV = superClassVariables;
        while (superCV != null) {
          name = superCV.tableName;
          if (name != null) {
            break;
          }
          superCV = superCV.superClassVariables;
        }
      }
      effectiveTableName = name;          // value first,
      effectiveTableNamePending = false;  // ... then the flag (see the fields)
    }
    return effectiveTableName;
  }


  /**
   * Gets the table alias.
   * <p>
   * If the class has no tablename, it is derived from its superclasses.
   *
   * @return the alias, null if the class does not map to any database table
   */
  public String getTableAlias () {
    if (effectiveTableAliasPending) {
      String alias = tableAlias;
      if (alias == null) {
        PersistentObjectClassVariables<? super T, ? super P> superCV = superClassVariables;
        while (superCV != null) {
          alias = superCV.tableAlias;
          if (alias != null) {
            break;
          }
          superCV = superCV.superClassVariables;
        }
      }
      effectiveTableAlias = alias;         // value first,
      effectiveTableAliasPending = false;  // ... then the flag (see the fields)
    }
    return effectiveTableAlias;
  }


  /**
   * Gets the full column name with the optional table alias.
   *
   * @param name the short name
   * @return the full name
   */
  public String getColumnName(String name) {
    return getColumnName(null, name);
  }

  /**
   * Gets the full column name with optional table alias and prefix.
   *
   * @param prefix the column prefix for embedded entities, null for none
   * @param name the short name
   * @return the full name
   */
  public String getColumnName(String prefix, String name) {
    StringBuilder buf = new StringBuilder();
    String alias = getTableAlias();
    if (alias != null) {
      buf.append(alias).append('.');
    }
    if (prefix != null) {
      buf.append(prefix);
    }
    buf.append(name);
    return buf.toString();
  }

  /**
   * Returns the topmost class variables.<br>
   * Useful for multi-inheritance.
   *
   * @return the topmost variables, this if not inherited, never null
   */
  public PersistentObjectClassVariables<? super T, ? super P> getTopSuperClassVariables() {
    if (topSuperClassVariablesPending) {
      PersistentObjectClassVariables<? super T, ? super P> variables = this;
      while (variables.superClassVariables != null) {
        variables = variables.superClassVariables;
      }
      topSuperClassVariables = variables;      // value first,
      topSuperClassVariablesPending = false;   // ... then the flag (see the fields)
    }
    return topSuperClassVariables;
  }


  public boolean isSecurityEnabled() {
    if (securityEnabledPending) {
      String prop = getProperty(PROPERTY_CHECKSECURITY);
      securityEnabled = prop == null ||                             // value first,
                        !(prop.equals("no") || prop.equals("off") || prop.equals("false") ||
                          prop.equals("disabled") || prop.equals("0"));
      securityEnabledPending = false;                               // ... then the flag (see the fields)
    }
    return securityEnabled;
  }


  @Override
  protected Class<?> determineImplementingClassOfReturnType(Class<?> clazz) {
    if (clazz.isInterface() && PersistentDomainObject.class.isAssignableFrom(clazz)) {
      return PdoFactory.getInstance().getPersistenceClass(clazz.getName());
    }
    return super.determineImplementingClassOfReturnType(clazz);
  }


  /**
   * Sets the initializer for the foreign references.<br>
   * The initializer is a runnable that invokes {@link #addReferencingClass(Class, String)} for each foreign reference.
   * It is invoked once from {@link #isReferenced(Db, long)} and usually points to a
   * static method in the persistence implementation of the referenced class.
   *
   * @param initializer the initializer to be executed once
   */
  public void setForeignReferencesInitializer(Runnable initializer) {
    synchronized(foreignReferences) {
      foreignReferencesInitializer = initializer;
    }
  }

  @Override
  public boolean isReferenced(Db db, long id) {
    PersistentObjectClassVariables<? super T, ? super P> cv = this;
    boolean referenced = false;
    while (!referenced && cv != null) {
      synchronized (cv.foreignReferences) {
        if (cv.foreignReferencesInitializer != null) {
          cv.foreignReferencesInitializer.run();
          cv.foreignReferencesInitializer = null;
        }
      }
      // iterate a snapshot: isReferencedImpl() queries the database per reference (see getForeignReferences)
      referenced = cv.isReferencedImpl(db, id, cv.getForeignReferences());
      cv = cv.superClassVariables;
    }
    return referenced;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(pdoClass.getName());
    buf.append("/").append(super.toString());
    if (!isSecurityEnabled()) {
      buf.append("/*NOSECURITY*");
    }
    return buf.toString();
  }

  /**
   * Returns whether the pdo class is abstract.
   *
   * @return true if abstract
   */
  public boolean isAbstract() {
    return classId == 0;
  }


  /**
   * Check the read security for this class.
   * The implementation checks that the class rules will accept
   * and that no object rule denies.
   * <p>
   * Notice that {@link SecurityPersistenceImpl} objects are always readable!
   *
   * @param context the domain context, null = all
   * @return false if operation (select) is denied.
   */
  public boolean isReadAllowed(DomainContext context) {
    return clazz.isAssignableFrom(SecurityPersistenceImpl.class) ||
            checkClassPermission(context, SecurityFactory.getInstance().getReadPermission());
  }

  /**
   * Check the read security for this class in all contexts.
   *
   * @return false if operation (select) is denied.
   */
  public boolean isReadAllowed() {
    return clazz.isAssignableFrom(SecurityPersistenceImpl.class) ||
            checkClassPermission(null, SecurityFactory.getInstance().getReadPermission());
  }



  /**
   * Check the write-security for this class.
   * The implementation checks that the class rules will accept
   * and that no object rule denies.
   *
   * @param context the domain context, null = all
   * @return false if operation (delete, update, or insert) is denied.
   */
  public boolean isWriteAllowed(DomainContext context) {
    return checkClassPermission(context, SecurityFactory.getInstance().getWritePermission());
  }

  /**
   * Check the write-security for this class in all contexts.
   *
   * @return false if operation (delete, update, or insert) is denied.
   */
  public boolean isWriteAllowed() {
    return checkClassPermission(null, SecurityFactory.getInstance().getWritePermission());
  }


  /**
   * Check the read permission for a PDO.
   * <p>
   * Components are readable if their root-entity is readable.
   *
   * @param object the object to check the security rules for.
   *
   * @return false if operation (select) is denied.
   */
  public boolean isReadAllowed(AbstractPersistentObject<?,?> object) {
    if (isSecurityEnabled()) {
      Permission readPermission = SecurityFactory.getInstance().getReadPermission();
      if (object.isRootEntity()) {
        return object.isPermissionAccepted(readPermission);
      }
      if (object.getRootClassId() > 0 && object.getRootId() > 0) {  // if root info provided
        return SecurityFactory.getInstance().getSecurityManager().evaluate(
                  object.getBaseContext(), readPermission, object.getRootClassId(), object.getRootId()).isAccepted();
      }
    }
    return true;
  }

  /**
   * Check the write-permission for a PDO.
   * <p>
   * Only root-entities are writable.
   * Components are not writable by default (because only the root can be persisted).
   * <p>
   * Notice that the persistence layer will not invoke isWriteAllowed on components
   * because the root is already checked when saving or deleting a PDO. Remember that
   * the application can persist only root entities!
   *
   * @param object the object to check the security rules for.
   *
   * @return false if the operation is denied
   */
  public boolean isWriteAllowed(AbstractPersistentObject<?,?> object) {
    return !isSecurityEnabled() ||
           (object.isRootEntity() &&
            object.isPermissionAccepted(SecurityFactory.getInstance().getWritePermission()));
  }

  /**
   * Check the view permission for a PDO.
   * <p>
   * Components are viewable if their root-entity is viewable.
   *
   * @param object the object to check the security rules for.
   *
   * @return false if the operation is denied
   */
  public boolean isViewAllowed(AbstractPersistentObject<?,?> object) {
    if (isSecurityEnabled()) {
      Permission viewPermission = SecurityFactory.getInstance().getViewPermission();
      if (object.isRootEntity()) {
        return object.isPermissionAccepted(viewPermission);
      }
      if (object.getRootClassId() > 0 && object.getRootId() > 0) {  // if root info provided
        return SecurityFactory.getInstance().getSecurityManager().evaluate(
                  object.getBaseContext(), viewPermission, object.getRootClassId(), object.getRootId()).isAccepted();
      }
    }
    return true;
  }

  /**
   * Check the edit permission for a PDO.
   * <p>
   * Only root-entities are editable.
   * Components are not editable by default (because only the root can be persisted).
   *
   * @param object the object to check the security rules for.
   *
   * @return false if the operation is denied
   */
  public boolean isEditAllowed(AbstractPersistentObject<?,?> object) {
    return !isSecurityEnabled() ||
           (object.isRootEntity() &&
            object.isPermissionAccepted(SecurityFactory.getInstance().getEditPermission()));
  }


  /**
   * Gets the method revertAttributesToSnapshot of the persistence object.
   *
   * @return the method
   * @throws NoSuchMethodException if not found
   */
  public Method getRevertAttributesToSnapshotMethod() throws NoSuchMethodException {
    if (revertAttributesToSnapshotMethod == null) {
      revertAttributesToSnapshotMethod = findSnapshotMethod("revertAttributesToSnapshot", clazz);
    }
    return revertAttributesToSnapshotMethod;
  }

  /**
   * Gets the method createComponentsInSnapshot of the persistence object.
   *
   * @return the method
   * @throws NoSuchMethodException if not found
   */
  public Method getCreateComponentsInSnapshotMethod() throws NoSuchMethodException {
    if (createComponentsInSnapshotMethod == null) {
      createComponentsInSnapshotMethod = findSnapshotMethod("createComponentsInSnapshot", clazz);
    }
    return createComponentsInSnapshotMethod;
  }

  /**
   * Gets the method revertComponentsToSnapshot of the persistence object.
   *
   * @return the method
   * @throws NoSuchMethodException if not found
   */
  public Method getRevertComponentsToSnapshotMethod() throws NoSuchMethodException {
    if (revertComponentsToSnapshotMethod == null) {
      revertComponentsToSnapshotMethod = findSnapshotMethod("revertComponentsToSnapshot", clazz);
    }
    return revertComponentsToSnapshotMethod;
  }


  /**
   * Finds a snapshot method.<br>
   * Snapshot methods
   * {@link AbstractPersistentObject#createComponentsInSnapshot(AbstractPersistentObject)},
   * {@link AbstractPersistentObject#revertAttributesToSnapshot(AbstractPersistentObject)} and
   * {@link AbstractPersistentObject#revertComponentsToSnapshot(AbstractPersistentObject)}) take the snapshot
   * object with the correct implementation type and are implemented at some implementation class.
   * This method finds the most specific snapshot-method along the inheritance hierarchy.
   * <p>
   * There is no createAttributesInSnapshot, since the snapshot is cloned and all attributes are either immutable or frozen.
   *
   * @param methodName the snapshot method name
   * @param clazz the implementation class
   * @return the method
   * @throws NoSuchMethodException if no such method found
   */
  protected Method findSnapshotMethod(String methodName, Class<?> clazz) throws NoSuchMethodException {
    while (clazz != null) {
      try {
        Method method = clazz.getDeclaredMethod(methodName, clazz);
        method.setAccessible(true);
        return method;
      }
      catch (NoSuchMethodException ex) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchMethodException(methodName);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to determine the priority according to the presence of a cache:
   * <ol>
   *  <li>high: class provides a preloading cache (low-volume data, usually master data)</li>
   *  <li>medium: class provides a non-preloading cache (medium volume data)</li>
   *  <li>low: class provides no cache (high volume; usually transaction data)</li>
   * </ol>
   */
  @Override
  protected int determineReferencePriority(Class<? extends AbstractDbObject<?>> clazz) {
    @SuppressWarnings("unchecked")
    PdoCache<T> cache = Pdo.getCache((Class<T>) clazz);
    if (cache != null) {
      return cache.isPreloading() ? 1 : 2;
    }
    return 3;   // no cache: check these last!
  }


  /**
   * Check security for this class.
   *
   * @param context the domain context
   * @param permission the permission
   * @return true if accepted
   */
  protected boolean checkClassPermission(DomainContext context, Permission permission) {
    try {
      return !isSecurityEnabled() ||
             SecurityFactory.getInstance().getSecurityManager().evaluate(context, permission, classId, 0).isAccepted();
    }
    catch (RuntimeException ex) {
      // either context not set or security manager or whatever...
      return false;
    }
  }

}
