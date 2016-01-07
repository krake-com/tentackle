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

import java.util.function.Function;


/**
 * A FormComponentPanel with an embedded FormFieldComponent.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormFieldComponentPanel extends FormComponentPanel implements FormFieldComponent {

  /**
   * Creates a {@code FormFieldComponentPanel} for a given {@code FormFieldComponent}.
   *
   * @param comp the component
   */
  public FormFieldComponentPanel(FormFieldComponent comp) {
    super(comp);
  }

  /**
   * Creates a {@code FormFieldComponentPanel} for a {@link StringFormField}.
   */
  public FormFieldComponentPanel() {
    super();
  }


  /**
   * Gets the embedded form field component.
   *
   * @return the component
   */
  @Override
  public FormFieldComponent getFormComponent() {
    return (FormFieldComponent)super.getFormComponent();
  }


  // ---------------------- implements FormFieldComponent --------------------

  @Override
  public void setColumns(int columns) {
    getFormComponent().setColumns(columns);
  }

  @Override
  public int getColumns() {
    return getFormComponent().getColumns();
  }

  @Override
  public void setFormat (String format) {
    getFormComponent().setFormat(format);
  }

  @Override
  public String getFormat() {
    return getFormComponent().getFormat();
  }

  @Override
  public String doFormat(Object object) {
    return getFormComponent().doFormat(object);
  }

  @Override
  public void clearText() {
    getFormComponent().clearText();
  }

  @Override
  public boolean isEmpty()  {
    return getFormComponent().isEmpty();
  }

  @Override
  public void setText (String str)  {
    getFormComponent().setText(str);
  }

  @Override
  public String getText() {
    return getFormComponent().getText();
  }

  @Override
  public void setEraseFirst(boolean erasefirst) {
    getFormComponent().setEraseFirst(erasefirst);
  }

  @Override
  public boolean isEraseFirst() {
    return getFormComponent().isEraseFirst();
  }

  @Override
  public void setMaxColumns (int columns) {
    getFormComponent().setMaxColumns(columns);
  }

  @Override
  public int  getMaxColumns ()  {
    return getFormComponent().getMaxColumns();
  }

  @Override
  public void setDefaultColumns (int columns) {
    getFormComponent().setDefaultColumns(columns);
  }

  @Override
  public int  getDefaultColumns ()  {
    return getFormComponent().getDefaultColumns();
  }

  @Override
  public  void  setConvert (char convert)  {
    getFormComponent().setConvert(convert);
  }

  @Override
  public  char  getConvert ()  {
    return getFormComponent().getConvert();
  }

  @Override
  public void setConverter(Function<String,String> converter) {
    getFormComponent().setConverter(converter);
  }

  @Override
  public Function<String,String> getConverter() {
    return getFormComponent().getConverter();
  }

  @Override
  public  void  setAdjust (char adjust)  {
    getFormComponent().setAdjust(adjust);
  }

  @Override
  public char getAdjust() {
    return getFormComponent().getAdjust();
  }

  @Override
  public  void  setAutoSelect (boolean autoselect)  {
    getFormComponent().setAutoSelect(autoselect);
  }

  @Override
  public boolean isAutoSelect()  {
    return getFormComponent().isAutoSelect();
  }

  @Override
  public  void  setAutoNext (boolean autonext)  {
    getFormComponent().setAutoNext(autonext);
  }

  @Override
  public boolean isAutoNext()  {
    return getFormComponent().isAutoNext();
  }

  @Override
  public  void  setValidChars (String str)  {
    getFormComponent().setValidChars(str);
  }

  @Override
  public String getValidChars ()  {
    return getFormComponent().getValidChars();
  }

  @Override
  public  void  setInvalidChars (String str)  {
    getFormComponent().setInvalidChars(str);
  }

  @Override
  public String getInvalidChars ()  {
    return getFormComponent().getInvalidChars();
  }

  @Override
  public  void  setFiller (char filler)  {
    getFormComponent().setFiller(filler);
  }

  @Override
  public char getFiller ()  {
    return getFormComponent().getFiller();
  }

  @Override
  public  void  setOverwrite (boolean override)  {
    getFormComponent().setOverwrite(override);
  }

  @Override
  public boolean isOverwrite()  {
    return getFormComponent().isOverwrite();
  }

  @Override
  public  void  setStartEditLeftmost (boolean startEditLeftmost)  {
    getFormComponent().setStartEditLeftmost(startEditLeftmost);
  }

  @Override
  public boolean isStartEditLeftmost()  {
    return getFormComponent().isStartEditLeftmost();
  }

  @Override
  public int getErrorOffset() {
    return getFormComponent().getErrorOffset();
  }

  @Override
  public void setErrorOffset(int errorOffset) {
    getFormComponent().setErrorOffset(errorOffset);
  }

  @Override
  public String getErrorMessage() {
    return getFormComponent().getErrorMessage();
  }

  @Override
  public void setErrorMessage(String errorMessage) {
    getFormComponent().setErrorMessage(errorMessage);
  }

  @Override
  public boolean isInhibitAutoSelect()  {
    return getFormComponent().isInhibitAutoSelect();
  }

  @Override
  public void setInhibitAutoSelect(boolean inhibitAutoSelect) {
    getFormComponent().setInhibitAutoSelect(inhibitAutoSelect);
  }

  @Override
  public boolean isCaretLeft()  {
    return getFormComponent().isCaretLeft();
  }

  @Override
  public boolean isCaretRight() {
    return getFormComponent().isCaretRight();
  }

  @Override
  public void setCaretPosition(int pos) {
    getFormComponent().setCaretPosition(pos);
  }

  @Override
  public int getCaretPosition() {
    return getFormComponent().getCaretPosition();
  }

  @Override
  public void setHorizontalAlignment(int align) {
    getFormComponent().setHorizontalAlignment(align);
  }

  @Override
  public int getHorizontalAlignment() {
    return getFormComponent().getHorizontalAlignment();
  }

  @Override
  public void setVerticalAlignment(int alignment) {
    getFormComponent().setVerticalAlignment(alignment);
  }

  @Override
  public int  getVerticalAlignment() {
    return getFormComponent().getVerticalAlignment();
  }

  @Override
  public void setCaretLeft() {
    getFormComponent().setCaretLeft();
  }

  @Override
  public void setCaretRight() {
    getFormComponent().setCaretRight();
  }

  @Override
  public void upLeft() {
    getFormComponent().upLeft();
  }

  @Override
  public void downRight() {
    getFormComponent().downRight();
  }

  @Override
  public void postActionEvent() {
    getFormComponent().postActionEvent();
  }

  @Override
  public void setToolTipText(String text) {
    getFormComponent().setToolTipText(text);
  }

  @Override
  public String getToolTipText() {
    return getFormComponent().getToolTipText();
  }

}