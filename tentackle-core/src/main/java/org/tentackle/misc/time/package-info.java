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

/**
 * Smart parsing of the {@code java.time} types.
 * <p>
 * The {@link org.tentackle.misc.time.SmartDateTimeParser} applies {@link org.tentackle.misc.time.SmartDateTimeParser.TextFilter}s,
 * {@link org.tentackle.misc.time.SmartDateTimeParser.Shortcut}s and
 * {@link org.tentackle.misc.time.SmartDateTimeParser.ErrorHandler}s to the input, so that users can enter dates and times
 * in a short or incomplete form. The predefined implementations live in this package and are
 * wired up by {@link org.tentackle.misc.DateTimeUtilities}.
 */
package org.tentackle.misc.time;
