/*******************************************************************************
 * Copyright (c) 2005 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSD License
 * which accompanies this distribution, and is available at
 * http://www.opensource.org/licenses/bsd-license.php
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.eclipse.jdepend.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;
import de.loskutov.eclipse.jdepend.JDependConstants;

/**
 * @author Andrei
 *
 */
public class JDependPreferenceInitializer extends AbstractPreferenceInitializer {

    /* (non-Javadoc)
     * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
     */
    public void initializeDefaultPreferences() {
        IPreferenceStore store = JDepend4EclipsePlugin.getDefault().getPreferenceStore();
        store.setDefault(JDependConstants.PREF_ACTIVE_FILTERS_LIST, "javax.*,java.*"); //$NON-NLS-1$
        store.setDefault(JDependConstants.PREF_INACTIVE_FILTERS_LIST, "com.ibm.*,com.sun.*,org.omg.*,sun.*,sunw.*"); //$NON-NLS-1$
        store.setDefault(JDependConstants.PREF_USE_FILTERS, true);
        store.setDefault(JDependConstants.PREF_USE_ALL_CYCLES_SEARCH, true);
        store.setDefault(JDependConstants.PREF_OUTPUT_XML, false);
        store.setDefault(JDependConstants.PREF_OUTPUT_NIX, true);
        store.setDefault(JDependConstants.SAVE_TO_SHOW_OPTIONS, true);
        store.setDefault(JDependConstants.SAVE_AS_XML, true);
    }

}
