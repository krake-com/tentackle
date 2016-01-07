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


package org.tentackle.swing.rdc;

import java.beans.*;

/**
 * BeanInfo for {@link PdoFieldPanel}.
 *
 * @author harald
 */
public class PdoFieldPanelBeanInfo extends SimpleBeanInfo {

    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.rdc.PdoFieldPanel.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "PdoFieldPanel" );
        beanDescriptor.setShortDescription ( "Field to select an Pdo by a unique key" );//GEN-HEADEREND:BeanDescriptor

    // Here you can add code for customizing the BeanDescriptor.

        return beanDescriptor;     }//GEN-LAST:BeanDescriptor


    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_autoSelect = 0;
    private static final int PROPERTY_background = 1;
    private static final int PROPERTY_bindable = 2;
    private static final int PROPERTY_bindingPath = 3;
    private static final int PROPERTY_changeable = 4;
    private static final int PROPERTY_checkExistsEnabled = 5;
    private static final int PROPERTY_columns = 6;
    private static final int PROPERTY_convert = 7;
    private static final int PROPERTY_defaultColumns = 8;
    private static final int PROPERTY_dropEnabled = 9;
    private static final int PROPERTY_editButtonVisible = 10;
    private static final int PROPERTY_font = 11;
    private static final int PROPERTY_foreground = 12;
    private static final int PROPERTY_helpURL = 13;
    private static final int PROPERTY_infoColumns = 14;
    private static final int PROPERTY_infoFieldVisible = 15;
    private static final int PROPERTY_invalidChars = 16;
    private static final int PROPERTY_link = 17;
    private static final int PROPERTY_linkId = 18;
    private static final int PROPERTY_mandatory = 19;
    private static final int PROPERTY_maxColumns = 20;
    private static final int PROPERTY_searchButtonVisible = 21;
    private static final int PROPERTY_toolTipText = 22;
    private static final int PROPERTY_validChars = 23;
    private static final int PROPERTY_visible = 24;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[25];
    
        try {
            properties[PROPERTY_autoSelect] = new PropertyDescriptor ( "autoSelect", org.tentackle.swing.rdc.PdoFieldPanel.class, "isAutoSelect", "setAutoSelect" ); // NOI18N
            properties[PROPERTY_autoSelect].setPreferred ( true );
            properties[PROPERTY_background] = new PropertyDescriptor ( "background", org.tentackle.swing.rdc.PdoFieldPanel.class, "getBackground", "setBackground" ); // NOI18N
            properties[PROPERTY_bindable] = new PropertyDescriptor ( "bindable", org.tentackle.swing.rdc.PdoFieldPanel.class, "isBindable", "setBindable" ); // NOI18N
            properties[PROPERTY_bindable].setPreferred ( true );
            properties[PROPERTY_bindingPath] = new PropertyDescriptor ( "bindingPath", org.tentackle.swing.rdc.PdoFieldPanel.class, "getBindingPath", "setBindingPath" ); // NOI18N
            properties[PROPERTY_bindingPath].setPreferred ( true );
            properties[PROPERTY_changeable] = new PropertyDescriptor ( "changeable", org.tentackle.swing.rdc.PdoFieldPanel.class, "isChangeable", "setChangeable" ); // NOI18N
            properties[PROPERTY_changeable].setPreferred ( true );
            properties[PROPERTY_checkExistsEnabled] = new PropertyDescriptor ( "checkExistsEnabled", org.tentackle.swing.rdc.PdoFieldPanel.class, "isCheckExistsEnabled", "setCheckExistsEnabled" ); // NOI18N
            properties[PROPERTY_checkExistsEnabled].setPreferred ( true );
            properties[PROPERTY_columns] = new PropertyDescriptor ( "columns", org.tentackle.swing.rdc.PdoFieldPanel.class, "getColumns", "setColumns" ); // NOI18N
            properties[PROPERTY_convert] = new PropertyDescriptor ( "convert", org.tentackle.swing.rdc.PdoFieldPanel.class, "getConvert", "setConvert" ); // NOI18N
            properties[PROPERTY_convert].setPreferred ( true );
            properties[PROPERTY_defaultColumns] = new PropertyDescriptor ( "defaultColumns", org.tentackle.swing.rdc.PdoFieldPanel.class, "getDefaultColumns", "setDefaultColumns" ); // NOI18N
            properties[PROPERTY_defaultColumns].setPreferred ( true );
            properties[PROPERTY_dropEnabled] = new PropertyDescriptor ( "dropEnabled", org.tentackle.swing.rdc.PdoFieldPanel.class, "isDropEnabled", "setDropEnabled" ); // NOI18N
            properties[PROPERTY_dropEnabled].setPreferred ( true );
            properties[PROPERTY_editButtonVisible] = new PropertyDescriptor ( "editButtonVisible", org.tentackle.swing.rdc.PdoFieldPanel.class, "isEditButtonVisible", "setEditButtonVisible" ); // NOI18N
            properties[PROPERTY_editButtonVisible].setPreferred ( true );
            properties[PROPERTY_font] = new PropertyDescriptor ( "font", org.tentackle.swing.rdc.PdoFieldPanel.class, "getFont", "setFont" ); // NOI18N
            properties[PROPERTY_foreground] = new PropertyDescriptor ( "foreground", org.tentackle.swing.rdc.PdoFieldPanel.class, "getForeground", "setForeground" ); // NOI18N
            properties[PROPERTY_helpURL] = new PropertyDescriptor ( "helpURL", org.tentackle.swing.rdc.PdoFieldPanel.class, "getHelpURL", "setHelpURL" ); // NOI18N
            properties[PROPERTY_helpURL].setPreferred ( true );
            properties[PROPERTY_infoColumns] = new PropertyDescriptor ( "infoColumns", org.tentackle.swing.rdc.PdoFieldPanel.class, "getInfoColumns", "setInfoColumns" ); // NOI18N
            properties[PROPERTY_infoColumns].setPreferred ( true );
            properties[PROPERTY_infoFieldVisible] = new PropertyDescriptor ( "infoFieldVisible", org.tentackle.swing.rdc.PdoFieldPanel.class, "isInfoFieldVisible", "setInfoFieldVisible" ); // NOI18N
            properties[PROPERTY_infoFieldVisible].setPreferred ( true );
            properties[PROPERTY_invalidChars] = new PropertyDescriptor ( "invalidChars", org.tentackle.swing.rdc.PdoFieldPanel.class, "getInvalidChars", "setInvalidChars" ); // NOI18N
            properties[PROPERTY_link] = new PropertyDescriptor ( "link", org.tentackle.swing.rdc.PdoFieldPanel.class, "getLink", "setLink" ); // NOI18N
            properties[PROPERTY_link].setExpert ( true );
            properties[PROPERTY_linkId] = new PropertyDescriptor ( "linkId", org.tentackle.swing.rdc.PdoFieldPanel.class, "getLinkId", null ); // NOI18N
            properties[PROPERTY_linkId].setExpert ( true );
            properties[PROPERTY_mandatory] = new PropertyDescriptor ( "mandatory", org.tentackle.swing.rdc.PdoFieldPanel.class, "isMandatory", "setMandatory" ); // NOI18N
            properties[PROPERTY_mandatory].setPreferred ( true );
            properties[PROPERTY_maxColumns] = new PropertyDescriptor ( "maxColumns", org.tentackle.swing.rdc.PdoFieldPanel.class, "getMaxColumns", "setMaxColumns" ); // NOI18N
            properties[PROPERTY_maxColumns].setPreferred ( true );
            properties[PROPERTY_searchButtonVisible] = new PropertyDescriptor ( "searchButtonVisible", org.tentackle.swing.rdc.PdoFieldPanel.class, "isSearchButtonVisible", "setSearchButtonVisible" ); // NOI18N
            properties[PROPERTY_searchButtonVisible].setPreferred ( true );
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", org.tentackle.swing.rdc.PdoFieldPanel.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_toolTipText].setPreferred ( true );
            properties[PROPERTY_validChars] = new PropertyDescriptor ( "validChars", org.tentackle.swing.rdc.PdoFieldPanel.class, "getValidChars", "setValidChars" ); // NOI18N
            properties[PROPERTY_validChars].setPreferred ( true );
            properties[PROPERTY_visible] = new PropertyDescriptor ( "visible", org.tentackle.swing.rdc.PdoFieldPanel.class, "isVisible", "setVisible" ); // NOI18N
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Properties

    // Here you can add code for customizing the properties array.

        return properties;     }//GEN-LAST:Properties

    // Event set information will be obtained from introspection.//GEN-FIRST:Events
    private static EventSetDescriptor[] eventSets = null;
    private static EventSetDescriptor[] getEdescriptor(){//GEN-HEADEREND:Events

    // Here you can add code for customizing the event sets array.

        return eventSets;     }//GEN-LAST:Events

    // Method information will be obtained from introspection.//GEN-FIRST:Methods
    private static MethodDescriptor[] methods = null;
    private static MethodDescriptor[] getMdescriptor(){//GEN-HEADEREND:Methods

    // Here you can add code for customizing the methods array.

        return methods;     }//GEN-LAST:Methods

  private static java.awt.Image iconColor16 = null;//GEN-BEGIN:IconsDef
  private static java.awt.Image iconColor32 = null;
  private static java.awt.Image iconMono16 = null;
  private static java.awt.Image iconMono32 = null;//GEN-END:IconsDef
    private static String iconNameC16 = "/org/tentackle/swing/rdc/images/images16/PdoFieldPanel.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/rdc/images/images32/PdoFieldPanel.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons

    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx


    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.rdc.PdoFieldPanel.class.getSuperclass();
        BeanInfo sbi = null;
        try {
            sbi = Introspector.getBeanInfo(superclass);//GEN-HEADEREND:Superclass

  // Here you can add code for customizing the Superclass BeanInfo.

            } catch(IntrospectionException ex) { }  return new BeanInfo[] { sbi }; }//GEN-LAST:Superclass

  /**
   * Gets the bean's <code>BeanDescriptor</code>s.
   *
   * @return BeanDescriptor describing the editable
   * properties of this bean.  May return null if the
   * information should be obtained by automatic analysis.
   */
  public BeanDescriptor getBeanDescriptor() {
    return getBdescriptor();
  }

  /**
   * Gets the bean's <code>PropertyDescriptor</code>s.
   *
   * @return An array of PropertyDescriptors describing the editable
   * properties supported by this bean.  May return null if the
   * information should be obtained by automatic analysis.
   * <p>
   * If a property is indexed, then its entry in the result array will
   * belong to the IndexedPropertyDescriptor subclass of PropertyDescriptor.
   * A client of getPropertyDescriptors can use "instanceof" to check
   * if a given PropertyDescriptor is an IndexedPropertyDescriptor.
   */
  public PropertyDescriptor[] getPropertyDescriptors() {
    return getPdescriptor();
  }

  /**
   * Gets the bean's <code>EventSetDescriptor</code>s.
   *
   * @return  An array of EventSetDescriptors describing the kinds of
   * events fired by this bean.  May return null if the information
   * should be obtained by automatic analysis.
   */
  public EventSetDescriptor[] getEventSetDescriptors() {
    return getEdescriptor();
  }

  /**
   * Gets the bean's <code>MethodDescriptor</code>s.
   *
   * @return  An array of MethodDescriptors describing the methods
   * implemented by this bean.  May return null if the information
   * should be obtained by automatic analysis.
   */
  public MethodDescriptor[] getMethodDescriptors() {
    return getMdescriptor();
  }

  /**
   * A bean may have a "default" property that is the property that will
   * mostly commonly be initially chosen for update by human's who are
   * customizing the bean.
   * @return  Index of default property in the PropertyDescriptor array
   * 		returned by getPropertyDescriptors.
   * <P>	Returns -1 if there is no default property.
   */
  public int getDefaultPropertyIndex() {
    return defaultPropertyIndex;
  }

  /**
   * A bean may have a "default" event that is the event that will
   * mostly commonly be used by human's when using the bean.
   * @return Index of default event in the EventSetDescriptor array
   *		returned by getEventSetDescriptors.
   * <P>	Returns -1 if there is no default event.
   */
  public int getDefaultEventIndex() {
    return defaultEventIndex;
  }

  /**
   * This method returns an image object that can be used to
   * represent the bean in toolboxes, toolbars, etc.   Icon images
   * will typically be GIFs, but may in future include other formats.
   * <p>
   * Beans aren't required to provide icons and may return null from
   * this method.
   * <p>
   * There are four possible flavors of icons (16x16 color,
   * 32x32 color, 16x16 mono, 32x32 mono).  If a bean choses to only
   * support a single icon we recommend supporting 16x16 color.
   * <p>
   * We recommend that icons have a "transparent" background
   * so they can be rendered onto an existing background.
   *
   * @param  iconKind  The kind of icon requested.  This should be
   *    one of the constant values ICON_COLOR_16x16, ICON_COLOR_32x32,
   *    ICON_MONO_16x16, or ICON_MONO_32x32.
   * @return  An image object representing the requested icon.  May
   *    return null if no suitable icon is available.
   */
  public java.awt.Image getIcon(int iconKind) {
    switch ( iconKind ) {
      case ICON_COLOR_16x16:
        if ( iconNameC16 == null )
          return null;
        else {
          if( iconColor16 == null )
            iconColor16 = loadImage( iconNameC16 );
          return iconColor16;
        }
      case ICON_COLOR_32x32:
        if ( iconNameC32 == null )
          return null;
        else {
          if( iconColor32 == null )
            iconColor32 = loadImage( iconNameC32 );
          return iconColor32;
        }
      case ICON_MONO_16x16:
        if ( iconNameM16 == null )
          return null;
        else {
          if( iconMono16 == null )
            iconMono16 = loadImage( iconNameM16 );
          return iconMono16;
        }
      case ICON_MONO_32x32:
        if ( iconNameM32 == null )
          return null;
        else {
          if( iconMono32 == null )
            iconMono32 = loadImage( iconNameM32 );
          return iconMono32;
        }
      default: return null;
    }
  }

}

