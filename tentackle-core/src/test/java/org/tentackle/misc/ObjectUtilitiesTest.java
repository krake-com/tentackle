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
package org.tentackle.misc;

import org.testng.annotations.Test;

import org.tentackle.bind.BindingEvent;
import org.tentackle.common.BMoney;
import org.tentackle.common.DMoney;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.TentackleRuntimeException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for {@link ObjectUtilities}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class ObjectUtilitiesTest {

  private final String MAGIC_WORD = "wave rod";

  @Test
  public void testConverters() {

    int moneyScale = LocaleProvider.getInstance().getCurrency().getDefaultFractionDigits();

    assertEquals(ObjectUtilities.getInstance().convert(String.class, this), MAGIC_WORD);
    assertEquals(ObjectUtilities.getInstance().convert(Character.TYPE, MAGIC_WORD), (Character) MAGIC_WORD.charAt(0));
    assertEquals(ObjectUtilities.getInstance().convert(Double.class, 10L).doubleValue(), 10.0d);
    assertEquals(ObjectUtilities.getInstance().convert(Float.class, 10).floatValue(), 10.0f);
    assertEquals(ObjectUtilities.getInstance().convert(Float.TYPE, 1.5).floatValue(), 1.5f);
    assertEquals(ObjectUtilities.getInstance().convert(Integer.TYPE, (byte) 5), Integer.valueOf(5));
    assertEquals(ObjectUtilities.getInstance().convert(DMoney.class, 100), new DMoney(100.0d, moneyScale));
    assertEquals(ObjectUtilities.getInstance().convert(BMoney.class, 100), new BMoney(100.0d, moneyScale));
    assertEquals(ObjectUtilities.getInstance().convert(BMoney.class, new BigDecimal(new BigInteger("10000"), 4)), new BMoney(1.0d, 4));
    assertEquals(ObjectUtilities.getInstance().convert(Boolean.TYPE, "true"), Boolean.TRUE);
    assertEquals(ObjectUtilities.getInstance().convert(BigInteger.class, 1000), BigInteger.valueOf(1000));
    assertEquals(ObjectUtilities.getInstance().convert(BigDecimal.class, 1.5), BigDecimal.valueOf(15, 1));

    LocalDateTime localDateTime = LocalDateTime.now();
    localDateTime = localDateTime.minusNanos(localDateTime.getNano());
    java.sql.Timestamp sqlTs = java.sql.Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    assertEquals(ObjectUtilities.getInstance().convert(java.sql.Timestamp.class, localDateTime), sqlTs);
    assertEquals(ObjectUtilities.getInstance().convert(LocalDateTime.class, sqlTs), localDateTime);

    LocalDate localDate = LocalDate.now();
    java.sql.Date date = java.sql.Date.valueOf(localDate.toString());
    assertEquals(ObjectUtilities.getInstance().convert(java.sql.Date.class, localDate), date);
    assertEquals(ObjectUtilities.getInstance().convert(LocalDate.class, date), localDate);

    LocalTime localTime = LocalTime.now();
    localTime = localTime.minusNanos(localTime.getNano());
    org.tentackle.common.Time time = new org.tentackle.common.Time(localTime.toSecondOfDay() * 1000L - TimeZone.getDefault().getRawOffset());
    assertEquals(ObjectUtilities.getInstance().convert(org.tentackle.common.Time.class, localTime), time);
    assertEquals(ObjectUtilities.getInstance().convert(LocalTime.class, time), localTime);

    OffsetDateTime offsetDateTime = OffsetDateTime.now();
    Instant instant = ObjectUtilities.getInstance().convert(Instant.class, offsetDateTime);
    assertEquals(ObjectUtilities.getInstance().convert(OffsetDateTime.class, instant), offsetDateTime);

    instant = ObjectUtilities.getInstance().convert(Instant.class, localDateTime);
    assertEquals(ObjectUtilities.getInstance().convert(LocalDateTime.class, instant), localDateTime);

    instant = ObjectUtilities.getInstance().convert(Instant.class, sqlTs);
    assertEquals(ObjectUtilities.getInstance().convert(java.sql.Timestamp.class, instant), sqlTs);

    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    instant = ObjectUtilities.getInstance().convert(Instant.class, zonedDateTime);
    assertEquals(ObjectUtilities.getInstance().convert(ZonedDateTime.class, instant), zonedDateTime);

    assertEquals(ObjectUtilities.getInstance().convert(BindingEvent.Type.class, "TO_VIEW_VIA_GETTER"), BindingEvent.Type.TO_VIEW_VIA_GETTER);

    try {
      ObjectUtilities.getInstance().convert(this.getClass(),"Blah");
      fail("unexpected conversion");
    }
    catch (IllegalArgumentException iax) {
      // expected!
      assertNull(iax.getCause());   // no parse exception or alike
    }
  }

  @Test
  public void testValueOf() {
    ObjectUtilities ou = ObjectUtilities.getInstance();
    // Integer.valueOf(String)
    assertEquals(ou.valueOf(Integer.class, "123"), Integer.valueOf(123));
    // Long.valueOf(String)
    assertEquals(ou.valueOf(Long.class, "5"), Long.valueOf(5));
    // enum valueOf(String)
    assertEquals(ou.valueOf(BindingEvent.Type.class, "TO_VIEW_VIA_GETTER"), BindingEvent.Type.TO_VIEW_VIA_GETTER);
  }

  @Test
  public void testValueOfNoMatchingMethod() {
    // there is no public static Integer#valueOf(Integer), so this must fail
    assertThrows(IllegalArgumentException.class, () -> ObjectUtilities.getInstance().valueOf(Integer.class, Integer.valueOf(5)));
  }

  @Test
  public void testValueOfInvocationFailure() {
    // Integer.valueOf(String) exists but throws NumberFormatException, which is wrapped
    assertThrows(TentackleRuntimeException.class, () -> ObjectUtilities.getInstance().valueOf(Integer.class, "not a number"));
  }

  @Test
  public void testIsZero() {
    ObjectUtilities ou = ObjectUtilities.getInstance();
    assertTrue(ou.isZero(0));
    assertTrue(ou.isZero(0L));
    assertTrue(ou.isZero((short) 0));
    assertTrue(ou.isZero((byte) 0));
    assertTrue(ou.isZero(0.0d));
    assertTrue(ou.isZero(0.0f));
    assertTrue(ou.isZero(BigInteger.ZERO));
    assertTrue(ou.isZero(BigDecimal.ZERO));
    assertTrue(ou.isZero(new BigDecimal("0.00")));    // scale must not matter
    assertTrue(ou.isZero(new BMoney(0.0d, 2)));

    assertFalse(ou.isZero(1));
    assertFalse(ou.isZero(-1L));
    assertFalse(ou.isZero(0.0001d));
    assertFalse(ou.isZero(0.0001f));
    assertFalse(ou.isZero(BigInteger.ONE));
    assertFalse(ou.isZero(BigDecimal.ONE));
    assertFalse(ou.isZero(new BMoney(1.0d, 2)));
    // null is explicitly not zero
    assertFalse(ou.isZero(null));
  }

  @Override
  public String toString() {
    return MAGIC_WORD;
  }
}
