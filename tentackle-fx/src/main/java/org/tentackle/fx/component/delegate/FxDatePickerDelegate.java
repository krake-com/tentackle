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

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.ValueTranslator;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.fx.component.FxDatePicker;
import org.tentackle.fx.component.auto.AutoCompletionController;
import org.tentackle.misc.ObjectUtilities;
import org.tentackle.misc.SubString;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.function.Function;


/**
 * Delegate for FxDatePicker.
 *
 * @author harald
 */
public class FxDatePickerDelegate extends FxTextComponentDelegate {

  private final FxDatePicker component;                         // the component
  private final DatePickerConverter converter;                  // the converter for the datepicker component
  private String lastText;                                      // copy of the editor's text, counterpart to lastDate below
  private AutoCompletionController autoCompletionController;    // != null if auto-completion enabled

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public FxDatePickerDelegate(FxDatePicker component) {
    this.component = component;
    this.converter = new DatePickerConverter();
    component.setConverter(converter);
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
    lastText = component.getEditor().getText();
    return StringHelper.isAllWhitespace(lastText) ? null : lastText;
  }

  @Override
  public void setViewObject(Object viewObject) {
    lastText = (String) viewObject;
    component.getEditor().setText(lastText);
    // necessary since FX18!
    // see javafx.scene.control.DatePicker#commitValue, which cannot be overridden since final,
    // like 99.99% of all the FX stuff :(
    component.setValue(converter.fromString(lastText));   // avoid invalidation due to changed value
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
    // allow only java.util.Date (except Time), LocalDate, LocalDateTime and LocalTime
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

    private LocalDate lastDate;     // counterpart to lastText above

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public String toString(LocalDate object) {
      if (component.isChangeable()) {
        if (object != null) {
          if (object.equals(lastDate) &&
              lastText != null && lastText.equals(component.getEditor().getText())) {
            // see javafx.scene.control.DatePicker#cancelEdit
            // same reason as in #setViewObject, necessary since FX18!
            // avoid clearing the time if date unchanged when just pressing ESC or closing w/o any change
            return lastText;
          }
          // date has changed
          ValueTranslator translator = getValueTranslator();
          Object modelValue = ObjectUtilities.getInstance().convert(component.getType(), object);
          return (String) translator.toView(modelValue);
        }
        else {
          return null;
        }
      }
      else {
        return lastText;
      }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public LocalDate fromString(String value) {
      if (getError() == null) {
        ValueTranslator translator = getValueTranslator();
        Object modelValue = translator.toModel(value);
        LocalDate oldLastDate = lastDate;
        lastDate = ObjectUtilities.getInstance().convert(LocalDate.class, modelValue);  // narrow to LocalDate for DatePicker
        if (lastDate != null && !lastDate.equals(oldLastDate)) {
          // this allows changing the date AND the time w/o the time being cleared to 00:00:00 (see FX18 hacks)
          FxComponentBinding binding = getBinding();
          if (binding != null) {
            binding.setModelValue(modelValue);
            updateView();
          }
        }
      }
      return lastDate;
    }
  }

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    super.setAutoCompletion(autoCompletion);
    TextField editor = component.getEditor();   // never null
    if (autoCompletion != null) {
      if (autoCompletionController == null) {
        autoCompletionController = FxUtilities.getInstance().createAutoCompletion(editor, autoCompletion);
      }
    }
    else {
      if (autoCompletionController != null) {
        autoCompletionController.close();
        autoCompletionController = null;
      }
    }
  }

}
