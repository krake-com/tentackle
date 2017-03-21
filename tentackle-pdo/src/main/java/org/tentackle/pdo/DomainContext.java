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
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

// Created on September 2, 2002, 3:55 PM


package org.tentackle.pdo;

import java.io.Serializable;
import java.util.Collection;
import org.tentackle.misc.TrackedList;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionHolder;


/**
 * The application's domain context.
 * <p>
 * Basically, this is a holder for a logical {@link Session}.
 * All {@link PersistentObject}s refer to a DomainContext instead of a {@link Session}.
 * The DomainContext carries the application's higher level context
 * which can be used to hold domain-related information, for example to implement multi-tenancy.
 * <p>
 * Furthermore, all PDOs must be either a root-entity or a direct or indirect component of a root-entity.
 * Each root-entity maintains its own copy of the application's domain context, which is known as
 * the root-context. The root-entity and all its components refer to that root-context.
 * <p>
 * The DomainContext can be extended
 * to hold the ID(s) of the object(s) describing the context. Because
 * an {@link PersistentDomainObject} carries a reference to that context, it can serve as
 * a root for a logical data space. For example, a {@link PersistentObject#selectAll()}
 * of accounts will deliver only the accounts belonging to that particular space.<br>
 * All classes depending on those extended contexts must also implement
 * an appropriate extension of {@link DomainContextDependable}. By further subclassing,
 * a context hierarchy can be achieved. For example, in a financial
 * accounting system, the hierarchy could be: TenantDb -&gt; FiscalYearDb -&gt; BookDb
 * with corresponding TenantDependable -&gt; FiscalYearDependable -&gt; BookDependable.
 * It is recommended to subclass AbstractPersistentObject accordingly, i.e. TenantDbObject -&gt;
 * FiscalYearDbObject -&gt; BookDbObject and/or to override the methods in {@link DomainContextDependable}
 * by applying multiple inheritance emulation via the Wurbelizer.
 * <p>
 * Note: the wurblets support multitenancy by means of the options
 * CONTEXT, CONTEXTID and UNIQUE.
 * <p>
 * The domain context may also be used to hold references to objects and collections of objects
 * that belong to the same root entity (aka "entity context").
 *
 * @author harald
 */
public interface DomainContext extends SessionHolder, Comparable<DomainContext>, Serializable, Cloneable {

  /**
   * Clones this context.
   * <p>
   * The clone gets the root entity and sessionImmutable cleared.
   * @return the cloned context
   */
  DomainContext clone();

  /**
   * Gets a clone of this context with a thread-local session.
   * <p>
   * Notice: A thread-local domain context is always a non-root context.
   *
   * @return the context with a thread-local session
   */
  DomainContext getThreadLocalSessionContext();

  /**
   * Clears the cached clone of this context.<br>
   * Forces cloning on next {@link #getThreadLocalSessionContext()}.
   */
  void clearThreadLocalSessionContext();

  /**
   * Asserts that this context is allowed to be used by the current user.<br>
   *
   * If it is not allowed, a {@link PersistenceException} is thrown.
   * The default implementation does nothing.
   * <p>
   * Override the method in middle tier servers, for example in a hypothetical
   * {@code TenantContext}:
   * <pre>
   *  void assertPermissions()  {
   *    if (DbGlobal.serverDb != null &amp;&amp;    // if in RMI server
   *        !getTenant().isUsageAllowed()) {
   *       throw new DbSecurityException("malicious client tried to use context " + this);
   *    }
   *  }
   * </pre>
   */
  void assertPermissions();


  /**
   * Gets the object that spans this context.<br>
   * The default implementation returns null.
   * Must be overridden in subclasses.
   *
   * @return the root object, null if in default context
   */
  PersistentDomainObject<?> getContextPdo();


  /**
   * Gets the ID of the context object.
   * The default implementation returns 0.
   * Must be overridden in subclasses.
   *
   * @return the object ID, 0 if in default context
   */
  long getContextId();


  /**
   * Determines whether this context belongs to an inheritance hierarchy that
   * is created from a given context object.
   * <p>
   * The method is invoked from the security manager to check whether a
   * security rule applies to a given context. Note that contextClass is the
   * basename of the context object's class, for example "Tenant" or "FiscalYear",
   * and not the classname of the DomainContext.
   * <p>
   * The default implementation returns {@code contextId &lt; 0 || contextId == getContextId()}.<br>
   * This is sufficient for zero or one level of context inheritance. For more than one
   * level this method must be overwridden in each level. Example for a hypothetical "TenantContext":
   * <pre>
   *  boolean isWithinContext(long contextId, String contextClass) {
   *    if (contextClass != null &amp;&amp; contextClass.equals("Tenant") &amp;&amp; contextId == getContextId()) {
   *      return true;
   *    }
   *    return super.isWithinContext(contextId, contextClass);
   *  }
   * </pre>
   * If the object IDs of the context objects are unique among all context entities the
   * contextClass can be ignored and the method reduces to:
   * <pre>
   *  boolean isWithinContext(long contextId, String contextClass) {
   *    return contextId == getContextId() ||
   *           super.isWithinContext(contextId, contextClass);
   *  }
   * </pre>
   *
   * @param contextId the object ID of a context object, 0 = default context
   * @param contextClassId the class id of the context object, 0 = default context
   * @return true if within that context, false if context does not apply
   */
  boolean isWithinContext(long contextId, int contextClassId);


  /**
   * Returns the generic string representation of this context.
   * <p>
   * Use this for logging as it will not invoke methods on other objects.
   *
   * @return the String as <code>Classname[contextId]</code>
   */
  String toGenericString();


  /**
   * Get the long diagnostic string of this context.<br>
   * Used for logging, for example.
   *
   * @return the long info
   */
  String toDiagnosticString();

  /**
   * Creates a root context for a given root entity.<br>
   * If this context is already a root context for the same root-entity, nothing will be created
   * and this context returned.
   *
   * @param rootEntity the root entity
   * @return the root context
   */
  DomainContext getRootContext(PersistentDomainObject<?> rootEntity);

  /**
   * Returns whether this is a root entity context.
   *
   * @return true if root context
   */
  boolean isRootContext();

  /**
   * Gets a domain context which does not belong to a root entity.<br>
   * This is the original context the root-context has been cloned from.
   *
   * @return the non-root context
   */
  DomainContext getNonRootContext();


  /**
   * Gets the root entity for this context.
   *
   * @return the root entity, null if none
   */
  PersistentDomainObject<?> getRootEntity();

  /**
   * Gets the ID of the root entity.
   *
   * @return the ID, 0 if root entity is new or there is no root entity
   */
  long getRootId();

  /**
   * Gets the class-ID of the root entity.
   *
   * @return the classid, 0 if there is no root entity
   */
  int getRootClassId();


  /**
   * Sets the context in a {@link DomainContextDependable}.<br>
   * The method invokes <code>obj.setDomainContext()</code> only if the context really differs.
   * This prevents infinite loops in object circular references.
   *
   * @param obj the PDO, null if ignore
   */
  default void applyTo(DomainContextDependable obj) {
    if (obj != null && obj.getDomainContext() != this)  {
      obj.setDomainContext(this);
    }
  }


  /**
   * Sets the context in a list of {@link DomainContextDependable}s.
   *
   * @param list the collection of data objects
   */
  default void applyTo(Collection<? extends DomainContextDependable> list) {
    if (list != null) {
      for (DomainContextDependable obj: list) {
        DomainContext.this.applyTo(obj);
      }
      if (list instanceof TrackedList<?>) {
        @SuppressWarnings("unchecked")
        Collection<DomainContextDependable> removedObjects = ((TrackedList<DomainContextDependable>) list).getRemovedObjects();
        if (removedObjects != null) {
          for (DomainContextDependable obj: removedObjects) {
            DomainContext.this.applyTo(obj);
          }
        }
      }
    }
  }

}
