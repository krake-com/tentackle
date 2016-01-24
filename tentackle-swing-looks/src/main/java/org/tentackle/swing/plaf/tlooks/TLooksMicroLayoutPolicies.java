/**
 * copied from JGoodies Look And Feel because of static methods.
 */


package org.tentackle.swing.plaf.tlooks;

import com.jgoodies.looks.LookUtils;
import com.jgoodies.looks.MicroLayout;
import com.jgoodies.looks.MicroLayoutPolicy;
import javax.swing.UIDefaults;

/**
 * Copied from JGoodies {@code MicroLayoutPolicies} because of
 * static methods.
 *
 * @author harald
 */
public class TLooksMicroLayoutPolicies {


    private TLooksMicroLayoutPolicies() {
        // Override default constructor; prevents instantation.
    }


    // Getting a MicroLayoutPolicy ********************************************

    /**
     * Returns the default MicroLayoutPolicy for the Plastic L&amp;fs.
     * Uses component insets that are similar to the Windows L&amp;f
     * micro layout, making it easier to
     *
     * @return a Windows-like micro layout policy for the Plastic L&amp;fs
     */
    public static MicroLayoutPolicy getDefaultPlasticPolicy() {
        return new DefaultPlasticPolicy();
    }


    /**
     * Returns the default MicroLayoutPolicy for the Windows L&amp;f.
     * It aims to describe component insets that follow the native guidelines.
     *
     * @return the default micro layout policy for the Windows platform.
     */
    public static MicroLayoutPolicy getDefaultWindowsPolicy() {
        return new DefaultWindowsPolicy();
    }


    // MicroLayoutPolicy Implementations **************************************

    /**
     * Implements the default font lookup for the Plastic L&f family
     * when running in a Windows environment.
     */
    private static final class DefaultPlasticPolicy implements MicroLayoutPolicy {

        @Override
        public MicroLayout getMicroLayout(String lafName, UIDefaults table) {
            boolean isClassic = !LookUtils.IS_LAF_WINDOWS_XP_ENABLED;
            boolean isVista = LookUtils.IS_OS_WINDOWS_VISTA;
            boolean isLowRes = LookUtils.IS_LOW_RESOLUTION;
            boolean isPlasticXP = lafName.equals("JGoodies Plastic XP");
            if (isPlasticXP) {
                if (isVista) {
                    return isClassic
                        ? TLooksMicroLayouts.createPlasticXPVistaClassicMicroLayout()
                        : TLooksMicroLayouts.createPlasticXPVistaMicroLayout();
                } else {
                    return isLowRes
                        ? TLooksMicroLayouts.createPlasticXPLowResMicroLayout()
                        : TLooksMicroLayouts.createPlasticXPHiResMicroLayout();
                }
            } else {
                if (isVista) {
                    return isClassic
                        ? TLooksMicroLayouts.createPlasticVistaClassicMicroLayout()
                        : TLooksMicroLayouts.createPlasticVistaMicroLayout();
                } else {
                    return isLowRes
                        ? TLooksMicroLayouts.createPlasticLowResMicroLayout()
                        : TLooksMicroLayouts.createPlasticHiResMicroLayout();
                }
            }
        }

    }


    /**
     * Implements the default font lookup on the Windows platform.
     */
    private static final class DefaultWindowsPolicy implements MicroLayoutPolicy {

        @Override
        public MicroLayout getMicroLayout(String lafName, UIDefaults table) {
            boolean isClassic = !LookUtils.IS_LAF_WINDOWS_XP_ENABLED;
            boolean isVista = LookUtils.IS_OS_WINDOWS_VISTA;
            boolean isLowRes = LookUtils.IS_LOW_RESOLUTION;
            if (isClassic) {
                return isLowRes
                    ? TLooksMicroLayouts.createWindowsClassicLowResMicroLayout()
                    : TLooksMicroLayouts.createWindowsClassicHiResMicroLayout();
            } else if (isVista) {
                return isLowRes
                    ? TLooksMicroLayouts.createWindowsVistaLowResMicroLayout()
                    : TLooksMicroLayouts.createWindowsVistaHiResMicroLayout();
            } else {
                return isLowRes
                ? TLooksMicroLayouts.createWindowsXPLowResMicroLayout()
                : TLooksMicroLayouts.createWindowsXPHiResMicroLayout();
            }
        }

    }



}
