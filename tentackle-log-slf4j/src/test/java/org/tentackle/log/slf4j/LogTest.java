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

package org.tentackle.log.slf4j;

import org.testng.annotations.Test;

import org.tentackle.log.Logger;

import static org.testng.Assert.assertTrue;

/**
 * Tests that {@link Logger#get()} resolves to {@link SLF4JLogger} end-to-end, i.e. that the
 * {@code @Service(Logger.class)} on {@link SLF4JLogger} is actually picked up by
 * {@code DefaultLoggerFactory}'s reflective backend discovery. The behavior of the resolved logger
 * itself is covered in detail by {@link SLF4JLoggerTest}, which constructs {@link SLF4JLogger}
 * directly.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LogTest {

  private static final Logger LOGGER = Logger.get();

  @Test
  public void testLogging() {
    assertTrue(LOGGER instanceof SLF4JLogger, LOGGER.getClass().getName());
    LOGGER.info("this is {0}", "fine");
  }

}
