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

import java.beans.*;

/**
 * BeanInfo for FormComboBox.
 * 
 * @author harald
 */
public class FormComboBoxBeanInfo extends SimpleBeanInfo {
  

    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.FormComboBox.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "FormComboBox" );
        beanDescriptor.setShortDescription ( "A form-aware combo box" );//GEN-HEADEREND:BeanDescriptor
    
    // Here you can add code for customizing the BeanDescriptor.
    
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
  
  
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_allowDeselect = 0;
    private static final int PROPERTY_autoUpdate = 1;
    private static final int PROPERTY_bindable = 2;
    private static final int PROPERTY_bindingPath = 3;
    private static final int PROPERTY_cellEditorUsage = 4;
    private static final int PROPERTY_changeable = 5;
    private static final int PROPERTY_fireRunning = 6;
    private static final int PROPERTY_focusTraversalGroup = 7;
    private static final int PROPERTY_formTraversable = 8;
    private static final int PROPERTY_formValue = 9;
    private static final int PROPERTY_helpURL = 10;
    private static final int PROPERTY_honourChangeable = 11;
    private static final int PROPERTY_leadKeyTimeout = 12;
    private static final int PROPERTY_mandatory = 13;
    private static final int PROPERTY_multiKeySelectionManager = 14;
    private static final int PROPERTY_popupRenderer = 15;
    private static final int PROPERTY_propertyGroup = 16;
    private static final int PROPERTY_shortLongPopupEnabled = 17;
    private static final int PROPERTY_smartValueEntered = 18;
    private static final int PROPERTY_toolTipText = 19;
    private static final int PROPERTY_triggerValueChangedEnabled = 20;
    private static final int PROPERTY_valueAdjusting = 21;
    private static final int PROPERTY_valueEnteredOnSelect = 22;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[23];
    
        try {
            properties[PROPERTY_allowDeselect] = new PropertyDescriptor ( "allowDeselect", org.tentackle.swing.FormComboBox.class, "isAllowDeselect", "setAllowDeselect" ); // NOI18N
            properties[PROPERTY_allowDeselect].setPreferred ( true );
            properties[PROPERTY_autoUpdate] = new PropertyDescriptor ( "autoUpdate", org.tentackle.swing.FormComboBox.class, "isAutoUpdate", "setAutoUpdate" ); // NOI18N
            properties[PROPERTY_autoUpdate].setPreferred ( true );
            properties[PROPERTY_bindable] = new PropertyDescriptor ( "bindable", org.tentackle.swing.FormComboBox.class, "isBindable", "setBindable" ); // NOI18N
            properties[PROPERTY_bindable].setPreferred ( true );
            properties[PROPERTY_bindingPath] = new PropertyDescriptor ( "bindingPath", org.tentackle.swing.FormComboBox.class, "getBindingPath", "setBindingPath" ); // NOI18N
            properties[PROPERTY_bindingPath].setPreferred ( true );
            properties[PROPERTY_cellEditorUsage] = new PropertyDescriptor ( "cellEditorUsage", org.tentackle.swing.FormComboBox.class, "isCellEditorUsage", "setCellEditorUsage" ); // NOI18N
            properties[PROPERTY_cellEditorUsage].setExpert ( true );
            properties[PROPERTY_changeable] = new PropertyDescriptor ( "changeable", org.tentackle.swing.FormComboBox.class, "isChangeable", "setChangeable" ); // NOI18N
            properties[PROPERTY_changeable].setPreferred ( true );
            properties[PROPERTY_fireRunning] = new PropertyDescriptor ( "fireRunning", org.tentackle.swing.FormComboBox.class, "isFireRunning", "setFireRunning" ); // NOI18N
            properties[PROPERTY_fireRunning].setExpert ( true );
            properties[PROPERTY_focusTraversalGroup] = new PropertyDescriptor ( "focusTraversalGroup", org.tentackle.swing.FormComboBox.class, "getFocusTraversalGroup", "setFocusTraversalGroup" ); // NOI18N
            properties[PROPERTY_focusTraversalGroup].setPreferred ( true );
            properties[PROPERTY_formTraversable] = new PropertyDescriptor ( "formTraversable", org.tentackle.swing.FormComboBox.class, "isFormTraversable", "setFormTraversable" ); // NOI18N
            properties[PROPERTY_formTraversable].setPreferred ( true );
            properties[PROPERTY_formValue] = new PropertyDescriptor ( "formValue", org.tentackle.swing.FormComboBox.class, "getFormValue", "setFormValue" ); // NOI18N
            properties[PROPERTY_formValue].setExpert ( true );
            properties[PROPERTY_helpURL] = new PropertyDescriptor ( "helpURL", org.tentackle.swing.FormComboBox.class, "getHelpURL", "setHelpURL" ); // NOI18N
            properties[PROPERTY_helpURL].setPreferred ( true );
            properties[PROPERTY_honourChangeable] = new PropertyDescriptor ( "honourChangeable", org.tentackle.swing.FormComboBox.class, "isHonourChangeable", "setHonourChangeable" ); // NOI18N
            properties[PROPERTY_honourChangeable].setPreferred ( true );
            properties[PROPERTY_leadKeyTimeout] = new PropertyDescriptor ( "leadKeyTimeout", org.tentackle.swing.FormComboBox.class, "getLeadKeyTimeout", "setLeadKeyTimeout" ); // NOI18N
            properties[PROPERTY_leadKeyTimeout].setPreferred ( true );
            properties[PROPERTY_mandatory] = new PropertyDescriptor ( "mandatory", org.tentackle.swing.FormComboBox.class, "isMandatory", "setMandatory" ); // NOI18N
            properties[PROPERTY_mandatory].setPreferred ( true );
            properties[PROPERTY_multiKeySelectionManager] = new PropertyDescriptor ( "multiKeySelectionManager", org.tentackle.swing.FormComboBox.class, "isMultiKeySelectionManager", "setMultiKeySelectionManager" ); // NOI18N
            properties[PROPERTY_multiKeySelectionManager].setExpert ( true );
            properties[PROPERTY_popupRenderer] = new PropertyDescriptor ( "popupRenderer", org.tentackle.swing.FormComboBox.class, "getPopupRenderer", "setPopupRenderer" ); // NOI18N
            properties[PROPERTY_popupRenderer].setExpert ( true );
            properties[PROPERTY_propertyGroup] = new PropertyDescriptor ( "propertyGroup", org.tentackle.swing.FormComboBox.class, "getPropertyGroup", "setPropertyGroup" ); // NOI18N
            properties[PROPERTY_propertyGroup].setPreferred ( true );
            properties[PROPERTY_shortLongPopupEnabled] = new PropertyDescriptor ( "shortLongPopupEnabled", org.tentackle.swing.FormComboBox.class, "isShortLongPopupEnabled", "setShortLongPopupEnabled" ); // NOI18N
            properties[PROPERTY_shortLongPopupEnabled].setPreferred ( true );
            properties[PROPERTY_smartValueEntered] = new PropertyDescriptor ( "smartValueEntered", org.tentackle.swing.FormComboBox.class, "isSmartValueEntered", "setSmartValueEntered" ); // NOI18N
            properties[PROPERTY_smartValueEntered].setPreferred ( true );
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", org.tentackle.swing.FormComboBox.class, "getToolTipText", null ); // NOI18N
            properties[PROPERTY_toolTipText].setExpert ( true );
            properties[PROPERTY_triggerValueChangedEnabled] = new PropertyDescriptor ( "triggerValueChangedEnabled", org.tentackle.swing.FormComboBox.class, "isTriggerValueChangedEnabled", "setTriggerValueChangedEnabled" ); // NOI18N
            properties[PROPERTY_triggerValueChangedEnabled].setPreferred ( true );
            properties[PROPERTY_valueAdjusting] = new PropertyDescriptor ( "valueAdjusting", org.tentackle.swing.FormComboBox.class, "isValueAdjusting", "setValueAdjusting" ); // NOI18N
            properties[PROPERTY_valueAdjusting].setExpert ( true );
            properties[PROPERTY_valueEnteredOnSelect] = new PropertyDescriptor ( "valueEnteredOnSelect", org.tentackle.swing.FormComboBox.class, "isValueEnteredOnSelect", "setValueEnteredOnSelect" ); // NOI18N
            properties[PROPERTY_valueEnteredOnSelect].setPreferred ( true );
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
    private static String iconNameC16 = "/org/tentackle/swing/images/images16/FormComboBox.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/images/images32/FormComboBox.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons
  
    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx
  
  
    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.FormComboBox.class.getSuperclass();
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

