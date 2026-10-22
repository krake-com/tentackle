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

import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Tests for the {@link CsvConverter}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CsvConverterTest {

  private final CsvConverter converter = CsvConverter.getInstance();

  @Test
  public void testToCsv() {

    // comma is ok
    assertEquals(converter.toCsvLenient(List.of(List.of("100.20", "200.0", "300"))).getFirst(),
                 "100.20,200.0,300");

    // skip to semicolon
    assertEquals(converter.toCsvLenient(List.of(List.of("100,20", "200,0", "300"))).getFirst(),
                 "100,20;200,0;300");

    // skip to blank
    assertEquals(converter.toCsvLenient(List.of(List.of("100,20", "200\tX", "300;"))).getFirst(),
                 "100,20 200\tX 300;");

    // skip to tab
    assertEquals(converter.toCsvLenient(List.of(List.of("100,20", "200 X", "300;"))).getFirst(),
                 "100,20\t200 X\t300;");

    // all separators exhausted -> fallback to RFC 4180
    assertEquals(converter.toCsvLenient(List.of(List.of("100,20", "200\tX", "300 ;"))).getFirst(),
                 "\"100,20\",200\tX,300 ;");

    // RFC 4180 conform
    assertEquals(converter.toCsv(List.of(List.of("100,20", "200,0", "300"))).getFirst(),
                 "\"100,20\",\"200,0\",300");

  }


  @Test
  public void testFromCsv() {
    assertEquals(converter.fromCsv(',', List.of("100.20,200.0,300")),
                 List.of(List.of("100.20", "200.0", "300")));

    assertEquals(converter.fromCsv(';', List.of("100,20;200,0;300")),
                 List.of(List.of("100,20", "200,0", "300")));

    assertEquals(converter.fromCsv(' ', List.of("100,20 200\tX 300;")),
                 List.of(List.of("100,20", "200\tX", "300;")));

    assertEquals(converter.fromCsv('\t', List.of("100,20\t200 X\t300;")),
                 List.of(List.of("100,20", "200 X", "300;")));

    assertEquals(converter.fromCsv(',', List.of("\"100,20\",200\tX,300 ;")),
                 List.of(List.of("100,20", "200\tX", "300 ;")));

    assertEquals(converter.fromCsv(',', List.of("\"100,20\",200\tX,\"300\"\";\"")),
                 List.of(List.of("100,20", "200\tX", "300\";")));

    assertEquals(converter.fromCsv(',', List.of("\"100,20\",200\tX,300\"\";")), // convenience... not standard
                 List.of(List.of("100,20", "200\tX", "300\"\";")));
  }

  @Test
  public void testFromCsvKeepsEmptyValues() {
    assertEquals(converter.fromCsv(',', List.of("a,b,")), List.of(List.of("a", "b", "")));
    assertEquals(converter.fromCsv(',', List.of(",a")), List.of(List.of("", "a")));
    assertEquals(converter.fromCsv(',', List.of("a,\"\"")), List.of(List.of("a", "")));
    assertEquals(converter.fromCsv(',', List.of(",")), List.of(List.of("", "")));
    assertEquals(converter.fromCsv(',', List.of("")), List.of(List.of()));
  }

  @Test
  public void testRoundTrip() {
    List<List<String>> lines = List.of(List.of("", "a", ""), List.of("x,y", "\"q\"", ""));
    assertEquals(converter.fromCsv(',', converter.toCsv(lines)), lines);
  }

}
