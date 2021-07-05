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
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

// Created on September 2, 2002, 3:55 PM

package org.tentackle.pdo;

import org.tentackle.misc.TrackedList;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionHolder;

import java.util.Collection;

/**
 * The domain context.
 * <p>
 * Basically, a domain context is a {@link SessionHolder} with additional information
 * about the root entity and the application's context.<br>
 * All {@link PersistentDomainObject}s must refer to a {@code DomainContext}.
 * The DomainContext carries the application's higher level context
 * which can be used to hold domain-related information, for example to implement multi-tenancy.
 * As this is application specific, it is recommended to provide an application specific context
 * via a {@link DomainContextFactory}.
 * <p>
 * Furthermore, since all PDOs must be either a root-entity or a direct or indirect component of a root-entity,
 * each root-entity maintains its own copy of the domain context, which is known as
 * the root-context. The root-entity and all of its components refer to that root-context and therefore
 * "know" their root entity. This information is used, for example, by the {@link org.tentackle.security.SecurityManager}
 * and when persisting child components at level 2 and greater (via the optional column {@code rootId}).
 * <p>
 * The DomainContext can be extended to hold the ID(s) of the object(s) describing the context in multi-tenant systems.
 * Because a {@link PersistentDomainObject} carries a reference to that context, it can serve as
 * a root for a logical data space. For example, a {@link PersistentObject#selectAll()}
 * of accounts will deliver only the accounts belonging to that particular space.<br>
 * All classes depending on those extended contexts must also implement
 * an appropriate extension of {@link DomainContextDependable}. By further subclassing,
 * a context hierarchy can be achieved. For example, in a financial
 * accounting system, the hierarchy could be:
 * <pre>
 * TenantContext -&gt; FiscalYearContext -&gt; BookContext
 * </pre>
 * with corresponding
 * <pre>
 * TenantDependable -&gt; FiscalYearDependable -&gt; BookDependable.
 * </pre>
 * and the root entities of that contexts
 * <pre>
 * Tenant -&gt; FiscalYear -&gt; Book.
 * </pre>
 * <p>
 * The domain model supports multitenancy by means of the option {@code CONTEXT}.
 * When generating the finders, the {@code WHERE}-clause is automatically {@code AND}ed with the context column
 * using the value from the domain context.
 * <p>
 * The domain context may also be used to hold references to objects and collections of objects
 * that belong to the same root entity (a.k.a. "entity context").
 *
 * @author harald
 */
public interface DomainContext extends SessionHolder, Comparable<DomainContext>, Cloneable {

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
   * Only invoked when session is updated after deserialization (objects traveling between jvms).
   * <p>
   * If it is not allowed, a {@link PersistenceException} must be thrown.<br>
   * Override the method in middle tier servers, usually only necessary in multi-tentant applications.
   * The default implementation does nothing.
   */
  void assertPermissions();

  /**
   * Gets the object that spans this context.<br>
   * The default implementation returns null.
   *
   * @return the root object, null if in default context
   */
  PersistentDomainObject<?> getContextPdo();

  /**
   * Gets the ID of the context object.<br>
   * The default implementation returns 0.
   *
   * @return the object ID, 0 if in default context
   */
  long getContextId();

  /**
   * Determines whether this context belongs to an inheritance hierarchy that
   * is created from a given context object.
   * <p>
   * The method is invoked from the security manager to check whether a
   * security rule applies to a given context. Note that contextClassId is the
   * class id of the context object's class.
   * <p>
   * The default implementation returns {@code contextId &lt; 0 || contextId == getContextId()}.<br>
   * This is sufficient for zero or one level of context inheritance. For more than one
   * level this method must be overwridden in each level. Example:
   * <pre>
   *  boolean isWithinContext(long contextId, int contextClassId) {
   *    return contextClassId == BLAH_CLASS_ID &amp;&amp; contextId == getContextId() ||
   *           super.isWithinContext(contextId, contextClassId);
   *  }
   * </pre>
   * If the object IDs of the context objects are unique among all context entities the
   * contextClass can be ignored and the method reduces to:
   * <pre>
   *  boolean isWithinContext(long contextId, int contextClassId) {
   *    return contextId == getContextId() ||
   *           super.isWithinContext(contextId, contextClassId);
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
    if (obj instanceof PersistentDomainObject) {
      // fast without invocation handler
      DomainContext otherCtx = ((PersistentDomainObject) obj).getPersistenceDelegate().getDomainContext();
      if (otherCtx != this) {
        ((PersistentDomainObject) obj).getPersistenceDelegate().setDomainContext(this);
      }
    }
    else if (obj != null && obj.getDomainContext() != this)  {
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
