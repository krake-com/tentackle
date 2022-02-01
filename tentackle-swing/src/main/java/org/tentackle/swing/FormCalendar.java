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

import org.tentackle.swing.plaf.PlafUtilities;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.DAY_OF_YEAR;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SECOND;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;


/**
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormCalendar extends org.tentackle.swing.FormPanel {


  // time modes
  /** show only date (default) **/
  public static final int  SHOW_DATE          = 0;
  /** show date and hour **/
  public static final int  SHOW_HOUR          = 1;
  /** show date, hour and minutes **/
  public static final int  SHOW_MINUTE        = 2;
  /** show date, hour, minutes and seconds **/
  public static final int  SHOW_SECOND        = 3;


  // button actions
  private static final String ACTION_THISMONTH      = "M";
  private static final String ACTION_PREVIOUSMONTH  = "P";
  private static final String ACTION_NEXTMONTH      = "N";



  private Locale locale;                    // locale (z.B. de_DE)
  private GregorianCalendar calendar;       // current calendar
  private String[] dayNames;                // Sun, Mon, ....
  private int firstDayIndex;                // offset for first Day in Month
  private JToggleButton firstClickButton;   // button that got the first click


  /**
   * special button with double-click recognition
   */
  private class CalToggleButton extends JToggleButton {

    private static final long serialVersionUID = 1L;

    CalToggleButton(String text) {
      super(text);
      setMargin(new Insets(0, 0, 0, 0));
      setBorderPainted(false);
      setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_PRESSED)  {
        if (e.getClickCount() == 2)  {
          if (firstClickButton == this && ACTION_THISMONTH.equals(getActionCommand())) {
            // double click on this month's button
            FormCalendar.this.fireActionPerformed(getText());
          }
          else  {
            // don't allow double click for previous/next month buttons
            e.consume();
            return;
          }
        }
        else  {
          firstClickButton = this;  // remember first clicked button
        }
      }
      super.processMouseEvent(e);
    }
  }




  /**
   * Creates a calendar panel.<br>
   *
   * @param locale the locale, null if default locale
   * @param day the date, null if "today"
   * @param timeMode whether and how to display the time, one of SHOW_...
   */
  public FormCalendar(Locale locale, Date day, int timeMode) {

    initComponents();
    ((FormComboBox) monthField.getFormComponent()).setValueEnteredOnSelect(true);

    setup(locale, day, timeMode);
  }

  /**
   * Creates a calendar panel in the current locale.
   *
   * @param day the date, null if "today"
   * @param timeMode whether and how to display the time, one of SHOW_...
   */
  public FormCalendar (Date day, int timeMode) {
    this(null, day, timeMode);
  }

  /**
   * Creates a calendar panel in current locale, no time.
   *
   * @param day the date, null if "today"
   */
  public FormCalendar (Date day) {
    this(day, SHOW_DATE);
  }

  /**
   * Creates a calendar panel in current locale, no time, today.
   */
  public FormCalendar() {
    this(null);
  }




  /**
   * Configures the calendar component.<br>
   *
   * @param locale the locale, null if default locale
   * @param day the date, null if "today"
   * @param timeMode whether and how to display the time, one of SHOW_...
   */
  public void setup(Locale locale, Date day, int timeMode) {

    this.locale = locale == null ? Locale.getDefault() : locale;
    if (day == null) {
      day = new Date();
    }

    calendar = new GregorianCalendar(this.locale);
    calendar.setTime(day);
    if (timeMode < SHOW_SECOND) {
      calendar.set(SECOND, 0);
    }
    if (timeMode < SHOW_MINUTE) {
      calendar.set(MINUTE, 0);
    }
    if (timeMode < SHOW_HOUR) {
      calendar.set(HOUR_OF_DAY, 0);
    }
    calendar.set(MILLISECOND, 0);

    String[] days = new DateFormatSymbols(this.locale).getWeekdays();
    dayNames = new String[7];
    // week starts on monday!
    for (int i=0; i < 6; i++) {
      dayNames[i] = days[i+2].substring(0,2);
    }
    dayNames[6] = days[1].substring(0,2);

    secondField.setVisible(timeMode >= SHOW_SECOND);
    minuteField.setVisible(timeMode >= SHOW_MINUTE);
    hourField.setVisible(timeMode >= SHOW_HOUR);

    // setup the calsheet
    setupCalSheet();
  }




  /**
   * Sets the current calendar value.
   *
   * @param cal the calendar value
   */
  public void setCalendar(GregorianCalendar cal)  {
    this.calendar = cal;
    setupCalSheet();
  }

  /**
   * Returns the current selected calendar value.
   *
   * @return the calendar value
   */
  public GregorianCalendar getCalendar()  {
    return calendar;
  }



  private void setupCalSheet() {

    // set year
    yearField.setYear(calendar.get(YEAR));
    // set month
    monthField.setMonth(calendar.get(MONTH));
    // set hour
    hourField.setHour(calendar.get(HOUR_OF_DAY));
    // set minute
    minuteField.setMinSec(calendar.get(MINUTE));
    // set seconds
    secondField.setMinSec(calendar.get(SECOND));

    // remove all buttons and labels from calPanel
    calendarPanel.removeAll();
    calendarPanel.add(new JLabel(""), null);  // week column
    for (int i=0; i < 7; i++) {
      JLabel dayLabel = new JLabel(dayNames[i]);
      dayLabel.setHorizontalAlignment(JLabel.CENTER);
      calendarPanel.add(dayLabel, null);
    }

    // figure out weekday of the first of the month
    GregorianCalendar firstCal = new GregorianCalendar(locale);
    firstCal.setTime(calendar.getTime());
    firstCal.set(DAY_OF_MONTH, 1);
    firstDayIndex =  firstCal.get(DAY_OF_WEEK) - 2;
    // this gives at least one day before the 1st
    if (firstDayIndex <= 0) {
      firstDayIndex += 7;
    }
    firstCal.add(DAY_OF_YEAR, -firstDayIndex); // go back to first day in week

    GregorianCalendar today = new GregorianCalendar(locale);  // current date
    Font thisMonthButtonFont  = new JToggleButton().getFont().deriveFont(Font.BOLD);
    Font otherMonthButtonFont = new JToggleButton().getFont().deriveFont(Font.ITALIC + Font.PLAIN);

    Color thisMonthBackgroundColor = PlafUtilities.getInstance().getTableSelectionBackgroundColor();
    Color thisMonthForegroundColor = PlafUtilities.getInstance().getTableSelectionForegroundColor();
    Color otherMonthBackgroundColor = PlafUtilities.getInstance().getTableBackgroundColor();
    Color otherMonthForegroundColor = PlafUtilities.getInstance().getTableForegroundColor();

    ButtonGroup dayButtonGroup = new ButtonGroup();

    // add day-buttons
    for (int compCount = 0; compCount < 48; compCount++)  {

      if (compCount % 8 == 0) {
        // insert week of year
        JLabel weekLabel = new JLabel(Integer.toString(firstCal.get(WEEK_OF_YEAR)));
        weekLabel.setHorizontalAlignment(JLabel.CENTER);
        calendarPanel.add(weekLabel, null);
        continue;
      }

      JToggleButton dayButton = new CalToggleButton(Integer.toString(firstCal.get(DAY_OF_MONTH)));

      if (firstCal.get(MONTH) == calendar.get(MONTH)) {
        dayButton.setBackground(thisMonthBackgroundColor);
        dayButton.setForeground(thisMonthForegroundColor);
        dayButton.setFont(thisMonthButtonFont);
        dayButton.setActionCommand(ACTION_THISMONTH);  // this month
      }
      else  {
        dayButton.setBackground(otherMonthBackgroundColor);
        dayButton.setForeground(otherMonthForegroundColor);
        dayButton.setFont(otherMonthButtonFont);
        if (compCount <= firstDayIndex)  {    // +1 bec. of week of year above!
          dayButton.setActionCommand(ACTION_PREVIOUSMONTH);  // previous month
        }
        else  {
          dayButton.setActionCommand(ACTION_NEXTMONTH);  // next month
        }
      }
      // check if today
      if (today.get(YEAR) == firstCal.get(YEAR) &&
          today.get(DAY_OF_YEAR) == firstCal.get(DAY_OF_YEAR))  {
        dayButton.setForeground(Color.blue);
      }
      dayButton.addActionListener(this::dayButton_actionPerformed);
      dayButtonGroup.add(dayButton);
      calendarPanel.add(dayButton, null);

      firstCal.add(DAY_OF_YEAR, 1);
    }

    selectDay();  // select the togglebutton for the current day of month
  }


  private void selectDay()  {
    Component[] comps = calendarPanel.getComponents();
    int buttonCount = 1;
    int day = calendar.get(DAY_OF_MONTH);
    Border border = BorderFactory.createLineBorder(PlafUtilities.getInstance().getAlarmColor());
    for (Component comp : comps) {
      if (comp instanceof JToggleButton) {
        JToggleButton button = (JToggleButton) comp;
        if (ACTION_THISMONTH.equals(button.getActionCommand())) {
          if (buttonCount == day) {
            button.setSelected(true);
            button.setBorder(border);
            button.setBorderPainted(true);
          }
          else {
            button.setBorder(null);
            button.setBorderPainted(false);
          }
          buttonCount++;
        }
      }
    }
  }


  private void dayButton_actionPerformed(ActionEvent e) {
    JToggleButton button = (JToggleButton)(e.getSource());
    String cmd = button.getActionCommand();   // one of P, M, N
    NumberFormat format = DecimalFormat.getNumberInstance();
    try {
      int day = format.parse(button.getText()).intValue();
      if (ACTION_THISMONTH.equals(cmd))  {
        calendar.set(DAY_OF_MONTH, day);
        selectDay();
      }
      else if (ACTION_PREVIOUSMONTH.equals(cmd)) {
        calendar.add(MONTH, -1);
        calendar.set(DAY_OF_MONTH, day);
        setupCalSheet();
      }
      else if (ACTION_NEXTMONTH.equals(cmd)) {
        calendar.add(MONTH, 1);
        calendar.set(DAY_OF_MONTH, day);
        setupCalSheet();
      }
    } catch (ParseException pe)  {
      // nothing to do?
    }
  }


  /**
   * notify all ActionListeners
   */
  private void fireActionPerformed (String action) {
    fireActionPerformed(new ActionEvent (this, ActionEvent.ACTION_PERFORMED, action));
  }





  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    selectPanel = new org.tentackle.swing.FormPanel();
    yearField = new org.tentackle.swing.YearSpinField();
    monthField = new org.tentackle.swing.MonthSpinField();
    hourField = new org.tentackle.swing.HourSpinField();
    minuteField = new org.tentackle.swing.MinSecSpinField();
    secondField = new org.tentackle.swing.MinSecSpinField();
    jLabel1 = new javax.swing.JLabel();
    calendarPanel = new org.tentackle.swing.FormPanel();

    setLayout(new java.awt.BorderLayout());

    selectPanel.setLayout(new java.awt.GridBagLayout());

    yearField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        yearFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        yearFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    selectPanel.add(yearField, gridBagConstraints);

    monthField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        monthFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        monthFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    selectPanel.add(monthField, gridBagConstraints);

    hourField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        hourFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        hourFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    selectPanel.add(hourField, gridBagConstraints);

    minuteField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        minuteFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        minuteFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    selectPanel.add(minuteField, gridBagConstraints);

    secondField.addValueListener(new org.tentackle.swing.ValueListener() {
      public void valueEntered(org.tentackle.swing.ValueEvent evt) {
        secondFieldValueEntered(evt);
      }
      public void valueChanged(org.tentackle.swing.ValueEvent evt) {
        secondFieldValueChanged(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 5);
    selectPanel.add(secondField, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    selectPanel.add(jLabel1, gridBagConstraints);

    add(selectPanel, java.awt.BorderLayout.PAGE_START);

    calendarPanel.setLayout(new java.awt.GridLayout(7, 8));
    add(calendarPanel, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents

  private void yearFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_yearFieldValueChanged
  }//GEN-LAST:event_yearFieldValueChanged

  private void yearFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_yearFieldValueEntered
    // user changed the year
    int yearDiff = yearField.getYear() - calendar.get(YEAR);
    if (yearDiff != 0)  {
      calendar.add(YEAR, yearDiff);
      setupCalSheet();
    }
  }//GEN-LAST:event_yearFieldValueEntered

  private void monthFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_monthFieldValueChanged
  }//GEN-LAST:event_monthFieldValueChanged

  private void monthFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_monthFieldValueEntered
    // user changed the month
    int monthDiff = monthField.getMonth() - calendar.get(MONTH);
    if (monthDiff != 0) {
      calendar.add(MONTH, monthDiff);
      setupCalSheet();
    }
  }//GEN-LAST:event_monthFieldValueEntered

  private void hourFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_hourFieldValueChanged
  }//GEN-LAST:event_hourFieldValueChanged

  private void hourFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_hourFieldValueEntered
    calendar.set(HOUR_OF_DAY, hourField.getHour());
  }//GEN-LAST:event_hourFieldValueEntered

  private void minuteFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_minuteFieldValueChanged
  }//GEN-LAST:event_minuteFieldValueChanged

  private void minuteFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_minuteFieldValueEntered
    calendar.set(MINUTE, minuteField.getMinSec());
  }//GEN-LAST:event_minuteFieldValueEntered

  private void secondFieldValueChanged(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_secondFieldValueChanged
  }//GEN-LAST:event_secondFieldValueChanged

  private void secondFieldValueEntered(org.tentackle.swing.ValueEvent evt) {//GEN-FIRST:event_secondFieldValueEntered
     calendar.set(SECOND, secondField.getMinSec());
  }//GEN-LAST:event_secondFieldValueEntered


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private org.tentackle.swing.FormPanel calendarPanel;
  private org.tentackle.swing.HourSpinField hourField;
  private javax.swing.JLabel jLabel1;
  private org.tentackle.swing.MinSecSpinField minuteField;
  private org.tentackle.swing.MonthSpinField monthField;
  private org.tentackle.swing.MinSecSpinField secondField;
  private org.tentackle.swing.FormPanel selectPanel;
  private org.tentackle.swing.YearSpinField yearField;
  // End of variables declaration//GEN-END:variables

}
