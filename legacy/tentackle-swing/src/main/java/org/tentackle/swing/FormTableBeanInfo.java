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
 * BeanInfo for FormTable.
 *
 * @author harald
 */
public class FormTableBeanInfo extends SimpleBeanInfo {

    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.FormTable.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "FormTable" );
        beanDescriptor.setShortDescription ( "An extended JTable" );//GEN-HEADEREND:BeanDescriptor

    // Here you can add code for customizing the BeanDescriptor.

        return beanDescriptor;     }//GEN-LAST:BeanDescriptor


    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_cellDragEnabled = 0;
    private static final int PROPERTY_cellEditorFixed = 1;
    private static final int PROPERTY_cellRectFixed = 2;
    private static final int PROPERTY_cellRendererFixed = 3;
    private static final int PROPERTY_cellTraversal = 4;
    private static final int PROPERTY_changeable = 5;
    private static final int PROPERTY_clickCountToAction = 6;
    private static final int PROPERTY_clickCountToStart = 7;
    private static final int PROPERTY_createDefaultColumnsFromPreferences = 8;
    private static final int PROPERTY_enterActionEnabled = 9;
    private static final int PROPERTY_focusedBackground = 10;
    private static final int PROPERTY_focusedForeground = 11;
    private static final int PROPERTY_format = 12;
    private static final int PROPERTY_formTraversable = 13;
    private static final int PROPERTY_helpURL = 14;
    private static final int PROPERTY_maxRowHeight = 15;
    private static final int PROPERTY_minRowHeight = 16;
    private static final int PROPERTY_selectedBackground = 17;
    private static final int PROPERTY_selectedForeground = 18;
    private static final int PROPERTY_sizeInPreferencesIgnored = 19;
    private static final int PROPERTY_unselectedBackground = 20;
    private static final int PROPERTY_unselectedForeground = 21;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[22];
    
        try {
            properties[PROPERTY_cellDragEnabled] = new PropertyDescriptor ( "cellDragEnabled", org.tentackle.swing.FormTable.class, "isCellDragEnabled", "setCellDragEnabled" ); // NOI18N
            properties[PROPERTY_cellDragEnabled].setPreferred ( true );
            properties[PROPERTY_cellEditorFixed] = new PropertyDescriptor ( "cellEditorFixed", org.tentackle.swing.FormTable.class, "isCellEditorFixed", "setCellEditorFixed" ); // NOI18N
            properties[PROPERTY_cellEditorFixed].setPreferred ( true );
            properties[PROPERTY_cellRectFixed] = new PropertyDescriptor ( "cellRectFixed", org.tentackle.swing.FormTable.class, "isCellRectFixed", "setCellRectFixed" ); // NOI18N
            properties[PROPERTY_cellRectFixed].setPreferred ( true );
            properties[PROPERTY_cellRendererFixed] = new PropertyDescriptor ( "cellRendererFixed", org.tentackle.swing.FormTable.class, "isCellRendererFixed", "setCellRendererFixed" ); // NOI18N
            properties[PROPERTY_cellRendererFixed].setPreferred ( true );
            properties[PROPERTY_cellTraversal] = new PropertyDescriptor ( "cellTraversal", org.tentackle.swing.FormTable.class, "getCellTraversal", "setCellTraversal" ); // NOI18N
            properties[PROPERTY_cellTraversal].setPreferred ( true );
            properties[PROPERTY_cellTraversal].setPropertyEditorClass ( CellTraversalPropertyEditor.class );
            properties[PROPERTY_changeable] = new PropertyDescriptor ( "changeable", org.tentackle.swing.FormTable.class, "isChangeable", "setChangeable" ); // NOI18N
            properties[PROPERTY_changeable].setPreferred ( true );
            properties[PROPERTY_clickCountToAction] = new PropertyDescriptor ( "clickCountToAction", org.tentackle.swing.FormTable.class, "getClickCountToAction", "setClickCountToAction" ); // NOI18N
            properties[PROPERTY_clickCountToAction].setPreferred ( true );
            properties[PROPERTY_clickCountToStart] = new PropertyDescriptor ( "clickCountToStart", org.tentackle.swing.FormTable.class, "getClickCountToStart", "setClickCountToStart" ); // NOI18N
            properties[PROPERTY_clickCountToStart].setPreferred ( true );
            properties[PROPERTY_createDefaultColumnsFromPreferences] = new PropertyDescriptor ( "createDefaultColumnsFromPreferences", org.tentackle.swing.FormTable.class, "isCreateDefaultColumnsFromPreferences", "setCreateDefaultColumnsFromPreferences" ); // NOI18N
            properties[PROPERTY_createDefaultColumnsFromPreferences].setPreferred ( true );
            properties[PROPERTY_enterActionEnabled] = new PropertyDescriptor ( "enterActionEnabled", org.tentackle.swing.FormTable.class, "isEnterActionEnabled", "setEnterActionEnabled" ); // NOI18N
            properties[PROPERTY_enterActionEnabled].setPreferred ( true );
            properties[PROPERTY_focusedBackground] = new PropertyDescriptor ( "focusedBackground", org.tentackle.swing.FormTable.class, "getFocusedBackground", "setFocusedBackground" ); // NOI18N
            properties[PROPERTY_focusedBackground].setPreferred ( true );
            properties[PROPERTY_focusedForeground] = new PropertyDescriptor ( "focusedForeground", org.tentackle.swing.FormTable.class, "getFocusedForeground", "setFocusedForeground" ); // NOI18N
            properties[PROPERTY_focusedForeground].setPreferred ( true );
            properties[PROPERTY_format] = new IndexedPropertyDescriptor ( "format", org.tentackle.swing.FormTable.class, "getFormat", "setFormat", "getFormat", "setFormat" ); // NOI18N
            properties[PROPERTY_format].setPreferred ( true );
            properties[PROPERTY_formTraversable] = new PropertyDescriptor ( "formTraversable", org.tentackle.swing.FormTable.class, "isFormTraversable", "setFormTraversable" ); // NOI18N
            properties[PROPERTY_formTraversable].setPreferred ( true );
            properties[PROPERTY_helpURL] = new PropertyDescriptor ( "helpURL", org.tentackle.swing.FormTable.class, "getHelpURL", "setHelpURL" ); // NOI18N
            properties[PROPERTY_helpURL].setPreferred ( true );
            properties[PROPERTY_maxRowHeight] = new PropertyDescriptor ( "maxRowHeight", org.tentackle.swing.FormTable.class, "getMaxRowHeight", "setMaxRowHeight" ); // NOI18N
            properties[PROPERTY_maxRowHeight].setPreferred ( true );
            properties[PROPERTY_minRowHeight] = new PropertyDescriptor ( "minRowHeight", org.tentackle.swing.FormTable.class, "getMinRowHeight", "setMinRowHeight" ); // NOI18N
            properties[PROPERTY_minRowHeight].setPreferred ( true );
            properties[PROPERTY_selectedBackground] = new PropertyDescriptor ( "selectedBackground", org.tentackle.swing.FormTable.class, "getSelectedBackground", "setSelectedBackground" ); // NOI18N
            properties[PROPERTY_selectedBackground].setPreferred ( true );
            properties[PROPERTY_selectedForeground] = new PropertyDescriptor ( "selectedForeground", org.tentackle.swing.FormTable.class, "getSelectedForeground", "setSelectedForeground" ); // NOI18N
            properties[PROPERTY_selectedForeground].setPreferred ( true );
            properties[PROPERTY_sizeInPreferencesIgnored] = new PropertyDescriptor ( "sizeInPreferencesIgnored", org.tentackle.swing.FormTable.class, "isSizeInPreferencesIgnored", "setSizeInPreferencesIgnored" ); // NOI18N
            properties[PROPERTY_unselectedBackground] = new PropertyDescriptor ( "unselectedBackground", org.tentackle.swing.FormTable.class, "getUnselectedBackground", "setUnselectedBackground" ); // NOI18N
            properties[PROPERTY_unselectedBackground].setPreferred ( true );
            properties[PROPERTY_unselectedForeground] = new PropertyDescriptor ( "unselectedForeground", org.tentackle.swing.FormTable.class, "getUnselectedForeground", "setUnselectedForeground" ); // NOI18N
            properties[PROPERTY_unselectedForeground].setPreferred ( true );
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
    private static String iconNameC16 = "/org/tentackle/swing/images/images16/FormTable.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/images/images32/FormTable.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons

    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx


    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.FormTable.class.getSuperclass();
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

