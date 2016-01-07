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
 * BeanInfo for FormComponentPanel.
 * 
 * @author harald
 */
public class FormComponentPanelBeanInfo extends SimpleBeanInfo {


    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.FormComponentPanel.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "FormComponentPanel" );
        beanDescriptor.setShortDescription ( "A FormPanel decorating a FormComponent" );//GEN-HEADEREND:BeanDescriptor
    
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
  
  
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_autoUpdate = 0;
    private static final int PROPERTY_bindable = 1;
    private static final int PROPERTY_bindingPath = 2;
    private static final int PROPERTY_cellEditorUsage = 3;
    private static final int PROPERTY_changeable = 4;
    private static final int PROPERTY_enabled = 5;
    private static final int PROPERTY_fireRunning = 6;
    private static final int PROPERTY_focusable = 7;
    private static final int PROPERTY_focusTraversalGroup = 8;
    private static final int PROPERTY_font = 9;
    private static final int PROPERTY_foreground = 10;
    private static final int PROPERTY_formComponent = 11;
    private static final int PROPERTY_formTraversable = 12;
    private static final int PROPERTY_formValue = 13;
    private static final int PROPERTY_helpURL = 14;
    private static final int PROPERTY_honourChangeable = 15;
    private static final int PROPERTY_mandatory = 16;
    private static final int PROPERTY_name = 17;
    private static final int PROPERTY_opaque = 18;
    private static final int PROPERTY_parentWindow = 19;
    private static final int PROPERTY_propertyGroup = 20;
    private static final int PROPERTY_smartValueEntered = 21;
    private static final int PROPERTY_toolTipText = 22;
    private static final int PROPERTY_triggerValueChangedEnabled = 23;
    private static final int PROPERTY_visible = 24;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[25];
    
        try {
            properties[PROPERTY_autoUpdate] = new PropertyDescriptor ( "autoUpdate", org.tentackle.swing.FormComponentPanel.class, "isAutoUpdate", "setAutoUpdate" ); // NOI18N
            properties[PROPERTY_autoUpdate].setPreferred ( true );
            properties[PROPERTY_bindable] = new PropertyDescriptor ( "bindable", org.tentackle.swing.FormComponentPanel.class, "isBindable", "setBindable" ); // NOI18N
            properties[PROPERTY_bindable].setPreferred ( true );
            properties[PROPERTY_bindingPath] = new PropertyDescriptor ( "bindingPath", org.tentackle.swing.FormComponentPanel.class, "getBindingPath", "setBindingPath" ); // NOI18N
            properties[PROPERTY_bindingPath].setPreferred ( true );
            properties[PROPERTY_cellEditorUsage] = new PropertyDescriptor ( "cellEditorUsage", org.tentackle.swing.FormComponentPanel.class, "isCellEditorUsage", "setCellEditorUsage" ); // NOI18N
            properties[PROPERTY_cellEditorUsage].setExpert ( true );
            properties[PROPERTY_changeable] = new PropertyDescriptor ( "changeable", org.tentackle.swing.FormComponentPanel.class, "isChangeable", "setChangeable" ); // NOI18N
            properties[PROPERTY_changeable].setPreferred ( true );
            properties[PROPERTY_enabled] = new PropertyDescriptor ( "enabled", org.tentackle.swing.FormComponentPanel.class, "isEnabled", "setEnabled" ); // NOI18N
            properties[PROPERTY_fireRunning] = new PropertyDescriptor ( "fireRunning", org.tentackle.swing.FormComponentPanel.class, "isFireRunning", "setFireRunning" ); // NOI18N
            properties[PROPERTY_fireRunning].setExpert ( true );
            properties[PROPERTY_focusable] = new PropertyDescriptor ( "focusable", org.tentackle.swing.FormComponentPanel.class, "isFocusable", "setFocusable" ); // NOI18N
            properties[PROPERTY_focusTraversalGroup] = new PropertyDescriptor ( "focusTraversalGroup", org.tentackle.swing.FormComponentPanel.class, "getFocusTraversalGroup", "setFocusTraversalGroup" ); // NOI18N
            properties[PROPERTY_focusTraversalGroup].setPreferred ( true );
            properties[PROPERTY_font] = new PropertyDescriptor ( "font", org.tentackle.swing.FormComponentPanel.class, "getFont", "setFont" ); // NOI18N
            properties[PROPERTY_foreground] = new PropertyDescriptor ( "foreground", org.tentackle.swing.FormComponentPanel.class, "getForeground", "setForeground" ); // NOI18N
            properties[PROPERTY_formComponent] = new PropertyDescriptor ( "formComponent", org.tentackle.swing.FormComponentPanel.class, "getFormComponent", "setFormComponent" ); // NOI18N
            properties[PROPERTY_formComponent].setExpert ( true );
            properties[PROPERTY_formTraversable] = new PropertyDescriptor ( "formTraversable", org.tentackle.swing.FormComponentPanel.class, "isFormTraversable", "setFormTraversable" ); // NOI18N
            properties[PROPERTY_formTraversable].setPreferred ( true );
            properties[PROPERTY_formValue] = new PropertyDescriptor ( "formValue", org.tentackle.swing.FormComponentPanel.class, "getFormValue", "setFormValue" ); // NOI18N
            properties[PROPERTY_formValue].setExpert ( true );
            properties[PROPERTY_helpURL] = new PropertyDescriptor ( "helpURL", org.tentackle.swing.FormComponentPanel.class, "getHelpURL", "setHelpURL" ); // NOI18N
            properties[PROPERTY_helpURL].setPreferred ( true );
            properties[PROPERTY_honourChangeable] = new PropertyDescriptor ( "honourChangeable", org.tentackle.swing.FormComponentPanel.class, "isHonourChangeable", "setHonourChangeable" ); // NOI18N
            properties[PROPERTY_honourChangeable].setPreferred ( true );
            properties[PROPERTY_mandatory] = new PropertyDescriptor ( "mandatory", org.tentackle.swing.FormComponentPanel.class, "isMandatory", "setMandatory" ); // NOI18N
            properties[PROPERTY_mandatory].setPreferred ( true );
            properties[PROPERTY_name] = new PropertyDescriptor ( "name", org.tentackle.swing.FormComponentPanel.class, "getName", "setName" ); // NOI18N
            properties[PROPERTY_opaque] = new PropertyDescriptor ( "opaque", org.tentackle.swing.FormComponentPanel.class, "isOpaque", "setOpaque" ); // NOI18N
            properties[PROPERTY_parentWindow] = new PropertyDescriptor ( "parentWindow", org.tentackle.swing.FormComponentPanel.class, "getParentWindow", null ); // NOI18N
            properties[PROPERTY_parentWindow].setExpert ( true );
            properties[PROPERTY_propertyGroup] = new PropertyDescriptor ( "propertyGroup", org.tentackle.swing.FormComponentPanel.class, "getPropertyGroup", "setPropertyGroup" ); // NOI18N
            properties[PROPERTY_propertyGroup].setPreferred ( true );
            properties[PROPERTY_smartValueEntered] = new PropertyDescriptor ( "smartValueEntered", org.tentackle.swing.FormComponentPanel.class, "isSmartValueEntered", "setSmartValueEntered" ); // NOI18N
            properties[PROPERTY_smartValueEntered].setPreferred ( true );
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", org.tentackle.swing.FormComponentPanel.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_toolTipText].setPreferred ( true );
            properties[PROPERTY_triggerValueChangedEnabled] = new PropertyDescriptor ( "triggerValueChangedEnabled", org.tentackle.swing.FormComponentPanel.class, "isTriggerValueChangedEnabled", "setTriggerValueChangedEnabled" ); // NOI18N
            properties[PROPERTY_triggerValueChangedEnabled].setPreferred ( true );
            properties[PROPERTY_visible] = new PropertyDescriptor ( "visible", org.tentackle.swing.FormComponentPanel.class, "isVisible", "setVisible" ); // NOI18N
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
    private static String iconNameC16 = "/org/tentackle/swing/images/images16/FormComponentPanel.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/images/images32/FormComponentPanel.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons
  
    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx
  
  
    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.FormComponentPanel.class.getSuperclass();
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

