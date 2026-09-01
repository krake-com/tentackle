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

import org.testng.annotations.Test;

import org.tentackle.misc.IdentifiableKey;
import org.tentackle.misc.TrackedArrayList;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.ns.pdo.NumberRange;
import org.tentackle.security.pdo.Security;
import org.tentackle.session.PersistenceException;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PdoUtilities}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoUtilitiesTest {

  /**
   * Creates a PDO stub answering {@code isModified()} only.
   *
   * @param modified the value to answer
   * @return the stub
   */
  private static Security securityStub(boolean modified) {
    return (Security) Proxy.newProxyInstance(
        Security.class.getClassLoader(), new Class<?>[] { Security.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "isModified" -> modified;
          case "toString"   -> "security-stub";
          case "hashCode"   -> System.identityHashCode(proxy);
          case "equals"     -> proxy == args[0];
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }


  @Test
  public void testSingularAndPlural() {
    PdoUtilities utilities = PdoUtilities.getInstance();
    assertEquals(utilities.getSingular(NumberPool.class), "number pool");
    assertEquals(utilities.getPlural(NumberPool.class), "number pools");
    assertEquals(utilities.getSingular(NumberRange.class), "number range");
    assertEquals(utilities.getPlural(NumberRange.class), "number ranges");
    assertEquals(utilities.getSingular(Security.class), "security rule");
    assertEquals(utilities.getPlural(Security.class), "security rules");
  }

  @Test
  public void testUnannotatedClassHasNoNames() {
    assertNull(PdoUtilities.getInstance().getSingular(String.class));
    assertNull(PdoUtilities.getInstance().getPlural(String.class));
  }

  @Test
  public void testClassNamesAreSorted() {
    List<String> classNames = new ArrayList<>(PdoUtilities.getInstance().getClassNames());
    assertTrue(classNames.contains(Security.class.getName()));
    assertTrue(classNames.contains(NumberPool.class.getName()));
    assertTrue(classNames.contains(NumberRange.class.getName()));
    List<String> sorted = new ArrayList<>(classNames);
    sorted.sort(null);
    assertEquals(classNames, sorted);
  }

  /**
   * Table name and class ID come from the annotations, so no PDO has to be created.
   */
  @Test
  public void testTableNameAndClassId() {
    assertEquals(PdoUtilities.getInstance().getTableName(Security.class), "secrules");
    assertEquals(PdoUtilities.getInstance().getClassId(Security.class), 5);
    assertEquals(PdoUtilities.getInstance().getTableNames(Security.class, NumberPool.class),
                 new String[] { "secrules", "numpool" });
  }

  @Test
  public void testTableNamesOfNoClasses() {
    assertNull(PdoUtilities.getInstance().getTableNames());
    assertNull(PdoUtilities.getInstance().getTableNames((Class<?>[]) null));
  }

  @Test
  public void testIdStringToIdentifiableKey() {
    IdentifiableKey<PersistentDomainObject<?>> key =
        PdoUtilities.getInstance().idStringToIdentifiableKey("5:42");
    assertEquals(key.getIdentifiableClass(), Security.class);
    assertEquals(key.getIdentifiableId(), 42);
  }

  @Test
  public void testMalformedIdStringRejected() {
    PdoUtilities utilities = PdoUtilities.getInstance();
    assertThrows(PersistenceException.class, () -> utilities.idStringToIdentifiableKey(null));
    assertThrows(PersistenceException.class, () -> utilities.idStringToIdentifiableKey(""));
    assertThrows("the object id is missing",
                 PersistenceException.class, () -> utilities.idStringToIdentifiableKey("5"));
    assertThrows("0 is not a valid object id",
                 PersistenceException.class, () -> utilities.idStringToIdentifiableKey("5:0"));
    assertThrows("no PDO is registered for that class id",
                 PersistenceException.class, () -> utilities.idStringToIdentifiableKey("4711:42"));
  }

  @Test
  public void testDefaultTransactionRetryPolicy() {
    PdoUtilities utilities = PdoUtilities.getInstance();
    TransactionRetryPolicy policy = utilities.getTransactionRetryPolicy(null);
    assertTrue(policy instanceof DefaultTransactionRetryPolicy);
    assertSame(utilities.getTransactionRetryPolicy(""), policy, "null and \"\" select the default");
    assertThrows(PersistenceException.class, () -> utilities.getTransactionRetryPolicy("nosuchpolicy"));
  }

  @Test
  public void testFilterPersistentDomainObjects() {
    assertTrue(PdoUtilities.getInstance().filterPersistentDomainObjects(null).isEmpty());
    assertTrue(PdoUtilities.getInstance().filterPersistentDomainObjects(List.of()).isEmpty());

    Security pdo = securityStub(false);
    assertEquals(PdoUtilities.getInstance().filterPersistentDomainObjects(List.of("no pdo", pdo, 42)),
                 List.of(pdo));
  }

  @Test
  public void testIsCollectionModified() {
    PdoUtilities utilities = PdoUtilities.getInstance();
    assertFalse(utilities.isCollectionModified(null));
    assertFalse(utilities.isCollectionModified(List.of()));
    assertFalse(utilities.isCollectionModified(List.of(securityStub(false))));
    assertTrue(utilities.isCollectionModified(List.of(securityStub(false), securityStub(true))));
  }

  /**
   * A tracked list is modified as soon as elements were added, removed, or replaced, even if none
   * of the elements themselves is modified.
   */
  @Test
  public void testTrackedListModificationCountsAsModified() {
    TrackedArrayList<Security> list = new TrackedArrayList<>(false);
    assertFalse(PdoUtilities.getInstance().isCollectionModified(list));
    list.add(securityStub(false));
    assertTrue(PdoUtilities.getInstance().isCollectionModified(list));
  }

  @Test
  public void testEmptyCollectionsNeedNoSession() {
    PdoUtilities utilities = PdoUtilities.getInstance();
    assertEquals(utilities.deleteCollection(null), 0);
    assertEquals(utilities.deleteCollection(List.of()), 0);
    assertEquals(utilities.deleteMissingInCollection(null, null), 0);
    assertEquals(utilities.deleteMissingInCollection(List.of(), List.of()), 0);
    assertEquals(utilities.saveCollection(null), 0);
    assertEquals(utilities.saveCollection(List.of()), 0);
  }

  /**
   * The members of a PDO are collected from the {@code @Persistent} getters and sorted by the
   * model's ordinal. The attributes are the members without the ones inherited from
   * {@link PersistentObject}, which belong to every PDO and are not part of the model.
   */
  @Test
  public void testAttributesOfAPdo() {
    List<String> names = PdoUtilities.getInstance().getAttributes(Security.class, false).stream()
        .map(PdoMember::getName).toList();
    assertEquals(names, List.of("objectClassName", "objectClassId", "objectId",
                                "domainContextClassId", "domainContextId",
                                "granteeClassId", "granteeId", "priority",
                                "permissions", "allowed", "message"));
  }

  @Test
  public void testMembersIncludeTheInheritedOnes() {
    List<PdoMember> members = PdoUtilities.getInstance().getMembers(Security.class);
    assertTrue(members.stream().anyMatch(m -> "id".equals(m.getName())),
               "the object id is a member of every PDO");
    assertSame(PdoUtilities.getInstance().getMembers(Security.class), members, "must be cached");
  }

}
