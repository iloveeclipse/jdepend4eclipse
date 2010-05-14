/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.eclipse.jdepend.views;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleFactory;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.part.IPageBookViewPage;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;
import de.loskutov.eclipse.jdepend.JDependConstants;

/**
 * @author Andrei
 */
public class JDependConsole extends MessageConsole {

    static JDependConsole console;

    boolean disposed;

    private static List packages;

    private static class ToggleXmlAction extends Action {

        public ToggleXmlAction() {
            super("Toggle text/XML output mode", IAction.AS_CHECK_BOX);
            setImageDescriptor(JDepend4EclipsePlugin.getDefault()
            .getImageDescriptor("asXml.gif"));
            setChecked(JDepend4EclipsePlugin.getDefault()
                    .getPreferenceStore().getBoolean(JDependConstants.PREF_OUTPUT_XML));

        }

        public void run() {
            IPreferenceStore preferenceStore = JDepend4EclipsePlugin.getDefault()
            .getPreferenceStore();
            boolean value = isChecked();
            preferenceStore.setValue(JDependConstants.PREF_OUTPUT_XML, value);
            showConsole(packages);
        }
    }


    private static class RemoveAction extends Action {
        public RemoveAction() {
            super("Close JDepend console", JDepend4EclipsePlugin.getDefault()
                    .getImageDescriptor(JDepend4EclipsePlugin.IMG_CLOSE));
        }

        public void run() {
            IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
            if (console != null) {
                manager.removeConsoles(new IConsole[] { console });
                console = null;
            }
        }
    }

    private JDependConsole(String name, ImageDescriptor imageDescriptor,
            boolean autoLifecycle) {
        super(name, imageDescriptor, autoLifecycle);
    }

    protected void dispose() {
        if (!disposed) {
            disposed = true;
            packages = null;
            super.dispose();
        }
    }

    public static class JDependConsoleFactory implements IConsoleFactory {

        public void openConsole() {
            showConsole();
        }

    }

    public static JDependConsole showConsole() {
        IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
        boolean exists = false;
        if (console != null) {
            IConsole[] existing = manager.getConsoles();
            for (int i = 0; i < existing.length; i++) {
                if (console == existing[i]) {
                    exists = true;
                }
            }
        } else {
            console = new JDependConsole("JDepend", null, true);
        }
        if (!exists) {
            manager.addConsoles(new IConsole[] { console });
        }
        manager.showConsoleView(console);
        return console;
    }

    public static void showConsole(final List packages1) {
        JDependConsole.packages = packages1;
        if(packages == null || JDepend4EclipsePlugin.getDefault().getPreferenceStore()
                    .getBoolean(JDependConstants.PREF_OUTPUT_NIX)){
            return;
        }

        final JDependConsole cons = showConsole();
        cons.clearConsole();

        Job job = new Job("JDepend text output processing"){
            protected IStatus run(IProgressMonitor monitor) {
                IOConsoleOutputStream stream = cons.newMessageStream();
                PrintWriter pw = new PrintWriter(stream);
                boolean asXml = JDepend4EclipsePlugin.getDefault().getPreferenceStore()
                .getBoolean(JDependConstants.PREF_OUTPUT_XML);
                jdepend.textui.JDepend jdep;
                if(asXml){
                    jdep = new jdepend.xmlui.JDepend(pw){
                        protected ArrayList getPackagesList() {
                            return (ArrayList) packages1;
                        }
                    };
                } else {
                    jdep = new jdepend.textui.JDepend(pw){
                        protected ArrayList getPackagesList() {
                            return (ArrayList) packages1;
                        }
                    };
                }
                jdep.analyze();
                return monitor.isCanceled()?  Status.CANCEL_STATUS : Status.OK_STATUS;
            }
        };
        Platform.getJobManager().cancel(console);
        job.setRule(cons.getSchedulingRule());
        job.schedule();
    }

    public static class JDependConsolePageParticipant implements IConsolePageParticipant {

        private RemoveAction removeAction;

        private ToggleXmlAction xmlAction;

        public void activated() {
            // noop
        }

        public void deactivated() {
            // noop
        }

        public void dispose() {
            removeAction = null;
            xmlAction = null;
            // followed causes sometimes problems with console removal
//            if (console != null) {
//                console.dispose();
//                console = null;
//            }
        }

        public void init(IPageBookViewPage page, IConsole console1) {
            removeAction = new RemoveAction();
            xmlAction = new ToggleXmlAction();
            IActionBars bars = page.getSite().getActionBars();
            bars.getToolBarManager().appendToGroup(IConsoleConstants.LAUNCH_GROUP,
                    removeAction);
            bars.getToolBarManager().appendToGroup(IConsoleConstants.LAUNCH_GROUP,
                    xmlAction);
        }

        public Object getAdapter(Class adapter) {
            return null;
        }
    }
}
