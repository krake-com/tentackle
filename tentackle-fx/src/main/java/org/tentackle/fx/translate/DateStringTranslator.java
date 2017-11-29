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

package org.tentackle.fx.translate;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.Node;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.DateHelper;
import org.tentackle.misc.FormatHelper;
import org.tentackle.common.StringHelper;

import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.DAY_OF_YEAR;
import static java.util.Calendar.HOUR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;

/**
 * Date translator.
 * <p>
 * The date can be entered in the specified format or as a shortcut.
 * The following shortcuts are defined:
 * <ul>
 * <li><tt>5/29/</tt>: expands to May 29 of the current year (midnight).</li>
 * <li><tt>0529</tt>: dto.</li>
 * <li><tt>05290"</tt>: if the year is 4-digits in length the century will be added
 *  which is closest to the current date, i.e.:</li>
 * <li><tt>5/29/99</tt>: will be expanded to "05/29/1999" and _not_ "05/29/2099".</li>
 * <li><tt>7:00</tt>: today at 7:00am</li>
 * </ul>
 *
 * Furthermore, the date can determined in relation to the current time:
 *
 * <ul>
 * <li><tt>/.,-=* or the date-delimiter of the current locale</tt>: current date</li>
 * <li><tt>:;'"</tt>: current time</li>
 * <li><tt>+3d</tt>: today 0:00:00 plus 3 days</li>
 * <li><tt>-2y</tt>: today 2 years ago.</li>
 * <li><tt>17</tt>: the "smallest" unit of the field will be set to 17.
 *  For dates this means the 17th of the current month.
 *  If the smalles unit are minutes, it means current hour 17 minutes.</li>
 * <li><tt>+14</tt>: same as above but the value will be added (or subtracted if negative)
 *    to (from) the current time. For date fields, for example, this is again shorter than "+14d".</li>
 * <li><tt>4m</tt>: the unit according to the letter following the number will be set _and_
 *  the next "smaller" unit set to its minimum.
 *  In this example, the time (if it is a time field) will be set to 4 minutes and 0 seconds.
 *  Likewise, "6y" would mean "January 1st, 2006". Consequently, "8h" is an even shorter
 *  way to express "today at 8am" than "8:00".</li>
 * </ul>
 *
 * The units are the same as described in {@link SimpleDateFormat} with some
 * minor differences:
 * <ul>
 * <li><tt>"y" or "Y"</tt>: year(s)
 * <li><tt>"M"</tt>: month(s). In date fields without minutes a lowercase "m" works as well.
 * <li><tt>"w or W"</tt>: week(s) or calendar week. For example: "-2w" means "two weeks ago"
 *   but "30w" means the first day of week 30.
 * <li><tt>"d oder D"</tt>: day(s)
 * <li><tt>"h oder H"</tt>: hour(s). Notice that "-24h" means "24 hours ago" and is not
 *   the dame as "-1d" which means "yesterday 0am".
 * <li><tt>"m"</tt>: minute(s)
 * <li><tt>"s oder S"</tt>: second(s)
 * </ul>
 *
 *
 * The shortcuts (except the units) are locale dependent. In German, for example, the
 * shortcuts are as follows:
 * <ul>
 * <li><tt>"29.5."</tt>: adds the current year</li>
 * <li><tt>"2905"</tt>: dto.</li>
 * <li><tt>"290506"</tt>: the closest year if the year is 4-digit, i.e.:</li>
 * <li><tt>"29.5.99"</tt>: becomes "29.05.1999" and not "29.05.2099".</li>
 * <li><tt>"7:00"</tt>: today 7 am</li>
 * </ul>
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = Date.class, viewClass = String.class)
public class DateStringTranslator extends ValueStringTranslator<Date> {

  /**
   * Property for a reference date supplier of type Supplier&lt;Date&gt;.<br>
   * '@' as input will be replaced by that date.
   */
  public static final String REFERENCE_DATE_SUPPLIER = "referenceDateSupplier";

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DateStringTranslator.class);


  /**
   * Determines how to handle information loss when a timestamp is edited
   * by a date field without a time format.<br>
   * Primarily this is the log-level, but the level also controls what to log
   * and/or when to throw an exception:
   *
   * <ul>
   * <li>FINER: log with stacktrace and throw a {@link FxRuntimeException}</li>
   * <li>FINE: log with stacktrace</li>
   * <li>SEVERE: check disabled</li>
   * <li>all other levels: just log without stacktrace</li>
   * </ul>
   *
   * The default is INFO.
   * <p>
   * The check can be turned off if the level of the logger does not
   * cover the given check level.
   */
  public static Logger.Level informationLossLogLevel = Logger.Level.INFO;


  private static final String LEGACY_DATE_DELIMITERS = ".,/*=-";
  private static final String LEGACY_TIME_DELIMITERS = ":;\"'";


  private String pattern;           // the cached pattern
  private String dateDelimiters;    // usually . or /
  private SimpleDateFormat format;  // the cached format
  private Date lastDate;            // last processed date

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public DateStringTranslator(FxTextComponent component) {
    super(component);
  }


  @Override
  public Function<Date, String> toViewFunction() {
    return this::format;
  }

  @Override
  public Function<String, Date> toModelFunction() {
    return s -> toType(parse(s));
  }



  /**
   * Formats the given date.
   *
   * @param value the date value
   * @return the formatted string
   */
  protected String format(Date value) {
    lastDate = value;
    if (value != null) {
      boolean infoLoss = false;
      if (value instanceof Timestamp || value instanceof Time) {
        if (!FormatHelper.isFormattingTime(getFormat())) {
          infoLoss = true;
        }
      }
      else {
        if (!FormatHelper.isFormattingDate(getFormat())) {
          infoLoss = true;
        }
      }
      if (infoLoss) {
        if (informationLossLogLevel != null &&
            informationLossLogLevel != Logger.Level.SEVERE &&
            LOGGER.isLoggable(informationLossLogLevel)) {
          String msg = "possible information loss while formatting " + value +
                       " with format " + getFormat().toPattern() + " in:\n" +
                       FxUtilities.getInstance().dumpComponentHierarchy((Node) getComponent());
          FxRuntimeException uix = informationLossLogLevel == Logger.Level.FINE ||
                                    informationLossLogLevel == Logger.Level.FINER ?
                                          new FxRuntimeException(msg) : null;
          LOGGER.log(informationLossLogLevel, uix == null ? msg : "", uix);
          if (informationLossLogLevel == Logger.Level.FINER) {
            throw uix;
          }
        }
      }
      return getFormat().format(value);
    }
    return null;
  }


  /**
   * Converts the date to the desired type.
   *
   * @param date the date
   * @return the desired type
   */
  protected Date toType(Date date) {
    if (date != null) {
      Class<?> type = getComponent().getType();
      if (org.tentackle.common.Timestamp.class.isAssignableFrom(type)) {
        org.tentackle.common.Timestamp ts = new org.tentackle.common.Timestamp(date.getTime());
        if (getComponent().isUTC()) {
          ts.setUTC(true);
        }
        return ts;
      }
      else if (org.tentackle.common.Time.class.isAssignableFrom(type)) {
        return new org.tentackle.common.Time(date.getTime());
      }
      else if (org.tentackle.common.Date.class.isAssignableFrom(type)) {
        return new org.tentackle.common.Date(date.getTime());
      }
      else if (java.sql.Timestamp.class.isAssignableFrom(type)) {
        return new java.sql.Timestamp(date.getTime());
      }
      else if (java.sql.Time.class.isAssignableFrom(type)) {
        return new java.sql.Time(date.getTime());
      }
      else if (java.sql.Date.class.isAssignableFrom(type)) {
        return new java.sql.Date(date.getTime());
      }
    }
    return date;
  }

  /**
   * Parses a string.
   *
   * @param str the string
   * @return the date
   */
  protected Date parse(String str) {
    if (str != null) {

      Date referenceDate = null;

      // decode format string, retry twice
      for (int loop=0; loop < 3; loop++)  {

        getComponent().setErrorOffset(null);
        getComponent().setError(null);

        str = str.replace(getComponent().getFiller(), ' ').trim();
        int slen = str.length();

        if (slen == 0) {
          return null;
        }

        if (str.charAt(0) == '@') {
          str = str.substring(1, slen);
          slen--;
          @SuppressWarnings("unchecked")
          Supplier<Date> dateSupplier = (Supplier<Date>) ((Node) getComponent()).getProperties().get(REFERENCE_DATE_SUPPLIER);
          if (dateSupplier != null) {
            referenceDate = dateSupplier.get();
            if (slen == 0) {
              str = "+0";
              slen = 2;
            }
          }
          else {
            referenceDate = null;
          }
        }
        if (str.charAt(0) == '$') {
          str = str.substring(1, slen);
          slen--;
          referenceDate = lastDate;
        }

        getFormat();    // make sure format is initialized and up to date

        boolean withDate = FormatHelper.isFormattingDate(format);
        boolean withTime = FormatHelper.isFormattingTime(format);

        if (slen == 1) {
          // only one char: check for shortcut
          char c = str.charAt(0);
          if (withDate &&
              (LEGACY_DATE_DELIMITERS.indexOf(c) >= 0 ||
               dateDelimiters.indexOf(c) >= 0)) {
            // current date at 0:00:00
            GregorianCalendar cal = new GregorianCalendar();
            DateHelper.setMidnight(cal);
            return parse(format(cal.getTime()));  // start over
          }
          if (withTime && LEGACY_TIME_DELIMITERS.indexOf(c) >= 0) {
            // current date and time
            return parse(format(new Date()));    // start over
          }
          // else not allowed
        }

        if (str.indexOf('-') == 0 || str.indexOf('+') == 0 ||
            (slen <= 2 && StringHelper.isAllDigits(str)) ||
            "sSmMhHdDwWyY".indexOf(str.charAt(slen-1)) >= 0) {
          /**
           * current +/-Nt expression, i.e. current time plus or minus
           * some seconds, minutes, hours, days, weeks, months or years.
           * E.g.: +1d
           * The type defaults to the least significant value according
           * to the format. I.e. if the format is dd.MM.yy hh:mm,
           * +7 means plus 7 minutes.
           * The + can also be ommitted for 1 or 2-digit numbers and means
           * 'set' instead of 'add'.
           * I.e. 17 means 17th of current month (if date-format) or
           * 12h means 12:00
           */

          boolean setValue = Character.isDigit(str.charAt(0));  // true = set instead of add

          try {

            GregorianCalendar cal = new GregorianCalendar();
            if (referenceDate != null)  {
              cal.setTime(referenceDate);
            }

            char type = str.charAt(slen-1);
            int value;
            if (Character.isDigit(type))  {
              // determine according to format
              if      (pattern.indexOf('s') >= 0) {
                type = 's';
              }
              else if (pattern.indexOf('m') >= 0) {
                type = 'm';
              }
              else if (pattern.indexOf('h') >= 0) {
                type = 'h';
              }
              else if (pattern.indexOf('H') >= 0) {
                type = 'H';
              }
              else if (pattern.indexOf('d') >= 0) {
                type = 'd';
              }
              else if (pattern.indexOf('M') >= 0) {
                type = 'M';
              }
              else if (pattern.indexOf('Y') >= 0) {
                type = 'y';
              }
              value = Integer.parseInt(str.charAt(0) == '+' ? str.substring(1) : str);
            }
            else  {
              value = Integer.parseInt(str.substring(str.charAt(0) == '+' ? 1 : 0, slen-1));
            }

            if (setValue) {
              switch (type) {
                case 's':
                case 'S':
                  setGregorianValue(cal, SECOND, value);
                  break;
                case 'm':
                  if (pattern.indexOf('m') == -1) {
                    // meant month (m entered instead of M)
                    setGregorianValue(cal, MONTH, value - 1);
                  }
                  else  {
                    setGregorianValue(cal, MINUTE, value);
                    cal.set(SECOND, 0);
                  }
                  break;
                case 'h':
                case 'H':
                  setGregorianValue(cal, HOUR_OF_DAY, value);
                  cal.set(MINUTE, 0);
                  cal.set(SECOND, 0);
                  break;
                case 'd':
                case 'D':
                  setGregorianValue(cal, DAY_OF_MONTH, value);
                  DateHelper.setMidnight(cal);
                  break;
                case 'w':
                case 'W':
                  setGregorianValue(cal, WEEK_OF_YEAR, value);
                  cal.set(DAY_OF_WEEK, cal.getFirstDayOfWeek());
                  DateHelper.setMidnight(cal);
                  break;
                case 'M':
                  setGregorianValue(cal, MONTH, value - 1);
                  cal.set(DAY_OF_MONTH, 1);
                  DateHelper.setMidnight(cal);
                  break;
                case 'y':
                case 'Y':
                  if (value < 100)  {
                    value = convert2DigitYearTo4DigitYear(value);
                  }
                  setGregorianValue(cal, YEAR, value);
                  cal.set(DAY_OF_YEAR, 1);
                  DateHelper.setMidnight(cal);
                  break;
              }
            }
            else  {
              switch (type) {
                case 's':
                case 'S':
                  cal.add(SECOND, value);
                  break;
                case 'm':
                  if (pattern.indexOf('m') == -1) {
                    // meant month (m entered instead of M)
                    cal.add(MONTH, value);
                  }
                  else  {
                    cal.add(MINUTE, value);
                  }
                  break;
                case 'h':
                case 'H':
                  cal.add(HOUR, value);
                  break;
                case 'd':
                case 'D':
                  cal.add(DATE, value);
                  DateHelper.setMidnight(cal);
                  break;
                case 'w':
                case 'W':
                  cal.add(WEEK_OF_YEAR, value);
                  DateHelper.setMidnight(cal);
                  break;
                case 'M':
                  cal.add(MONTH, value);
                  DateHelper.setMidnight(cal);
                  break;
                case 'y':
                case 'Y':
                  cal.add(YEAR, value);
                  DateHelper.setMidnight(cal);
                  break;
              }
            }

            // start over
            return parse(format(cal.getTime()));
          }
          catch (ParseException e) {
            getComponent().setErrorOffset(e.getErrorOffset());
            getComponent().setError(e.getMessage());
            return null;    // start over
          }
          catch (Exception e) {
            // fall through...
          }
        }

        try {
          // parse input
          Date date = format.parse(str);
          GregorianCalendar cal = new GregorianCalendar();
          cal.setTime(date);

          // cut time information if format does not contain time
          if (!withTime) {
            DateHelper.setMidnight(cal);
            date = cal.getTime();
          }

          // expand 2-digit year to 4-digits, e.g. 66 to 1966 and 02 to 2002
          int year = cal.get(YEAR);
          if (year < 100)  {
            // user entered 66 instead of 1966
            year = convert2DigitYearTo4DigitYear(year);
            cal.set(YEAR, year);
            date = cal.getTime();
          } // else user entered a 4-digit year

          return date;
        }

        catch (ParseException e) {
          getComponent().setErrorOffset(e.getErrorOffset());
          getComponent().setError(MessageFormat.format(FxFxBundle.getString("INVALID DATE: {0}"), str));
          // check for user entered 1.1. and meant 1.1.<current year>
          if (e.getErrorOffset() > 0 && e.getErrorOffset() == slen &&
              dateDelimiters.indexOf(str.charAt(e.getErrorOffset() - 1)) >= 0) {
            // last char was a date-delimiter: try appending current year
            str += new GregorianCalendar().get(YEAR);
          }
          else { // check for user omitted the delimiters at all, e.g. 0105
            StringBuilder nBuf = new StringBuilder();    // new generated input
            int dlen = dateDelimiters.length();         // length of delimiters
            int spos = 0;                               // index in user input
            int dpos = 0;                               // index in format
            while (spos < slen) {
              char c = str.charAt(spos);
              if (dateDelimiters.indexOf(c) >= 0 || LEGACY_DATE_DELIMITERS.indexOf(c) >= 0)  {
                break; // some delimiter, real error
              }
              if (dpos < dlen && spos > 0 && spos % 2 == 0)  {
                // insert delimiter
                nBuf.append(dateDelimiters.charAt(dpos++));
              }
              nBuf.append(c);
              spos++;
            }
            if (spos == slen) {   // delimiters inserted
              if (slen % 2 == 0 && dpos < dlen) {
                nBuf.append(dateDelimiters.charAt(dpos));
              }
              if (nBuf.length() == 6) { // day + month. and year missing?
                nBuf.append(new GregorianCalendar().get(YEAR));
              }
              str = nBuf.toString();
            }
            else  {
              // try if time entered only: add current date.
              // the colon is international (at least in western countries)
              boolean timeOnly = true;
              int colonCount = 0;
              for (int i=0; i < slen; i++)  {
                char c = str.charAt(i);
                if (c == ':') {
                  colonCount++;
                }
                else if (!Character.isDigit(c))  {
                  timeOnly = false;
                  break;
                }
              }
              if (timeOnly) {
                try {
                  GregorianCalendar cal = new GregorianCalendar();
                  cal.setTime(colonCount == 1 ? FormatHelper.parseShortTime(str) : FormatHelper.parseTime(str));
                  int hour = cal.get(HOUR_OF_DAY);
                  int minute = cal.get(MINUTE);
                  int second = cal.get(SECOND);
                  cal.setTime(new Date());   // today
                  cal.set(HOUR_OF_DAY, hour);
                  cal.set(MINUTE, minute);
                  cal.set(SECOND, second);
                  getComponent().setErrorOffset(null);
                  getComponent().setError(null);
                  return cal.getTime();
                }
                catch (ParseException ex) {
                  // did not work
                }
              }
              else  {
                // try appending 00:00:00 if only date entered (there is a small chance ;-)
                String newstr = str + " 00:00:00";
                try {
                  // just parse
                  format.parse(newstr);
                  // worked!
                  str = newstr;
                  // start over
                }
                catch (ParseException ex) {
                  // try to replace legacy delimiters to the (first) date delimiter
                  if (dateDelimiters.length() > 0) {
                    StringBuilder buf = new StringBuilder(str);
                    String delimStr = dateDelimiters.substring(0, 1);
                    for (int i=0; i < buf.length(); i++) {
                      char c = buf.charAt(i);
                      if (LEGACY_DATE_DELIMITERS.indexOf(c) >= 0) {
                        buf.replace(i, i+1, delimStr);
                      }
                    }
                    newstr = buf.toString();
                    if (!newstr.equals(str)) {
                      try {
                        // just parse
                        format.parse(newstr);
                        // worked!
                        str = newstr;
                        // start over
                      }
                      catch (ParseException ex2) {
                        // nice try but didn't work out
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } // start over
    }
    return null;
  }


  /**
   * Gets the default pattern according to the type.
   *
   * @return the pattern
   */
  protected String getDefaultPattern() {
    String pat;
    // derive from type
    Class<?> type = getComponent().getType();
    if (Time.class.isAssignableFrom(type)) {
      pat = FormatHelper.getTimePattern();
    }
    else if (Timestamp.class.isAssignableFrom(type)) {
      pat = FormatHelper.getTimestampPattern();
    }
    else {
      pat = FormatHelper.getDatePattern();
    }
    return pat;
  }


  /**
   * Gets the date format.
   *
   * @return the date format
   */
  protected SimpleDateFormat getFormat() {
    String pat = getComponent().getPattern();
    if (pat == null) {
      pat = getDefaultPattern();
    }
    if (format == null || !pat.equals(pattern)) {
      pattern = pat;
      format = new SimpleDateFormat(pattern);
    }

    // extract date-delimiters
    dateDelimiters = "";
    String f = format.toPattern();
    for (int i=0; i < f.length(); i++)  {
      char c = f.charAt(i);
      if (!Character.isLetterOrDigit(c))  {
        dateDelimiters += c;
      }
    }

    return format;
  }


  /**
   * Sets the gregorian value and checks whether the value is valid if date format is not lenient.
   *
   * @param cal the gregorian calendar object
   * @param field the field index
   * @param value the value
   * @throws ParseException if value out of bounds (if not lenient)
   */
  protected void setGregorianValue(GregorianCalendar cal, int field, int value) throws ParseException {
    // check the bounds
    int min = cal.getActualMinimum(field);
    int max = cal.getActualMaximum(field);
    if (value < min || value > max) {
      if (field == MONTH) {
        value++;
        min++;
        max++;
      }
      throw new ParseException(
              MessageFormat.format(FxFxBundle.getString("INVALID {0}: {1} MUST BE BETWEEN {2} AND {3}"),
                                   FormatHelper.calendarFieldToString(field, false), value, min, max),
                               0);
    }
    cal.set(field, value);
  }


  /**
   * Converts a short 2-digit year to a 4-digit year.
   *
   * @param year2 the 2-digit year
   * @return the 4-digit year
   */
  protected int convert2DigitYearTo4DigitYear(int year2) {
    return DateHelper.convert2DigitYearTo4DigitYear(year2, new GregorianCalendar().get(YEAR));
  }

}
