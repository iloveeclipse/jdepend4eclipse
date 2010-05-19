/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

/**
 * Created on 10.01.2003
 */
package de.loskutov.eclipse.jdepend;

import java.util.ArrayList;
import java.util.Collection;

import jdepend.framework.PackageFilter;

/**
 * @author Andrei
 */
public class JDepend extends jdepend.framework.JDepend {

    protected PackageFilter filter;
    protected ArrayList packagesList;

    /**
     * Constructor for JDepend.
     */
    public JDepend() {
        super();
    }

    /* (non-Javadoc)
     * @see jdepend.framework.JDepend#getFilter()
     */
    public PackageFilter getFilter() {
        if(filter == null){
            filter = new PackageFilter(getPackagesList());
        }
        return filter;
    }

    /**
     * Adds the specified package name to the collection
     * of packages to be filtered.
     *
     * @param name Package name.
     */
    public void addPackageToFilter(String name) {
        if (name.endsWith(".*")) { //$NON-NLS-1$
            name = name.substring(0, name.length()-2);
        }
        Collection packages = getPackagesList();
        if (name.length() > 0 && !packages.contains(name)) {
            packages.add(name);
        }
        /*for (int i = 0; i < packagesList.size(); i++) {
            System.out.println(packagesList.get(i));
        }*/
    }

    public Collection getPackagesList(){
        if(packagesList == null){
            packagesList = new ArrayList();
        }
        return packagesList;
    }


}
