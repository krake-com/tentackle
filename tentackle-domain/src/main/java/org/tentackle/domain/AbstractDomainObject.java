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


package org.tentackle.domain;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainException;
import org.tentackle.pdo.DomainObject;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.Session;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Base domain implementation of a PDO.
 * <p>
 * Every domain delegate of an entity extends this class. It holds a reference to the PDO proxy it is a
 * delegate for and forwards the domain context and session to the persistence delegate, so the domain
 * logic never accesses the database directly. All business logic lives in the concrete subclasses.
 * <p>
 * Subclasses must provide two public constructors: one with the PDO as its only argument, used when the
 * factory creates the PDO and its delegates in one go, and a no-args constructor, used when the domain
 * delegate is created on its own and linked to the PDO later via {@link #setPdo(PersistentDomainObject)}.
 * Both are enforced by the annotation processor for {@code @DomainObjectService}.
 * <p>
 * Domain delegates should be stateless, since PDOs are shared, cached and transferred between tiers.
 *
 * @param <T> the PDO type (interface)
 * @param <D> the domain implementation type
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)  // only proxies are referenced, not implementations
public abstract class AbstractDomainObject<T extends PersistentDomainObject<T>, D extends AbstractDomainObject<T,D>>
       implements DomainObject<T>, EffectiveClassProvider<T>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * The PDO proxy this is a delegate for.
   */
  private T pdo;



  /**
   * Creates the domain delegate for the given PDO.
   *
   * @param pdo the PDO proxy this is a delegate for
   */
  public AbstractDomainObject(T pdo) {
    this.pdo = pdo;
  }


  /**
   * Creates the domain delegate without a PDO.<br>
   * The PDO must be linked later via {@link #setPdo(PersistentDomainObject)}.
   */
  public AbstractDomainObject() {
  }

  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    return pdo.getPersistenceDelegate();
  }


  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public T me() {
    return pdo;
  }

  /**
   * Sets the PDO this is a delegate for.<br>
   * Invoked by {@link DefaultDomainDelegateLinker}.
   *
   * @param pdo the PDO proxy
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

  /**
   * {@inheritDoc}
   * <p>
   * Forwarded to the persistence delegate.
   */
  @Override
  public DomainContext getDomainContext() {
    return pdo.getPersistenceDelegate().getDomainContext();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Forwarded to the persistence delegate.
   */
  @Override
  public Session getSession() {
    return pdo.getPersistenceDelegate().getSession();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The generic string is the diagnostic fallback used whenever {@link #toString()} may fail
   * (see {@code DomainException}, for example), so it must stay printable even for a delegate
   * that is not linked to its PDO yet &mdash; which is exactly the state it tends to be logged in.
   */
  @Override
  public String toGenericString() {
    T thisPdo = me();
    return  getClass().getName() +
            '[' +
            (thisPdo == null ? "unlinked"
                             : String.valueOf(thisPdo.getId()) + '/' + thisPdo.getSerial()) +
            ']';
  }


  /**
   * Gets the string representation of this domain object.
   * <p>
   * The default implementation returns the unique domain key if this is a root entity,
   * or the singular name if it is a component.
   * If that fails, the generic string is returned along with the exception in parentheses,
   * or just the class name if even the generic string fails.
   * This is because {@code toString()} should never throw
   * an exception as it is used for logging and debugging.
   *
   * @return the string representation
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    try {
      Object key = me().getPersistenceDelegate().isRootEntity() ? getUniqueDomainKey() : getSingular();
      if (key != null) {
        buf.append(key);
      }
    }
    catch (RuntimeException re) {
      /*
       * The fallback dereferences the PDO once more, so it can fail for the very same reason the
       * attempt above did (an unlinked delegate, a missing persistence delegate). Guard it: this
       * method is used for logging and debugging, where throwing would mask the actual problem.
       */
      try {
        buf.append(toGenericString());
      }
      catch (RuntimeException ignored) {
        buf.append(getClass().getName());
      }
      buf.append("(");
      buf.append(re);
      buf.append(")");
    }
    return buf.toString();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns false.
   * Must be overridden along with the other domain key methods if the entity provides a unique domain key.
   */
  @Override
  public boolean isUniqueDomainKeyProvided() {
    return false;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation throws a {@link DomainException}.
   *
   * @throws DomainException if not a root entity or not overridden
   */
  @Override
  public Class<?> getUniqueDomainKeyType() {
    assertRootEntity();
    throw new DomainException(pdo, "method getUniqueDomainKeyType not implemented in " + getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation throws a {@link DomainException}.
   *
   * @throws DomainException if not a root entity or not overridden
   */
  @Override
  public Object getUniqueDomainKey() {
    assertRootEntity();
    throw new DomainException(pdo, "method getUniqueDomainKey not implemented in " + getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation throws a {@link DomainException}.
   *
   * @throws DomainException if not a root entity or not overridden
   */
  @Override
  public void setUniqueDomainKey(Object domainKey) {
    assertRootEntity();
    throw new DomainException(pdo, "method setUniqueDomainKey not implemented in " + getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation throws a {@link DomainException}.
   *
   * @throws DomainException if not a root entity or not overridden
   */
  @Override
  public T findByUniqueDomainKey(Object domainKey) {
    assertRootEntity();
    throw new DomainException(pdo, "method findByUniqueDomainKey not implemented in " + getClass());
  }

  /**
   * {@inheritDoc}
   * <p>
   * Defaults to the simple name of the effective class if no name is configured.
   */
  @Override
  public String getSingular() {
    String name = PdoUtilities.getInstance().getSingular(me().getEffectiveClass());
    if (name == null) {
      name = me().getEffectiveClass().getSimpleName();
    }
    return name;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Defaults to the singular name with the English plural suffix ("s" or "es") if no name is configured.
   */
  @Override
  public String getPlural() {
    String name = PdoUtilities.getInstance().getPlural(me().getEffectiveClass());
    if (name == null) {   // default to EN rule
      name = getSingular();
      name += name.endsWith("s") ? "es" : "s";
    }
    return name;
  }

  /**
   * Asserts that the PDO is a root entity.
   *
   * @throws DomainException if not a root entity
   */
  public void assertRootEntity() {
    if (!me().getPersistenceDelegate().isRootEntity()) {
      throw new DomainException(me(), "not a root-entity");
    }
  }

}
