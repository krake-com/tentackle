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

import java.util.Collection;
import java.util.List;
import org.tentackle.persist.AbstractDbObject;
import org.tentackle.persist.Db;
import org.tentackle.persist.DbObjectClassVariables;
import org.tentackle.persist.StatementId;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.pdo.SecurityPersistenceImpl;



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

  /** the declared name of the static class variables. */
  public static final String CLASSVARIABLES_NAME = "CLASSVARIABLES";


  /**
   * The associated PDO class (i.e. the interface)
   */
  public final Class<T> pdoClass;

  /**
   * The optional eager join.
   */
  public final List<Join<T,?>> eagerJoins;

  /**
   * the pdo base-classname.
   */
  public final String pdoClassBaseName;

  /**
   * The table alias to be used in joined selects.
   */
  public String tableAlias;

  /**
   * The classvariables of the parent class.<br>
   * null if not inherited.
   */
  public final PersistentObjectClassVariables<? super T, ? super P> superClassVariables;

  /**
   * The classvariables of the topmost parent class.<br>
   * "this" if not inherited.
   */
  public PersistentObjectClassVariables<? super T, ? super P> topSuperClassVariables;

  /**
   * Flag is true to check the security rules for each access to objects of this class.
   * The rules will be checked in local db access only (i.e. the check will be applied
   * already at the RMI-side *before* the data is leaving the client!)
   * Because this check can be somewhat time consuming, it's turned off by default.
   * The flag cannot be changed at runtime anymore!
   */
  public boolean checkSecurity;



  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormText}.
   */
  public final StatementId selectByNormTextStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAllInContext}.
   */
  public final StatementId selectAllStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectByNormTextAsCursor}.
   */
  public final StatementId selectByNormTextCursorStatementId = new StatementId();

  /**
   * prepared statement ID for {@link AbstractPersistentObject#selectAllInContextCursor}.
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


  /**
   * The effective tablename.
   */
  private String effectiveTableName;

  /**
   * The effective table alias.
   */
  private String effectiveTableAlias;


  /**
   * Constructs a classvariable.<p>
   * Notice: the superPoClass is necessary only in SINGLE and MULTI table inheritance configurations.
   *
   * @param pdoClass the PDO's class (i.e. interface)
   * @param poClass the class of the persistence implementation
   * @param tableAlias the table alias to be used in joined selects
   * @param superClassVariables the class variables of the superclass, null if not inherited
   * @param eagerJoins the optional eager joins, null of empty if none
   */
  public PersistentObjectClassVariables(Class<T> pdoClass, Class<P> poClass, String tableAlias,
                                        PersistentObjectClassVariables<? super T, ? super P> superClassVariables,
                                        List<Join<T,?>> eagerJoins)  {
    super(poClass,
          PdoUtilities.getInstance().determineClassId(pdoClass),
          PdoUtilities.getInstance().determineTablename(pdoClass));
    this.pdoClass = pdoClass;
    this.tableAlias = tableAlias;
    this.superClassVariables = superClassVariables;
    this.eagerJoins = eagerJoins;
    pdoClassBaseName = ReflectionHelper.getClassBaseName(this.pdoClass);
    loadMoreProperties();
  }

  /**
   * Constructs a classvariable.
   *
   * @param pdoClass the PDO's class (i.e. interface)
   * @param poClass the class of the persistence implementation
   * @param alias the table alias to be used in joined selects
   */
  public PersistentObjectClassVariables(Class<T> pdoClass, Class<P> poClass, String alias)  {
    this(pdoClass, poClass, alias, null, null);
  }

  /**
   * Gets the eager joins.
   *
   * @return the join, null or empty if none
   */
  public List<Join<T, ?>> getEagerJoins() {
    return eagerJoins;
  }


  /**
   * Returns the tablename for this classvariable.
   * <p>
   * If the class has no tablename, it is derived from its superclasses.
   *
   * @return the tablename, null if class does not map to any database table
   */
  public String getTableName () {
    if (effectiveTableName == null) {
      effectiveTableName = tableName;
      if (effectiveTableName == null) {
        PersistentObjectClassVariables<? super T, ? super P> superCV = superClassVariables;
        while (superCV != null) {
          effectiveTableName = superCV.tableName;
          if (effectiveTableName != null) {
            break;
          }
          superCV = superCV.superClassVariables;
        }
      }
    }
    return effectiveTableName;
  }


  /**
   * Gets the table alias.
   * <p>
   * If the class has no tablename, it is derived from its superclasses.
   *
   * @return the alias, null if class does not map to any database table
   */
  public String getTableAlias () {
    if (effectiveTableAlias == null) {
      effectiveTableAlias = tableAlias;
      if (effectiveTableAlias == null) {
        PersistentObjectClassVariables<? super T, ? super P> superCV = superClassVariables;
        while (superCV != null) {
          effectiveTableAlias = superCV.tableAlias;
          if (effectiveTableAlias != null) {
            break;
          }
          superCV = superCV.superClassVariables;
        }
      }
    }
    return effectiveTableAlias;
  }


  /**
   * Gets the full column name with optional table alias.
   *
   * @param name the short name
   * @return the full name
   */
  public String getColumnName(String name) {
    StringBuilder buf = new StringBuilder();
    String alias = getTableAlias();
    if (alias != null) {
      buf.append(alias).append('.');
    }
    buf.append(name);
    return buf.toString();
  }

  /**
   * Returns the topmost classvariables.<br>
   * Useful for multi-inheritance.
   *
   * @return the topmost variables, this if not inherited, never null
   */
  public PersistentObjectClassVariables<? super T, ? super P> getTopSuperClassVariables() {
    if (topSuperClassVariables == null) {
      topSuperClassVariables = this;
      while (topSuperClassVariables.superClassVariables != null) {
        topSuperClassVariables = topSuperClassVariables.superClassVariables;
      }
    }
    return topSuperClassVariables;
  }


  /**
   * Loads more properties.<br>
   * Invoked from constructor.
   */
  protected void loadMoreProperties() {
    String prop = getProperty(PROPERTY_CHECKSECURITY);
    if (prop != null) {
      checkSecurity = !(prop.equals("no") || prop.equals("off") || prop.equals("false") ||
                        prop.equals("disabled") || prop.equals("0"));
    }
    else  {
      checkSecurity = true;
    }
  }


  @Override
  public boolean isReferenced(Db db, long id) {
    PersistentObjectClassVariables<? super T, ? super P> cv = this;
    boolean referenced = false;
    while (!referenced && cv != null) {
      referenced = cv.isReferencedImpl(db, id, cv.foreignReferences);
      cv = cv.superClassVariables;
    }
    return referenced;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(pdoClass.getName());
    buf.append("/").append(super.toString());
    if (!checkSecurity) {
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
   * @param context the current domain context, null = all
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
   * Check the write security for this class.
   * The implementation checks that the class rules will accept
   * and that no object rule denies.
   *
   * @param context the current domain context, null = all
   * @return false if operation (delete, update or insert) is denied.
   */
  public boolean isWriteAllowed(DomainContext context) {
    return checkClassPermission(context, SecurityFactory.getInstance().getWritePermission());
  }

  /**
   * Check the write security for this class in all contexts.
   *
   * @return false if operation (delete, update or insert) is denied.
   */
  public boolean isWriteAllowed() {
    return checkClassPermission(null, SecurityFactory.getInstance().getWritePermission());
  }


  /**
   * Check the read security for a single object.
   * <p>
   * This is a low-level method.
   * Only root entities are checked.
   * Components must check their root-entity!
   *
   * @param object the object to check the security rules for.
   *
   * @return false if operation (select) is denied.
   */
  public boolean isReadAllowed(AbstractPersistentObject<?,?> object) {
    return !checkSecurity ||
           !object.isRootEntity() ||
           object.isPermissionAccepted(SecurityFactory.getInstance().getReadPermission());
  }

  /**
   * Check the write security for a single object.
   * <p>
   * This is a low-level method.
   * Only root entities are checked.
   * Components must check their root-entity!
   *
   * @param object the object to check the security rules for.
   *
   * @return false if operation is denied
   */
  public boolean isWriteAllowed(AbstractPersistentObject<?,?> object) {
    return !checkSecurity ||
           !object.isRootEntity() ||
           object.isPermissionAccepted(SecurityFactory.getInstance().getWritePermission());
  }

  /**
   * Check the view security for a single object.
   * <p>
   * This is a low-level method.
   * Only root entities are checked.
   * Components must check their root-entity!
   *
   * @param object the object to check the security rules for.
   *
   * @return false if operation is denied
   */
  public boolean isViewAllowed(AbstractPersistentObject<?,?> object) {
    return !checkSecurity ||
           !object.isRootEntity() ||
           object.isPermissionAccepted(SecurityFactory.getInstance().getViewPermission());
  }

  /**
   * Check the edit security for a single object.
   * <p>
   * This is a low-level method.
   * Only root entities are checked.
   * Components must check their root-entity!
   *
   * @param object the object to check the security rules for.
   *
   * @return false if operation is denied
   */
  public boolean isEditAllowed(AbstractPersistentObject<?,?> object) {
    return !checkSecurity ||
           !object.isRootEntity() ||
           object.isPermissionAccepted(SecurityFactory.getInstance().getEditPermission());
  }


  /**
   * Checks the read security for a collection of objects of this class.<br>
   * The returned collection is of the same type as the original collection.
   * Notice that this method is provided for applications that circumvent
   * readFromResultsetWrapper() somehow (which checks the read permission).
   * Tentackle is not using this method.
   * <p>
   * Notice that {@link SecurityPersistenceImpl} objects are always readable!
   *
   * @param <T> AbstractPersistentObject class
   * @param <C> Collection class
   * @param objects the collection to check the security rules for.
   *
   * @return the objects with permission accepted.
   */
  @SuppressWarnings("unchecked")
  public <T extends AbstractPersistentObject<?,?>, C extends Collection<T>> C isReadAllowed(C objects) {
    if (checkSecurity && objects != null && !clazz.isAssignableFrom(SecurityPersistenceImpl.class))  {
      try {
        C newObjects = (C)objects.getClass().newInstance();
        for (T object: objects) {
          if (object.isPermissionAccepted(SecurityFactory.getInstance().getReadPermission())) {
            newObjects.add(object);
          }
        }
        objects = newObjects;
      }
      catch (Exception ex) {
        throw new PersistenceException("can't instantiate collection for checking read security rules", ex);
      }
    }
    return objects;
  }


  /**
   * Checks the write security for a collection of objects of this class.<br>
   * The returned collection is of the same type as the original collection.
   * Notice that this method is provided for applications that circumvent
   * deleteObject, updateObject or insertObject somehow (all of them check the write permission).
   * Tentackle is not using this method.
   * Furthermore, special care must be taken for "deleteBy..." methods that
   * delete objects by an sql-statement via "DELETE ...".
   *
   * @param <T> AbstractPersistentObject class
   * @param <C> Collection class
   * @param objects the collection to check the security rules for.
   *
   * @return the objects with permission accepted.
   */
  @SuppressWarnings("unchecked")
  public <T extends AbstractPersistentObject<?,?>, C extends Collection<T>> C isWriteAllowed(C objects) {
    if (checkSecurity && objects != null)  {
      try {
        C newObjects = (C)objects.getClass().newInstance();
        for (T object: objects) {
          if (object.isPermissionAccepted(SecurityFactory.getInstance().getWritePermission())) {
            newObjects.add(object);
          }
        }
        objects = newObjects;
      }
      catch (Exception ex) {
        throw new PersistenceException("can't instantiate collection for checking write security rules", ex);
      }
    }
    return objects;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to determine the priority according to the presence of a cache:
   * <ol>
   *  <li>high: class provides a preloading cache (low volume data, usually master data)</li>
   *  <li>medium: class provides a non-preloading cache (medium volume data)</li>
   *  <li>low: class provides no cache (high volume, usually transaction data)</li>
   * </ol>
   * Notice that the cache must be named "cache". This is the case for wurblet generated caches.
   */
  @Override
  protected int determineReferencePriority(Class<? extends AbstractDbObject<?>> clazz) {
    @SuppressWarnings("unchecked")
    PdoCache<T> cache = Pdo.getPdoCache((Class<T>) clazz);
    if (cache != null) {
      return cache.isPreloading() ? 1 : 2;
    }
    return 3;   // no cache: check these last!
  }


  /**
   * Check security for this class.
   */
  protected boolean checkClassPermission(DomainContext context, Permission privilege) {
    try {
      return !checkSecurity ||
             SecurityFactory.getInstance().getSecurityManager().evaluate(context, privilege, classId, 0).isAccepted();
    }
    catch (Exception ex) {
      // either context not set or security manager or whatever...
      return false;
    }
  }

}
