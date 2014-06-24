/*******************************************************************************
 * Copyright (c) 2010 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.eclipse.jdepend.views;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import jdepend.framework.ClassFileParser;
import jdepend.framework.JDepend;
import jdepend.framework.JavaClass;
import jdepend.framework.JavaPackage;
import jdepend.framework.PackageComparator;
import jdepend.framework.PackageFilter;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.part.ISetSelectionTarget;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;
import de.loskutov.eclipse.jdepend.JDependConstants;


/**
 *
 */
public class PackageTreeView extends ViewPart implements IShowInSource {
    protected TreeViewer viewer;
    protected ViewContentProvider treeContent;
    protected Action actionOpenJava;
    protected Action actionRefresh;
    protected Action actionConsole;
    protected Action actionSave;
    protected List<JavaPackage> analyzedPackages;
    protected ToolTipHandler tooltip;
    protected TreeSelectionListener treeSelectionHandler;

    /** key is Resource, value is treeObject*/
    protected HashMap<IResource, TreeObject> resourceMap;

    /** The view's identifier */
    public static final String ID = JDepend4EclipsePlugin.ID + ".views.PackageTreeView"; //$NON-NLS-1$

    final class TreeSelectionListener implements ISelectionChangedListener, ISelectionProvider {

        private TreeObject lastSelection;
        private final List<ISelectionChangedListener> listeners;

        public TreeSelectionListener() {
            super();
            listeners = new ArrayList<ISelectionChangedListener>();
        }

        @Override
        public void selectionChanged(SelectionChangedEvent event) {
            IStructuredSelection selection = (IStructuredSelection)event.getSelection();

            ArrayList<IResource> al = new ArrayList<IResource>();
            Iterator<?> iter = selection.iterator();
            IResource [] resources;
            while (iter.hasNext()) {
                TreeObject o = (TreeObject) iter.next();
                if (o.isLeaf()) {
                    TreeLeaf tleaf = (TreeLeaf) o;
                    if(tleaf.getIResource() != null){
                        al.add(tleaf.getIResource());
                    }
                } else {
                    TreeFolder tfolder = (TreeFolder)o;
                    resources = tfolder.getIResources();
                    for (int i = 0; i < resources.length; i++) {
                        if(!al.contains(resources[i])){
                            al.add(resources[i]);
                        }
                    }
                }
            }
            resources = al.toArray(new IResource[al.size()]);

            updateDependencyView(analyzedPackages, resources);
            updateMetricsView(analyzedPackages, resources);
            boolean singleSelection = selection.size() == 1;
            actionOpenJava.setEnabled(singleSelection);
            IStatusLineManager manager = getViewSite().getActionBars().getStatusLineManager();
            if(singleSelection){
                lastSelection = (TreeObject) selection.getFirstElement();
                if (lastSelection.isLeaf()) {
                    manager.setMessage(lastSelection.getName() + " -> "
                            + ((TreeLeaf) lastSelection).getByteCodePath());
                } else {
                    manager.setMessage(lastSelection.getPackageName() + " -> "
                            + lastSelection.getIResource());
                }
            } else {
                lastSelection = null;
                manager.setMessage(selection.size() + " elements selected");
            }
            try {
                // to select this view again, if selection was lost
                getSite().getPage().showView(ID);
            } catch (PartInitException e) {
                JDepend4EclipsePlugin.handle(e);
            }
            fireSelectionChanged();
        }

        public void fireSelectionChanged() {
            ISelection selection = getSelection();
            SelectionChangedEvent event = new SelectionChangedEvent(this, selection);
            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).selectionChanged(event);
            }
        }

        @Override
        public void addSelectionChangedListener(ISelectionChangedListener listener) {
            if(listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public ISelection getSelection() {
            if(lastSelection == null){
                return StructuredSelection.EMPTY;
            }
            Object selectedObj;
            if(lastSelection.isLeaf()) {
                TreeLeaf child = (TreeLeaf) lastSelection;
                selectedObj = child.getIJavaElement();
            } else {
                TreeFolder folder = (TreeFolder) lastSelection;
                selectedObj = folder.getIJavaElement();
            }
            if(selectedObj == null){
                return StructuredSelection.EMPTY;
            }
            return new StructuredSelection(selectedObj);
        }

        @Override
        public void removeSelectionChangedListener(ISelectionChangedListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void setSelection(ISelection selection) {
            // noop
        }
    }

    private TreeFolder getRoot(){
        return (TreeFolder) viewer.getInput();
    }

    private void setRoot(TreeFolder root){
        viewer.setInput(root);
    }

    final class ViewContentProvider implements IStructuredContentProvider, ITreeContentProvider {

        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            // ignored
        }
        @Override
        public void dispose() {
            // ignored
        }
        @Override
        public Object[] getElements(Object parent) {
            if (getRoot() == parent) {
                return getChildren(getRoot());
            }
            return getChildren(parent);
        }
        @Override
        public Object getParent(Object child) {
            if (child instanceof TreeObject) {
                return ((TreeObject) child).getParent();
            }
            return null;
        }
        @Override
        public Object[] getChildren(Object parent) {
            if (parent instanceof TreeFolder) {
                return ((TreeFolder) parent).getChildren();
            }
            return new Object[0];
        }
        @Override
        public boolean hasChildren(Object parent) {
            if (parent instanceof TreeFolder) {
                return ((TreeFolder) parent).hasChildren();
            }
            return false;
        }

        /**
         * @return result of JDepend run, never null
         */
        public List<JavaPackage> analyze(TreeFolder newRoot, IResource[] resource) {
            resourceMap.clear();
            if (resource == null) {
                return new ArrayList<JavaPackage>();
            }

            JDepend jdepend =  JDepend4EclipsePlugin.getJDependInstance();
            PackageFilter filter = jdepend.getFilter();

            for (int i = 0; i < resource.length; i++) {
                TreeFolder [] tp = createTree(resource[i], filter);
                for (int j = 0; j < tp.length; j++) {
                    // tree object check if child is exist
                    if(tp[j].hasChildren()) {
                        newRoot.addChild(tp[j]);
                    }
                }
            }
            List<JavaPackage> packages = runJDepend(jdepend, newRoot.getChildren());
            // update cycle info
            // set cycle to true, if one of packages is under the founded tree root
            for (int j = 0; j < packages.size(); j++) {
                JavaPackage jp = packages.get(j);
                boolean cycle = jp.containsCycle();
                if(cycle){
                    if(jp.getName().length() == 0){
                        newRoot.setContainsCycle(cycle);
                        continue;
                    }
                    TreeObject child = newRoot.findChild(jp);
                    if(child != null && !child.isLeaf()){
                        ((TreeFolder)child).setContainsCycle(cycle);
                    }
                }
            }
            return packages;
        }

        /**
         * Create object tree and initialize map with new IResource->TreeObject
         * pairs.
         * @param resource
         * @return TreeParent
         */
        private TreeFolder[] createTree(IResource resource, PackageFilter filter) {
            if (resource.getType() != IResource.FOLDER) {
                return new TreeFolder[0];
            }
            IContainer folder = (IContainer) resource;

            IResource[] resources = null;

            try {
                resources = folder.members();
            } catch (CoreException e) {
                JDepend4EclipsePlugin.handle(e);
            }

            if(resources == null || resources.length == 0){
                return new TreeFolder[0] ;
            }

            boolean isPackageRoot = false;
            IJavaElement javaElement = JavaCore.create(folder);

            if(javaElement != null){
                try {
                    isPackageRoot = TreeObject.isPackageRoot(javaElement
                            .getJavaProject(), folder);
                } catch (JavaModelException e) {
                    // too many errors on 3.3...
                    //                    JDepend4EclipsePlugin.handle(e);
                }
            } else {
                isPackageRoot = false;
            }


            if (!isPackageRoot && resourceMap.containsKey(folder)) {
                // @todo System.out.println("OOOO!! map contains: " + folder);
                return new TreeFolder[0];
            }

            TreeFolder tree = (TreeFolder) resourceMap.get(folder);
            ArrayList<TreeFolder> treeRoots = null;
            //add parent package, if not exist
            if (tree == null) {
                tree = new TreeFolder(javaElement);
                if (filter.accept(tree.getPackageName())) {
                    treeRoots = new ArrayList<TreeFolder>();
                    treeRoots.add(tree);
                    resourceMap.put(folder, tree);
                } else {
                    return new TreeFolder[0];
                }
            }
            if(treeRoots == null){
                return new TreeFolder[0];
            }
            TreeFolder[] results;
            TreeLeaf tleaf;
            IJavaElement javaChild;
            String tname;
            for (int i = 0; i < resources.length; i++) {
                if (resources[i].getType() == IResource.FOLDER) {
                    results = createTree(resources[i], filter);
                    for (int j = 0; j < results.length; j++) {
                        if (!treeRoots.contains(results[j])) {
                            treeRoots.add(results[j]);
                        }
                    }
                } else {
                    tname = resources[i].getName();
                    if (tname.endsWith(".java")) { //$NON-NLS-1$
                        javaChild = JavaCore.create(resources[i]);
                        if (javaChild == null) {
                            continue;
                        }
                        tleaf = new TreeLeaf(javaChild);
                        resourceMap.put(resources[i], tleaf);
                        tree.addChild(tleaf);
                    } else if (tname.endsWith(".class")) { //$NON-NLS-1$
                        tleaf = new TreeLeaf(resources[i]);
                        resourceMap.put(resources[i], tleaf);
                        tree.addChild(tleaf);
                    }
                }
            }

            return  treeRoots.toArray(new TreeFolder[treeRoots.size()]);
        }

    }

    static final class ViewLabelProvider extends LabelProvider {

        @Override
        public String getText(Object obj) {
            return obj.toString();
        }
        @Override
        public Image getImage(Object obj) {
            String imageKey = org.eclipse.jdt.ui.ISharedImages.IMG_OBJS_CLASS;
            if (obj instanceof TreeFolder){
                TreeFolder tp = (TreeFolder) obj;
                if(tp.hasCycle()){
                    return PlatformUI.getWorkbench().getSharedImages()
                            .getImage(ISharedImages.IMG_OBJS_WARN_TSK);
                }
                imageKey = org.eclipse.jdt.ui.ISharedImages.IMG_OBJS_PACKAGE;
            }
            return JavaUI.getSharedImages().getImage(imageKey);
        }
    }

    static final class NameSorter extends ViewerSorter {
        // exist here because in 2.x Eclipse the parent class is abstract
    }

    /**
     * Emulated tooltip handler
     * Notice that we could display anything in a tooltip besides text and images.
     */
    private final class ToolTipHandler {
        protected Shell  tipShell;
        protected Label  tipLabelImage, tipLabelText;
        protected Point  tipPosition; // the position being hovered over

        /**
         * Creates a new tooltip handler
         *
         * @param parent the parent Shell
         */
        public ToolTipHandler(Shell parent) {
            final Display display = parent.getDisplay();

            tipShell = new Shell(parent, SWT.ON_TOP);
            GridLayout gridLayout = new GridLayout();
            gridLayout.numColumns = 2;
            gridLayout.marginWidth = 2;
            gridLayout.marginHeight = 2;
            tipShell.setLayout(gridLayout);

            tipShell.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));

            tipLabelImage = new Label(tipShell, SWT.NONE);
            tipLabelImage.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
            tipLabelImage.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
            tipLabelImage.setLayoutData(new GridData(GridData.FILL_HORIZONTAL |
                    GridData.VERTICAL_ALIGN_CENTER));

            tipLabelText = new Label(tipShell, SWT.NONE);
            tipLabelText.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
            tipLabelText.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
            tipLabelText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL |
                    GridData.VERTICAL_ALIGN_CENTER));
        }

        void dispose() {
            if (tipShell != null) {
                tipShell.dispose();
                tipShell = null;
            }
        }

        /**
         * Enables customized hover help for a specified control
         *
         * @control the control on which to enable hoverhelp
         */
        public void activateHoverHelp(final Control control) {
            /*
             * Get out of the way if we attempt to activate the control underneath the tooltip
             */
            control.addMouseListener(new MouseAdapter () {
                @Override
                public void mouseDown (MouseEvent e) {
                    if (tipShell.isVisible()) {
                        tipShell.setVisible(false);
                    }
                }
            });

            /*
             * Trap hover events to pop-up tooltip
             */
            control.addMouseTrackListener(new MouseTrackAdapter () {
                @Override
                public void mouseExit(MouseEvent e) {
                    if (tipShell.isVisible()) {tipShell.setVisible(false);}
                }
                @Override
                public void mouseHover (MouseEvent event) {
                    Point pt = new Point (event.x, event.y);

                    TreeItem ti = viewer.getTree().getItem(pt);

                    StringBuffer sb = new StringBuffer();
                    if(ti == null || ti.getText() == null){
                        if(analyzedPackages.isEmpty()){
                            sb.append(JDepend4EclipsePlugin.getResourceString("EmptyToolTipHint"));
                        } else {
                            return;
                        }
                    }

                    getTreeText(sb, ti);

                    if (sb.length() == 0) {
                        tipShell.setVisible(false);
                        return;
                    }

                    tipPosition = control.toDisplay(pt);

                    tipLabelText.setText(sb.toString());
                    //tipLabelImage.setImage(image); // accepts null
                    tipShell.pack();
                    setHoverLocation(tipShell, tipPosition);
                    tipShell.setVisible(true);
                }

                private void getTreeText(StringBuffer sb, TreeItem ti) {
                    if(ti == null || ti.getData() == null){
                        return;
                    }
                    sb.append(ti.getText());
                    TreeObject to = (TreeObject)ti.getData();

                    if(!to.isLeaf()){
                        try {
                            ArrayList<String> paths = ((TreeFolder)to).getClassesLocation();
                            for (int i = 0; i < paths.size(); i++) {
                                sb.append('\n').append(paths.get(i));
                            }
                        } catch (JavaModelException e) {
                            JDepend4EclipsePlugin.handle(e);
                        }
                    } else {
                        sb.append('\n').append(((TreeLeaf)to).getByteCodePath());
                    }
                }
            });

        }

        /**
         * Sets the location for a hovering shell
         * @param shell the object that is to hover
         * @param position the position of a widget to hover over
         */
        protected void setHoverLocation(Shell shell, Point position) {
            Rectangle displayBounds = shell.getDisplay().getBounds();
            Rectangle shellBounds = shell.getBounds();
            shellBounds.x = Math.max(Math.min(position.x + 8, displayBounds.width - shellBounds.width), 0);
            shellBounds.y = Math.max(Math.min(position.y + 16, displayBounds.height - shellBounds.height), 0);
            shell.setBounds(shellBounds);
        }
    }

    /**
     * The constructor.
     */
    public PackageTreeView() {
        super();
        resourceMap = new HashMap<IResource, TreeObject>(29);
        analyzedPackages = new ArrayList<JavaPackage>();
    }

    /**
     * @param treeObjects not null
     * @return non null list
     */
    protected List<JavaPackage> runJDepend(JDepend jdepend, TreeObject[] treeObjects) {
        for (int i = 0; i < treeObjects.length; i++) {
            try {
                if(!treeObjects[i].isLeaf()){
                    TreeFolder folder = (TreeFolder)treeObjects[i];
                    // add roots
                    ArrayList<String> dirs = folder.getClassesLocation();
                    for (int j = 0; j < dirs.size(); j++) {
                        jdepend.addDirectory("" + dirs.get(j)); //$NON-NLS-1$
                    }
                }
            } catch (Exception e) {
                // if directory doesn't exist, may be project need to be rebuild
                JDepend4EclipsePlugin.handle(e);
            }
        }

        final List<?> packages = new ArrayList<Object>(jdepend.analyze());
        List<JavaPackage> filteredPackages = filterPackages(packages, jdepend);
        Collections.sort(filteredPackages, new PackageComparator(PackageComparator.byName()));
        return filteredPackages;
    }

    public void updateUI(TreeFolder newRoot, IResource[] folder, List<JavaPackage> packages){
        analyzedPackages = packages;
        boolean hasContent = !packages.isEmpty();
        actionSave.setEnabled(hasContent);
        actionConsole.setEnabled(hasContent);
        actionOpenJava.setEnabled(hasContent);
        actionRefresh.setEnabled(hasContent);
        viewer.setInput(newRoot);
        // actualize tree
        // should select elements
        viewer.setSelection(new StructuredSelection(getRoot().getChildren()), true);
        JDependConsole.showConsole(packages);
        updateDependencyView(packages, folder);
        updateMetricsView(packages, folder);
    }

    protected void updateMetricsView(List<JavaPackage> packages, IResource[] folder){
        if(packages == null){
            return;
        }
        JavaPackage tempPack1;
        List<JavaPackage> selPackages = new ArrayList<JavaPackage>();
        // find corresponding packages
        for (int i = 0; i < folder.length; i++) {
            TreeObject to = resourceMap.get(folder[i]);
            if(to == null){
                continue;
            }
            if(!to.isLeaf()){
                // find packages, matching to folder
                String tempFolderName = to.getPackageName();
                for (int j = 0; j < packages.size(); j++) {
                    tempPack1 = packages.get(j);
                    if(tempFolderName.equals(tempPack1.getName())
                            || tempFolderName.endsWith("." + tempPack1.getName()) ){  //$NON-NLS-1$

                        selPackages.add(tempPack1);
                    }
                }
            }
        }
        if(folder.length > 0 && selPackages.isEmpty() && folder[0] instanceof IFolder){
            selPackages = packages;
        }

        MetricsView metricsView = (MetricsView) getView(MetricsView.ID);
        if(metricsView != null){
            metricsView.setInput(selPackages.toArray(new JavaPackage[0]));
        }
    }

    private static List<JavaPackage> filterPackages(List<?> packages, JDepend jdepend){
        ArrayList<JavaPackage> filteredPackages = new ArrayList<JavaPackage>();
        PackageFilter filter = jdepend.getFilter();
        for (int i = 0; i < packages.size(); i++) {
            JavaPackage tempPackage = (JavaPackage)packages.get(i);
            if(filter.accept(tempPackage.getName())){
                filteredPackages.add(tempPackage);
            }
        }
        return filteredPackages;
    }

    protected List<JavaPackage> updateDependencyView(List<JavaPackage> packages, IResource[] folder) {

        TreeObject to = null;
        List<JavaClass> selClasses = new ArrayList<JavaClass>();
        ClassFileParser classParser = JDepend4EclipsePlugin.getJDependClassFileParserInstance();

        JDepend jdepend =  JDepend4EclipsePlugin.getJDependInstance();
        PackageFilter filter = jdepend.getFilter();
        JavaPackage root = new JavaPackage("root");     //$NON-NLS-1$
        List<JavaPackage> selPackages = new ArrayList<JavaPackage>();

        JavaPackage tempPack1;
        JavaPackage tempPack2;

        String tempName;
        JavaClass tempClass = null;
        // find corresponding packages
        for (int i = 0; i < folder.length; i++) {
            to = resourceMap.get(folder[i]);
            if(to == null){
                continue;
            }
            if(!to.isLeaf()){
                // find packages, matching to folder
                String tempFolderName = to.getPackageName();
                for (int j = 0; j < packages.size(); j++) {
                    tempPack1 = packages.get(j);
                    if(tempFolderName.equals(tempPack1.getName())
                            || tempFolderName.endsWith("." + tempPack1.getName()) ){  //$NON-NLS-1$
                        selPackages.add(tempPack1);
                    }
                }
            } else {
                try {
                    tempName = ((TreeLeaf)to).getByteCodePath();
                    tempClass = classParser.parse(new File(tempName));
                    if(tempClass != null){
                        selClasses.add(tempClass);
                    }
                } catch (IOException e) {
                    // exception: if class file doesn't exist, may be project must be rebuilded!
                    JDepend4EclipsePlugin.handle(e);
                }
            }
        }

        List<?> aff;
        List<?> eff;

        if(folder.length > 0 && selPackages.isEmpty() && folder[0] instanceof IFolder){
            selPackages = packages;
        }

        for (int i = 0; i < selPackages.size(); i++) {
            tempPack1 = selPackages.get(i);

            aff = tempPack1.getAfferents();
            for (int j = 0; j < aff.size(); j++) {
                tempPack2 = (JavaPackage)aff.get(j);
                if(filter.accept(tempPack2.getName())){
                    root.addAfferent(tempPack2);
                }
            }

            eff = tempPack1.getEfferents();
            for (int j = 0; j < eff.size(); j++) {
                tempPack2 = (JavaPackage)eff.get(j);
                if(filter.accept(tempPack2.getName())){
                    root.addEfferent(tempPack2);
                }
            }
        }

        ArrayList<JavaPackage> imported = new ArrayList<JavaPackage>();
        for (int i = 0; i < selClasses.size(); i++) {
            tempClass = selClasses.get(i);

            List<?> effP = new ArrayList<Object>(tempClass.getImportedPackages());
            for (int j = 0; j < effP.size(); j++) {
                tempPack2 = (JavaPackage)effP.get(j);
                if(!imported.contains(tempPack2) && filter.accept(tempPack2.getName())){
                    imported.add(tempPack2);
                }
            }
        }

        if(selClasses.size()> 0){
            for (int j = 0; j < imported.size(); j++) {
                tempName = imported.get(j).getName();
                for (int i = 0; i < packages.size(); i++) {
                    tempPack1 = packages.get(i);
                    if(tempPack1.getName().equals(tempName)){
                        //System.out.println("add efferent from class:" + tempPack1.getName());
                        root.addEfferent(tempPack1);
                        break;
                    }
                }
            }
        }

        ArrayList<Object> cl = new ArrayList<Object>();

        IPreferenceStore settings = JDepend4EclipsePlugin.getDefault().getPreferenceStore();
        if (settings.getBoolean(JDependConstants.PREF_USE_ALL_CYCLES_SEARCH)) {
            root.collectAllCycles(cl);
        } else {
            root.collectCycle(cl);
        }

        // root is fiction
        if(cl.contains(root)){
            cl.remove(root);
            if(cl.contains(root)){
                cl.remove(root);
            }
        }

        aff = root.getAfferents();
        eff = root.getEfferents();

        PackageComparator pc = new PackageComparator(PackageComparator.byName());
        Collections.sort(aff, pc);
        Collections.sort(eff, pc);
        Collections.sort(cl, pc);

        // clear multiple packages in cycle list
        ArrayList<Object> cl2 = new ArrayList<Object>();
        Object tempObj;
        for (int i = 0; i < cl.size(); i++) {
            tempObj = cl.get(i);
            if(!cl2.contains(tempObj)){
                cl2.add(tempObj);
            }
        }
        cl.clear();
        cl = cl2;

        DependencyView dependView = (DependencyView) getView(DependencyView.ID);
        if(dependView != null){
            if(analyzedPackages.isEmpty()) {
                dependView.setTooltipText(JDepend4EclipsePlugin.getResourceString("EmptyToolTipHint"));
            } else {
                dependView.setTooltipText(null);
            }
            dependView.getAffViewer().setInput(aff.toArray(new JavaPackage[0]));
            dependView.getEffViewer().setInput(eff.toArray(new JavaPackage[0]));
            dependView.getCycleViewer().setInput(cl.toArray(new JavaPackage[0]));
            dependView.getSelViewer().setInput(selPackages.toArray(new JavaPackage[0]));
        }
        return selPackages;
    }

    private IViewPart getView(String id){
        IViewPart part = getSite().getPage().findView(id);
        if(part == null){
            try {
                part = getSite().getPage().showView(id);
            } catch (PartInitException e) {
                JDepend4EclipsePlugin.handle(e);
            }
        }
        return part;
    }

    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    @Override
    public void createPartControl(Composite parent) {

        tooltip = new ToolTipHandler(parent.getShell());
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        treeContent = new ViewContentProvider();
        viewer.setContentProvider(treeContent);
        viewer.setLabelProvider(new ViewLabelProvider());
        viewer.setSorter(new NameSorter());
        setRoot(new TreeFolder());
        treeSelectionHandler = new TreeSelectionListener();
        viewer.addSelectionChangedListener(treeSelectionHandler);
        getSite().setSelectionProvider(treeSelectionHandler);

        makeActions();
        hookContextMenu();
        hookDoubleClickAction();
        contributeToActionBars();
        tooltip.activateHoverHelp(viewer.getTree());
        PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(),
                "de.loskutov.eclipse.jdepend.jdepend"); //$NON-NLS-1$
    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillContextMenu(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, viewer);
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalPullDown(bars.getMenuManager());
        fillLocalToolBar(bars.getToolBarManager());
    }

    private void fillLocalPullDown(IMenuManager manager) {
        manager.add(actionRefresh);
        manager.add(new Separator());
        manager.add(actionConsole);
        manager.add(actionSave);
    }

    protected void fillContextMenu(IMenuManager manager) {
        if(getSelection().size() == 1) {
            manager.add(actionOpenJava);
        }
        // Other plug-ins can contribute there actions here
        manager.add(new Separator("additions")); //$NON-NLS-1$
    }

    private void fillLocalToolBar(IToolBarManager manager) {
        manager.add(actionRefresh);
        manager.add(actionOpenJava);
        manager.add(new Separator());
        manager.add(actionConsole);
        manager.add(actionSave);
    }

    private void makeActions() {
        final IPreferenceStore prefs = JDepend4EclipsePlugin.getDefault().getPreferenceStore();
        actionOpenJava = new Action() {
            @Override
            public void run() {

                TreeObject to = getSelectedElement();
                if(to == null){
                    return;
                }

                if(!to.isLeaf()){
                    IJavaElement [] javaElements = ((TreeFolder)to).getIJavaElements().toArray(new IJavaElement [0]);
                    Action oa = new OpenJavaPerspectiveAction();
                    oa.run();

                    IViewPart part = getView(JavaUI.ID_PACKAGES);
                    if(part instanceof ISetSelectionTarget){
                        ISetSelectionTarget target = (ISetSelectionTarget) part;
                        target.selectReveal(new StructuredSelection(javaElements));
                    }
                    return;
                }
                IJavaElement javaElement = ((TreeLeaf)to).getIJavaElement();
                if(javaElement == null){
                    return;
                }
                IEditorPart javaEditor = null;
                try {
                    Action oa = new OpenJavaPerspectiveAction();
                    oa.run();
                    javaEditor = JavaUI.openInEditor(javaElement);
                    JavaUI.revealInEditor(javaEditor, javaElement);
                } catch (PartInitException e) {
                    JDepend4EclipsePlugin.handle(e);
                } catch (JavaModelException e) {
                    JDepend4EclipsePlugin.handle(e);
                }
            }
        };
        actionOpenJava.setText(JDepend4EclipsePlugin.getResourceString("PackageTreeView.Switch_to_package_view")); //$NON-NLS-1$
        actionOpenJava.setToolTipText(JDepend4EclipsePlugin.getResourceString("PackageTreeView.Change_to_Java_Perspective")); //$NON-NLS-1$
        actionOpenJava.setImageDescriptor(JavaUI.getSharedImages().getImageDescriptor(org.eclipse.jdt.ui.ISharedImages.IMG_OBJS_PACKAGE));
        actionOpenJava.setEnabled(false);

        actionRefresh = new Action() {
            @Override
            public void run() {
                TreeObject [] childs = getRoot().getChildren();
                ArrayList<IResource> al = new ArrayList<IResource>();
                IResource [] roots;
                for (int i = 0; i < childs.length; i++) {
                    if(childs[i].isLeaf()){
                        al.add(((TreeLeaf)childs[i]).getIResource());
                    } else {
                        roots = ((TreeFolder)childs[i]).getIResources();
                        for (int j = 0; j < roots.length; j++) {
                            if(!al.contains(roots[j])){
                                al.add(roots[j]);
                            }
                        }
                    }
                }
                IResource [] dirs = al.toArray(new IResource[al.size()]);
                runJDependJob(dirs);
            }
        };
        actionRefresh.setText(JDepend4EclipsePlugin.getResourceString("PackageTreeView.Refresh_view")); //$NON-NLS-1$
        actionRefresh.setToolTipText(JDepend4EclipsePlugin.getResourceString("PackageTreeView.Run_JDepend_again")); //$NON-NLS-1$
        actionRefresh.setImageDescriptor(JDepend4EclipsePlugin.getDefault().getImageDescriptor(JDepend4EclipsePlugin.IMG_REFRESH));
        actionRefresh.setEnabled(false);

        actionConsole = new Action("Show JDepend output", IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                if(isChecked()) {
                    prefs.setValue(JDependConstants.PREF_OUTPUT_NIX, false);
                    JDependConsole.showConsole(analyzedPackages);
                } else {
                    prefs.setValue(JDependConstants.PREF_OUTPUT_NIX, true);
                    IViewPart part = getSite().getPage().findView(IConsoleConstants.ID_CONSOLE_VIEW);
                    if(part != null){
                        getSite().getPage().hideView(part);
                    }
                }
            }
        };
        actionConsole.setToolTipText("Show JDepend output"); //$NON-NLS-1$
        actionConsole.setImageDescriptor(JDepend4EclipsePlugin.getDefault().getImageDescriptor("console.gif"));
        actionConsole.setEnabled(false);
        actionConsole.setChecked(!prefs.getBoolean(JDependConstants.PREF_OUTPUT_NIX));

        actionSave = new SaveToFileAction("Save JDepend output") {
            @Override
            protected void write(FileWriter fw) {
                boolean asXml = prefs.getBoolean(JDependConstants.SAVE_AS_XML);
                jdepend.textui.JDepend jdep;
                if(asXml){
                    jdep = new jdepend.xmlui.JDepend(new PrintWriter(fw)){
                        @Override
                        protected ArrayList<JavaPackage> getPackagesList() {
                            return (ArrayList<JavaPackage>) analyzedPackages;
                        }
                    };
                } else {
                    jdep = new jdepend.textui.JDepend(new PrintWriter(fw)){
                        @Override
                        protected ArrayList<JavaPackage> getPackagesList() {
                            return (ArrayList<JavaPackage>) analyzedPackages;
                        }
                    };
                }
                jdep.analyze();
            }
        };
        actionSave.setToolTipText("Save JDepend output"); //$NON-NLS-1$
        actionSave.setImageDescriptor(JDepend4EclipsePlugin.getDefault().getImageDescriptor("saveToFile.gif"));
        actionSave.setEnabled(false);
    }

    protected TreeObject getSelectedElement(){
        ISelection selection = viewer.getSelection();
        Object obj = ((IStructuredSelection) selection).getFirstElement();
        if(!(obj instanceof TreeObject)){
            return null;
        }
        return (TreeObject)obj;
    }

    private void hookDoubleClickAction() {
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                actionOpenJava.run();
            }
        });
    }

    @Override
    public void dispose() {
        getSite().setSelectionProvider(null);
        tooltip.dispose();
        viewer.getTree().dispose();
        viewer = null;
        analyzedPackages = null;
        resourceMap = null;
        super.dispose();
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /** Makes the view visible in the active perspective. If there
     * isn't a view registered <code>null</code> is returned.
     * Otherwise the opened view part is returned.
     */
    public static PackageTreeView openInActivePerspective() {
        try {
            return (PackageTreeView) JDepend4EclipsePlugin.getActivePage().showView(
                    PackageTreeView.ID);
        } catch (Exception pe) {
            return null;
        }
    }

    @Override
    public ShowInContext getShowInContext() {
        IStructuredSelection selection = getSelection();
        if(selection.size() != 1){
            return null;
        }
        TreeObject firstElement = (TreeObject) selection.getFirstElement();
        if(firstElement.getIResource() != null) {
            return new ShowInContext(null,  new StructuredSelection(firstElement.getIResource()));
        }
        return null;
    }

    @Override
    public Object getAdapter(Class adapter) {
        if (adapter.equals(ISelectionProvider.class)) {
            return treeSelectionHandler;
        }
        if (adapter == IShowInSource.class) {
            return this;
        }
        return super.getAdapter(adapter);
    }

    private IStructuredSelection getSelection() {
        return ((IStructuredSelection)viewer.getSelection());
    }

    /**
     * Convenience method for running an operation with progress and
     * error feedback.
     */
    public final void runJDependJob(final IResource[] resources) {
        Job job = new Job("JDepend analysis"){
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    final TreeFolder newRoot = new TreeFolder();
                    final List<JavaPackage> packages = treeContent.analyze(newRoot, resources);
                    PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            updateUI(newRoot, resources, packages);

                        }
                    });
                } catch (Exception e) {
                    JDepend4EclipsePlugin.handle(e);
                    monitor.setCanceled(true);
                }
                return monitor.isCanceled()? Status.CANCEL_STATUS : Status.OK_STATUS;
            }
        };
        job.setUser(true);
        job.setPriority(Job.INTERACTIVE);
        job.schedule();
    }
}
