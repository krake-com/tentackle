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



package org.tentackle.swing;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;
import javax.swing.JTextField;
import javax.swing.text.Document;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.DateHelper;
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.StringHelper;

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
 * AbstractFormField to edit a {@link Date} object.
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
 * Furthermore, the date can determined in relation
 * to a reference date by certain commands. By default,
 * the reference date is the current time:
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
 * <li><tt>"29.5."</tt>: ergaenzt zum 29.Mai des aktuellen Jahres 0 Uhr.</li>
 * <li><tt>"2905"</tt>: dto.</li>
 * <li><tt>"290506"</tt>: dto. falls die Jahreszahl 4-stellig erwartet wird. Dabei wird
 *  das Jahrhundert so ergaenzt, dass es m�glichst nah am aktuellen Datum liegt, d.h.:</li>
 * <li><tt>"29.5.99"</tt>: wird auf "29.05.1999" erweitert und nicht auf "29.05.2099".</li>
 * <li><tt>"7:00"</tt>: heute um 7:00 Uhr</li>
 * </ul>
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class DateFormField extends AbstractFormField  implements SqlDateField {

  static {
    // register the special binding due to util/sql/Date/Time/Timestamp multi use
    FormUtilities.getInstance().getBindingFactory().setFormComponentBindingClass(DateFormField.class, SqlDateFieldBinding.class);
  }

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DateFormField.class);



  /**
   * Provider for the reference date.<br>
   * Invoked whenever the input starts with a '@'
   * which will invoke<br>
   * {@code setReferenceDate(getReferenceDateProvider().getReferenceDate())}
   */
  public interface ReferenceDateProvider {

    /**
     * Retrieves the reference date.
     *
     * @return the reference date, null if default (current)
     */
    Date getReferenceDate();

  }



  /**
   * The default minimum date for all instances.
   * null if no such default.
   */
  public static Date defaultMinDate;

  /**
   * The default maximum date for all instances.
   * null if no such default.
   */
  public static Date defaultMaxDate;


  /**
   * Determines how to handle information loss when a timestamp is edited
   * by a date field without a time format.<br>
   * Primarily this is the log-level, but the level also controls what to log
   * and/or when to throw an exception:
   *
   * <ul>
   * <li>FINER: log with stacktrace and throw a {@link GUIRuntimeException}</li>
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
  public static Level defaultInformationLossLogLevel = Level.INFO;


  private static final String LEGACY_DATE_DELIMITERS = ".,/*=-";
  private static final String LEGACY_TIME_DELIMITERS = ":;\"'";


  private String            format;           // format set by application, null if default format
  private String            oldDefaultFormat; // initially used default format
  private SimpleDateFormat  dateFormat;       // formatting
  private String            dateDelimiters;   // usually . or /
  private boolean           lenient;          // default format.setLenient()
  private Date              referenceDate;    // reference date for arith-funcs, null = now
  private Date              lastDate;         // last processed date
  private char              defaultUnit;      // the default unit (Blank if derive from format)
  private ReferenceDateProvider refProvider;  // reference date provider, null if none
  private Date              minDate;          // the minimum date
  private Date              maxDate;          // the maximum date
  private Level             informationLossLogLevel;   // information loss checking and logging level


  /**
   * Creates an empty DateFormField.<br>
   * Notice: setting doc != null requires a doc derived from FormFieldDocument.
   *
   * @param doc the document model, null = default
   * @param columns the number of columns, 0 = minimum width
   */
  public DateFormField (Document doc, int columns) {
    super (doc, null, columns);
    setHorizontalAlignment(JTextField.CENTER);
    defaultUnit = ' ';
  }

  /**
   * Creates an empty DateFormField with the default document model.<br>
   *
   * @param columns the number of columns, 0 = minimum width
   */
  public DateFormField (int columns)  {
    this (null, columns);
  }

  /**
   * Creates an empty DateFormField with the default document model
   * and minimum width.<br>
   */
  public DateFormField () {
    this (0);
  }

  /**
   * Creates an empty DateFormField with the default document model,
   * mininum width and a given format.<br>
   *
   * @param pattern the date format string
   */
  public DateFormField (String pattern) {
    this (0);
    setFormat(pattern);
  }


  @Override
  public void setFormValue (Object date)  {
    setText (doFormat(date));
  }


  /**
   * Sets the information log level.<br>
   * Overwrites the default {@link #defaultInformationLossLogLevel}.
   *
   * @param informationLossLogLevel the level, null if default
   */
  public void setInformationLossLogLevel(Level informationLossLogLevel) {
    this.informationLossLogLevel = informationLossLogLevel;
  }

  /**
   * Gets the information log level.
   *
   * @return the level
   */
  public Level getInformationLossLogLevel() {
    return informationLossLogLevel;
  }

  /**
   * Gets the effective log level.
   *
   * @return the level
   */
  protected Level getEffectiveInformationLossLogLevel() {
    return informationLossLogLevel != null ? informationLossLogLevel : defaultInformationLossLogLevel;
  }



  @Override
  public String doFormat(Object value) {

    if (value instanceof Date) {
      boolean infoLoss = false;
      if (value instanceof Timestamp || value instanceof Time) {
        if (!FormatHelper.isFormattingTime(getDateFormat())) {
          infoLoss = true;
        }
      }
      else {
        if (!FormatHelper.isFormattingDate(getDateFormat())) {
          infoLoss = true;
        }
      }
      if (infoLoss) {
        Level effectiveLevel = getEffectiveInformationLossLogLevel();
        if (effectiveLevel != null && effectiveLevel != Level.SEVERE && LOGGER.isLoggable(effectiveLevel)) {
          String msg = "possible information loss while formatting " + value + " with format " + getDateFormat().toPattern() + " in:\n" +
                       FormUtilities.getInstance().dumpComponentHierarchy(this, null);
          GUIRuntimeException uix = effectiveLevel == Level.FINE ||
                                    effectiveLevel == Level.FINER ?
                                          new GUIRuntimeException(msg) : null;
          LOGGER.log(effectiveLevel, uix == null ? msg : "", uix);
          if (effectiveLevel == Level.FINER) {
            throw uix;
          }
        }
      }
      lastDate = (Date) value;
      return getDateFormat().format((Date) value);
    }

    else if (value instanceof GregorianCalendar) {
      return doFormat(((GregorianCalendar) value).getTime());
    }

    else {
      return "";
    }
  }


  @Override
  public void setText (String str)  {
    if (str == null || str.isEmpty()) {
      // clearing the field reverts to current date for +/- arithmetic
      lastDate = null;
    }
    super.setText(str);
  }


  /**
   * {@inheritDoc}
   * <p>
   * @return the date, null if field is empty
   */
  @Override
  public Date getFormValue ()  {

    // decode format string, retry twice
    for (int loop=0; loop < 3; loop++)  {

      errorOffset  = -1;
      errorMessage = null;

      String str = getText().replace(getFiller(), ' ').trim();
      int slen = str.length();

      if (slen == 0) { /* empty */
        return null;
      }

      if (str.charAt(0) == '@') {
        ReferenceDateProvider refPro = getReferenceDateProvider();
        if (refPro != null) {
          setReferenceDate(refPro.getReferenceDate());
          str = str.substring(1, slen);
          slen--;
          if (slen == 0) {
            str = "+0";
            slen = 2;
          }
        }
        else  {
          errorOffset  = 0;
          errorMessage = "no reference provider for @";
          return null;
        }
      }

      String fmt = getFormat();   // this will also initialize the format if not yet done

      boolean withDate = FormatHelper.isFormattingDate(dateFormat);
      boolean withTime = FormatHelper.isFormattingTime(dateFormat);

      if (slen == 1) {
        // only one char: check for shortcut
        char c = str.charAt(0);
        if (withDate &&
            (LEGACY_DATE_DELIMITERS.indexOf(c) >= 0 ||
             dateDelimiters.indexOf(c) >= 0)) {
          // current date at 0:00:00
          GregorianCalendar cal = new GregorianCalendar();
          DateHelper.setMidnight(cal);
          setFormValue(cal);
          return getFormValue();  // start over
        }
        if (withTime && LEGACY_TIME_DELIMITERS.indexOf(c) >= 0) {
          // current date and time
          setFormValue(new Date());
          return getFormValue();    // start over
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
          Date refDate = getReferenceDate();
          if (refDate != null)  {
            cal.setTime(refDate);
          }
          else if (!setValue && lastDate != null)  {
            // relative to current setting
            cal.setTime(lastDate);
          }

          char type = str.charAt(slen-1);
          int value;
          if (Character.isDigit(type))  {
            char defUnit = getDefaultUnit();
            if (defUnit != 0 && defUnit != ' ') {
              type = defUnit;
            }
            else  {
              // determine according to format
              if      (fmt.indexOf('s') >= 0) {
                type = 's';
              }
              else if (fmt.indexOf('m') >= 0) {
                type = 'm';
              }
              else if (fmt.indexOf('h') >= 0) {
                type = 'h';
              }
              else if (fmt.indexOf('H') >= 0) {
                type = 'H';
              }
              else if (fmt.indexOf('d') >= 0) {
                type = 'd';
              }
              else if (fmt.indexOf('M') >= 0) {
                type = 'M';
              }
              else if (fmt.indexOf('Y') >= 0) {
                type = 'y';
              }
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
                if (fmt.indexOf('m') == -1) {
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
                if (fmt.indexOf('m') == -1) {
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
          setFormValue(cal.getTime());
          return getFormValue();
        }
        catch (ParseException e) {
          errorOffset = e.getErrorOffset();
          errorMessage = e.getMessage();
          return null;    // start over
        }
        catch (Exception e) {
          // fall through...
        }
      }

      try {
        // parse input
        Date date = getDateFormat().parse(str);
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


        Date effMinDate = getEffectiveMinDate();
        if (effMinDate != null) {
          if (date.before(effMinDate)) {
            errorOffset = 0;
            errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE >= {0}"), getDateFormat().format(effMinDate));
            date = null;
          }
        }
        Date effMaxDate = getEffectiveMaxDate();
        if (effMaxDate != null) {
          if (date.after(effMaxDate)) {
            errorOffset = 0;
            errorMessage = MessageFormat.format(SwingSwingBundle.getString("VALUE MUST BE <= {0}"), getDateFormat().format(effMaxDate));
            date = null;
          }
        }

        return (date);
      }

      catch (ParseException e) {
        errorOffset  = e.getErrorOffset();
        errorMessage = MessageFormat.format(SwingSwingBundle.getString("INVALID DATE: {0}"), str);
        // check for user entered 1.1. and meant 1.1.<current year>
        if (errorOffset > 0 && errorOffset == slen &&
            dateDelimiters.indexOf(str.charAt(errorOffset-1)) >= 0) {
          // last char was a date-delimiter: try appending current year
          setText(str + new GregorianCalendar().get(YEAR));
        }
        else { // check for user omitted the delimiters at all, e.g. 0105
          StringBuilder nBuf = new StringBuilder();   // new generated input
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
            setText(nBuf.toString());
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
                errorOffset = -1;
                errorMessage = null;
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
                getDateFormat().parse(newstr);
                // worked!
                setText(newstr);
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
                      getDateFormat().parse(newstr);
                      // worked!
                      setText(newstr);
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

    return null;
  }


  /**
   * Sets the minimum date.
   *
   * @param minDate the minimum date, null if none
   */
  public void setMinDate(Date minDate) {
    this.minDate = minDate;
  }

  /**
   * Gets the minimum date.
   *
   * @return the minimum date, null if none
   */
  public Date getMinDate() {
    return minDate;
  }

  /**
   * Sets the maximum date.
   *
   * @param maxDate the maximum date, null if none
   */
  public void setMaxDate(Date maxDate) {
    this.maxDate = maxDate;
  }

  /**
   * Gets the maximum date.
   *
   * @return the maximum date, null if none
   */
  public Date getMaxDate() {
    return maxDate;
  }


  /**
   * Gets the SQL-Date.
   *
   * @return the org.tentackle.misc.Date, null if field is empty
   */
  @Override
  public org.tentackle.common.Date getDate() {
    Date date = getFormValue();
    return date == null ? null : new org.tentackle.common.Date(date.getTime());
  }


  /**
   * Gets the SQL-Timestamp.
   *
   * @return the org.tentackle.misc.Timestamp, null if field is empty
   */
  @Override
  public org.tentackle.common.Timestamp getTimestamp() {
    Date date = getFormValue();
    return date == null ? null : new org.tentackle.common.Timestamp(date.getTime());
  }


  /**
   * Gets the SQL-Time.
   *
   * @return the org.tentackle.misc.Time, null if field is empty
   */
  @Override
  public org.tentackle.common.Time getTime() {
    Date date = getFormValue();
    return date == null ? null : new org.tentackle.common.Time(date.getTime());
  }


  /**
   * Sets the date format.
   * <p>
   * Useful if format cannot be fully described by {@link #setFormat(java.lang.String)}.
   * @param fmt the format
   */
  public void setDateFormat(SimpleDateFormat fmt) {

    dateFormat = fmt;
    dateFormat.setLenient(lenient);

    // extract date-delimiters
    dateDelimiters = "";
    String f = fmt.toPattern();
    for (int i=0; i < f.length(); i++)  {
      char c = f.charAt(i);
      if (!Character.isLetterOrDigit(c))  {
        dateDelimiters += c;
      }
    }
  }


  /**
   * Gets the date format.
   * <p>
   * Useful if the format must be tailored beyond the possibilities offered by {@link #setFormat(java.lang.String)}.
   * @return the current format
   */
  public SimpleDateFormat getDateFormat() {
    String shortDatePattern = FormatHelper.getShortDatePattern();
    if (dateFormat == null ||
        (format == null && !Objects.equals(oldDefaultFormat, shortDatePattern))) {
      /**
       * dateFormat not set or format not set by application and
       * default format has changed due to locale change
       */
      setDateFormat(new SimpleDateFormat(shortDatePattern));
      oldDefaultFormat = shortDatePattern;
    }
    return dateFormat;
  }


  /**
   * @see SimpleDateFormat
   */
  @Override
  public void setFormat (String pattern)  {
    // set the format string
    this.format = pattern;   // remember that it has been set by the application!
    setDateFormat(new SimpleDateFormat(pattern));
  }

  @Override
  public String getFormat ()  {
    return format != null ? format : getDateFormat().toPattern();
  }


  /**
   * Sets the "lenient" flag for the date format.
   *
   * @param lenient true if lenient
   * @see SimpleDateFormat
   */
  public void setLenient(boolean lenient) {
    getDateFormat().setLenient(lenient);
    this.lenient = dateFormat.isLenient();
  }

  /**
   * Returns the lenient flag for the date format
   * @return true if lenient
   */
  public boolean isLenient() {
    lenient = getDateFormat().isLenient();
    return lenient;
  }


  /**
   * Gets the reference date for the input shortcuts.
   *
   * @return the reference date for input shortcuts, null = now (default)
   */
  public Date getReferenceDate() {
    return referenceDate;
  }

  /**
   * Sets the reference date for the input shortcuts.
   *
   * @param referenceDate reference date for input shortcuts, null = now
   */
  public void setReferenceDate(Date referenceDate) {
    this.referenceDate = referenceDate;
  }


  /**
   * Gets the default unit.
   *
   * @return the default unit, blank if default
   */
  public char getDefaultUnit() {
    return defaultUnit;
  }


  /**
   * Sets the default unit.
   * <p>
   * The default unit applies to number-only input, for example "6" in a time field.
   * By default, the unit is derived from the format and corresponds to the
   * smallest unit. For example, in "HH:mm" the default unit is minute and
   * in "yy/mm/dd" the default unit is day. Input of "6" will by default
   * be translated to "6 minutes (in current hour)", respectively 6th day
   * in current month.
   * If the default unit, however, is explicitly set to "H" (or "y") an input
   * of "6" will result in "06:00" ("06/xx/xx" respectively).
   *
   * @param defaultUnit the default unit (formatting char), blank or 0 if default
   */
  public void setDefaultUnit(char defaultUnit) {
    if (defaultUnit == 0) {
      defaultUnit = ' ';
    }
    this.defaultUnit = defaultUnit;
  }


  /**
   * Gets the reference date provider.
   *
   * @return the provider, null if none
   */
  public ReferenceDateProvider getReferenceDateProvider() {
    return refProvider;
  }

  /**
   * Sets the reference date provider.
   * <p>
   * The provider is consulted whenever the input starts with a '@'.
   * It will be used to retrieve the reference date.
   * For example: "@+7" to add 7 days to a specific date from the model.
   *
   * @param refProvider the provider, null to clear
   */
  public void setReferenceDateProvider(ReferenceDateProvider refProvider) {
    this.refProvider = refProvider;
  }



  /**
   * Gets the effective minimum date.
   *
   * @return the mindate, null if none
   */
  protected Date getEffectiveMinDate() {
    Date d = getMinDate();
    if (d == null) {
      d = defaultMinDate;
    }
    return d;
  }


  /**
   * Gets the effective maximum date.
   *
   * @return the maxdate, null if none
   */
  protected Date getEffectiveMaxDate() {
    Date d = getMaxDate();
    if (d == null) {
      d = defaultMaxDate;
    }
    return d;
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
    if (!lenient) {
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
                MessageFormat.format(SwingSwingBundle.getString("INVALID {0}: {1} MUST BE BETWEEN {2} AND {3}"),
                                     FormatHelper.calendarFieldToString(field, false), value, min, max),
                                 0);
      }
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
