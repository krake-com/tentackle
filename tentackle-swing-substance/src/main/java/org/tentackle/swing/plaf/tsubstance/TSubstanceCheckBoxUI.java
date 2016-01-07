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

import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.plaf.ComponentUI;
import org.jvnet.substance.SubstanceCheckBoxUI;
import org.jvnet.substance.utils.SubstanceCoreUtilities;

/**
 * UI for JCheckBox/FormCheckBox.
 *
 * @todo mandatory property still missing
 * 
 * @author harald
 */
public class TSubstanceCheckBoxUI extends SubstanceCheckBoxUI {


	public static ComponentUI createUI(JComponent comp) {
		SubstanceCoreUtilities.testComponentCreationThreadingViolation(comp);
		return new TSubstanceCheckBoxUI((JToggleButton) comp);
	}


	public TSubstanceCheckBoxUI(JToggleButton button) {
		super(button);
	}


}
