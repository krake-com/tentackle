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

import javax.swing.Icon;
import javax.swing.JLabel;
import org.tentackle.locale.I18nTranslatable;
import org.tentackle.locale.I18nTranslation;

/**
 * A form label.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class FormLabel extends JLabel implements I18nTranslatable {

  private PropertyGroup propertyGroup;              // the property group, null = none
  private I18nTranslation i18nTranslation;          // the I18N translated text



  /**
   * Creates a <code>FormLabel</code> instance with the specified
   * text, image, and horizontal alignment.
   * The label is centered vertically in its display area.
   * The text is on the trailing edge of the image.
   *
   * @param text  The text to be displayed by the label.
   * @param icon  The image to be displayed by the label.
   * @param horizontalAlignment  One of the following constants
   *           defined in <code>SwingConstants</code>:
   *           <code>LEFT</code>,
   *           <code>CENTER</code>,
   *           <code>RIGHT</code>,
   *           <code>LEADING</code> or
   *           <code>TRAILING</code>.
   */
  public FormLabel(String text, Icon icon, int horizontalAlignment) {
    super(text, icon, horizontalAlignment);
  }

  /**
   * Creates a <code>FormLabel</code> instance with the specified
   * text and horizontal alignment.
   * The label is centered vertically in its display area.
   *
   * @param text  The text to be displayed by the label.
   * @param horizontalAlignment  One of the following constants
   *           defined in <code>SwingConstants</code>:
   *           <code>LEFT</code>,
   *           <code>CENTER</code>,
   *           <code>RIGHT</code>,
   *           <code>LEADING</code> or
   *           <code>TRAILING</code>.
   */
  public FormLabel(String text, int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  /**
   * Creates a <code>FormLabel</code> instance with the specified text.
   * The label is aligned against the leading edge of its display area,
   * and centered vertically.
   *
   * @param text  The text to be displayed by the label.
   */
  public FormLabel(String text) {
    super(text);
  }

  /**
   * Creates a <code>FormLabel</code> instance with the specified
   * image and horizontal alignment.
   * The label is centered vertically in its display area.
   *
   * @param image  The image to be displayed by the label.
   * @param horizontalAlignment  One of the following constants
   *           defined in <code>SwingConstants</code>:
   *           <code>LEFT</code>,
   *           <code>CENTER</code>,
   *           <code>RIGHT</code>,
   *           <code>LEADING</code> or
   *           <code>TRAILING</code>.
   */
  public FormLabel(Icon image, int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  /**
   * Creates a <code>FormLabel</code> instance with the specified image.
   * The label is centered vertically and horizontally
   * in its display area.
   *
   * @param image  The image to be displayed by the label.
   */
  public FormLabel(Icon image) {
    super(image);
  }

  /**
   * Creates a <code>FormLabel</code> instance with
   * no image and with an empty string for the title.
   * The label is centered vertically
   * in its display area.
   * The label's contents, once set, will be displayed on the leading edge
   * of the label's display area.
   */
  public FormLabel() {
    super();
  }




  /**
   * Sets the translated text.<br>
   * This method is used by I18n-Wizards in IDEs to set the original text,
   * the locale and the translated text.
   *
   * @param i18nTranslation the translated text, null if none
   */
  public void setText(I18nTranslation i18nTranslation) {
    setI18nTranslation(i18nTranslation);
  }

  @Override
  public void setI18nTranslation(I18nTranslation i18nTranslation) {
    this.i18nTranslation = i18nTranslation;
    setText(i18nTranslation == null ? null : i18nTranslation.getText());
  }

  @Override
  public I18nTranslation getI18nTranslation() {
    return i18nTranslation;
  }



  /**
   * Sets the property group for this component.
   *
   * @param group the property group
   */
  public void setPropertyGroup(PropertyGroup group) {
    if (this.propertyGroup != group) {
      if (this.propertyGroup != null) {
        if (this.propertyGroup.removeComponent(this)) {
          this.propertyGroup = null;
        }
        else  {
          throw new GUIRuntimeException("label " + this + " not in property group " + this.propertyGroup);
        }
      }
      if (group != null) {
        if (group.addComponent(this)) {
          this.propertyGroup = group;
        }
        else  {
          throw new GUIRuntimeException("label " + this + " already in property group " + group);
        }
      }
    }
  }

  /**
   * Gets the property group for this component.
   *
   * @return the property group
   */
  public PropertyGroup getPropertyGroup() {
    return propertyGroup;
  }

}
