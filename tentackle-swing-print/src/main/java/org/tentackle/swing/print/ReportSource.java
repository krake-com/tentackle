/**
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


package org.tentackle.swing.print;

/**
 * Datasource for a {@link Report}.<br>
 *
 * Reports are implemented as state machines and get the data
 * to be printed from a {@code ReportSource}.
 *
 * @author harald
 */
public interface ReportSource {


  // returnvalues from advance()
  /** some error, abort! **/
  int ERROR   = -1;
  /** end of data: print "trailer" **/
  int EOF     = 0;
  /** print "data" **/
  int PRINT   = 1;
  /** increment "level", print subheader **/
  int PUSH    = 2;
  /** print subfooter, decrement level **/
  int POP     = 3;



  /**
   * Opens this report source.
   */
  void open();

  /**
   * Closes this report source and do any necessary cleanup.
   */
  void close();

  /**
   * Rewinds this report source.
   */
  void rewind();

  /**
   * Saves the current state.
   * @see #restore()
   */
  void save();

  /**
   * Restores the last saved state.
   * @see #save()
   */
  void restore();


  /**
   * Checks if there is more data to print.<br>
   * Notice that advance() may still be invoked even if
   * hasNext() has returned false!
   * Furthermore, advance() will not always be invoked after hasNext(),
   * thus hasNext() must not change any state!
   *
   * @return true if there is more data
   * @see #advance
   */
  boolean hasNext();


  /**
   * Prepare the data in this report source so that it can be printed.
   * This method is the basic workhorse to setup the print panels,
   * switch logical levels, sum up, etc...
   *
   * @param   report the Report requesting the preparation
   * @return  the new state
   * @see #hasNext()
   */
  int advance(Report report);


  /**
   * Prepares the intro panel for printing.
   * The intro is printed once at the start of the report.
   *
   * @param report the report
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareIntro(Report report);


  /**
   * Prepares the trailer panel for printing.
   * The trailer is printed once at the end of the report.
   *
   * @param report the report
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareTrailer(Report report);


  /**
   * Prepares the header panel for printing.
   * The header is printed at the beginning of each page, except
   * the first if an intro is defined.
   *
   * @param report the report
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareHeader(Report report);


  /**
   * Prepares the footer panel for printing.
   * The footer is printed at the end of each page, except
   * the last if a trailer is defined.
   *
   * @param report the report
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareFooter(Report report);


  /**
   * Prepares the line panel for printing.
   * The line panel prints the current "data row".
   *
   * @param report the report
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareLine(Report report);


  /**
   * Prepares the subheader panel for printing.
   * The subheader is printed at start of each logical "data group".
   *
   * @param report the report
   * @param level the logical group level (starting at 1)
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareSubHeader(Report report, int level);


  /**
   * Prepares the subfooter for printing.
   * The subheader is printed at end of each logical "data group".
   *
   * @param report the report
   * @param level the logical group level (starting at 1)
   * @return the pixels +/- to start printing relative to current vertical offset (usually 0)
   */
  int prepareSubFooter(Report report, int level);

}