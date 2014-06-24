/*******************************************************************************
 * Copyright (c) 2010 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/

/**
 * Created on 31.12.2002
 */
package de.loskutov.eclipse.jdepend.views;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

public abstract class TreeObject implements IAdaptable {
    protected TreeFolder parent;
    protected IResource iResource;
    final static protected String DEFAULT_PACKAGE_NAME = "default";// TODO hardcoded JDepend name !!! (Default package) //$NON-NLS-1$

    public abstract String getName();

    public void setParent(TreeFolder parent) {
        this.parent = parent;
    }

    public IResource getIResource(){
        return iResource;
    }

    public void setIResource(IResource resource){
        this.iResource = resource;
    }

    public TreeFolder getParent() {
        return parent;
    }
    @Override
    public String toString() {
        return getName();
    }

    public abstract boolean isLeaf() ;

    public abstract String getPackageName();

    public static String getJavaPackageName(IJavaElement resource) {
        String name = resource == null? "" : resource.getElementName(); //$NON-NLS-1$
        if(resource == null || name.length() == 0){
            return name;
        }
        int type = resource.getElementType();
        if( type == IJavaElement.PACKAGE_FRAGMENT){
            return name;
        }
        if(type == IJavaElement.PACKAGE_FRAGMENT_ROOT){
            return DEFAULT_PACKAGE_NAME;
        }
        IJavaElement ancestor = resource.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
        if(ancestor != null){
            return ancestor.getElementName();
        }
        return ""; //$NON-NLS-1$
    }

    protected String getPackageOutputPath(IJavaElement jElement) throws JavaModelException {
        String dir = ""; //$NON-NLS-1$
        if(jElement == null){
            return dir;
        }

        IJavaProject project = jElement.getJavaProject();

        if(project == null){
            return dir;
        }
        IPath path = project.getOutputLocation();

        IResource resource = jElement.getUnderlyingResource();
        if(resource == null){
            return dir;
        }
        // resolve multiple output locations here
        if (project.exists() && project.getProject().isOpen()) {
            IClasspathEntry entries[] = project.getRawClasspath();

            for (int i = 0; i < entries.length; i++) {
                IClasspathEntry classpathEntry = entries[i];
                if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                    IPath outputPath = classpathEntry.getOutputLocation();
                    if (outputPath != null) {
                        // if this source folder contains specified java resource
                        // then take bytecode location from himself
                        if(classpathEntry.getPath().isPrefixOf(resource.getFullPath())){
                            path = outputPath;
                            break;
                        }
                    }
                }
            }
        }

        if (path == null) {
            // check the default location if not already included
            IPath def = project.getOutputLocation();
            if (def != null && def.isPrefixOf(resource.getFullPath())){
                path = def;
            }
        }

        if (path == null) {
            return dir;
        }

        IWorkspace workspace = ResourcesPlugin.getWorkspace();

        if (!project.getPath().equals(path)) {
            IFolder outputFolder = workspace.getRoot().getFolder(path);
            if (outputFolder != null) {
                // linked resources will be resolved here!
                IPath rawPath = outputFolder.getRawLocation();
                if (rawPath != null) {
                    path = rawPath;
                }
            }
        } else {
            path = project.getProject().getLocation();
        }

        if (path == null) {
            return dir;
        }

        // here we should resolve path variables,
        // probably existing at first place of path
        IPathVariableManager pathManager = workspace.getPathVariableManager();
        path = pathManager.resolvePath(path);

        if (path == null) {
            return dir;
        }

        boolean packageRoot = false;
        try {
            packageRoot = isPackageRoot(project, resource);
        } catch (JavaModelException e) {
            // seems to be a bug in 3.3
        }
        if (packageRoot) {
            dir = path.toOSString();
        } else {
            String packPath = getPackageName().replace('.', '/');
            dir = path.append(packPath).toOSString();
        }
        return dir;
    }

    public static boolean isPackageRoot(IJavaProject project, IResource pack) throws JavaModelException{
        boolean isRoot = false;
        if(project == null || pack == null){
            return isRoot;
        }
        IPackageFragmentRoot root = project.getPackageFragmentRoot(pack);
        IClasspathEntry clPathEntry = null;
        if(root != null){
            clPathEntry = root.getRawClasspathEntry();
        }
        isRoot = clPathEntry != null;
        return isRoot;
    }

    public abstract boolean hasCycle();

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if(obj == this){
            return true;
        }
        if(! (obj instanceof TreeObject)){
            return false;
        }
        TreeObject treeObj = (TreeObject) obj;

        return this.getName().equals(treeObj.getName());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

}
