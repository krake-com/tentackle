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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

/**
 * Tests for {@link ObjectUtilities}.
 */
public class ObjectUtilitiesTest {

  private final String MAGIC_WORD = "wave rod";

  @Test
  public void testConverters() {

    int moneyScale = Currency.getInstance(LocaleProvider.getInstance().getLocale()).getDefaultFractionDigits();

    assertEquals(ObjectUtilities.getInstance().convert(String.class, this), MAGIC_WORD);
    assertEquals(ObjectUtilities.getInstance().convert(Character.TYPE, MAGIC_WORD), (Character) MAGIC_WORD.charAt(0));
    assertEquals(ObjectUtilities.getInstance().convert(Double.class, 10L), 10.0d);
    assertEquals(ObjectUtilities.getInstance().convert(Float.class, 10), 10.0f);
    assertEquals(ObjectUtilities.getInstance().convert(Float.TYPE, 1.5), 1.5f);
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
    org.tentackle.common.Time time = new org.tentackle.common.Time(localTime.toSecondOfDay() * 1000 - TimeZone.getDefault().getRawOffset());
    assertEquals(ObjectUtilities.getInstance().convert(org.tentackle.common.Time.class, localTime), time);
    assertEquals(ObjectUtilities.getInstance().convert(LocalTime.class, time), localTime);

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

  @Override
  public String toString() {
    return MAGIC_WORD;
  }
}
