/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

/**
 * Created on 31.12.2002
 */
package de.loskutov.eclipse.jdepend.ui;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;

import de.loskutov.eclipse.jdepend.views.DependencyView;
import de.loskutov.eclipse.jdepend.views.MetricsView;
import de.loskutov.eclipse.jdepend.views.PackageTreeView;

/**
 * @author Andy the Great
 */
public class JDependPerspectiveFactory implements IPerspectiveFactory {

    /**
     * Constructor for JDependPerspectiveFactory.
     */
    public JDependPerspectiveFactory() {
        super();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IPerspectiveFactory#createInitialLayout(org.eclipse.ui.IPageLayout)
     */
    public void createInitialLayout(IPageLayout layout) {
        String editorArea = layout.getEditorArea();

        layout.setEditorAreaVisible(false);

        IFolderLayout top =
            layout.createFolder("top", IPageLayout.TOP, 1f, editorArea);  //$NON-NLS-1$
        top.addView(DependencyView.ID);
        top.addPlaceholder(IConsoleConstants.ID_CONSOLE_VIEW);

        IFolderLayout bottom_left =
            layout.createFolder("bottom_left", IPageLayout.LEFT, 0.3f, DependencyView.ID);  //$NON-NLS-1$
        bottom_left.addView(MetricsView.ID);

        IFolderLayout left =
            layout.createFolder("left", IPageLayout.TOP, 0.7f, MetricsView.ID);    //$NON-NLS-1$
        left.addView(PackageTreeView.ID);
    }

}
