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

// Created on May 5, 2003, 4:39 PM

package org.tentackle.misc;

import java.util.StringTokenizer;


/**
 * A phone-number.
 * <p>
 * A phone-number is defined as follows:
 * <pre>
 * +CCC AAAA NNNN[-EEEE]
 *
 *  C = country code
 *  A = area code
 *  N = number
 *  E = extension (optional)
 * </pre>
 *
 * @author harald
 */
public class Telno {


  private boolean valid;        // true if number is valid
  private String error;         // error message, null = ok
  private int country;          // country code
  private int area;             // area code
  private int number;           // the number
  private int extension;        // the extension, -1 if none.
  private int defaultCountry;   // default country if +... missing, 0 = none


  /**
   * Creates an empty (and invalid) phono number without extension
   */
  public Telno()  {
    clear();
  }


  /**
   * Clears the phone number
   */
  public void clear() {
    valid = false;
    error = null;
    country = 0;
    area = 0;
    number = 0;
    extension = -1;
  }


  /**
   * Sets the phone number and verifies it.<br>
   * The digits must be either seperated by a dash or a space.
   * The national prefix is mandatory!
   *
   * @param telno the string to parse, null if "not set"
   * @return true if ok, false if error
   */
  public boolean setTelno(String telno) {
    clear();
    if (telno != null)  {
      // check if starts with a +
      telno = telno.trim();
      if (telno.length() > 0)  {
        if (telno.charAt(0) != '+')  {
          if (defaultCountry > 0) {
            telno = "+" + defaultCountry + " " + telno;
          }
          else  {
            error = "country code must start with a '+'";
            return false;
          }
        }
        StringTokenizer st = new StringTokenizer(telno, " -");
        int pos = 0;    // 0 = prefix, 1=area, 2=number, 3=extension
        valid = false;
        extension = -1;
        while (st.hasMoreTokens() && pos < 4)  {
          String token = st.nextToken();
          switch (pos)  {
            case 0:
              try {
                country = Integer.parseInt(token.substring(1));
              }
              catch (NumberFormatException e) {
                error = "malformed country code";
                return false;
              }
              break;

            case 1:
              try {
                area = Integer.parseInt(token);
              }
              catch (NumberFormatException e) {
                error = "malformed area code";
                return false;
              }
              break;

            case 2:
              try {
                number = Integer.parseInt(token);
              }
              catch (NumberFormatException e) {
                error = "malformed local number";
                return false;
              }
              break;

            case 3:
              try {
                extension = Integer.parseInt(token);
              }
              catch (NumberFormatException e) {
                error = "malformed extension";
                return false;
              }
              break;
          }
          pos++;
        }
        if      (pos == 1)  {
          error = "missing area code";
          return false;
        }
        else if (pos == 2)  {
          error = "missing local number";
          return false;
        }
        valid = true;
      }
    }
    return true;
  }



  /**
   * Gets the phone number as a string.
   *
   * @return the phone-number as a string
   */
  public String getTelno()  {
    StringBuilder buf = new StringBuilder();
    buf.append('+');
    buf.append(country);
    buf.append(' ');
    buf.append(area);
    buf.append(' ');
    buf.append(number);
    if (extension >= 0) {
      buf.append('-');
      buf.append(extension);
    }
    return buf.toString();
  }


  @Override
  public String toString()  {
    return getTelno();
  }


  /**
   * Returns whether number is valid
   * @return true if valid, else invalid or empty
   */
  public boolean isValid() {
    return valid;
  }


  /**
   * Gets the country code.
   *
   * @return the country code
   */
  public int getCountry() {
    return country;
  }

  /**
   * Sets the country code.
   *
   * @param country the country code
   */
  public void setCountry(int country) {
    this.country = country;
  }



  /**
   * Gets the area code.
   *
   * @return the area code
   */
  public int getArea() {
    return area;
  }

  /**
   * Sets the area code.
   *
   * @param area the area code
   */
  public void setArea(int area) {
    this.area = area;
  }



  /**
   * Gets the number part.
   *
   * @return the phone number part
   */
  public int getNumber() {
    return number;
  }

  /**
   * Sets the number part.
   *
   * @param number the phone number part
   */
  public void setNumber(int number) {
    this.number = number;
  }



  /**
   * Gets the extension.
   *
   * @return the extension, -1 if none
   */
  public int getExtension() {
    return extension;
  }

  /**
   * Sets the extension.
   *
   * @param extension  the extension, -1 if none
   */
  public void setExtension(int extension) {
    this.extension = extension;
  }



  /**
   * Gets the default country code.
   *
   * @return the default country code, 0 if no default
   */
  public int getDefaultCountry() {
    return defaultCountry;
  }

  /**
   * Sets the default country code.
   * Will be prepended if missing.
   *
   * @param defaultCountry the default country code, 0 if no default
   */
  public void setDefaultCountry(int defaultCountry) {
    this.defaultCountry = defaultCountry;
  }



  /**
   * Gets the error message.
   *
   * @return the error message, null if none
   */
  public String getError() {
    return error;
  }

  /**
   * Sets the error message.
   *
   * @param error  the error message, null if none
   */
  public void setError(String error) {
    this.error = error;
  }

}
