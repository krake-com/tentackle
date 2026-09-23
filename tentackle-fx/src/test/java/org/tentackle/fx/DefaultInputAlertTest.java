/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx;

import org.testng.annotations.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the input alert.<p>
 * No control is created, so that the tests run without a JavaFX toolkit.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultInputAlertTest {

  @Test
  public void testDefaultSound() throws Exception {
    assertNotNull(DefaultInputAlert.DEFAULT_SOUND, "default sound resource missing");
    AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(DefaultInputAlert.DEFAULT_SOUND);
    assertEquals(fileFormat.getType(), AudioFileFormat.Type.WAVE);
    AudioFormat format = fileFormat.getFormat();
    assertEquals(format.getChannels(), 1);
    assertEquals(format.getSampleSizeInBits(), 16);
    double millis = fileFormat.getFrameLength() * 1000.0 / format.getFrameRate();
    assertTrue(millis > 50 && millis < 300, "unexpected duration " + millis + " ms");
  }

  @Test
  public void testNothingToDelete() {
    assertTrue(InputAlert.isNothingToDelete(0, 0, true, false));
    assertFalse(InputAlert.isNothingToDelete(1, 0, true, false));     // something to delete
    assertFalse(InputAlert.isNothingToDelete(0, 3, true, false));     // deletes the selection
    assertFalse(InputAlert.isNothingToDelete(0, 0, false, false));    // not editable
    assertFalse(InputAlert.isNothingToDelete(0, 0, true, true));      // disabled
  }

  @Test
  public void testDefaults() {
    DefaultInputAlert alert = new DefaultInputAlert();
    assertFalse(alert.isSoundEnabled());
    assertTrue(alert.isVisualEnabled());
    assertEquals(alert.getSound(), DefaultInputAlert.DEFAULT_SOUND);
    URL custom = DefaultInputAlertTest.class.getResource("DefaultInputAlertTest.class");
    alert.setSound(custom);
    assertEquals(alert.getSound(), custom);
    alert.setSound(null);
    assertEquals(alert.getSound(), DefaultInputAlert.DEFAULT_SOUND);
  }

  @Test
  public void testUnsupportedSound() {
    DefaultInputAlert alert = new DefaultInputAlert();
    URL notASound = DefaultInputAlertTest.class.getResource("DefaultInputAlertTest.class");
    alert.setSound(notASound);
    assertNull(alert.getClip());    // logged, but no exception
    assertNull(alert.getClip());    // not loaded again
  }

}
