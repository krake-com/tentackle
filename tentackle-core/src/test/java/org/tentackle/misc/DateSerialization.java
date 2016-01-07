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

package org.tentackle.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TimeZone;
import org.tentackle.common.Date;
import org.tentackle.common.Time;
import org.tentackle.common.Timestamp;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests the date and time serialization.
 *
 * @author harald
 */
public class DateSerialization {

  private static final TimeZone TZ1 = TimeZone.getTimeZone("Europe/Berlin");
  private static final TimeZone TZ2 = TimeZone.getTimeZone("America/Los_Angeles");

  private byte[] serializedData;    // serialization buffer
  private String tz1String;         // string representation in TZ1
  private String tz2String;         // string representation in TZ2
  private long offset;              // offset between TZ1 and TZ2
  private TimeZone orgTimeZone;     // original timezone before test


  private void serializeObject(Object object) throws IOException {
    TimeZone.setDefault(TZ1);
    tz1String = object.toString();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bout);
    out.writeObject(object);
    out.flush();
    serializedData = bout.toByteArray();
  }

  private Object deserializeObject() throws IOException, ClassNotFoundException {
    TimeZone.setDefault(TZ2);
    ByteArrayInputStream bin = new ByteArrayInputStream(serializedData);
    ObjectInputStream in = new ObjectInputStream(bin);
    Object obj = in.readObject();
    tz2String = obj.toString();
    return obj;
  }


  @BeforeClass
  public void setUp() {
    orgTimeZone = TimeZone.getDefault();
    java.util.Date date = new java.util.Date();
    try {
      serializeObject(date);
      Assert.assertEquals(TimeZone.getDefault(), TZ1);
      java.util.Date obj = (java.util.Date) deserializeObject();
      Assert.assertEquals(TimeZone.getDefault(), TZ2);
      offset = TZ1.getOffset(obj.getTime()) - TZ2.getOffset(obj.getTime());
    }
    catch (ClassNotFoundException | IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @AfterClass
  public void cleanup() {
    TimeZone.setDefault(null);  // back to original timezone
    Assert.assertEquals(orgTimeZone.getID(), TimeZone.getDefault().getID());
  }


  @Test
  public void serializeDate() {
    Date now = new Date(System.currentTimeMillis());
    try {
      serializeObject(now);
      Date obj = (Date) deserializeObject();
      Assert.assertEquals(tz1String, tz2String);                // visually equal
      Assert.assertNotEquals(obj.getTime(), now.getTime());     // different milliseconds
    }
    catch (ClassNotFoundException | IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  public void serializeTime() {
    Time now = new Time(System.currentTimeMillis());
    try {
      serializeObject(now);
      Time obj = (Time) deserializeObject();
      Assert.assertEquals(tz1String, tz2String);                // visually equal
      Assert.assertNotEquals(obj.getTime(), now.getTime());     // different milliseconds
    }
    catch (ClassNotFoundException | IOException ex) {
      throw new RuntimeException(ex);
    }
  }


  @Test
  public void serializeTimestamp() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    try {
      serializeObject(now);
      Timestamp obj = (Timestamp) deserializeObject();
      Assert.assertEquals(now.getTime(), obj.getTime());        // same milliseconds (epochal)
      Assert.assertNotEquals(tz1String, tz2String);             // visually different
      Assert.assertFalse(obj.isUTC());

      now.setUTC(true);
      serializeObject(now);
      obj = (Timestamp) deserializeObject();
      Assert.assertEquals(tz1String, tz2String);                    // visually equal
      Assert.assertEquals(now.getTime(), obj.getTime() - offset);   // different milliseconds (by offset)
      Assert.assertTrue(obj.isUTC());
    }
    catch (ClassNotFoundException | IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
