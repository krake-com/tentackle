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


package org.tentackle.swing.plaf.tmetal;

import org.tentackle.common.Service;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

/**
 * Extended Tentackle Ocean Look And Feel.
 * 
 * @author harald
 */
@Service(TentackleLookAndFeel.class)
public class TOceanLookAndFeel extends TMetalLookAndFeel {

  private static final long serialVersionUID = 1L;
  
  /** Creates a new instance of TOceanLookAndFeel */
  public TOceanLookAndFeel() {
    super();
  }
  
  
  public static String getTOceanName() {
      return "TOcean";
  }


  public static String getTOceanDescription() {
      return "Enhanced Ocean LnF for Tentackle";
  }  
  
  @Override
  public String getName() {
      return getTOceanName();
  }
  
  @Override
  public String getDescription() {
      return getTOceanDescription();
  }
  
  @Override
  public void initialize() {
    super.initialize();
    TMetalLookAndFeel.setCurrentTheme(new TOceanTheme());
  }
  
}
