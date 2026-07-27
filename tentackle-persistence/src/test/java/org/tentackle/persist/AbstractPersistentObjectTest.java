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

import org.testng.annotations.Test;

import org.tentackle.common.DateHelper;
import org.tentackle.common.StringNormalizer;
import org.tentackle.common.Timestamp;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.session.PersistenceException;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the in-memory state of {@link AbstractPersistentObject}.
 * <p>
 * Everything below happens before (or without) any database access: object identity, the domain
 * context, the root columns of a component, the normtext, the token-lock attributes and the cache
 * bookkeeping. The persistent operations themselves are covered by the database-backed tests of
 * {@code tentackle-test-pdo}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPersistentObjectTest {

  /**
   * Creates the persistence delegate of a freshly created PDO.
   *
   * @return the persistence delegate
   */
  private DemoEntityPersistenceImpl createEntity() {
    return (DemoEntityPersistenceImpl) Pdo.create(DemoEntity.class).getPersistenceDelegate();
  }

  /**
   * Creates the persistence delegate of a freshly created component PDO.
   *
   * @return the persistence delegate
   */
  private DemoComponentPersistenceImpl createComponent() {
    return (DemoComponentPersistenceImpl) Pdo.create(DemoComponent.class).getPersistenceDelegate();
  }


  // ---------------- identity and PDO linkage ----------------

  @Test
  public void testTheDelegateKnowsItsPdo() {
    DemoEntity pdo = Pdo.create(DemoEntity.class);
    DemoEntityPersistenceImpl po = (DemoEntityPersistenceImpl) pdo.getPersistenceDelegate();
    assertSame(po.getPdo(), pdo);
    assertSame(po.me(), pdo);
    assertSame(po.getEffectiveClass(), DemoEntity.class);
    assertSame(po.getPdoClass(), DemoEntity.class);
  }

  @Test
  public void testFreshObjectIsNewAndVirgin() {
    DemoEntityPersistenceImpl po = createEntity();
    assertTrue(po.isNew(), "no ID yet");
    assertTrue(po.isVirgin(), "never been persisted");
    assertTrue(po.isModified(), "new objects are modified by definition");
    assertFalse(po.attributesModified(), "but none of its attributes has been changed");
    assertEquals(po.getId(), 0);
    assertEquals(po.getSerial(), 0);
    assertEquals(po.getClassId(), 9001, "taken from the class variables");
  }

  @Test
  public void testSettersMarkTheObjectModified() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setName("Hello");
    assertEquals(po.getName(), "Hello");
    assertTrue(po.attributesModified());

    po.setModified(false);
    assertFalse(po.attributesModified());
  }

  @Test
  public void testUntrackedObjectsRefuseIsModified() {
    // isModified() is answered by tracked entities only, all others must be saved unconditionally
    DemoComponentPersistenceImpl po = createComponent();
    assertFalse(po.isTracked());
    assertTrue(expectThrows(PersistenceException.class, po::isModified)
                   .getMessage().contains("isModified() invoked on untracked object"));
    assertFalse(po.attributesModified(), "the plain flag is available for untracked entities as well");
  }

  @Test
  public void testIdAndSerialDoNotModifyTheObject() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setId(4711);
    po.setSerial(3);
    assertEquals(po.getId(), 4711);
    assertEquals(po.getSerial(), 3);
    assertFalse(po.isNew(), "an object with an ID is persistent");
    assertFalse(po.isVirgin(), "a serial means it has been stored");
    assertFalse(po.isModified(), "setId/setSerial are no attribute changes");
    assertFalse(po.attributesModified());
  }

  @Test
  public void testDeletedObjectKeepsItsIdNegated() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setId(-4711);
    po.setSerial(3);
    assertEquals(po.getId(), 4711, "the ID is returned positive");
    assertTrue(po.isDeleted());
    assertTrue(po.isNew(), "a deleted object can be inserted again");
  }

  @Test
  public void testToGenericString() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setId(7);
    po.setSerial(2);
    assertEquals(po.toGenericString(), DemoEntityPersistenceImpl.class.getName() + "[7/2]");
    assertEquals(po.toIdString(), "9001:7");
  }

  @Test
  public void testImmutableObjectRejectsSetters() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setImmutable(true);
    assertTrue(po.isImmutable());
    expectThrows(PersistenceException.class, () -> po.setName("nope"));
  }


  // ---------------- domain context ----------------

  @Test
  public void testDomainContextIsSettable() {
    DemoEntityPersistenceImpl po = createEntity();
    assertNull(po.getDomainContext(), "no context without a session");

    DomainContext context = Pdo.createDomainContext(null);
    po.setDomainContext(context);
    assertNotNull(po.getDomainContext());
    assertSame(po.getBaseContext(), po.getDomainContext());
    assertEquals(po.getContextId(), -1, "the default implementation does not depend on another object");
  }

  @Test
  public void testImmutableDomainContextCannotBeReplaced() {
    DemoEntityPersistenceImpl po = createEntity();
    DomainContext context = Pdo.createDomainContext(null);
    po.setDomainContext(context);

    assertFalse(po.isDomainContextImmutable());
    po.setDomainContextImmutable(true);
    assertTrue(po.isDomainContextImmutable());

    DomainContext otherContext = Pdo.createDomainContext(null);
    assertTrue(expectThrows(PersistenceException.class, () -> po.setDomainContext(otherContext))
                   .getMessage().contains("domain context is immutable"));

    po.setDomainContext(po.getDomainContext());   // setting the very same context is no change at all
  }

  @Test
  public void testPersistableRequiresADomainContext() {
    DemoEntityPersistenceImpl po = createEntity();
    assertTrue(po.isPersistable() == false, "no context: nothing to persist into");

    po.setDomainContext(Pdo.createDomainContext(null));
    assertTrue(po.isPersistable());

    po.setPersistable(false);
    assertFalse(po.isPersistable());
    assertTrue(expectThrows(PersistenceException.class, po::assertPersistable)
                   .getMessage().contains("not persistable"));

    po.setPersistable(true);
    po.assertPersistable();
  }


  // ---------------- root entity and components ----------------

  @Test
  public void testRootEntityFlags() {
    assertTrue(createEntity().isRootEntity());
    assertFalse(createComponent().isRootEntity(), "a component is no root entity");
  }

  @Test
  public void testRootColumnsAreProvidedByComponentsOnly() {
    DemoEntityPersistenceImpl entity = createEntity();
    assertFalse(entity.isRootIdProvided());
    assertFalse(entity.isRootClassIdProvided());

    DemoComponentPersistenceImpl component = createComponent();
    assertTrue(component.isRootIdProvided());
    assertTrue(component.isRootClassIdProvided());
  }

  @Test
  public void testRootEntityOfItsComponents() {
    DemoEntity entityPdo = Pdo.create(DemoEntity.class);
    DemoEntityPersistenceImpl entity = (DemoEntityPersistenceImpl) entityPdo.getPersistenceDelegate();
    entity.setId(4711);

    DemoComponent componentPdo = Pdo.create(DemoComponent.class);
    DemoComponentPersistenceImpl component = (DemoComponentPersistenceImpl) componentPdo.getPersistenceDelegate();
    assertFalse(entity.isRootEntityOf(componentPdo), "the root columns are not set yet");

    component.setRootId(4711);
    component.setRootClassId(9001);
    assertEquals(component.getRootId(), 4711);
    assertEquals(component.getRootClassId(), 9001);
    assertTrue(entity.isRootEntityOf(componentPdo), "the component points back to its root entity");

    component.setRootClassId(9999);
    assertFalse(entity.isRootEntityOf(componentPdo), "another root class");
    assertFalse(entity.isRootEntityOf(null));
  }

  @Test
  public void testComponentIsNoRootEntityOfAnything() {
    DemoComponent componentPdo = Pdo.create(DemoComponent.class);
    assertFalse(createComponent().isRootEntityOf(componentPdo));
  }


  // ---------------- embedded PDOs ----------------

  @Test
  public void testNonEmbeddedPdoHasNoEmbeddingParent() {
    DemoComponentPersistenceImpl po = createComponent();
    assertFalse(po.isEmbedded());
    assertTrue(expectThrows(PersistenceException.class, po::getEmbeddingParent)
                   .getMessage().contains("not an embedded PDO"));
    expectThrows(PersistenceException.class, po::getColumnPrefix);
    expectThrows(PersistenceException.class, () -> po.setEmbeddingPersistentObject(createEntity(), "pre_"));
  }

  @Test
  public void testEmbeddedPdoNeedsItsEmbeddingParent() {
    DemoEmbeddedPersistenceImpl embedded = new DemoEmbeddedPersistenceImpl();
    assertTrue(embedded.isEmbedded());
    assertTrue(expectThrows(PersistenceException.class, embedded::getClassVariables)
                   .getMessage().contains("parent not set for embedded"));
  }

  @Test
  public void testEmbeddedPdoUsesTheClassVariablesOfItsParent() {
    DemoBasePersistenceImpl parent = new DemoBasePersistenceImpl();
    DemoEmbeddedPersistenceImpl embedded = new DemoEmbeddedPersistenceImpl();
    embedded.setEmbeddingPersistentObject(parent, "adr_");

    assertEquals(embedded.getColumnPrefix(), "adr_");
    assertSame(embedded.getClassVariables(), DemoBasePersistenceImpl.CLASSVARIABLES,
               "an embedded PDO maps to the table of its parent");
    assertEquals(embedded.getTableName(), "demobase");
    assertEquals(embedded.getTableAlias(), "dbs");
  }

  @Test
  public void testColumnsOfAnEmbeddedPdoAreQualifiedAndPrefixed() {
    DemoBasePersistenceImpl parent = new DemoBasePersistenceImpl();
    DemoEmbeddedPersistenceImpl embedded = new DemoEmbeddedPersistenceImpl();
    embedded.setEmbeddingPersistentObject(parent, "adr_");

    // the prefix separates the embedded columns from the parent's own ones within the same table
    assertEquals(embedded.getColumnName("street"), "dbs.adr_street");
    assertEquals(embedded.getColumnNames(List.of("street", "city")), "dbs.adr_street,dbs.adr_city",
                 "the column list must be qualified exactly like a single column name");
  }

  @Test
  public void testEmbeddedPdoIsPersistedAlongWithItsParent() {
    DemoEmbeddedPersistenceImpl embedded = new DemoEmbeddedPersistenceImpl();
    embedded.setEmbeddingPersistentObject(new DemoBasePersistenceImpl(), "adr_");
    embedded.setDomainContext(Pdo.createDomainContext(null));

    assertFalse(embedded.isPersistable(), "an embedded PDO is never persisted on its own");
    assertTrue(embedded.toGenericString().endsWith("[embedded]"));
  }

  @Test
  public void testEmbeddingParentIsReachedThroughItsPdo() {
    DemoEntity parentPdo = Pdo.create(DemoEntity.class);
    DemoEmbeddedPersistenceImpl embedded = new DemoEmbeddedPersistenceImpl();
    embedded.setEmbeddingPersistentObject((DemoEntityPersistenceImpl) parentPdo.getPersistenceDelegate(), "adr_");
    assertSame(embedded.getEmbeddingParent(), parentPdo);
  }

  @Test
  public void testNonEmbeddedPdoIsPersistedOnItsOwn() {
    DemoEntityPersistenceImpl po = createEntity();
    po.setDomainContext(Pdo.createDomainContext(null));
    assertFalse(po.isEmbedded());
    assertTrue(po.isPersistable());
  }


  // ---------------- normtext ----------------

  @Test
  public void testNormTextIsProvidedByTheComponentOnly() {
    assertFalse(createEntity().isNormTextProvided());
    assertTrue(createComponent().isNormTextProvided());
  }

  @Test
  public void testNormTextIsIgnoredWithoutANormTextColumn() {
    DemoEntityPersistenceImpl po = createEntity();
    po.updateNormText();
    assertNull(po.getNormText(), "no normtext column, nothing to update");
  }

  @Test
  public void testNormTextIsNormalizedAndPrefixedWithASeparator() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setAttributesNormText(new StringBuilder("Hello World"));
    po.updateNormText();
    assertEquals(po.getNormText(), StringNormalizer.getInstance().normalize(",Hello World"));
    assertTrue(po.getNormText().startsWith(","), "the leading separator makes the first word searchable");
  }

  @Test
  public void testNormTextOfAttributesAndRelations() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setAttributesNormText(new StringBuilder("Hello World"));
    po.setRelationsNormText(new StringBuilder(",Meyer"));
    po.updateNormText();
    assertEquals(po.getNormText(), StringNormalizer.getInstance().normalize(",Hello World,Meyer"));
  }

  @Test
  public void testNormTextOfRelationsOnly() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setRelationsNormText(new StringBuilder(",Meyer"));
    po.updateNormText();
    assertEquals(po.getNormText(), StringNormalizer.getInstance().normalize(",,Meyer"));
  }

  @Test
  public void testNormTextIsClearedIfNothingToNormalize() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setNormText("something");
    po.updateNormText();
    assertNull(po.getNormText(), "no normtext contents anymore");
  }

  @Test
  public void testSettingTheNormTextMarksTheObjectModified() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setNormText("hello");
    assertTrue(po.attributesModified());

    po.setModified(false);
    po.setNormText("hello");
    assertFalse(po.attributesModified(), "the same normtext is no change");
  }


  // ---------------- token lock ----------------

  @Test
  public void testTokenLockIsProvidedByTheComponentOnly() {
    DemoEntityPersistenceImpl entity = createEntity();
    assertFalse(entity.isTokenLockProvided());
    assertEquals(entity.getTokenLockTimeout(), 0, "no timeout without token-lock columns");
    assertFalse(entity.isTokenLocked());
    assertFalse(entity.isTokenLockableByMe());

    DemoComponentPersistenceImpl component = createComponent();
    assertTrue(component.isTokenLockProvided());
    assertEquals(component.getTokenLockTimeout(), 3600000L, "one hour by default");
  }

  @Test
  public void testTokenLockAttributes() {
    DemoComponentPersistenceImpl po = createComponent();
    Timestamp since = DateHelper.now();
    Timestamp expiry = DateHelper.now(60000);

    po.setEditedBy(42);
    po.setEditedSince(since);
    po.setEditedExpiry(expiry);
    assertEquals(po.getEditedBy(), 42);
    assertSame(po.getEditedSince(), since);
    assertSame(po.getEditedExpiry(), expiry);
    assertFalse(po.attributesModified(), "the token lock is no attribute of the application's model");
  }

  @Test
  public void testNewObjectsAreNeverTokenLocked() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setEditedBy(42);
    po.setEditedExpiry(DateHelper.now(60000));
    assertTrue(po.isNew());
    assertFalse(po.isTokenLocked(), "a PDO that is not persistent cannot be locked for other users");
  }

  @Test
  public void testTokenLockedByUser() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setId(1);
    po.setEditedBy(42);
    po.setEditedExpiry(DateHelper.now(60000));
    po.setContextUserId(42);

    assertTrue(po.isTokenLocked());
    assertTrue(po.isTokenLockedBy(42));
    assertFalse(po.isTokenLockedBy(43));
    assertTrue(po.isTokenLockedByMe());
    assertTrue(po.isTokenLockableByMe(), "the holder may lock it again");

    po.setContextUserId(43);
    assertFalse(po.isTokenLockedByMe());
    assertFalse(po.isTokenLockableByMe(), "someone else holds the token");
  }

  @Test
  public void testExpiredTokenLockIsNoLock() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setId(1);
    po.setEditedBy(42);
    po.setEditedExpiry(DateHelper.now(-60000));
    assertFalse(po.isTokenLocked(), "the token expired a minute ago");
    assertTrue(po.isTokenLockableByMe());
  }

  @Test
  public void testApplyTokenLockInfo() {
    DemoComponentPersistenceImpl po = createComponent();
    Timestamp since = DateHelper.now();
    Timestamp expiry = DateHelper.now(60000);

    po.applyTokenLockInfo(new TokenLockInfo(42, since, expiry));
    assertEquals(po.getEditedBy(), 42);
    assertSame(po.getEditedSince(), since);
    assertSame(po.getEditedExpiry(), expiry);

    po.applyTokenLockInfo(null);
    assertEquals(po.getEditedBy(), 42, "null leaves the token lock untouched");
  }

  @Test
  public void testClearTokenLock() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setEditedBy(42);
    po.setEditedExpiry(DateHelper.now(60000));

    assertFalse(po.clearTokenLock(), "false: the token was cleared, not renewed");
    assertEquals(po.getEditedBy(), 0);
    assertNull(po.getEditedExpiry());
  }

  @Test
  public void testClearTokenLockWithoutTokenLockColumns() {
    DemoEntityPersistenceImpl po = createEntity();
    assertFalse(po.clearTokenLock());
  }

  @Test
  public void testRenewRequestedTokenLock() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setContextUserId(42);
    assertFalse(po.isRenewTokenLockRequested());

    po.setRenewTokenLockRequested(true);
    assertTrue(po.isRenewTokenLockRequested());

    long before = System.currentTimeMillis();
    assertTrue(po.clearTokenLock(), "true: the token was renewed instead of cleared");
    assertEquals(po.getEditedBy(), 42);
    assertNotNull(po.getEditedSince());
    assertTrue(DateHelper.toMillis(po.getEditedExpiry()) >= before + po.getTokenLockTimeout(),
               "the token expires after the token-lock timeout");
  }

  @Test
  public void testRenewingTheTokenLockOfAnotherUserIsRejected() {
    DemoComponentPersistenceImpl po = createComponent();
    po.setId(1);
    po.setEditedBy(42);
    po.setEditedExpiry(DateHelper.now(60000));
    po.setContextUserId(43);

    expectThrows(org.tentackle.pdo.LockException.class, () -> po.setRenewTokenLockRequested(true));
    assertFalse(po.isRenewTokenLockRequested());
  }


  // ---------------- cache bookkeeping ----------------

  @Test
  public void testCacheAccessIsCounted() {
    DemoEntityPersistenceImpl po = createEntity();
    assertFalse(po.isCached());
    assertEquals(po.getCacheAccessCount(), 0);
    po.assertNotCached();

    long before = System.currentTimeMillis();
    po.markCacheAccess();
    assertEquals(po.getCacheAccessCount(), 1);
    assertTrue(po.getCacheAccessTime() >= before);
    assertTrue(po.isCached());
    assertTrue(expectThrows(PersistenceException.class, po::assertNotCached).getMessage().contains("cached"));

    po.markCacheAccess();
    assertEquals(po.getCacheAccessCount(), 2, "LFU caching strategies need the access count");
  }

  @Test
  public void testExpiredFlag() {
    DemoEntityPersistenceImpl po = createEntity();
    assertFalse(po.isExpired());
    po.setExpired(true);
    assertTrue(po.isExpired());
  }

  @Test
  public void testUncachedEntity() {
    assertNull(createEntity().getCache(), "no cache configured for the demo entity");
    assertTrue(createEntity().isCacheable(), "cacheable by default, but no cache is configured");
  }


  // ---------------- misc defaults ----------------

  @Test
  public void testDefaultsOfAPlainEntity() {
    DemoEntityPersistenceImpl po = createEntity();
    assertNull(po.orderBy(), "unordered by default");
    assertEquals(po.getSqlContextCondition(), "", "no context condition by default");
    assertNull(po.getValidClassIds(), "no inheritance, no class-ID restriction");
    assertFalse(po.isComposite(), "no composite relations");
    assertFalse(po.isAbstract());
    assertFalse(po.isExplicitIdAliasRequiredInJoins());
    assertFalse(po.containsPattern("whatever"), "no full-text search by default");
  }

  @Test
  public void testValidationWithoutValidators() {
    DemoEntityPersistenceImpl po = createEntity();
    assertFalse(po.isValidated());
    po.validate();
    assertTrue(po.isValidated(), "nothing to validate, hence valid");
  }

}
