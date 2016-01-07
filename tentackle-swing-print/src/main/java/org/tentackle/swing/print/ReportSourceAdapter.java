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
 * Provides some default-implementations for a ReportSource Interface
 *
 * @author harald
 */
public class ReportSourceAdapter implements ReportSource {

  @Override
  public void open() {
  }

  @Override
  public void close() {
  }

  @Override
  public void rewind() {
  }

  @Override
  public void save()  {
  }

  @Override
  public void restore() {
  }
  
  @Override
  public boolean hasNext()  {
    return true;
  }

  @Override
  public int advance(Report report) {
    return ReportSource.EOF;
  }

  @Override
  public int prepareIntro(Report report) {
    return 0;
  }

  @Override
  public int prepareTrailer(Report report) {
    return 0;
  }

  @Override
  public int prepareHeader(Report report) {
    return 0;
  }

  @Override
  public int prepareFooter(Report report) {
    return 0;
  }

  @Override
  public int prepareLine(Report report) {
    return 0;
  }

  @Override
  public int prepareSubHeader(Report report, int level) {
    return 0;
  }

  @Override
  public int prepareSubFooter(Report report, int level) {
    return 0;
  }

}