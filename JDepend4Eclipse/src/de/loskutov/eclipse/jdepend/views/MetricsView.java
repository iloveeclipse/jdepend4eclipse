/*******************************************************************************
 * Copyright (c) 2010 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.eclipse.jdepend.views;

import jdepend.framework.JavaPackage;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;

public class MetricsView extends ViewPart {

    /** The view's identifier */
    public static final String ID = JDepend4EclipsePlugin.ID + ".views.MetricsView"; //$NON-NLS-1$

    // paint surface for drawing
    protected PaintSurface paintSurface;

    protected ToolTipHandler tooltip;
    protected Canvas paintCanvas;
    private boolean disposed;

    private JavaPackage[] packages;

    /**
     * The constructor.
     */
    public MetricsView() {
        super();
    }

    /**
     * Cleanup
     */
    @Override
    public void dispose() {
        paintCanvas.dispose();
        paintSurface.dispose();
        tooltip.dispose();
        disposed = true;
        packages = null;
        super.dispose();
    }

    public void setInput(JavaPackage[] newPackages) {
        this.packages = newPackages;
        redrawPackages(newPackages);
    }

    protected void redrawPackages(JavaPackage[] metrics) {
        if (paintSurface != null) {
            paintSurface.drawMetrics(metrics);
        }
    }

    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    @Override
    public void createPartControl(Composite parent) {
        paintCanvas = new Canvas(parent, SWT.NONE);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        paintCanvas.setLayoutData(gridData);

        // paintSurface
        paintSurface = new PaintSurface(paintCanvas, parent.getDisplay().getSystemColor(
                SWT.COLOR_WHITE));

        tooltip = new ToolTipHandler(parent.getShell());
        tooltip.activateHoverHelp(paintCanvas);

        PlatformUI.getWorkbench().getHelpSystem().setHelp(paintCanvas,
                "de.loskutov.eclipse.jdepend.jdepend"); //$NON-NLS-1$

        if (packages != null) {
            redrawPackages(packages);
        }
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus() {
        paintSurface.setFocus();
    }

    public String getMetricInfo(int x, int y) {
        PaintSurface.Metric m = paintSurface.getMetric(x, y);
        return m == null ? null : m.toString();
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Emulated tooltip handler
     * Notice that we could display anything in a tooltip besides text and images.
     * For instance, it might make sense to embed large tables of data or buttons linking
     * data under inspection to material elsewhere, or perform dynamic lookup for creating
     * tooltip text on the fly.
     */
    protected class ToolTipHandler {
        protected Shell tipShell;

        protected Label tipLabelImage, tipLabelText;

        //private Widget tipWidget; // widget this tooltip is hovering over
        protected Point tipPosition; // the position being hovered over

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
            tipLabelImage
            .setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
            tipLabelImage
            .setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
            tipLabelImage.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
                    | GridData.VERTICAL_ALIGN_CENTER));

            tipLabelText = new Label(tipShell, SWT.NONE);
            tipLabelText.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
            tipLabelText.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
            tipLabelText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
                    | GridData.VERTICAL_ALIGN_CENTER));
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
            control.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseDown(MouseEvent e) {
                    if (tipShell.isVisible()) {
                        tipShell.setVisible(false);
                    }
                }
            });

            /*
             * Trap hover events to pop-up tooltip
             */
            control.addMouseTrackListener(new MouseTrackAdapter() {
                @Override
                public void mouseExit(MouseEvent e) {
                    if (tipShell.isVisible()) {
                        tipShell.setVisible(false);
                    }
                }

                @Override
                public void mouseHover(MouseEvent event) {
                    Point pt = new Point(event.x, event.y);
                    String text = getMetricInfo(event.x, event.y);
                    if (text == null && (packages != null && packages.length > 0)) {
                        tipShell.setVisible(false);
                        return;
                    }
                    if(text == null){
                        text = JDepend4EclipsePlugin.getResourceString("EmptyToolTipHint");
                    }

                    tipPosition = control.toDisplay(pt);

                    tipLabelText.setText(text);
                    //tipLabelImage.setImage(image); // accepts null
                    tipShell.pack();
                    setHoverLocation(tipShell, tipPosition);
                    tipShell.setVisible(true);
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
            shellBounds.x = Math.max(Math.min(position.x + 8, displayBounds.width
                    - shellBounds.width), 0);
            shellBounds.y = Math.max(Math.min(position.y - 40 + 16, displayBounds.height
                    - shellBounds.height), 0);
            shell.setBounds(shellBounds);
        }
    }

    /**
     * ToolTip help handler
     */
    protected interface ToolTipHelpTextHandler {
        /**
         * Get help text
         * @param widget the widget that is under help
         * @return a help text string
         */
        public String getHelpText(Widget widget);
    }
}