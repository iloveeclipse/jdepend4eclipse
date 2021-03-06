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
import java.util.ArrayList;

import jdepend.framework.JavaPackage;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;

public class TreeFolder extends TreeObject {
    protected boolean cycle;
    private final ArrayList<TreeObject> children;
    private final ArrayList<IJavaElement> iJavaElements;
    private final IJavaElement javaElement;

    public TreeFolder(IJavaElement javaElement) {
        super();
        this.javaElement = javaElement;
        children = new ArrayList<TreeObject>();
        iJavaElements = new ArrayList<IJavaElement>();
        addIJavaElement(javaElement);
        if(javaElement != null) {
            try {
                setIResource(javaElement.getCorrespondingResource());
            } catch (JavaModelException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public TreeFolder() {
        this((IJavaElement)null);
    }

    public IJavaElement getIJavaElement(){
        return javaElement;
    }

    @Override
    public Object getAdapter(Class key) {
        if(key == IJavaElement.class){
            return getIJavaElement();
        }
        if(key == IResource.class){
            return getIResource();
        }
        return null;
    }

    private boolean addIJavaElement(IJavaElement resource){
        if(resource == null){
            return false;
        }

        if(iJavaElements.contains(resource)){
            return false;
        }
        iJavaElements.add(resource);
        return true;
    }


    public void addChild(TreeObject child) {
        if(child == null){
            return;
        }
        if(!children.contains(child)){
            children.add(child);
            child.setParent(this);
        } else if(!child.isLeaf()){
            TreeFolder tf = (TreeFolder)children.get(children.indexOf(child));
            TreeObject [] tc = ((TreeFolder)child).getChildren();
            for (int i = 0; i < tc.length; i++) {
                tf.addChild(tc[i]);
            }
            IJavaElement [] elements = ((TreeFolder)child).getIJavaElements().toArray(new IJavaElement [0]);
            for (int i = 0; i < elements.length; i++) {
                tf.addIJavaElement(elements[i]);
            }
        }
    }

    public void removeChild(TreeObject child) {
        children.remove(child);
        if(child!= null){
            child.setParent(null);
        }
    }
    public TreeObject[] getChildren() {
        return children.toArray(new TreeObject[children.size()]);
    }
    public boolean hasChildren() {
        return children.size() > 0;
    }
    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public boolean hasCycle(){
        if(cycle) {
            return cycle;
        }
        if(children != null){
            TreeObject to;
            for (int i = 0; i < children.size(); i++) {
                to = children.get(i);
                if(to.hasCycle()){
                    return true;
                }
            }
        }
        return cycle;
    }

    public TreeObject findChild(JavaPackage pack){
        if(children == null) {
            return null;
        }
        TreeObject to;
        for (int i = 0; i < children.size(); i++) {
            to = children.get(i);
            if(to.getPackageName().endsWith(pack.getName())){
                return to;
            }
            if(!to.isLeaf()){
                to = ((TreeFolder)to).findChild(pack);
                if(to != null){
                    return to;
                }
            }
        }
        return null;
    }

    public ArrayList<String> getClassesLocation() throws JavaModelException {
        ArrayList<IJavaElement> myIJavaElements = getIJavaElements();
        String dir;
        ArrayList<String> dirs = new ArrayList<String>();
        for (int i = 0; i < myIJavaElements.size(); i++) {
            dir = getPackageOutputPath(myIJavaElements.get(i));
            if(!dirs.contains(dir)){
                dirs.add(dir);
            }
        }
        if(dirs.size() == 0){
            if(getIResource() != null){
                dirs.add(getIResource().getLocation().toOSString());
            }
        }

        return dirs;
    }

    public ArrayList<IJavaElement> getIJavaElements(){
        return iJavaElements;
    }

    public IResource [] getIResources(){
        IJavaElement [] elements = getIJavaElements().toArray(new IJavaElement [0]);
        ArrayList<IResource> resources = new ArrayList<IResource>();
        IResource tresource;
        for (int i = 0; i < elements.length; i++) {
            try {
                tresource = elements[i].getCorrespondingResource();
                if(tresource != null && !resources.contains(tresource)){
                    resources.add(tresource);
                }
            } catch (JavaModelException e) {
                JDepend4EclipsePlugin.handle(e);
            }
        }
        if(getIResource() != null){
            resources.add(getIResource());
        }
        return resources.toArray(new IResource [resources.size()]);
    }

    public void setContainsCycle(boolean cycle){
        this.cycle = cycle;
    }

    @Override
    public String getName() {
        return getPackageName();
    }

    @Override
    public String getPackageName() {
        ArrayList<IJavaElement> elements = this.getIJavaElements();
        if(elements.size() == 0){
            if(getIResource() != null){
                String path = iResource.getFullPath().removeFirstSegments(1).toString();
                return path.replace('/', '.');
            }
            return "";        //$NON-NLS-1$
        }
        return getJavaPackageName(elements.get(0));
    }
}
