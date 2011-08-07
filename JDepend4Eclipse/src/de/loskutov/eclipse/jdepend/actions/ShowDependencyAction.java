/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.eclipse.jdepend.actions;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;
import de.loskutov.eclipse.jdepend.views.PackageTreeView;

public class ShowDependencyAction implements IObjectActionDelegate {

    /** Stores the selection. */
    private IStructuredSelection selection;

    public ShowDependencyAction() {
        super();
    }

    /** @see org.eclipse.ui.IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart) */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // noop
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
     */
    public void run(IAction action) {
        final IResource[] resources = getSelectedResources();
        if(resources.length == 0){
            return;
        }
        IWorkbench workb = JDepend4EclipsePlugin.getDefault().getWorkbench();
        try {
            workb.showPerspective(
                    "de.loskutov.eclipse.jdepend.ui.JDependPerspective", //$NON-NLS-1$
                    workb.getActiveWorkbenchWindow());
        } catch (Exception e) {
            JDepend4EclipsePlugin.handle(e);
            return;
        }
        final PackageTreeView packageView = PackageTreeView.openInActivePerspective();
        if(packageView == null){
            return;
        }
        packageView.runJDependJob(resources);
    }


    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction, org.eclipse.jface.viewers.ISelection)
     */
    public void selectionChanged(IAction action, ISelection mySelection) {
        if (mySelection instanceof IStructuredSelection) {
            this.selection = (IStructuredSelection) mySelection;
            if (action != null) {
                try {
                    action.setEnabled(isEnabled());
                } catch (Exception e) {
                    action.setEnabled(false);
                    JDepend4EclipsePlugin.handle(e);
                }
            }
        }
    }

    protected boolean isEnabled() throws Exception {
        IResource[] resources = getSelectedResources();
        return resources.length > 0;
    }

    /** Returns the selected resources.
     *
     * @return the selected resources
     */
    protected IResource[] getSelectedResources() {
        ArrayList<Object> resources = new ArrayList<Object>();
        if (selection != null && !selection.isEmpty()) {
            for (Iterator<?> elements = selection.iterator(); elements.hasNext();) {
                Object next = elements.next();
                if(next == null){
                    continue;
                }
                if (next instanceof IFolder) {
                    resources.add(next);
                    continue;
                } else if (next instanceof IAdaptable) {
                    IAdaptable a = (IAdaptable) next;
                    Object adapter = a.getAdapter(IFolder.class);
                    if (adapter instanceof IFolder) {
                        resources.add(adapter);
                        continue;
                    }
                }
                if(next instanceof IJavaElement){
                    try {
                        IResource javaRes = ((IJavaElement)next).getCorrespondingResource();
                        if(javaRes != null && javaRes.getType() == IResource.FOLDER) {
                            resources.add(javaRes);
                        }
                    } catch (JavaModelException e) {
                        JDepend4EclipsePlugin.handle(e);
                    }
                }
            }
        }

        if (!resources.isEmpty()) {
            return resources.toArray(new IResource[0]);
        }
        return new IResource[0];
    }

}
