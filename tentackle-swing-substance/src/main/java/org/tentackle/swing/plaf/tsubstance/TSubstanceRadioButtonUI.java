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
package org.tentackle.swing.plaf.tsubstance;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import org.jvnet.substance.SubstanceRadioButtonUI;
import org.jvnet.substance.utils.SubstanceCoreUtilities;
import org.tentackle.swing.FormComponent;
import org.tentackle.swing.FormRadioButton;

/**
 * UI for JRadioButton/FormRadioButton.
 *
 * @todo mandatory property still missing
 * 
 * @author harald
 */
public class TSubstanceRadioButtonUI extends SubstanceRadioButtonUI implements PropertyChangeListener {

  public static ComponentUI createUI(JComponent comp) {
    SubstanceCoreUtilities.testComponentCreationThreadingViolation(comp);
    return new TSubstanceRadioButtonUI((JToggleButton) comp);
  }

  public TSubstanceRadioButtonUI(JToggleButton button) {
    super(button);
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (c instanceof FormRadioButton && ((FormRadioButton) c).isMandatory()) {
      updateBackground((JRadioButton) c);
    }
  }

  @Override
  protected void installListeners(final AbstractButton b) {
    super.installListeners(b);
    b.addPropertyChangeListener(this);
  }

  @Override
  protected void uninstallListeners(AbstractButton b) {
    super.uninstallListeners(b);
    b.removePropertyChangeListener(this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals("mandatory")) {
      updateBackground((JRadioButton) evt.getSource());
    }
  }

  
  // copied from BasicTextUI and extended by mandatory option

  /**
   * Updates the background of the text component based on whether the
   * text component is editable and/or enabled.
   *
   * @param c the JRadioButton that needs its background color updated
   */
  private void updateBackground(JRadioButton c) {
    Color background = c.getBackground();
    if (background instanceof UIResource) {
      String prefix = getPropertyPrefix();

      Color mandatoryBG =
              UIManager.getColor(prefix + ".mandatoryBackground", c.getLocale());
      Color bg =
              UIManager.getColor(prefix + ".background", c.getLocale());

      Color newColor = null;

      if (c instanceof FormComponent && ((FormComponent) c).isMandatory()) {
        newColor = mandatoryBG;
      }

      if (newColor == null) {
        newColor = bg;
      }

      if (newColor != null && newColor != background) {
        c.setBackground(newColor);
      }
    }
  }
}
