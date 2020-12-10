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
package org.tentackle.model.migrate;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class MigrationUtilitiesTest {

  private final MigrationUtilities mu = MigrationUtilities.getInstance();

  @Test
  public void testSyllableExtration() {
    Assert.assertEquals(mu.extractSyllables("pickupposition"),
                        List.of("pic", "kup", "os", "it", "ion"));

    Assert.assertEquals(mu.extractSyllables("pickup_position"),
                        List.of("pic", "kup", "pos", "it", "ion"));

    Assert.assertEquals(mu.extractSyllables("release_pos_x"),
                        List.of("rel", "ease", "pos", "x"));

    Assert.assertEquals(mu.extractSyllables("blah100"),
                        List.of("blah", "100"));

    Assert.assertEquals(mu.extractSyllables("blah100_200xx"),
                        List.of("blah", "100", "200", "x"));

    Assert.assertEquals(mu.extractSyllables("ingot_id"),
                        List.of("in", "got", "id"));

    Assert.assertEquals(mu.extractSyllables("crane_mode"),
                        List.of("crane", "mode"));

    Assert.assertEquals(mu.extractSyllables("loading_no"),
                        List.of("load", "ing", "no"));

    Assert.assertEquals(mu.extractSyllables("completed"),
                        List.of("com", "plet", "ed"));

    Assert.assertEquals(mu.extractSyllables("calloff_id"),
                        List.of("cal", "of", "id"));

    Assert.assertEquals(mu.extractSyllables("product_suffix"),
                        List.of("prod", "uct", "suf", "ix"));

    Assert.assertEquals(mu.extractSyllables("weight"),
                        List.of("weight"));

    Assert.assertEquals(mu.extractSyllables("in_stock_since"),
                        List.of("in", "stock", "sin", "ce"));
  }

  @Test
  public void testMatch() {
    Assert.assertEquals(mu.bestMatch("release_x_position", List.of("release_pos_x", "release_pos_y", "release_pos_z")), "release_pos_x");
    Assert.assertEquals(mu.bestMatch("rel_z_pos", List.of("release_pos_x", "release_pos_y", "release_pos_z")), "release_pos_z");
    Assert.assertEquals(mu.bestMatch("rel_z_pos",
                                     List.of("release_pos_x", "release_pos_y", "release_pos_z", "z_pos")), "release_pos_z");
    Assert.assertEquals(mu.bestMatch("pos_z",
                                     List.of("release_pos_x", "release_pos_y", "release_pos_z", "z_pos")), "z_pos");
    Assert.assertEquals(mu.bestMatch("posz",
                                     List.of("release_pos_x", "release_pos_y", "release_pos_z", "z_pos")), "z_pos");
    Assert.assertEquals(mu.bestMatch("relypos",
                                     List.of("release_pos_x", "release_pos_y", "release_pos_z",
                                             "pickup_pos_x", "pickup_pos_y", "pickup_pos_z")), "release_pos_y");
    Assert.assertEquals(mu.bestMatch("xpick",
                                     List.of("release_pos_x", "release_pos_y", "release_pos_z",
                                             "pickup_pos_x", "pickup_pos_y", "pickup_pos_z")), "pickup_pos_x");

    Assert.assertEquals(mu.bestMatch("date_trans", List.of("transmit_date", "delay_estimated_date")), "transmit_date");
  }
}
