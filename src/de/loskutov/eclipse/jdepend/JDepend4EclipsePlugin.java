/*******************************************************************************
 * Copyright (c) 2004 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSD License
 * which accompanies this distribution, and is available at
 * http://www.opensource.org/licenses/bsd-license.php
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.eclipse.jdepend;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import jdepend.framework.ClassFileParser;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * The main plugin class to be used in the desktop.
 */
public class JDepend4EclipsePlugin extends AbstractUIPlugin implements IPropertyChangeListener {
    //The shared instance.
    private static JDepend4EclipsePlugin plugin;
   // private static boolean useSourcesOnly = true;
    //Resource bundle.
    private ResourceBundle resourceBundle;

    public static final String ID = "de.loskutov.eclipse.jdepend"; //$NON-NLS-1$
    public static final String ICON_PATH = "icons/";                         //$NON-NLS-1$
    /** Map containing preloaded ImageDescriptors */
    private Map imageDescriptors = new HashMap(13);
    public static final String IMG_REFRESH = "refresh.gif"; //$NON-NLS-1$
    public static final String IMG_CLOSE = "close.gif"; //$NON-NLS-1$
    /**
     * The constructor.
     */
    public JDepend4EclipsePlugin() {
        super();
        plugin = this;
        try {
            resourceBundle= ResourceBundle.getBundle(getClass().getName());
        } catch (MissingResourceException x) {
            resourceBundle = null;
        }
    }

    /**
     * Returns the shared instance.
     */
    public static JDepend4EclipsePlugin getDefault() {
        return plugin;
    }

    /**
     * Returns the workspace instance.
     */
    public static IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    /**
     * Returns the string from the plugin's resource bundle,
     * or 'key' if not found.
     */
    public static String getResourceString(String key) {
        ResourceBundle bundle = JDepend4EclipsePlugin.getDefault().getResourceBundle();
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Returns the plugin's resource bundle,
     */
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    /**
     * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent event) {
        /*if (event.getProperty().equals(JDependConstants.PREF_USE_SOURCES_ONLY)){
            useSourcesOnly = event.getNewValue().equals(Boolean.TRUE);
        } */
    }

    /** Call this method to retrieve the currently active Workbench page.
      *
      * @return the active workbench page.
      */
    public static IWorkbenchPage getActivePage() {
        IWorkbenchWindow window = getDefault().getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return null;
        }
        return window.getActivePage();
    }

    /** Call this method to log the given status.
      *
      * @param status the status to log.
      */
    private static void log(IStatus status) {
        getDefault().getLog().log(status);
    }

    public static void logError(Throwable t, String message) {
        getDefault().getLog().log(new Status(IStatus.ERROR, ID, 0, message, t));
    }

    public static JDepend getJDependInstance() {
        JDepend jdepend = new JDepend();
        IPreferenceStore settings = getDefault().getPreferenceStore();
        if (settings.getBoolean(JDependConstants.PREF_USE_FILTERS)) {
            String[] strings =
                parseList(settings.getString(JDependConstants.PREF_ACTIVE_FILTERS_LIST));
            for (int i = 0; i < strings.length; i++) {
                jdepend.addPackageToFilter(strings[i]);
            }
        }
        return jdepend;
    }

    public static ClassFileParser getJDependClassFileParserInstance() {
        return new ClassFileParser(getJDependInstance().getFilter());
    }

    /**
     * Parses the comma separated string into an array of strings
     *
     * @return list
     */
    private static String[] parseList(String listString) {
        List list = new ArrayList(10);
        StringTokenizer tokenizer = new StringTokenizer(listString, ","); //$NON-NLS-1$
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            list.add(token);
        }
        return (String[]) list.toArray(new String[list.size()]);
    }


    /** Call this method to retrieve the (cache) ImageDescriptor for the given id.
      *
      * @param id the id of the image descriptor.
      * @return the ImageDescriptor instance.
      */
    public ImageDescriptor getImageDescriptor(String id) {
        ImageDescriptor imageDescriptor = (ImageDescriptor) imageDescriptors
                .get(id);
        if (imageDescriptor == null) {
            imageDescriptor = AbstractUIPlugin.imageDescriptorFromPlugin(
                    getDefault().getBundle().getSymbolicName(), ICON_PATH + id);
            imageDescriptors.put(id, imageDescriptor);
        }
        return imageDescriptor;
    }

    /**
     * Handles exceptions.
     *
     * @param t
     *            exception that should be handled.
     */
    public static void handle(Throwable t) {
        if (t instanceof InvocationTargetException) {
            t = ((InvocationTargetException) t).getTargetException();
        }

        IStatus error = null;
        if (t instanceof CoreException) {
            error = ((CoreException) t).getStatus();
        } else {
            error = new Status(IStatus.ERROR, JDepend4EclipsePlugin.ID, 1, "JDepend error", t); //$NON-NLS-1$
        }
        JDepend4EclipsePlugin.log(error);
    }

}
