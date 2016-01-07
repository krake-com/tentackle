/**
 * copied from JGoodies Look And Feel because of static methods.
 */


package org.tentackle.swing.plaf.tlooks;

import com.jgoodies.looks.MicroLayout;
import com.jgoodies.looks.Options;
import java.awt.Insets;
import javax.swing.plaf.InsetsUIResource;

/**
 * Copied from JGoodies {@code MicroLayouts} because of
 * static methods.
 * 
 * @author harald
 */
public class TLooksMicroLayouts {
  
    private TLooksMicroLayouts() {
        // Override default constructor; prevents instantation.
    }
    
    
    // Plastic and Plastic3D MicroLayouts *************************************
    
    private static final InsetsUIResource PLASTIC_MENU_ITEM_MARGIN = 
        new InsetsUIResource(2, 0, 2, 0);
    
    private static final InsetsUIResource PLASTIC_MENU_MARGIN = 
        new InsetsUIResource(2, 2, 2, 2);
    
    private static final InsetsUIResource PLASTIC_CHECK_BOX_MARGIN =
        new InsetsUIResource(2, 0, 2, 1);
    
    
    public static MicroLayout createPlasticLowResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 1, 1, 1), // text insets
                new InsetsUIResource(1, 2, 1, 1), // wrapped text insets
                new InsetsUIResource(1, 1, 2, 1), // combo box editor insets
                -1,                               // combo border size
                1 ,                               // combo popup border size
                new Insets(2, 3, 3, 3),           // button border insets
                getButtonMargin(1, 1),            // button margin                
                getButtonMargin(1, 1),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticHiResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 1, 1, 1), // text insets
                new InsetsUIResource(1, 2, 1, 1), // wrapped text insets
                new InsetsUIResource(1, 1, 2, 1), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(1, 3, 1, 3),           // button border insets
                getButtonMargin(2, 3),            // button margin                
                getButtonMargin(2, 3),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticVistaMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 1, 1, 1), // text insets
                new InsetsUIResource(1, 2, 1, 1), // wrapped text insets
                new InsetsUIResource(1, 1, 1, 1), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(2, 3, 3, 3),           // button border insets
                getButtonMargin(0, 1),            // button margin                
                getButtonMargin(0, 1),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticVistaClassicMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 1, 2, 1), // text insets
                new InsetsUIResource(2, 2, 2, 1), // wrapped text insets
                new InsetsUIResource(1, 1, 2, 1), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(3, 3, 3, 3),           // button border insets
                getButtonMargin(0, 1),            // button margin                
                getButtonMargin(0, 1),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    // PlasticXP MicroLayouts *************************************************
    
    public static MicroLayout createPlasticXPLowResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(2, 2, 3, 2), // text insets
                new InsetsUIResource(2, 2, 3, 2), // wrapped text insets
                new InsetsUIResource(2, 2, 3, 2), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 3, 2),           // button border insets
                getButtonMargin(0, 1),            // button margin                
                getButtonMargin(0, 1),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticXPHiResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(2, 2, 3, 2), // text insets
                new InsetsUIResource(2, 2, 3, 2), // wrapped text insets
                new InsetsUIResource(2, 2, 3, 2), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(2, 2, 2, 2),           // button border insets
                getButtonMargin(1, 2),            // button margin                
                getButtonMargin(1, 2),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticXPVistaMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 2, 2, 2), // text insets
                new InsetsUIResource(1, 2, 2, 2), // wrapped text insets
                new InsetsUIResource(1, 2, 2, 2), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(2, 2, 3, 2),           // button border insets
                getButtonMargin(0, 0),            // button margin                
                getButtonMargin(0, 0),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    public static MicroLayout createPlasticXPVistaClassicMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(2, 2, 3, 2), // text insets
                new InsetsUIResource(2, 2, 3, 2), // wrapped text insets
                new InsetsUIResource(2, 2, 3, 2), // combo box editor insets
                -1,                               // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(0, 0),            // button margin                
                getButtonMargin(0, 0),            // commit button margin
                PLASTIC_CHECK_BOX_MARGIN,         // check box margin 
                PLASTIC_MENU_ITEM_MARGIN,         // menu item margin
                PLASTIC_MENU_MARGIN,              // menu margin
                null                              // popup menu separator margin
                );
    }

    
    // Windows MicroLayouts ***************************************************
    
    private static final InsetsUIResource WINDOWS_CHECK_BOX_MARGIN = 
        new InsetsUIResource(2, 0, 2, 0);
    
    
    public static MicroLayout createWindowsClassicLowResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 2, 2, 2), // text insets
                new InsetsUIResource(1, 2, 2, 2), // wrapped text insets
                new InsetsUIResource(1, 2, 2, 2), // combo box editor insets
                2,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(1, 1),            // button margin                
                getButtonMargin(1, 1),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(3, 0, 3, 0), // menu item margin
                new InsetsUIResource(2, 3, 2, 3), // menu margin
                new InsetsUIResource(2, 0, 3, 0)  // popup menu separator margin
                );
    }
    
    
    public static MicroLayout createWindowsClassicHiResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 2, 2, 2), // text insets
                new InsetsUIResource(1, 2, 2, 2), // wrapped text insets
                new InsetsUIResource(1, 2, 2, 2), // combo box editor insets
                2,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(1, 1),            // button margin                
                getButtonMargin(1, 1),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(2, 0, 2, 0), // menu item margin
                new InsetsUIResource(2, 4, 2, 4), // menu margin
                new InsetsUIResource(3, 0, 4, 0)  // popup menu separator margin
                );
    }
    
    
    public static MicroLayout createWindowsXPLowResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(2, 2, 3, 2), // text insets
                new InsetsUIResource(2, 2, 3, 2), // wrapped text insets
                new InsetsUIResource(2, 2, 3, 2), // combo box editor insets
                1,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(2, 3),            // button margin                
                getButtonMargin(2, 3),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(3, 0, 3, 0), // menu item margin
                new InsetsUIResource(2, 3, 2, 4), // menu margin
                new InsetsUIResource(2, 3, 3, 3)  // popup menu separator margin
                );
    }
    
    
    public static MicroLayout createWindowsXPHiResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(2, 2, 3, 2), // text insets
                new InsetsUIResource(2, 2, 3, 2), // wrapped text insets
                new InsetsUIResource(2, 2, 3, 2), // combo box editor insets
                1,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(2, 3),            // button margin                
                getButtonMargin(2, 3),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(2, 0, 2, 0), // menu item margin
                new InsetsUIResource(2, 5, 2, 6), // menu margin
                new InsetsUIResource(3, 3, 4, 3)  // popup menu separator margin
                );
    }
    
    
    public static MicroLayout createWindowsVistaLowResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 2, 2, 2), // text insets
                new InsetsUIResource(1, 2, 2, 2), // wrapped text insets
                new InsetsUIResource(1, 2, 2, 2), // combo box editor insets
                1,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(1, 2),            // button margin                
                getButtonMargin(1, 2),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(3, 0, 3, 0), // menu item margin
                new InsetsUIResource(2, 3, 2, 4), // menu margin
                new InsetsUIResource(2, 3, 3, 3)  // popup menu separator margin
                );
    }
    
    
    public static MicroLayout createWindowsVistaHiResMicroLayout() {
        return new MicroLayout(
                new InsetsUIResource(1, 2, 2, 2), // text insets
                new InsetsUIResource(1, 2, 2, 2), // wrapped text insets
                new InsetsUIResource(1, 2, 2, 2), // combo box editor insets
                1,                                // combo border size
                1,                                // combo popup border size
                new Insets(3, 2, 4, 2),           // button border insets
                getButtonMargin(1, 2),            // button margin                
                getButtonMargin(1, 2),            // commit button margin
                WINDOWS_CHECK_BOX_MARGIN,         // check box margin 
                new InsetsUIResource(2, 0, 2, 0), // menu item margin
                new InsetsUIResource(2, 5, 2, 6), // menu margin
                new InsetsUIResource(3, 3, 4, 3)  // popup menu separator margin
                );
    }
    
    
    // Helper Code ************************************************************
    
    private static InsetsUIResource getButtonMargin(int top, int bottom) {
        int pad = Options.getUseNarrowButtons() ? 4 : 14;
        return new InsetsUIResource(top, pad, bottom, pad);
    }
    
    
  
}
