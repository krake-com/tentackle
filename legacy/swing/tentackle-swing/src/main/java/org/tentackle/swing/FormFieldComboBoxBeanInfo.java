/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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
 * BeanInfo for FormFieldComboBox.
 * 
 * @author harald
 */
public class FormFieldComboBoxBeanInfo extends SimpleBeanInfo {
  
    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.FormFieldComboBox.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "FormFieldComboBox" );
        beanDescriptor.setShortDescription ( "A FormComboBox with a FormField as its editor" );//GEN-HEADEREND:BeanDescriptor
    
    // Here you can add code for customizing the BeanDescriptor.
    
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
  
  
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_adjust = 0;
    private static final int PROPERTY_autoNext = 1;
    private static final int PROPERTY_autoSelect = 2;
    private static final int PROPERTY_autoUpdate = 3;
    private static final int PROPERTY_bindable = 4;
    private static final int PROPERTY_columns = 5;
    private static final int PROPERTY_convert = 6;
    private static final int PROPERTY_editorField = 7;
    private static final int PROPERTY_eraseFirst = 8;
    private static final int PROPERTY_filler = 9;
    private static final int PROPERTY_format = 10;
    private static final int PROPERTY_horizontalAlignment = 11;
    private static final int PROPERTY_inhibitAutoSelect = 12;
    private static final int PROPERTY_invalidChars = 13;
    private static final int PROPERTY_maxColumns = 14;
    private static final int PROPERTY_multiKeySelectionManager = 15;
    private static final int PROPERTY_overwrite = 16;
    private static final int PROPERTY_smartValueEntered = 17;
    private static final int PROPERTY_startEditLeftmost = 18;
    private static final int PROPERTY_text = 19;
    private static final int PROPERTY_toolTipText = 20;
    private static final int PROPERTY_validChars = 21;
    private static final int PROPERTY_verticalAlignment = 22;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[23];
    
        try {
            properties[PROPERTY_adjust] = new PropertyDescriptor ( "adjust", org.tentackle.swing.FormFieldComboBox.class, "getAdjust", "setAdjust" ); // NOI18N
            properties[PROPERTY_adjust].setPreferred ( true );
            properties[PROPERTY_adjust].setPropertyEditorClass ( AdjustPropertyEditor.class );
            properties[PROPERTY_autoNext] = new PropertyDescriptor ( "autoNext", org.tentackle.swing.FormFieldComboBox.class, "isAutoNext", "setAutoNext" ); // NOI18N
            properties[PROPERTY_autoNext].setPreferred ( true );
            properties[PROPERTY_autoSelect] = new PropertyDescriptor ( "autoSelect", org.tentackle.swing.FormFieldComboBox.class, "isAutoSelect", "setAutoSelect" ); // NOI18N
            properties[PROPERTY_autoSelect].setPreferred ( true );
            properties[PROPERTY_autoUpdate] = new PropertyDescriptor ( "autoUpdate", org.tentackle.swing.FormFieldComboBox.class, "isAutoUpdate", "setAutoUpdate" ); // NOI18N
            properties[PROPERTY_autoUpdate].setPreferred ( true );
            properties[PROPERTY_bindable] = new PropertyDescriptor ( "bindable", org.tentackle.swing.FormFieldComboBox.class, "isBindable", "setBindable" ); // NOI18N
            properties[PROPERTY_bindable].setPreferred ( true );
            properties[PROPERTY_columns] = new PropertyDescriptor ( "columns", org.tentackle.swing.FormFieldComboBox.class, "getColumns", "setColumns" ); // NOI18N
            properties[PROPERTY_columns].setPreferred ( true );
            properties[PROPERTY_convert] = new PropertyDescriptor ( "convert", org.tentackle.swing.FormFieldComboBox.class, "getConvert", "setConvert" ); // NOI18N
            properties[PROPERTY_convert].setPreferred ( true );
            properties[PROPERTY_convert].setPropertyEditorClass ( ConvertPropertyEditor.class );
            properties[PROPERTY_editorField] = new PropertyDescriptor ( "editorField", org.tentackle.swing.FormFieldComboBox.class, "getEditorField", "setEditorField" ); // NOI18N
            properties[PROPERTY_editorField].setPreferred ( true );
            properties[PROPERTY_eraseFirst] = new PropertyDescriptor ( "eraseFirst", org.tentackle.swing.FormFieldComboBox.class, null, "setEraseFirst" ); // NOI18N
            properties[PROPERTY_filler] = new PropertyDescriptor ( "filler", org.tentackle.swing.FormFieldComboBox.class, "getFiller", "setFiller" ); // NOI18N
            properties[PROPERTY_filler].setPreferred ( true );
            properties[PROPERTY_format] = new PropertyDescriptor ( "format", org.tentackle.swing.FormFieldComboBox.class, "getFormat", "setFormat" ); // NOI18N
            properties[PROPERTY_format].setPreferred ( true );
            properties[PROPERTY_horizontalAlignment] = new PropertyDescriptor ( "horizontalAlignment", org.tentackle.swing.FormFieldComboBox.class, "getHorizontalAlignment", "setHorizontalAlignment" ); // NOI18N
            properties[PROPERTY_horizontalAlignment].setPreferred ( true );
            properties[PROPERTY_horizontalAlignment].setPropertyEditorClass ( HorizontalAlignmentPropertyEditor.class );
            properties[PROPERTY_inhibitAutoSelect] = new PropertyDescriptor ( "inhibitAutoSelect", org.tentackle.swing.FormFieldComboBox.class, "isInhibitAutoSelect", "setInhibitAutoSelect" ); // NOI18N
            properties[PROPERTY_invalidChars] = new PropertyDescriptor ( "invalidChars", org.tentackle.swing.FormFieldComboBox.class, "getInvalidChars", "setInvalidChars" ); // NOI18N
            properties[PROPERTY_invalidChars].setPreferred ( true );
            properties[PROPERTY_maxColumns] = new PropertyDescriptor ( "maxColumns", org.tentackle.swing.FormFieldComboBox.class, "getMaxColumns", "setMaxColumns" ); // NOI18N
            properties[PROPERTY_maxColumns].setPreferred ( true );
            properties[PROPERTY_multiKeySelectionManager] = new PropertyDescriptor ( "multiKeySelectionManager", org.tentackle.swing.FormFieldComboBox.class, "isMultiKeySelectionManager", "setMultiKeySelectionManager" ); // NOI18N
            properties[PROPERTY_multiKeySelectionManager].setPreferred ( true );
            properties[PROPERTY_overwrite] = new PropertyDescriptor ( "overwrite", org.tentackle.swing.FormFieldComboBox.class, "isOverwrite", "setOverwrite" ); // NOI18N
            properties[PROPERTY_overwrite].setPreferred ( true );
            properties[PROPERTY_smartValueEntered] = new PropertyDescriptor ( "smartValueEntered", org.tentackle.swing.FormFieldComboBox.class, "isSmartValueEntered", "setSmartValueEntered" ); // NOI18N
            properties[PROPERTY_smartValueEntered].setPreferred ( true );
            properties[PROPERTY_startEditLeftmost] = new PropertyDescriptor ( "startEditLeftmost", org.tentackle.swing.FormFieldComboBox.class, "isStartEditLeftmost", "setStartEditLeftmost" ); // NOI18N
            properties[PROPERTY_text] = new PropertyDescriptor ( "text", org.tentackle.swing.FormFieldComboBox.class, "getText", "setText" ); // NOI18N
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", org.tentackle.swing.FormFieldComboBox.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_toolTipText].setPreferred ( true );
            properties[PROPERTY_validChars] = new PropertyDescriptor ( "validChars", org.tentackle.swing.FormFieldComboBox.class, "getValidChars", "setValidChars" ); // NOI18N
            properties[PROPERTY_validChars].setPreferred ( true );
            properties[PROPERTY_verticalAlignment] = new PropertyDescriptor ( "verticalAlignment", org.tentackle.swing.FormFieldComboBox.class, "getVerticalAlignment", "setVerticalAlignment" ); // NOI18N
            properties[PROPERTY_verticalAlignment].setPreferred ( true );
            properties[PROPERTY_verticalAlignment].setPropertyEditorClass ( VerticalAlignmentPropertyEditor.class );
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
    private static String iconNameC16 = "/org/tentackle/swing/images/images16/FormFieldComboBox.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/images/images32/FormFieldComboBox.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons
  
    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx
  
  
    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.FormFieldComboBox.class.getSuperclass();
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

