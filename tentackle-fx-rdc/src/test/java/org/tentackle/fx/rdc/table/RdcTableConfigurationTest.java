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

package org.tentackle.fx.rdc.table;

import org.testng.annotations.Test;

import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.table.edit.RowWiseEditHandler;
import org.tentackle.fx.table.edit.TableEditHandler;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link RdcTableConfiguration} and {@link RdcTableColumnConfiguration}.
 * <p>
 * These exercise the non-UI configuration logic (no JavaFX toolkit required).
 */
@SuppressWarnings("missing-explicit-ctor")
public class RdcTableConfigurationTest {

  private RdcTableConfiguration<Object> newConfig() {
    return new RdcTableConfiguration<>(Object.class, "myTable");
  }

  @Test
  public void testScalarProperties() {
    RdcTableConfiguration<Object> config = newConfig();
    assertEquals(config.getName(), "myTable");
    assertEquals(config.toString(), "myTable");
    assertSame(config.getObjectClass(), Object.class);
    assertNull(config.getTemplate(), "no template when constructed from a class");
  }

  @Test
  public void testNameDefaultsToSimpleClassName() {
    RdcTableConfiguration<Object> config = new RdcTableConfiguration<>(Object.class, null);
    assertEquals(config.getName(), "Object");
  }

  @Test
  public void testDefaultFlags() {
    RdcTableConfiguration<Object> config = newConfig();
    assertFalse(config.isSortingIncluded(), "sorting excluded by default");
    assertTrue(config.isViewSizeIncluded(), "view size included by default");
    assertNull(config.getEditHandler(), "no edit handler by default");
    assertEquals(config.getBindingType(), TableConfiguration.BINDING.YES);
    assertNull(config.getBaseBundleName());
  }

  @Test
  public void testFlagSetters() {
    RdcTableConfiguration<Object> config = newConfig();
    config.setSortingIncluded(true);
    config.setViewSizeIncluded(false);
    config.setBindingType(TableConfiguration.BINDING.NO);
    config.setBaseBundleName("my.Bundle");
    assertTrue(config.isSortingIncluded());
    assertFalse(config.isViewSizeIncluded());
    assertEquals(config.getBindingType(), TableConfiguration.BINDING.NO);
    assertEquals(config.getBaseBundleName(), "my.Bundle");
  }

  @Test
  public void testEditHandler() {
    RdcTableConfiguration<Object> config = newConfig();
    TableEditHandler<Object> handler = new RowWiseEditHandler<>();
    config.setEditHandler(handler);
    assertSame(config.getEditHandler(), handler);
    config.setEditHandler(null);
    assertNull(config.getEditHandler());
  }

  @Test
  public void testAddColumnCreatesRdcColumnConfiguration() {
    RdcTableConfiguration<Object> config = newConfig();
    TableColumnConfiguration<Object, ?> column = config.addColumn("foo", "Foo");
    assertTrue(column instanceof RdcTableColumnConfiguration, "factory must create an RDC column configuration");
    assertEquals(column.getName(), "foo");
    assertEquals(column.getDisplayedName(), "Foo");
  }

  @Test
  public void testColumnLookupAndRemoval() {
    RdcTableConfiguration<Object> config = newConfig();
    TableColumnConfiguration<Object, ?> a = config.addColumn("a", "A");
    TableColumnConfiguration<Object, ?> b = config.addColumn("b", "B");

    assertSame(config.getColumnConfiguration("a"), a);
    assertSame(config.getColumnConfiguration("b"), b);
    assertNull(config.getColumnConfiguration("missing"));
    assertNull(config.getColumnConfiguration((String) null));

    Collection<TableColumnConfiguration<Object, ?>> columns = config.getColumnConfigurations();
    assertEquals(columns.size(), 2);

    assertSame(config.removeColumn("a"), a);
    assertEquals(config.getColumnConfigurations().size(), 1);
    assertNull(config.getColumnConfiguration("a"));
  }

  @Test
  public void testColumnOrderIsInsertionOrder() {
    RdcTableConfiguration<Object> config = newConfig();
    config.addColumn("first", "First");
    config.addColumn("second", "Second");
    config.addColumn("third", "Third");
    assertEquals(config.getColumnConfigurations().stream().map(TableColumnConfiguration::getName).toList(),
                 java.util.List.of("first", "second", "third"));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testPdoTypeDetection() {
    RdcTableColumnConfiguration<Object, Object> column =
        new RdcTableColumnConfiguration<>(newConfig(), "ref", "Ref");
    assertFalse(column.isPdoTypeConfigured(), "no type configured yet");

    column.setType((Class) String.class);
    assertFalse(column.isPdoTypeConfigured(), "String is not a PDO type");

    column.setType((Class) PersistentDomainObject.class);
    assertTrue(column.isPdoTypeConfigured(), "a PersistentDomainObject type must be detected");

    column.setType(null);
    assertFalse(column.isPdoTypeConfigured(), "null type resets the flag");
  }

}
