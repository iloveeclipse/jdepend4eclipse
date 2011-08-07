/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.eclipse.jdepend.views;

import jdepend.framework.JavaPackage;

import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;

public class DependencyView extends ViewPart {
    private static final int FIRST_COLUMN = 0;
    private static final int LAST_COLUMN = 8;

    public class PackageSorter extends ViewerSorter {
        protected boolean reverse;
        protected int column;

        protected int getPriority(){
            return column;
        }

        public void reversePriority() {
            reverse = !reverse;
        }

        public void setPriority(int column) {
            this.column = column;
        }

        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            if(e1 instanceof String [] && e2 instanceof String []){
                String[] strings1 = (String[]) e1;
                String[] strings2 = (String[]) e2;
                if(strings1.length <= column || strings2.length <= column){
                    return super.compare(viewer, e1, e2);
                }
                int result;
                switch (column) {
                case FIRST_COLUMN:
                case LAST_COLUMN:
                    result = strings1[column].compareTo(strings2[column]);
                    break;
                default:
                    result = Float.valueOf(strings1[column]).compareTo(Float.valueOf(strings2[column]));
                    break;
                }
                if(reverse){
                    result = -result;
                }
                return result;
            }
            return super.compare(viewer, e1, e2);
        }
    }

    private SashForm sashForm;

    private Table cycleTable;
    private Table effTable;
    private Table affTable;
    private Table selTable;

    private TableViewer selViewer;
    private TableViewer cycleViewer;
    private TableViewer affViewer;
    private TableViewer effViewer;

    private boolean disposed;

    final static String[] columnHeaders =
        {
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_Package"),
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_CC"),
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_AC"),
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_Ca"),
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_Ce"),
        JDepend4EclipsePlugin.getResourceString("DependencyView.A"), // abstractness
        JDepend4EclipsePlugin.getResourceString("DependencyView.I"), // instability
        JDepend4EclipsePlugin.getResourceString("DependencyView.D"), // distance
        JDepend4EclipsePlugin.getResourceString("DependencyView.column_Cycle") };

    private final ColumnLayoutData[] columnLayouts =
        {
            new ColumnWeightData(50, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true),
            new ColumnWeightData(10, true)};

    /** The view's identifier */
    public static final String ID =
            JDepend4EclipsePlugin.ID + ".views.DependencyView";

    static final class ViewLabelProvider
    extends LabelProvider
    implements ITableLabelProvider {
        public String getColumnText(Object obj, int index) {
            if(obj instanceof String[]){
                String [] arr = (String[]) obj;
                if(index < arr.length ){
                    if(index + 1 ==arr.length){
                        return "";
                    }
                    return arr[index];
                }
            }
            return getText(obj);
        }
        public Image getColumnImage(Object obj, int index) {
            if(obj instanceof String[]){
                String [] arr = (String[]) obj;
                if(index < arr.length ){
                    if( "true".equalsIgnoreCase(arr[index])){
                        return //JavaUI.getSharedImages().getImage( ISharedImages.
                                PlatformUI.getWorkbench().getSharedImages().getImage(
                                        org.eclipse.ui.ISharedImages.IMG_OBJS_WARN_TSK);
                    }
                }
            }
            return index == 0? getImage(obj) : null;
        }
        @Override
        public Image getImage(Object obj) {
            return JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_PACKAGE);
        }

    }

    static final class PackageViewContentProvider implements IStructuredContentProvider {

        JavaPackage[] elements;
        String [][] data;

        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            if(newInput!= null && newInput instanceof JavaPackage[]){
                setElements((JavaPackage[])newInput);
            }
        }

        public void dispose() {
            // ignored
        }

        private String getShortFloat(float f){
            StringBuffer sb = new StringBuffer();
            int i = (int)f;
            sb.append(i);
            f = (f - i) * 100;
            i = (int)f;
            sb.append('.');
            if(i < 10){
                sb.append('0');
            }
            sb.append(i);
            return sb.toString();
        }

        public Object[] getElements(Object parent) {
            if(elements == null) {
                return new Object[0];
            }
            data = new String[elements.length][];
            for (int elt = 0; elt < data.length; elt++) {
                String[] row = new String [columnHeaders.length];
                for (int column = 0; column < row.length; column++) {
                    switch (column) {
                    case FIRST_COLUMN : row[column] = "" + elements[elt].getName();
                    break;
                    case FIRST_COLUMN + 1 : row[column] = "" + elements[elt].getConcreteClassCount();
                    break;
                    case FIRST_COLUMN + 2 : row[column] = "" + elements[elt].getAbstractClassCount();
                    break;
                    case FIRST_COLUMN + 3 : row[column] = "" + elements[elt].afferentCoupling();
                    break;
                    case FIRST_COLUMN + 4 : row[column] = "" + elements[elt].efferentCoupling();
                    break;
                    case FIRST_COLUMN + 5 : row[column] = getShortFloat(elements[elt].abstractness());
                    break;
                    case FIRST_COLUMN + 6 : row[column] = getShortFloat(elements[elt].instability());
                    break;
                    case FIRST_COLUMN + 7 : row[column] = getShortFloat(elements[elt].distance());
                    break;
                    case LAST_COLUMN : row[column] = "" + elements[elt].containsCycle();
                    break;
                    default :
                        break;
                    }
                }
                data[elt] = row;
            }
            return data;
        }


        /**
         * Sets the elements.
         * @param elements The elements to set
         */
        public void setElements(JavaPackage[] elements) {
            this.elements = elements;
        }

    }

    /**
     * The constructor.
     */
    public DependencyView() {
        super();
    }

    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    @Override
    public void createPartControl(Composite parent) {
        sashForm = new SashForm(parent, SWT.VERTICAL);
        sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
        PlatformUI.getWorkbench().getHelpSystem().setHelp(sashForm,
                "de.loskutov.eclipse.jdepend.jdepend");

        Group group1 = new Group(sashForm, SWT.NONE);
        GridLayout gridLayout1 = new GridLayout();
        group1.setLayout (gridLayout1);
        gridLayout1.numColumns = 1;
        group1.setLayoutData (new GridData (GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
        group1.setText (JDepend4EclipsePlugin.getResourceString("DependencyView.Selected_objects"));

        selTable = createTable(group1);
        selViewer = new TableViewer(selTable);
        selViewer.setContentProvider(new PackageViewContentProvider());
        selViewer.setLabelProvider(new ViewLabelProvider());
        createColumns(selTable, selViewer);

        Group group = new Group(sashForm, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        group.setLayout (gridLayout);
        gridLayout.numColumns = 1;
        group.setLayoutData (new GridData (GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
        group.setText (JDepend4EclipsePlugin.getResourceString("DependencyView.Packages_with_cycle"));

        cycleTable = createTable(group);
        cycleViewer = new TableViewer(cycleTable);
        cycleViewer.setContentProvider(new PackageViewContentProvider());
        cycleViewer.setLabelProvider(new ViewLabelProvider());
        createColumns(cycleTable, cycleViewer);

        // second table with depends on
        Group group2 = new Group(sashForm, SWT.NONE);
        GridLayout gridLayout2 = new GridLayout();
        group2.setLayout (gridLayout2);
        gridLayout.numColumns = 1;
        group2.setLayoutData (new GridData (GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
        group2.setText (JDepend4EclipsePlugin.getResourceString("DependencyView.Depends_upon"));

        effTable = createTable(group2);
        effViewer = new TableViewer(effTable);
        effViewer.setContentProvider(new PackageViewContentProvider());
        effViewer.setLabelProvider(new ViewLabelProvider());
        createColumns(effTable, effViewer);

        // third table with used by
        Group group3 = new Group(sashForm, SWT.NONE);
        GridLayout gridLayout3 = new GridLayout();
        group3.setLayout (gridLayout3);
        gridLayout.numColumns = 1;
        group3.setLayoutData (new GridData (GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
        group3.setText (JDepend4EclipsePlugin.getResourceString("DependencyView.Used_by"));


        affTable = createTable(group3);
        affViewer = new TableViewer(affTable);
        affViewer.setContentProvider(new PackageViewContentProvider());
        affViewer.setLabelProvider(new ViewLabelProvider());
        createColumns(affTable, affViewer);

        // set percentual
        sashForm.setWeights(new int[] { 15, 15, 35, 35 });

        setTooltipText(JDepend4EclipsePlugin.getResourceString("EmptyToolTipHint"));
    }

    public void setTooltipText(String text){
        selTable.setToolTipText(text);
        effTable.setToolTipText(text);
        affTable.setToolTipText(text);
        cycleTable.setToolTipText(text);
    }

    protected Table createTable(Composite mySashForm) {
        Table table =
                new Table(
                        mySashForm,
                        SWT.H_SCROLL | SWT.V_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(GridData.FILL_BOTH));
        TableLayout layout = new TableLayout();
        table.setLayout(layout);
        return table;
    }

    private void createColumns(final Table table, final TableViewer viewer) {
        TableLayout layout = (TableLayout) table.getLayout();
        final PackageSorter sorter = new PackageSorter();
        viewer.setSorter(sorter);
        SelectionListener headerListener = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selectedCol = table.indexOf((TableColumn) e.widget);
                if (selectedCol == sorter.getPriority()) {
                    sorter.reversePriority();
                } else {
                    sorter.setPriority(selectedCol);
                }
                viewer.refresh();
            }
        };
        for (int i = 0, lentgh = columnHeaders.length; i < lentgh; i++) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setResizable(true);
            column.setText(columnHeaders[i]);
            layout.addColumnData(columnLayouts[i]);
            column.addSelectionListener(headerListener);
        }
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        if(sashForm!= null){
            sashForm.setFocus();
        }
    }

    /**
     * Returns the affTable.
     * @return Table
     */
    public TableViewer getAffViewer() {
        return affViewer;
    }

    /**
     * Returns the cycleTable.
     * @return Table
     */
    public TableViewer getCycleViewer() {
        return cycleViewer;
    }

    /**
     * Returns the effTable.
     * @return Table
     */
    public TableViewer getEffViewer() {
        return effViewer;
    }

    /**
     * Returns the selTable.
     * @return Table
     */
    public TableViewer getSelViewer() {
        return selViewer;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IWorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        sashForm.dispose();
        disposed = true;
        super.dispose();
    }

    public boolean isDisposed(){
        return disposed;
    }

}