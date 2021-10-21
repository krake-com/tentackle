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

package org.tentackle.fx.component.delegate;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import org.tentackle.common.DateHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.component.FxDatePicker;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;


/**
 * Delegate for FxDatePicker.
 *
 * @author harald
 */
public class FxDatePickerDelegate extends FxTextComponentDelegate {

  private final FxDatePicker component;   // the component
  private String text;                    // copy of the editor's text

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxDatePickerDelegate(FxDatePicker component) {
    this.component = component;
    component.setConverter(new DatePickerConverter());
    component.getEditor().setTextFormatter(new TextFormatter<>(this));
  }

  @Override
  public FxDatePicker getComponent() {
    return component;
  }

  @Override
  public void setColumns(int columns) {
    component.getEditor().setPrefColumnCount(columns);
  }

  @Override
  public int getColumns() {
    return component.getEditor().getPrefColumnCount();
  }

  @Override
  public String getViewObject() {
    text = component.getEditor().getText();
    return StringHelper.isAllWhitespace(text) ? null : text;
  }

  @Override
  public void setViewObject(Object viewObject) {
    text = (String) viewObject;
    component.getEditor().setText(text);
  }

  @Override
  public void setViewValue(Object value) {
    Pos alignment = component.getTextAlignment();
    if (alignment != null) {
      component.getEditor().setAlignment(alignment);
    }
    super.setViewValue(value);
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    Integer errorOffset = getErrorOffset();
    if (errorOffset != null) {
      Platform.runLater(() -> {
        component.getEditor().deselect();
        component.getEditor().positionCaret(errorOffset);
      });
    }
  }

  @Override
  public void autoSelect() {
    if (isAutoSelect()) {
      component.getEditor().selectAll();
    }
    else {
      component.getEditor().deselect();
    }
  }

  @Override
  public void setType(Class<?> type) {
    // allow only Date and Timestamp, others don't make any sense to the datepicker popup.
    if ((!Date.class.isAssignableFrom(type) || Time.class.isAssignableFrom(type)) &&
        !LocalDate.class.isAssignableFrom(type) &&
        !LocalTime.class.isAssignableFrom(type) &&
        !LocalDateTime.class.isAssignableFrom(type)) {
      throw new FxRuntimeException(type.getName() + " not applicable to " + component.getClass().getName());
    }
    super.setType(type);
  }

  @Override
  public Node getNode() {
    return component.getEditor();
  }



  /**
   * Converter used for the LocalDate part only.
   */
  private class DatePickerConverter extends StringConverter<LocalDate> {

    private Date lastDate;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
		public String toString(LocalDate object) {
      if (component.isChangeable()) {
        if (object != null) {
          // merge localDate into lastDate
          Calendar cal = DateHelper.today();
          if (lastDate != null) {
            cal.setTime(lastDate);
          }
          else {
            cal.set(HOUR_OF_DAY, 0);
            cal.set(MINUTE, 0);
            cal.set(SECOND, 0);
            cal.set(MILLISECOND, 0);
          }
          cal.set(object.getYear(), object.getMonthValue() - 1, object.getDayOfMonth());
          ValueTranslator translator = getValueTranslator();
          Object modelValue;
          if (LocalDate.class.isAssignableFrom(component.getType())) {
            modelValue = new java.sql.Date(cal.getTimeInMillis()).toLocalDate();
          }
          else if (LocalDateTime.class.isAssignableFrom(component.getType())) {
            modelValue = new java.sql.Timestamp(cal.getTimeInMillis()).toLocalDateTime();
          }
          else {
            modelValue = cal.getTime();
          }
          return (String) translator.toView(modelValue);
        }
        else {
          return null;
        }
      }
      else {
        return text;
      }
		}

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
		public LocalDate fromString(String value) {
      if (getError() == null) {
        ValueTranslator translator = getValueTranslator();
        Object modelValue = translator.toModel(value);
        if (modelValue instanceof Date) {
          lastDate = (Date) modelValue;
        }
        else if (modelValue instanceof LocalDate) {
          lastDate = java.sql.Date.valueOf((LocalDate) modelValue);
        }
        else if (modelValue instanceof LocalDateTime) {
          lastDate = java.sql.Timestamp.valueOf((LocalDateTime) modelValue);
        }
        else {
          lastDate = null;
        }
      }
			return fromDate(lastDate);
		}

    private LocalDate fromDate(Date date) {
      LocalDate localDate = null;
      if (date != null) {
        Instant instant = Instant.ofEpochMilli(date.getTime());
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        localDate = localDateTime.toLocalDate();
      }
      return localDate;
    }
	}

}
