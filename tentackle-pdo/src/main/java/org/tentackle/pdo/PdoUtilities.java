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

import org.tentackle.app.Application;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.ServiceFinder;
import org.tentackle.common.StringHelper;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.TrackedList;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Persistent;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


interface PdoUtilitiesHolder {
  PdoUtilities INSTANCE = ServiceFactory.createService(PdoUtilities.class, PdoUtilities.class);
}


/**
 * Utility methods for PDOs.
 *
 * @author harald
 */
@Service(PdoUtilities.class)      // defaults to self
public class PdoUtilities {

  private static final Logger LOGGER = Logger.get(PdoUtilities.class);

  private static final String NULL_SESSION = "session in PDO is null";
  private static final String UNEXPECTED_SESSION = "unexpected session: ";
  private static final String EXPECTED_SESSION = ", expected: ";


  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static PdoUtilities getInstance() {
    return PdoUtilitiesHolder.INSTANCE;
  }


  /**
   * The map of pdo members.
   */
  private final Map<Class<?>, List<PdoMember>> memberMap = new ConcurrentHashMap<>();

  /**
   * The map of table names.
   */
  private final Map<Class<?>, String> tableNameMap = new ConcurrentHashMap<>();

  /**
   * The map of class IDs.
   */
  private final Map<Class<?>, Integer> tableClassIdMap = new ConcurrentHashMap<>();

  /**
   * The map of singulars (class -> singular)
   */
  private final Map<String, String> singularMap;

  /**
   * The map of plurals (class -> plural)
   */
  private final Map<String, String> pluralMap;

  /**
   * The map of transaction retry policies.
   */
  private final Map<String, TransactionRetryPolicy> transactionRetryPolicyMap;

  /**
   * List of all class names.
   */
  private final Collection<String> classNames;


  /**
   * Creates a utility instance.
   */
  public PdoUtilities() {
    ServiceFinder finder = ServiceFactory.getServiceFinder();

    Set<String> sortedNames = new TreeSet<>();

    singularMap = new HashMap<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(Singular.class.getName()).entrySet()) {
      String singleName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      singularMap.put(entry.getValue(), singleName);
      sortedNames.add(entry.getValue());
    }

    pluralMap = new HashMap<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(Plural.class.getName()).entrySet()) {
      String multiName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      pluralMap.put(entry.getValue(), multiName);
      sortedNames.add(entry.getValue());
    }

    transactionRetryPolicyMap = new HashMap<>();
    for (Map.Entry<String,String> entry: finder.createNameMap(TransactionRetryPolicyService.class.getName()).entrySet()) {
      String policyName = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
      try {
        Class<?> retryPolicyClass = Class.forName(entry.getValue());
        Object object = retryPolicyClass.getConstructor().newInstance();
        if (object instanceof TransactionRetryPolicy) {
          transactionRetryPolicyMap.putIfAbsent(policyName, (TransactionRetryPolicy) object);
        }
        else {
          LOGGER.severe(retryPolicyClass + " is not a TransactionRetryPolicy");
        }
      }
      catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
        LOGGER.severe("cannot instantiate transaction retry policy", e);
      }
    }

    classNames = new ArrayList<>(sortedNames);
  }


  /**
   * Gets the singular of given class.
   *
   * @param clazz the class
   * @return the singular, null if not annotated with @Singular
   */
  public String getSingular(Class<?> clazz) {
    return singularMap.get(clazz.getName());
    // don't refer to Pdo because the default implementation uses PdoUtilities in return (-> would loop)
  }


  /**
   * Gets the plural of given class.
   *
   * @param clazz the class
   * @return the plural, null if not annotated with @Plural
   */
  public String getPlural(Class<?> clazz) {
    return pluralMap.get(clazz.getName());
    // don't refer to Pdo because the default implementation uses PdoUtilities in return (-> would loop)
  }


  /**
   * Gets a list of all class names.<br>
   * Sorted by name.
   *
   * @return the class names
   */
  public Collection<String> getClassNames() {
    return classNames;
  }


  /**
   * Gets the table name of given class.
   *
   * @param <T> the pdo type
   * @param clazz the pdo class
   * @return the tablename, null if no tablename
   */
  public <T extends PersistentDomainObject<T>> String getTableName(Class<T> clazz) {
    String tableName = tableNameMap.computeIfAbsent(clazz, cls -> {
      String tName = SessionUtilities.getInstance().getTableName(cls.getName());
      if (tName == null) {  // not annotated with @TableName
        // get from pdo instance
        tName = Pdo.create(clazz).getPersistenceDelegate().getTableName();
        if (tName == null) {
          tName = "";
        }
      }
      return tName;
    });
    return tableName.isEmpty() ? null : tableName;
  }


  /**
   * Gets the class id of given class.
   *
   * @param <T> the pdo type
   * @param clazz the pdo class
   * @return the class id, 0 if no class id
   */
  public <T extends PersistentDomainObject<T>> int getClassId(Class<T> clazz) {
    return tableClassIdMap.computeIfAbsent(clazz, cls -> {
      int id = SessionUtilities.getInstance().getClassId(cls.getName());
      if (id == 0) {  // not annotated with @ClassId
        // get from pdo instance
        id = Pdo.create(clazz).getPersistenceDelegate().getClassId();
      }
      return id;
    });
  }


  /**
   * Gets the table names of given classes.
   *
   * @param classes the PDO classes
   * @return the names, null if classes is null or empty
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public String[] getTableNames(Class<?>... classes) {
    String[] names;
    if (classes != null && classes.length > 0) {
      names = new String[classes.length];
      int i=0;
      for (Class<?> clazz: classes) {
        names[i++] = getTableName((Class<? extends PersistentDomainObject>) clazz);
      }
    }
    else  {
      names = null;
    }
    return names;
  }


  /**
   * Determines the serviced class according to the annotation.
   *
   * @param implementingClass the implementing class
   * @return the serviced class, null if no annotation found
   * @see PersistentObjectService
   * @see PersistentOperationService
   * @see DomainObjectService
   * @see DomainOperationService
   */
  public Class<?> determineServicedClass(Class<?> implementingClass) {
    PersistentObjectService pobj = implementingClass.getAnnotation(PersistentObjectService.class);
    if (pobj != null) {
      return pobj.value();
    }
    PersistentOperationService popr = implementingClass.getAnnotation(PersistentOperationService.class);
    if (popr != null) {
      return popr.value();
    }
    DomainObjectService dom = implementingClass.getAnnotation(DomainObjectService.class);
    if (dom != null) {
      return dom.value();
    }
    DomainOperationService dop = implementingClass.getAnnotation(DomainOperationService.class);
    if (dop != null) {
      return dop.value();
    }
    return null;
  }


  /**
   * Filters a collection of Objects, returning only
   * those that are persistent domain objects.
   *
   * @param col the collection of objects
   * @return a list of persistent domain objects, the rest is ignored. Never null.
   */
  public List<PersistentDomainObject<?>> filterPersistentDomainObjects(Collection<?> col) {
    List<PersistentDomainObject<?>> list = new ArrayList<>();
    if (col != null)  {
      for (Object obj: col)  {
        if (obj instanceof PersistentDomainObject) {
          list.add((PersistentDomainObject<?>) obj);
        }
      }
    }
    return list;
  }


  /**
   * Deletes a collection of objects.<br>
   * Virgin objects are not deleted.
   * All objects must belong to the same session.
   * The objects are deleted within a transaction, which is rolled back if deleting fails.
   *
   * @param pdos the objects to delete
   * @return the number of objects deleted
   */
  public int deleteCollection (Collection<? extends PersistentDomainObject<?>> pdos) {
    int deleted = 0;
    if (pdos != null && !pdos.isEmpty()) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj: pdos) {
          if (obj != null && !obj.isVirgin()) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PdoRuntimeException(obj, NULL_SESSION);
              }
              txVoucher = session.begin("delete collection");
            }
            else if (obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PdoRuntimeException(obj, UNEXPECTED_SESSION + obj.getSession() + EXPECTED_SESSION + session);
            }
            obj.delete();
            deleted++;
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
        if (pdos instanceof TrackedList<?>) {
          ((TrackedList<?>) pdos).setModified(false);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return deleted;
  }


  /**
   * Deletes all objects in oldList that are not in newList.<br>
   * The method is handy for deleting cascaded composite lists.
   *
   * @param oldCollection the list of objects stored in db
   * @param newCollection the new list of objects
   *
   * @return the number of objects deleted
   */
  public int deleteMissingInCollection(Collection<? extends PersistentDomainObject<?>> oldCollection,
                                              Collection<? extends PersistentDomainObject<?>> newCollection) {
    int deleted = 0;
    if (oldCollection != null && !oldCollection.isEmpty()) {
      Session session = null;
      long txVoucher = 0;
      try {
        for (PersistentDomainObject<?> obj: oldCollection) {
          if (obj != null && !obj.isVirgin() && (newCollection == null || !newCollection.contains(obj))) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PersistenceException(obj, NULL_SESSION);
              }
              txVoucher = session.begin("delete missing in collection");
            }
            else if(obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PersistenceException(obj, UNEXPECTED_SESSION + obj.getSession() + EXPECTED_SESSION + session);
            }
            obj.delete();
            deleted++;
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
    return deleted;
  }


  /**
   * Checks whether elements of a collection are modified.<br>
   * If the collection is a {@link TrackedList}, {@link TrackedList#isModified()} is invoked as well
   * to detect whether some elements were added, removed or replaced.<br>
   * The method returns false if the collection is empty or null.
   *
   * @param pdos the objects
   * @return true if modified
   */
  public boolean isCollectionModified(Collection<? extends PersistentDomainObject<?>> pdos) {
    if (pdos instanceof TrackedList<?> trackedList && trackedList.isModified())  {
      return true;
    }
    return pdos != null && pdos.stream().anyMatch(PersistentDomainObject::isModified);
  }


  /**
   * Saves a list of PDOs.<br>
   * All objects with isPersistable() == true will be saved.
   * If modifiedOnly is true, only isModified() objects will be saved.
   * All objects with isPersistable() == false and isNew() == false
   * are removed!
   * All objects must belong to the same session.
   * The objects are saved within a transaction, which is rolled back if saving fails.
   *
   * @param pdos the list to save
   * @param modifiedOnly is true if only modified objects are saved
   * @param usePersist true to use persist() instead of save() and replace objects in the collection
   * @return the number of objects saved
   */
  @SuppressWarnings("unchecked")
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos, boolean modifiedOnly, boolean usePersist) {
    int saved = 0;
    if (pdos != null && !pdos.isEmpty()) {
      Session session = null;
      long txVoucher = 0;
      List<PersistentDomainObject<?>> persistedObjects = null;
      if (usePersist) {
        persistedObjects = new ArrayList<>();
      }
      try {
        for (PersistentDomainObject<?> obj: pdos) {
          if (obj != null) {
            if (session == null) {
              session = obj.getSession();
              if (session == null) {
                throw new PdoRuntimeException(obj, NULL_SESSION);
              }
              txVoucher = session.begin("save collection");
            }
            else if (obj.getSession() != session) {
              // must be the same instance of Db!
              throw new PdoRuntimeException(obj, UNEXPECTED_SESSION + obj.getSession() + EXPECTED_SESSION + session);
            }
            if (obj.isPersistable()) {
              if (!modifiedOnly || obj.isModified())  {
                if (usePersist) {
                  persistedObjects.add(obj.persist());
                }
                else  {
                  obj.save();
                }
                saved++;
              }
            }
            else  {
              if (!obj.isNew()) {
                // already stored on disk: remove it!
                obj.delete();
              }
            }
          }
        }
        if (session != null) {
          session.commit(txVoucher);
        }
        if (usePersist) {
          pdos.clear();
          ((Collection<PersistentDomainObject<?>>) pdos).addAll(persistedObjects);
        }
        if (pdos instanceof TrackedList<?>) {
          ((TrackedList<?>) pdos).setModified(false);
        }
      }
      catch (RuntimeException rex) {
        if (session != null) {
          session.rollback(txVoucher);
        }
        throw rex;
      }
    }
    return saved;
  }


  /**
   * Saves a list of PDOs.<br>
   * All objects with isPersistable() == true will be saved.
   * If modifiedOnly is true, only isModified() objects will be saved.
   * All objects with isPersistable() == false and isNew() == false
   * are removed!
   * All objects must belong to the same session.
   * The objects are saved within a transaction, which is rolled back if saving fails.
   *
   * @param pdos the list to save
   * @param modifiedOnly is true if only modified objects are saved
   * @return the number of objects saved
   */
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos, boolean modifiedOnly) {
    return saveCollection(pdos, modifiedOnly, false);
  }


  /**
   * Saves a collection of PDOs.
   *
   * @param pdos the collection of PDOs
   *
   * @return the number of objects saved
   */
  public int saveCollection(Collection<? extends PersistentDomainObject<?>> pdos) {
    return saveCollection(pdos, false);
  }


  /**
   * Determines the identifiable key from a string created by {@link PersistentObject#toIdString()}.
   *
   * @param idString the id string "classid:objectid"
   * @return the key
   * @throws PersistenceException if not an id string or invalid class-id
   */
  public IdentifiableKey<PersistentDomainObject<?>> idStringToIdentifiableKey(String idString) {
    if (idString != null) {
      StringTokenizer stok = new StringTokenizer(idString, ":");
      if (stok.countTokens() == 2) {
        int classId;
        long id;
        try {
          classId = Integer.parseInt(stok.nextToken());
          id = Long.parseLong(stok.nextToken());
        }
        catch (NumberFormatException nx) {
          throw new PersistenceException("not an id-string: '" + idString + "'", nx);
        }
        String className = SessionUtilities.getInstance().getClassName(classId);
        if (className != null && id != 0) {
          try {
            return new IdentifiableKey<>(className, id);
          }
          catch (ClassNotFoundException cx) {
            throw new PersistenceException("cannot load PDO class " + className + " for classId " + classId, cx);
          }
        }
        throw new PersistenceException("no PDO key for " + classId + ":" + id);
      }
    }
    throw new PersistenceException("not an id-string: '" + idString + "'");
  }


  /**
   * Determines all members of a PDO class.
   *
   * @param clazz the PDO class
   * @param <T> the PDO type
   * @return the members sorted by the model's ordinal
   */
  public <T extends PersistentDomainObject<T>> List<PdoMember> getMembers(Class<T> clazz) {
    return memberMap.computeIfAbsent(clazz, cls -> {
      List<PdoMember> members = new ArrayList<>();
      for (Method method : cls.getMethods()) {
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
          continue;   // not a getter
        }
        String name = method.getName();
        if (name.startsWith("is")) {
          name = StringHelper.firstToLower(name.substring(2));
        }
        else if (name.startsWith("get")) {
          name = StringHelper.firstToLower(name.substring(3));
        }
        else {
          continue;   // not a getter
        }
        Persistent anno = method.getAnnotation(Persistent.class);
        if (anno != null) {   // getter annotated with @Persistent
          Method setter = null;
          try {
            setter = cls.getMethod("set" + StringHelper.firstToUpper(name), method.getReturnType());
          }
          catch (NoSuchMethodException e) {
            // no setter
          }
          PdoMember.Type type = List.class.isAssignableFrom(method.getReturnType()) ||
                                PersistentDomainObject.class.isAssignableFrom(method.getReturnType()) ?
                                PdoMember.Type.RELATION : PdoMember.Type.ATTRIBUTE;
          members.add(new PdoMember(type, name, method, setter,
                                    anno.component(), anno.parent(), anno.embedded(),
                                    method.getAnnotation(DomainKey.class) != null,
                                    anno.comment(), anno.ordinal()));
        }
      }
      members.sort(Comparator.comparingInt(PdoMember::getOrdinal));
      return members;
    });
  }


  /**
   * Determines all member attributes of a PDO class.
   *
   * @param clazz the PDO class
   * @param withRelations true if include non-component object relations
   * @param <T> the PDO type
   * @return the attributes sorted by the model's ordinal
   */
  public <T extends PersistentDomainObject<T>> List<PdoMember> getAttributes(Class<T> clazz, boolean withRelations) {

    // build a name map to check against relations
    Map<String, PdoMember> memberMap = new LinkedHashMap<>();
    for (PdoMember member: getMembers(clazz)) {
      memberMap.put(member.getName(), member);
    }

    List<PdoMember> attributes = new ArrayList<>();

    for (PdoMember member: memberMap.values()) {
      if (member.getType() == PdoMember.Type.ATTRIBUTE &&
          member.getGetter().getDeclaringClass() != PersistentObject.class) {
        if (member.getName().endsWith("Id")) {
          // check if this ID belongs to a relation
          String relationName = member.getName().substring(0, member.getName().length() - 2);
          PdoMember relation = memberMap.get(relationName);
          if (relation != null && relation.getType() == PdoMember.Type.RELATION) {
            if (withRelations && !relation.isComponent() && !relation.isParent()) {
              // non-component object relation instead of the object id
              member = new PdoMember(relation.getType(), relation.getName(), relation.getGetter(), relation.getSetter(),
                                     false, false, false,
                                     member.isDomainKey(), relation.getComment(), member.getOrdinal());
            }
            else {
              continue;   // skip component or parent relations
            }
          }
        }
        attributes.add(member);
      }
    }

    return attributes;
  }


  /**
   * Adds a listener for a modification on given PDO classes.<br>
   * This is just a convenience wrapper for {@link PdoListener} to make use of lambdas.
   *
   * @param handler the handler of the {@link ModificationEvent}
   * @param classes the PDO classes
   * @return the registered PDO listener
   */
  public PdoListener listen(Consumer<ModificationEvent> handler, Class<?>... classes) {
    PdoListener listener = new PdoListener(classes) {
      @Override
      public void dataChanged(ModificationEvent ev) {
        handler.accept(ev);
      }
    };
    ModificationTracker.getInstance().addModificationListener(listener);
    return listener;
  }

  /**
   * Adds a listener for a modification on given PDO classes.<br>
   * This is just a convenience wrapper for {@link PdoListener} to make use of lambdas.<br>
   * Same as {@link #listen(Consumer, Class[])}, but for handlers that don't need to refer to the
   * {@link ModificationEvent}.
   *
   * @param handler the handler to be invoked
   * @param classes the PDO classes
   * @return the registered PDO listener
   */
  public PdoListener listen(Runnable handler, Class<?>... classes) {
    PdoListener listener = new PdoListener(classes) {
      @Override
      public void dataChanged(ModificationEvent ev) {
        handler.run();
      }
    };
    ModificationTracker.getInstance().addModificationListener(listener);
    return listener;
  }

  /**
   * Unregisters a registered PDO listener.
   *
   * @param listener the listener to remove
   */
  public void unlisten(PdoListener listener) {
    ModificationTracker.getInstance().removeModificationListener(listener);
  }


  /**
   * Gets the transaction retry policy.
   *
   * @param policyName the name of the policy, empty string or null for default
   * @return the policy, never null
   */
  public TransactionRetryPolicy getTransactionRetryPolicy(String policyName) {
    TransactionRetryPolicy policy = transactionRetryPolicyMap.get(policyName == null ? "" : policyName);
    if (policy == null) {
      throw new PersistenceException("no such TransactionRetryPolicy: '" + policyName + "'");
    }
    return policy;
  }


  /**
   * Creates a localized string from a {@link LockException} for a PDO.
   *
   * @param lockException the lock exception
   * @param pdo the PDO
   * @return the message
   */
  public String lockExceptionToString(LockException lockException, PersistentDomainObject<?> pdo) {
    String str;
    if (lockException.isLockedByAnotherUser()) {
      long userId = lockException.getTokenLockInfo().editedBy();
      Timestamp since = lockException.getTokenLockInfo().editedSince();
      Identifiable user = getUser(pdo.getDomainContext(), userId);
      String userTxt = user == null ? ("ID=" + userId) : user.toString();
      str = MessageFormat.format(PdoPdoBundle.getString("{0} {1} is being locked by {2} since {3}"),
                                 pdo.getSingular(), pdo.toString(), userTxt,
                                 since == null ? "?" : FormatHelper.formatTimestamp(since));
    }
    else {
      str = MessageFormat.format(PdoPdoBundle.getString("{0} {1} is already locked in context {2}"),
                                 pdo.getSingular(), pdo.toString(), lockException.getContextName());
    }
    return str;
  }


  /**
   * Gets the identifiable corresponding to the ID of a user.
   *
   * @param <U> the user type
   * @param context the domain context
   * @param userId the ID of the identifiable (user)
   * @return the user, null if session not attached to a user (background server thread, for example) or no running {@link Application}
   */
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    U user = null;
    if (userId != 0) {
      Application application = Application.getInstance();
      if (application != null) {    // application may be null in unit tests
        user = application.getUser(context, userId);
      }
    }
    return user;
  }

  /**
   * Gets the identifiable corresponding to the current user related to the session of a domain context.
   *
   * @param <U> the user type
   * @param context the domain context
   * @return the user, null if session not attached to a user (background server thread, for example) or no running {@link Application}
   */
  public <U extends Identifiable> U getUser(DomainContext context) {
    SessionInfo sessionInfo = context.getSessionInfo();
    return getUser(context, sessionInfo == null ? 0 : sessionInfo.getUserId());
  }

}
