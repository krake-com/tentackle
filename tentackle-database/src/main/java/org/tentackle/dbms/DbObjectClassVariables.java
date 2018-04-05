/*
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



package org.tentackle.dbms;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.tentackle.common.FileHelper;
import org.tentackle.dbms.rmi.AbstractDbObjectRemoteDelegate;
import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.PersistenceException;

/**
 * Holds static class variables for classes derived from DbObject.
 * This is a "singleton per class".
 * All classvariables register to a static {@link Map} that can be queried by apps
 * by tablename.
 *
 * @param <P> the persistence type
 * @author harald
 */
public class DbObjectClassVariables<P extends AbstractDbObject<P>> {

  private static final Logger LOGGER = Logger.get(DbObjectClassVariables.class);

  // all classes register here:
  private static final Map<String,DbObjectClassVariables<? extends AbstractDbObject<?>>> CLASSMAP = new HashMap<>();


  /**
   * Gets the classvariables for a given tablename.
   *
   * @param <P> the persistence type
   * @param tableName is the database tablename
   * @return the classvariables or null if no such tablename
   */
  @SuppressWarnings("unchecked")
  public static <P extends AbstractDbObject<P>> DbObjectClassVariables<P> getVariables(String tableName) {
    synchronized(CLASSMAP) {
      return (DbObjectClassVariables<P>) CLASSMAP.get(tableName);
    }
  }

  /**
   * Registers a classvariables instance.
   *
   * @param <P> the persistence type
   * @param variables the classvariables to register
   */
  public static <P extends AbstractDbObject<P>> void putVariables(DbObjectClassVariables<P> variables) {
    if (variables.tableName != null) {
      synchronized(CLASSMAP) {
        @SuppressWarnings("unchecked")
        DbObjectClassVariables<P> classVar = (DbObjectClassVariables<P>) CLASSMAP.get(variables.tableName);
        if (classVar != null) {
          /*
           * Using an extended class with the same tablename is okay.
           * This is because the class hierarchy will register along the extension path.
           * In all other cases the tablename must be unique.
           */
          if (!classVar.clazz.isAssignableFrom(variables.clazz)) {
            throw new PersistenceException(
                    "classvariables for '" + variables.tableName + "' already registered, old=" + classVar.clazz.getName() +
                    ", new=" + variables.clazz.getName());
          }
        }
        else  {
          CLASSMAP.put(variables.tableName, variables);
        }
      }
    }
  }




  protected static class ForeignReference implements Comparable<ForeignReference> {

    private final Method method;     // the ...IsReferencing method
    private final int priority;      // the priority, i.e. order in which the methods should be invoked, 0 = first

    ForeignReference(Method method, int priority) {
      this.method = method;
      this.priority = priority;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sorted by priority.
     */
    @Override
    public int compareTo(ForeignReference o) {
      int rv = priority - o.priority;
      if (rv == 0) {
        rv = method.hashCode() - o.method.hashCode();
      }
      return rv;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final ForeignReference other = (ForeignReference) obj;
      if (this.method != other.method && (this.method == null || !this.method.equals(other.method))) {
        return false;
      }
      return this.priority == other.priority;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 67 * hash + (this.method != null ? this.method.hashCode() : 0);
      hash = 67 * hash + this.priority;
      return hash;
    }
  }


  /**
   * Referencing classes and their isReferencing-methods.
   */
  protected final Set<ForeignReference> foreignReferences = new TreeSet<>();


  /**
   * the class.
   */
  public final Class<P> clazz;

  /**
   * The unique class id.
   */
  public final int classId;

  /**
   * the base-classname.
   */
  public final String classBaseName;

  /**
   * database table name.
   */
  public String tableName;

  /**
   * ID for the remote delegate for this class.
   */
  public int remoteDelegateId;

  /**
   * Number of columns in a resultset.
   */
  public int columnCount;

  /**
   * true if prepared statements should always be prepared (i.e. if
   * the statement is changing for some reasons, e.g. the tablename).
   */
  public boolean alwaysPrepare;

  /**
   * prepared statement ID for select().
   */
  public final StatementId selectObjectStatementId = new StatementId();

  /**
   * prepared statement ID for selectAll().
   */
  public final StatementId selectAllObjectsStatementId = new StatementId();

  /**
   * prepared statement ID for selectAllIdSerial().
   */
  public final StatementId selectAllIdSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectLocked().
   */
  public final StatementId selectLockedStatementId = new StatementId();

  /**
   * prepared statement ID for selectSerial().
   */
  public final StatementId selectSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectMaxId().
   */
  public final StatementId selectMaxIdStatementId = new StatementId();

  /**
   * prepared statement ID for selectMaxTableSerial().
   */
  public final StatementId selectMaxTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for insert().
   */
  public final StatementId insertStatementId = new StatementId();

  /**
   * prepared statement ID for update().
   */
  public final StatementId updateStatementId = new StatementId();

  /**
   * prepared statement ID for delete().
   */
  public final StatementId deleteStatementId = new StatementId();

  /**
   * prepared statement ID for dummyUpdate().
   */
  public final StatementId dummyUpdateStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerial().
   */
  public final StatementId updateSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerial(long).
   */
  public final StatementId updateAndSetSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateTableSerial().
   */
  public final StatementId updateTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for updateSerialAndTableSerial().
   */
  public final StatementId updateSerialAndTableSerialStatementId = new StatementId();

  /**
   * prepared statement ID for selectExpiredTableSerials().
   */
  public final StatementId selectExpiredTableSerials1StatementId = new StatementId();
  public final StatementId selectExpiredTableSerials2StatementId = new StatementId();

  /**
   * prepared statement ID for selectObjectsWithExpiredTableSerials
   */
  public final StatementId selectObjectsWithExpiredTableSerialsStatementId = new StatementId();

  /**
   * table serial expiration backlog.
   */
  public final TableSerialExpirationBacklog expirationBacklog;

  /**
   * the source for obtaining a new ID.
   */
  public IdSource idSource;


  /**
   * class properties.
   */
  private Properties properties;


  /**
   * constructs a classvariable.
   * Throws IllegalStateException if already constructed.
   *
   * @param clazz is the class of the derived DbObject
   * @param classId the unique class id, 0 if abstract
   * @param tableName is the SQL tablename
   */
  public DbObjectClassVariables(Class<P> clazz, int classId, String tableName)  {

    this.clazz = clazz;
    this.classId = classId;
    this.tableName = tableName;

    classBaseName = ReflectionHelper.getClassBaseName(clazz);
    expirationBacklog = createBacklog();

    // register and check extension hierarchy
    putVariables(this);
  }


  /**
   * Creates the table serial backlog.
   *
   * @return the backlog
   */
  protected TableSerialExpirationBacklog createBacklog() {
    return new TableSerialExpirationBacklog();
  }


  /**
   * Lazily gets the properties.<br>
   * Loads and initially processes the properties.
   *
   * @return the properties, never null
   */
  public Properties getProperties() {
    if (properties == null) {
      // load properties, if any from file or resource
      String propertiesName = clazz.getName().replace('.', '/');
      try {
        properties = FileHelper.loadProperties(propertiesName);
      }
      catch (FileNotFoundException nf) {
        // missing props is okay and default
      }
      catch (IOException io) {
        LOGGER.severe("could not load class variable properties from " + propertiesName, io);
      }
      finally {
        if (properties == null) {
          properties = new Properties();
        }
      }
    }
    return properties;
  }


  /**
   * Get the IdSource.<br>
   *
   * If the IdSource is null, the method will look for
   * a property file which is the classname + ".properties".
   * The property file must contain an idsource-line
   * similar to that in Db.properties.<br>
   * If there is no such file, the source from the db will be used.
   *
   * @param db the db connection
   * @return the idSource
   */
  public synchronized IdSource getIdSource(Db db) {
    if (idSource == null) {  // setup IdSource once for this class
      // check property "idsource = ..."
      String idConfig = getProperty(Db.PROPERTY_IDSOURCE);
      if (idConfig == null) {
        // no such property: use default id source from Db
        idSource = db.getDefaultIdSource();
      }
      else  {
        // create configurator and source from property
        idSource = IdSourceConfigurator.getInstance().configure(db, idConfig);
      }
    }
    return idSource;
  }



  /**
   * Determine the referencing priority.
   * <p>
   * The default implementation returns 0.
   * Provided to be overridden.
   *
   * @param clazz the referencing class
   * @return the priority
   */
  protected int determineReferencePriority(Class<? extends AbstractDbObject<?>> clazz) {
    // DbObjects all get the same priority
    return 0;
  }

  /**
   * Adds a PDO class that is referencing the class of this class variable holder.
   * <p>
   * The referencing class must provide a static method <tt>isReferencing&lt;ClassBasename&gt;</tt>,
   * while <tt>ClassBaseName</tt> is the {@link #classBaseName} of the referenced class.
   * The method must provide the parameters {@link Db} and objectId as generated
   * by the *IsReferencing-wurblets.
   *
   * @param clazz the class to add
   * @param methodName a method name, null if default
   * @return true if added, false if already in map
   * @see #removeReferencingClass
   */
  public boolean addReferencingClass(Class<? extends AbstractDbObject<?>> clazz, String methodName) {
    ForeignReference ref = createForeignReference(clazz, methodName);
    synchronized(foreignReferences) {
      return foreignReferences.add(ref);
    }
  }


  /**
   * Removes a PDO class that is referencing the class from this class variable holder.
   *
   * @param clazz the class to be removed
   * @param methodName a method name, null if default
   * @return true if removed, false if not in map
   * @see #addReferencingClass
   */
  public boolean removeReferencingClass(Class<? extends AbstractDbObject<?>> clazz, String methodName) {
    ForeignReference ref = createForeignReference(clazz, methodName);
    synchronized(foreignReferences) {
      return foreignReferences.remove(ref);
    }
  }



  private ForeignReference createForeignReference(Class<? extends AbstractDbObject<?>> clazz, String methodName) {
    if (methodName == null) {
      methodName = "isReferencing" + classBaseName;
    }
    try {
      Method method;
      try {
        method = clazz.getMethod(methodName, Long.TYPE);
      }
      catch (NoSuchMethodException e1) {
        // try Long instead of long
        method = clazz.getMethod(methodName, Long.class);
      }
      int modifiers = method.getModifiers();
      if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
        throw new PersistenceException(clazz.getName() + "." + method.getName() + " must be public and non-static");
      }
      return new ForeignReference(method, determineReferencePriority(clazz));
    }
    catch (NoSuchMethodException ex) {
      throw new PersistenceException("please add an *IsReferencing-wurblet to generate " + clazz.getName() + "." + methodName, ex);
    }
  }


  /**
   * Determines whether the class of this variable holder is referenced.
   *
   * @param db the Db connection
   * @param id the object id to test whether it is referenced by any other objects
   * @return true if referenced
   */
  public boolean isReferenced(Db db, long id) {
    return isReferencedImpl(db, id, foreignReferences);
  }


  /**
   * Determines whether the class of this variable holder is referenced.
   *
   * @param db the Db connection
   * @param id the object id to test whether it is referenced by any other objects
   * @param foreignReferences the foreign rereferences
   * @return true if referenced
   */
  protected boolean isReferencedImpl(Db db, long id, Set<ForeignReference> foreignReferences) {
    for (ForeignReference ref : foreignReferences) {
      try {
        @SuppressWarnings("unchecked")
        AbstractDbObject<?> obj = AbstractDbObject.newInstance(db, (Class<AbstractDbObject<?>>) ref.method.getDeclaringClass());
        if (ref.method.invoke(obj, id).equals(Boolean.TRUE)) {
          return true;
        }
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        throw new PersistenceException("couldn't invoke " + ref.method, ex);
      }
    }
    return false;
  }


  /**
   * Gets the delegateId of the class, i.e. subclass of DbObject.
   * If the remoteDelegateId is 0, it will be prepared
   * The delegateId is unique for each class. It is valid only in remote connections and
   * is the same for all remote Db's.
   * The RMI-server creates a delegate for each subclass of DbObject (DbObjectRemoteDelegateImpl resp.)
   *
   * @return the delegate id
   */
  public int getRemoteDelegateId() {
    if (remoteDelegateId == 0)  {
      remoteDelegateId = Db.prepareRemoteDelegate(clazz);
    }
    return remoteDelegateId;
  }

  /**
   * Gets the RemoteDelegate for the class and db.
   * @param db the db connection
   * @return the delegate
   */
  @SuppressWarnings("unchecked")
  public AbstractDbObjectRemoteDelegate<P> getRemoteDelegate(Db db) {
    return (AbstractDbObjectRemoteDelegate<P>) db.getRemoteDelegate(getRemoteDelegateId());
  }


  /**
   * Gets a class property.
   *
   * @param key the property key
   * @return the property value
   */
  public String getProperty(String key) {
    return getProperties().getProperty(key);
  }

  /**
   * Sets a class property.
   *
   * @param key the property key
   * @param value the property value
   * @return the old property value, null if new
   */
  public String putProperty(String key, String value) {
    return (String) getProperties().put(key, value);
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(clazz.getName());
    if (tableName != null) {
      buf.append("/'").append(tableName).append("'");
    }
    return buf.toString();
  }

}
