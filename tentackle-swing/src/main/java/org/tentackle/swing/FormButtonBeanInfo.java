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
 * BeanInfo for FormButton.
 * 
 * @author harald
 */
public class FormButtonBeanInfo extends SimpleBeanInfo {
  
    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( org.tentackle.swing.FormButton.class , null ); // NOI18N
        beanDescriptor.setDisplayName ( "FormButton" );
        beanDescriptor.setShortDescription ( "A button which is aware of forms" );//GEN-HEADEREND:BeanDescriptor
    
    // Here you can add code for customizing the BeanDescriptor.
    
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
  
  
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_accessibleContext = 0;
    private static final int PROPERTY_action = 1;
    private static final int PROPERTY_actionCommand = 2;
    private static final int PROPERTY_actionListeners = 3;
    private static final int PROPERTY_actionMap = 4;
    private static final int PROPERTY_actionPerformedDelayed = 5;
    private static final int PROPERTY_alignmentX = 6;
    private static final int PROPERTY_alignmentY = 7;
    private static final int PROPERTY_ancestorListeners = 8;
    private static final int PROPERTY_autoscrolls = 9;
    private static final int PROPERTY_background = 10;
    private static final int PROPERTY_backgroundSet = 11;
    private static final int PROPERTY_baselineResizeBehavior = 12;
    private static final int PROPERTY_border = 13;
    private static final int PROPERTY_borderPainted = 14;
    private static final int PROPERTY_bounds = 15;
    private static final int PROPERTY_changeListeners = 16;
    private static final int PROPERTY_clickOnEnter = 17;
    private static final int PROPERTY_colorModel = 18;
    private static final int PROPERTY_component = 19;
    private static final int PROPERTY_componentCount = 20;
    private static final int PROPERTY_componentListeners = 21;
    private static final int PROPERTY_componentOrientation = 22;
    private static final int PROPERTY_componentPopupMenu = 23;
    private static final int PROPERTY_components = 24;
    private static final int PROPERTY_containerListeners = 25;
    private static final int PROPERTY_contentAreaFilled = 26;
    private static final int PROPERTY_cursor = 27;
    private static final int PROPERTY_cursorSet = 28;
    private static final int PROPERTY_debugGraphicsOptions = 29;
    private static final int PROPERTY_defaultButton = 30;
    private static final int PROPERTY_defaultCapable = 31;
    private static final int PROPERTY_disabledIcon = 32;
    private static final int PROPERTY_disabledSelectedIcon = 33;
    private static final int PROPERTY_displayable = 34;
    private static final int PROPERTY_displayedMnemonicIndex = 35;
    private static final int PROPERTY_doubleBuffered = 36;
    private static final int PROPERTY_dropTarget = 37;
    private static final int PROPERTY_enabled = 38;
    private static final int PROPERTY_focusable = 39;
    private static final int PROPERTY_focusCycleRoot = 40;
    private static final int PROPERTY_focusCycleRootAncestor = 41;
    private static final int PROPERTY_focusListeners = 42;
    private static final int PROPERTY_focusOwner = 43;
    private static final int PROPERTY_focusPainted = 44;
    private static final int PROPERTY_focusTraversable = 45;
    private static final int PROPERTY_focusTraversalKeys = 46;
    private static final int PROPERTY_focusTraversalKeysEnabled = 47;
    private static final int PROPERTY_focusTraversalPolicy = 48;
    private static final int PROPERTY_focusTraversalPolicyProvider = 49;
    private static final int PROPERTY_focusTraversalPolicySet = 50;
    private static final int PROPERTY_font = 51;
    private static final int PROPERTY_fontSet = 52;
    private static final int PROPERTY_foreground = 53;
    private static final int PROPERTY_foregroundSet = 54;
    private static final int PROPERTY_formTraversable = 55;
    private static final int PROPERTY_graphics = 56;
    private static final int PROPERTY_graphicsConfiguration = 57;
    private static final int PROPERTY_height = 58;
    private static final int PROPERTY_helpURL = 59;
    private static final int PROPERTY_hideActionText = 60;
    private static final int PROPERTY_hierarchyBoundsListeners = 61;
    private static final int PROPERTY_hierarchyListeners = 62;
    private static final int PROPERTY_horizontalAlignment = 63;
    private static final int PROPERTY_horizontalTextPosition = 64;
    private static final int PROPERTY_icon = 65;
    private static final int PROPERTY_iconTextGap = 66;
    private static final int PROPERTY_ignoreRepaint = 67;
    private static final int PROPERTY_inheritsPopupMenu = 68;
    private static final int PROPERTY_inputContext = 69;
    private static final int PROPERTY_inputMap = 70;
    private static final int PROPERTY_inputMethodListeners = 71;
    private static final int PROPERTY_inputMethodRequests = 72;
    private static final int PROPERTY_inputVerifier = 73;
    private static final int PROPERTY_insets = 74;
    private static final int PROPERTY_itemListeners = 75;
    private static final int PROPERTY_keyListeners = 76;
    private static final int PROPERTY_label = 77;
    private static final int PROPERTY_layout = 78;
    private static final int PROPERTY_lightweight = 79;
    private static final int PROPERTY_locale = 80;
    private static final int PROPERTY_location = 81;
    private static final int PROPERTY_locationOnScreen = 82;
    private static final int PROPERTY_managingFocus = 83;
    private static final int PROPERTY_margin = 84;
    private static final int PROPERTY_maximumSize = 85;
    private static final int PROPERTY_maximumSizeSet = 86;
    private static final int PROPERTY_minimumSize = 87;
    private static final int PROPERTY_minimumSizeSet = 88;
    private static final int PROPERTY_mnemonic = 89;
    private static final int PROPERTY_model = 90;
    private static final int PROPERTY_mouseListeners = 91;
    private static final int PROPERTY_mouseMotionListeners = 92;
    private static final int PROPERTY_mousePosition = 93;
    private static final int PROPERTY_mouseWheelListeners = 94;
    private static final int PROPERTY_multiClickThreshhold = 95;
    private static final int PROPERTY_name = 96;
    private static final int PROPERTY_nextFocusableComponent = 97;
    private static final int PROPERTY_opaque = 98;
    private static final int PROPERTY_optimizedDrawingEnabled = 99;
    private static final int PROPERTY_paintingForPrint = 100;
    private static final int PROPERTY_paintingTile = 101;
    private static final int PROPERTY_parent = 102;
    private static final int PROPERTY_peer = 103;
    private static final int PROPERTY_preferredSize = 104;
    private static final int PROPERTY_preferredSizeSet = 105;
    private static final int PROPERTY_pressedIcon = 106;
    private static final int PROPERTY_propertyChangeListeners = 107;
    private static final int PROPERTY_registeredKeyStrokes = 108;
    private static final int PROPERTY_requestFocusEnabled = 109;
    private static final int PROPERTY_rolloverEnabled = 110;
    private static final int PROPERTY_rolloverIcon = 111;
    private static final int PROPERTY_rolloverSelectedIcon = 112;
    private static final int PROPERTY_rootPane = 113;
    private static final int PROPERTY_selected = 114;
    private static final int PROPERTY_selectedIcon = 115;
    private static final int PROPERTY_selectedObjects = 116;
    private static final int PROPERTY_showing = 117;
    private static final int PROPERTY_size = 118;
    private static final int PROPERTY_text = 119;
    private static final int PROPERTY_toolkit = 120;
    private static final int PROPERTY_toolTipText = 121;
    private static final int PROPERTY_topLevelAncestor = 122;
    private static final int PROPERTY_transferHandler = 123;
    private static final int PROPERTY_treeLock = 124;
    private static final int PROPERTY_UI = 125;
    private static final int PROPERTY_UIClassID = 126;
    private static final int PROPERTY_valid = 127;
    private static final int PROPERTY_validateRoot = 128;
    private static final int PROPERTY_verifyInputWhenFocusTarget = 129;
    private static final int PROPERTY_verticalAlignment = 130;
    private static final int PROPERTY_verticalTextPosition = 131;
    private static final int PROPERTY_vetoableChangeListeners = 132;
    private static final int PROPERTY_visible = 133;
    private static final int PROPERTY_visibleRect = 134;
    private static final int PROPERTY_width = 135;
    private static final int PROPERTY_x = 136;
    private static final int PROPERTY_y = 137;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[138];
    
        try {
            properties[PROPERTY_accessibleContext] = new PropertyDescriptor ( "accessibleContext", org.tentackle.swing.FormButton.class, "getAccessibleContext", null ); // NOI18N
            properties[PROPERTY_action] = new PropertyDescriptor ( "action", org.tentackle.swing.FormButton.class, "getAction", "setAction" ); // NOI18N
            properties[PROPERTY_actionCommand] = new PropertyDescriptor ( "actionCommand", org.tentackle.swing.FormButton.class, "getActionCommand", "setActionCommand" ); // NOI18N
            properties[PROPERTY_actionListeners] = new PropertyDescriptor ( "actionListeners", org.tentackle.swing.FormButton.class, "getActionListeners", null ); // NOI18N
            properties[PROPERTY_actionMap] = new PropertyDescriptor ( "actionMap", org.tentackle.swing.FormButton.class, "getActionMap", "setActionMap" ); // NOI18N
            properties[PROPERTY_actionPerformedDelayed] = new PropertyDescriptor ( "actionPerformedDelayed", org.tentackle.swing.FormButton.class, "isActionPerformedDelayed", "setActionPerformedDelayed" ); // NOI18N
            properties[PROPERTY_actionPerformedDelayed].setPreferred ( true );
            properties[PROPERTY_alignmentX] = new PropertyDescriptor ( "alignmentX", org.tentackle.swing.FormButton.class, "getAlignmentX", "setAlignmentX" ); // NOI18N
            properties[PROPERTY_alignmentY] = new PropertyDescriptor ( "alignmentY", org.tentackle.swing.FormButton.class, "getAlignmentY", "setAlignmentY" ); // NOI18N
            properties[PROPERTY_ancestorListeners] = new PropertyDescriptor ( "ancestorListeners", org.tentackle.swing.FormButton.class, "getAncestorListeners", null ); // NOI18N
            properties[PROPERTY_autoscrolls] = new PropertyDescriptor ( "autoscrolls", org.tentackle.swing.FormButton.class, "getAutoscrolls", "setAutoscrolls" ); // NOI18N
            properties[PROPERTY_background] = new PropertyDescriptor ( "background", org.tentackle.swing.FormButton.class, "getBackground", "setBackground" ); // NOI18N
            properties[PROPERTY_backgroundSet] = new PropertyDescriptor ( "backgroundSet", org.tentackle.swing.FormButton.class, "isBackgroundSet", null ); // NOI18N
            properties[PROPERTY_baselineResizeBehavior] = new PropertyDescriptor ( "baselineResizeBehavior", org.tentackle.swing.FormButton.class, "getBaselineResizeBehavior", null ); // NOI18N
            properties[PROPERTY_border] = new PropertyDescriptor ( "border", org.tentackle.swing.FormButton.class, "getBorder", "setBorder" ); // NOI18N
            properties[PROPERTY_borderPainted] = new PropertyDescriptor ( "borderPainted", org.tentackle.swing.FormButton.class, "isBorderPainted", "setBorderPainted" ); // NOI18N
            properties[PROPERTY_bounds] = new PropertyDescriptor ( "bounds", org.tentackle.swing.FormButton.class, "getBounds", "setBounds" ); // NOI18N
            properties[PROPERTY_changeListeners] = new PropertyDescriptor ( "changeListeners", org.tentackle.swing.FormButton.class, "getChangeListeners", null ); // NOI18N
            properties[PROPERTY_clickOnEnter] = new PropertyDescriptor ( "clickOnEnter", org.tentackle.swing.FormButton.class, "isClickOnEnter", "setClickOnEnter" ); // NOI18N
            properties[PROPERTY_clickOnEnter].setPreferred ( true );
            properties[PROPERTY_colorModel] = new PropertyDescriptor ( "colorModel", org.tentackle.swing.FormButton.class, "getColorModel", null ); // NOI18N
            properties[PROPERTY_component] = new IndexedPropertyDescriptor ( "component", org.tentackle.swing.FormButton.class, null, null, "getComponent", null ); // NOI18N
            properties[PROPERTY_componentCount] = new PropertyDescriptor ( "componentCount", org.tentackle.swing.FormButton.class, "getComponentCount", null ); // NOI18N
            properties[PROPERTY_componentListeners] = new PropertyDescriptor ( "componentListeners", org.tentackle.swing.FormButton.class, "getComponentListeners", null ); // NOI18N
            properties[PROPERTY_componentOrientation] = new PropertyDescriptor ( "componentOrientation", org.tentackle.swing.FormButton.class, "getComponentOrientation", "setComponentOrientation" ); // NOI18N
            properties[PROPERTY_componentPopupMenu] = new PropertyDescriptor ( "componentPopupMenu", org.tentackle.swing.FormButton.class, "getComponentPopupMenu", "setComponentPopupMenu" ); // NOI18N
            properties[PROPERTY_components] = new PropertyDescriptor ( "components", org.tentackle.swing.FormButton.class, "getComponents", null ); // NOI18N
            properties[PROPERTY_containerListeners] = new PropertyDescriptor ( "containerListeners", org.tentackle.swing.FormButton.class, "getContainerListeners", null ); // NOI18N
            properties[PROPERTY_contentAreaFilled] = new PropertyDescriptor ( "contentAreaFilled", org.tentackle.swing.FormButton.class, "isContentAreaFilled", "setContentAreaFilled" ); // NOI18N
            properties[PROPERTY_cursor] = new PropertyDescriptor ( "cursor", org.tentackle.swing.FormButton.class, "getCursor", "setCursor" ); // NOI18N
            properties[PROPERTY_cursorSet] = new PropertyDescriptor ( "cursorSet", org.tentackle.swing.FormButton.class, "isCursorSet", null ); // NOI18N
            properties[PROPERTY_debugGraphicsOptions] = new PropertyDescriptor ( "debugGraphicsOptions", org.tentackle.swing.FormButton.class, "getDebugGraphicsOptions", "setDebugGraphicsOptions" ); // NOI18N
            properties[PROPERTY_defaultButton] = new PropertyDescriptor ( "defaultButton", org.tentackle.swing.FormButton.class, "isDefaultButton", null ); // NOI18N
            properties[PROPERTY_defaultCapable] = new PropertyDescriptor ( "defaultCapable", org.tentackle.swing.FormButton.class, "isDefaultCapable", "setDefaultCapable" ); // NOI18N
            properties[PROPERTY_disabledIcon] = new PropertyDescriptor ( "disabledIcon", org.tentackle.swing.FormButton.class, "getDisabledIcon", "setDisabledIcon" ); // NOI18N
            properties[PROPERTY_disabledSelectedIcon] = new PropertyDescriptor ( "disabledSelectedIcon", org.tentackle.swing.FormButton.class, "getDisabledSelectedIcon", "setDisabledSelectedIcon" ); // NOI18N
            properties[PROPERTY_displayable] = new PropertyDescriptor ( "displayable", org.tentackle.swing.FormButton.class, "isDisplayable", null ); // NOI18N
            properties[PROPERTY_displayedMnemonicIndex] = new PropertyDescriptor ( "displayedMnemonicIndex", org.tentackle.swing.FormButton.class, "getDisplayedMnemonicIndex", "setDisplayedMnemonicIndex" ); // NOI18N
            properties[PROPERTY_doubleBuffered] = new PropertyDescriptor ( "doubleBuffered", org.tentackle.swing.FormButton.class, "isDoubleBuffered", "setDoubleBuffered" ); // NOI18N
            properties[PROPERTY_dropTarget] = new PropertyDescriptor ( "dropTarget", org.tentackle.swing.FormButton.class, "getDropTarget", "setDropTarget" ); // NOI18N
            properties[PROPERTY_enabled] = new PropertyDescriptor ( "enabled", org.tentackle.swing.FormButton.class, "isEnabled", "setEnabled" ); // NOI18N
            properties[PROPERTY_focusable] = new PropertyDescriptor ( "focusable", org.tentackle.swing.FormButton.class, "isFocusable", "setFocusable" ); // NOI18N
            properties[PROPERTY_focusCycleRoot] = new PropertyDescriptor ( "focusCycleRoot", org.tentackle.swing.FormButton.class, "isFocusCycleRoot", "setFocusCycleRoot" ); // NOI18N
            properties[PROPERTY_focusCycleRootAncestor] = new PropertyDescriptor ( "focusCycleRootAncestor", org.tentackle.swing.FormButton.class, "getFocusCycleRootAncestor", null ); // NOI18N
            properties[PROPERTY_focusListeners] = new PropertyDescriptor ( "focusListeners", org.tentackle.swing.FormButton.class, "getFocusListeners", null ); // NOI18N
            properties[PROPERTY_focusOwner] = new PropertyDescriptor ( "focusOwner", org.tentackle.swing.FormButton.class, "isFocusOwner", null ); // NOI18N
            properties[PROPERTY_focusPainted] = new PropertyDescriptor ( "focusPainted", org.tentackle.swing.FormButton.class, "isFocusPainted", "setFocusPainted" ); // NOI18N
            properties[PROPERTY_focusTraversable] = new PropertyDescriptor ( "focusTraversable", org.tentackle.swing.FormButton.class, "isFocusTraversable", null ); // NOI18N
            properties[PROPERTY_focusTraversalKeys] = new IndexedPropertyDescriptor ( "focusTraversalKeys", org.tentackle.swing.FormButton.class, null, null, null, "setFocusTraversalKeys" ); // NOI18N
            properties[PROPERTY_focusTraversalKeysEnabled] = new PropertyDescriptor ( "focusTraversalKeysEnabled", org.tentackle.swing.FormButton.class, "getFocusTraversalKeysEnabled", "setFocusTraversalKeysEnabled" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicy] = new PropertyDescriptor ( "focusTraversalPolicy", org.tentackle.swing.FormButton.class, "getFocusTraversalPolicy", "setFocusTraversalPolicy" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicyProvider] = new PropertyDescriptor ( "focusTraversalPolicyProvider", org.tentackle.swing.FormButton.class, "isFocusTraversalPolicyProvider", "setFocusTraversalPolicyProvider" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicySet] = new PropertyDescriptor ( "focusTraversalPolicySet", org.tentackle.swing.FormButton.class, "isFocusTraversalPolicySet", null ); // NOI18N
            properties[PROPERTY_font] = new PropertyDescriptor ( "font", org.tentackle.swing.FormButton.class, "getFont", "setFont" ); // NOI18N
            properties[PROPERTY_fontSet] = new PropertyDescriptor ( "fontSet", org.tentackle.swing.FormButton.class, "isFontSet", null ); // NOI18N
            properties[PROPERTY_foreground] = new PropertyDescriptor ( "foreground", org.tentackle.swing.FormButton.class, "getForeground", "setForeground" ); // NOI18N
            properties[PROPERTY_foregroundSet] = new PropertyDescriptor ( "foregroundSet", org.tentackle.swing.FormButton.class, "isForegroundSet", null ); // NOI18N
            properties[PROPERTY_formTraversable] = new PropertyDescriptor ( "formTraversable", org.tentackle.swing.FormButton.class, "isFormTraversable", "setFormTraversable" ); // NOI18N
            properties[PROPERTY_formTraversable].setPreferred ( true );
            properties[PROPERTY_graphics] = new PropertyDescriptor ( "graphics", org.tentackle.swing.FormButton.class, "getGraphics", null ); // NOI18N
            properties[PROPERTY_graphicsConfiguration] = new PropertyDescriptor ( "graphicsConfiguration", org.tentackle.swing.FormButton.class, "getGraphicsConfiguration", null ); // NOI18N
            properties[PROPERTY_height] = new PropertyDescriptor ( "height", org.tentackle.swing.FormButton.class, "getHeight", null ); // NOI18N
            properties[PROPERTY_helpURL] = new PropertyDescriptor ( "helpURL", org.tentackle.swing.FormButton.class, "getHelpURL", "setHelpURL" ); // NOI18N
            properties[PROPERTY_helpURL].setPreferred ( true );
            properties[PROPERTY_hideActionText] = new PropertyDescriptor ( "hideActionText", org.tentackle.swing.FormButton.class, "getHideActionText", "setHideActionText" ); // NOI18N
            properties[PROPERTY_hierarchyBoundsListeners] = new PropertyDescriptor ( "hierarchyBoundsListeners", org.tentackle.swing.FormButton.class, "getHierarchyBoundsListeners", null ); // NOI18N
            properties[PROPERTY_hierarchyListeners] = new PropertyDescriptor ( "hierarchyListeners", org.tentackle.swing.FormButton.class, "getHierarchyListeners", null ); // NOI18N
            properties[PROPERTY_horizontalAlignment] = new PropertyDescriptor ( "horizontalAlignment", org.tentackle.swing.FormButton.class, "getHorizontalAlignment", "setHorizontalAlignment" ); // NOI18N
            properties[PROPERTY_horizontalTextPosition] = new PropertyDescriptor ( "horizontalTextPosition", org.tentackle.swing.FormButton.class, "getHorizontalTextPosition", "setHorizontalTextPosition" ); // NOI18N
            properties[PROPERTY_icon] = new PropertyDescriptor ( "icon", org.tentackle.swing.FormButton.class, "getIcon", "setIcon" ); // NOI18N
            properties[PROPERTY_iconTextGap] = new PropertyDescriptor ( "iconTextGap", org.tentackle.swing.FormButton.class, "getIconTextGap", "setIconTextGap" ); // NOI18N
            properties[PROPERTY_ignoreRepaint] = new PropertyDescriptor ( "ignoreRepaint", org.tentackle.swing.FormButton.class, "getIgnoreRepaint", "setIgnoreRepaint" ); // NOI18N
            properties[PROPERTY_inheritsPopupMenu] = new PropertyDescriptor ( "inheritsPopupMenu", org.tentackle.swing.FormButton.class, "getInheritsPopupMenu", "setInheritsPopupMenu" ); // NOI18N
            properties[PROPERTY_inputContext] = new PropertyDescriptor ( "inputContext", org.tentackle.swing.FormButton.class, "getInputContext", null ); // NOI18N
            properties[PROPERTY_inputMap] = new PropertyDescriptor ( "inputMap", org.tentackle.swing.FormButton.class, "getInputMap", null ); // NOI18N
            properties[PROPERTY_inputMethodListeners] = new PropertyDescriptor ( "inputMethodListeners", org.tentackle.swing.FormButton.class, "getInputMethodListeners", null ); // NOI18N
            properties[PROPERTY_inputMethodRequests] = new PropertyDescriptor ( "inputMethodRequests", org.tentackle.swing.FormButton.class, "getInputMethodRequests", null ); // NOI18N
            properties[PROPERTY_inputVerifier] = new PropertyDescriptor ( "inputVerifier", org.tentackle.swing.FormButton.class, "getInputVerifier", "setInputVerifier" ); // NOI18N
            properties[PROPERTY_insets] = new PropertyDescriptor ( "insets", org.tentackle.swing.FormButton.class, "getInsets", null ); // NOI18N
            properties[PROPERTY_itemListeners] = new PropertyDescriptor ( "itemListeners", org.tentackle.swing.FormButton.class, "getItemListeners", null ); // NOI18N
            properties[PROPERTY_keyListeners] = new PropertyDescriptor ( "keyListeners", org.tentackle.swing.FormButton.class, "getKeyListeners", null ); // NOI18N
            properties[PROPERTY_label] = new PropertyDescriptor ( "label", org.tentackle.swing.FormButton.class, "getLabel", "setLabel" ); // NOI18N
            properties[PROPERTY_layout] = new PropertyDescriptor ( "layout", org.tentackle.swing.FormButton.class, "getLayout", "setLayout" ); // NOI18N
            properties[PROPERTY_lightweight] = new PropertyDescriptor ( "lightweight", org.tentackle.swing.FormButton.class, "isLightweight", null ); // NOI18N
            properties[PROPERTY_locale] = new PropertyDescriptor ( "locale", org.tentackle.swing.FormButton.class, "getLocale", "setLocale" ); // NOI18N
            properties[PROPERTY_location] = new PropertyDescriptor ( "location", org.tentackle.swing.FormButton.class, "getLocation", "setLocation" ); // NOI18N
            properties[PROPERTY_locationOnScreen] = new PropertyDescriptor ( "locationOnScreen", org.tentackle.swing.FormButton.class, "getLocationOnScreen", null ); // NOI18N
            properties[PROPERTY_managingFocus] = new PropertyDescriptor ( "managingFocus", org.tentackle.swing.FormButton.class, "isManagingFocus", null ); // NOI18N
            properties[PROPERTY_margin] = new PropertyDescriptor ( "margin", org.tentackle.swing.FormButton.class, "getMargin", "setMargin" ); // NOI18N
            properties[PROPERTY_maximumSize] = new PropertyDescriptor ( "maximumSize", org.tentackle.swing.FormButton.class, "getMaximumSize", "setMaximumSize" ); // NOI18N
            properties[PROPERTY_maximumSizeSet] = new PropertyDescriptor ( "maximumSizeSet", org.tentackle.swing.FormButton.class, "isMaximumSizeSet", null ); // NOI18N
            properties[PROPERTY_minimumSize] = new PropertyDescriptor ( "minimumSize", org.tentackle.swing.FormButton.class, "getMinimumSize", "setMinimumSize" ); // NOI18N
            properties[PROPERTY_minimumSizeSet] = new PropertyDescriptor ( "minimumSizeSet", org.tentackle.swing.FormButton.class, "isMinimumSizeSet", null ); // NOI18N
            properties[PROPERTY_mnemonic] = new PropertyDescriptor ( "mnemonic", org.tentackle.swing.FormButton.class, null, "setMnemonic" ); // NOI18N
            properties[PROPERTY_model] = new PropertyDescriptor ( "model", org.tentackle.swing.FormButton.class, "getModel", "setModel" ); // NOI18N
            properties[PROPERTY_mouseListeners] = new PropertyDescriptor ( "mouseListeners", org.tentackle.swing.FormButton.class, "getMouseListeners", null ); // NOI18N
            properties[PROPERTY_mouseMotionListeners] = new PropertyDescriptor ( "mouseMotionListeners", org.tentackle.swing.FormButton.class, "getMouseMotionListeners", null ); // NOI18N
            properties[PROPERTY_mousePosition] = new PropertyDescriptor ( "mousePosition", org.tentackle.swing.FormButton.class, "getMousePosition", null ); // NOI18N
            properties[PROPERTY_mouseWheelListeners] = new PropertyDescriptor ( "mouseWheelListeners", org.tentackle.swing.FormButton.class, "getMouseWheelListeners", null ); // NOI18N
            properties[PROPERTY_multiClickThreshhold] = new PropertyDescriptor ( "multiClickThreshhold", org.tentackle.swing.FormButton.class, "getMultiClickThreshhold", "setMultiClickThreshhold" ); // NOI18N
            properties[PROPERTY_name] = new PropertyDescriptor ( "name", org.tentackle.swing.FormButton.class, "getName", "setName" ); // NOI18N
            properties[PROPERTY_nextFocusableComponent] = new PropertyDescriptor ( "nextFocusableComponent", org.tentackle.swing.FormButton.class, "getNextFocusableComponent", "setNextFocusableComponent" ); // NOI18N
            properties[PROPERTY_opaque] = new PropertyDescriptor ( "opaque", org.tentackle.swing.FormButton.class, "isOpaque", "setOpaque" ); // NOI18N
            properties[PROPERTY_optimizedDrawingEnabled] = new PropertyDescriptor ( "optimizedDrawingEnabled", org.tentackle.swing.FormButton.class, "isOptimizedDrawingEnabled", null ); // NOI18N
            properties[PROPERTY_paintingForPrint] = new PropertyDescriptor ( "paintingForPrint", org.tentackle.swing.FormButton.class, "isPaintingForPrint", null ); // NOI18N
            properties[PROPERTY_paintingTile] = new PropertyDescriptor ( "paintingTile", org.tentackle.swing.FormButton.class, "isPaintingTile", null ); // NOI18N
            properties[PROPERTY_parent] = new PropertyDescriptor ( "parent", org.tentackle.swing.FormButton.class, "getParent", null ); // NOI18N
            properties[PROPERTY_peer] = new PropertyDescriptor ( "peer", org.tentackle.swing.FormButton.class, "getPeer", null ); // NOI18N
            properties[PROPERTY_preferredSize] = new PropertyDescriptor ( "preferredSize", org.tentackle.swing.FormButton.class, "getPreferredSize", "setPreferredSize" ); // NOI18N
            properties[PROPERTY_preferredSizeSet] = new PropertyDescriptor ( "preferredSizeSet", org.tentackle.swing.FormButton.class, "isPreferredSizeSet", null ); // NOI18N
            properties[PROPERTY_pressedIcon] = new PropertyDescriptor ( "pressedIcon", org.tentackle.swing.FormButton.class, "getPressedIcon", "setPressedIcon" ); // NOI18N
            properties[PROPERTY_propertyChangeListeners] = new PropertyDescriptor ( "propertyChangeListeners", org.tentackle.swing.FormButton.class, "getPropertyChangeListeners", null ); // NOI18N
            properties[PROPERTY_registeredKeyStrokes] = new PropertyDescriptor ( "registeredKeyStrokes", org.tentackle.swing.FormButton.class, "getRegisteredKeyStrokes", null ); // NOI18N
            properties[PROPERTY_requestFocusEnabled] = new PropertyDescriptor ( "requestFocusEnabled", org.tentackle.swing.FormButton.class, "isRequestFocusEnabled", "setRequestFocusEnabled" ); // NOI18N
            properties[PROPERTY_rolloverEnabled] = new PropertyDescriptor ( "rolloverEnabled", org.tentackle.swing.FormButton.class, "isRolloverEnabled", "setRolloverEnabled" ); // NOI18N
            properties[PROPERTY_rolloverIcon] = new PropertyDescriptor ( "rolloverIcon", org.tentackle.swing.FormButton.class, "getRolloverIcon", "setRolloverIcon" ); // NOI18N
            properties[PROPERTY_rolloverSelectedIcon] = new PropertyDescriptor ( "rolloverSelectedIcon", org.tentackle.swing.FormButton.class, "getRolloverSelectedIcon", "setRolloverSelectedIcon" ); // NOI18N
            properties[PROPERTY_rootPane] = new PropertyDescriptor ( "rootPane", org.tentackle.swing.FormButton.class, "getRootPane", null ); // NOI18N
            properties[PROPERTY_selected] = new PropertyDescriptor ( "selected", org.tentackle.swing.FormButton.class, "isSelected", "setSelected" ); // NOI18N
            properties[PROPERTY_selectedIcon] = new PropertyDescriptor ( "selectedIcon", org.tentackle.swing.FormButton.class, "getSelectedIcon", "setSelectedIcon" ); // NOI18N
            properties[PROPERTY_selectedObjects] = new PropertyDescriptor ( "selectedObjects", org.tentackle.swing.FormButton.class, "getSelectedObjects", null ); // NOI18N
            properties[PROPERTY_showing] = new PropertyDescriptor ( "showing", org.tentackle.swing.FormButton.class, "isShowing", null ); // NOI18N
            properties[PROPERTY_size] = new PropertyDescriptor ( "size", org.tentackle.swing.FormButton.class, "getSize", "setSize" ); // NOI18N
            properties[PROPERTY_text] = new PropertyDescriptor ( "text", org.tentackle.swing.FormButton.class, "getText", "setText" ); // NOI18N
            properties[PROPERTY_toolkit] = new PropertyDescriptor ( "toolkit", org.tentackle.swing.FormButton.class, "getToolkit", null ); // NOI18N
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", org.tentackle.swing.FormButton.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_topLevelAncestor] = new PropertyDescriptor ( "topLevelAncestor", org.tentackle.swing.FormButton.class, "getTopLevelAncestor", null ); // NOI18N
            properties[PROPERTY_transferHandler] = new PropertyDescriptor ( "transferHandler", org.tentackle.swing.FormButton.class, "getTransferHandler", "setTransferHandler" ); // NOI18N
            properties[PROPERTY_treeLock] = new PropertyDescriptor ( "treeLock", org.tentackle.swing.FormButton.class, "getTreeLock", null ); // NOI18N
            properties[PROPERTY_UI] = new PropertyDescriptor ( "UI", org.tentackle.swing.FormButton.class, "getUI", "setUI" ); // NOI18N
            properties[PROPERTY_UIClassID] = new PropertyDescriptor ( "UIClassID", org.tentackle.swing.FormButton.class, "getUIClassID", null ); // NOI18N
            properties[PROPERTY_valid] = new PropertyDescriptor ( "valid", org.tentackle.swing.FormButton.class, "isValid", null ); // NOI18N
            properties[PROPERTY_validateRoot] = new PropertyDescriptor ( "validateRoot", org.tentackle.swing.FormButton.class, "isValidateRoot", null ); // NOI18N
            properties[PROPERTY_verifyInputWhenFocusTarget] = new PropertyDescriptor ( "verifyInputWhenFocusTarget", org.tentackle.swing.FormButton.class, "getVerifyInputWhenFocusTarget", "setVerifyInputWhenFocusTarget" ); // NOI18N
            properties[PROPERTY_verticalAlignment] = new PropertyDescriptor ( "verticalAlignment", org.tentackle.swing.FormButton.class, "getVerticalAlignment", "setVerticalAlignment" ); // NOI18N
            properties[PROPERTY_verticalTextPosition] = new PropertyDescriptor ( "verticalTextPosition", org.tentackle.swing.FormButton.class, "getVerticalTextPosition", "setVerticalTextPosition" ); // NOI18N
            properties[PROPERTY_vetoableChangeListeners] = new PropertyDescriptor ( "vetoableChangeListeners", org.tentackle.swing.FormButton.class, "getVetoableChangeListeners", null ); // NOI18N
            properties[PROPERTY_visible] = new PropertyDescriptor ( "visible", org.tentackle.swing.FormButton.class, "isVisible", "setVisible" ); // NOI18N
            properties[PROPERTY_visibleRect] = new PropertyDescriptor ( "visibleRect", org.tentackle.swing.FormButton.class, "getVisibleRect", null ); // NOI18N
            properties[PROPERTY_width] = new PropertyDescriptor ( "width", org.tentackle.swing.FormButton.class, "getWidth", null ); // NOI18N
            properties[PROPERTY_x] = new PropertyDescriptor ( "x", org.tentackle.swing.FormButton.class, "getX", null ); // NOI18N
            properties[PROPERTY_y] = new PropertyDescriptor ( "y", org.tentackle.swing.FormButton.class, "getY", null ); // NOI18N
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
    private static String iconNameC16 = "/org/tentackle/swing/images/images16/FormButton.gif";//GEN-BEGIN:Icons
    private static String iconNameC32 = "/org/tentackle/swing/images/images32/FormButton.gif";
    private static String iconNameM16 = null;
    private static String iconNameM32 = null;//GEN-END:Icons
  
    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx
  
  
    public BeanInfo[] getAdditionalBeanInfo() {//GEN-FIRST:Superclass
        Class superclass = org.tentackle.swing.FormButton.class.getSuperclass();
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

