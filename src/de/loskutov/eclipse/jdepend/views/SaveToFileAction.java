/*******************************************************************************
 * Copyright (c) 2010 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.eclipse.jdepend.views;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import de.loskutov.eclipse.jdepend.JDepend4EclipsePlugin;
import de.loskutov.eclipse.jdepend.JDependConstants;

public class SaveToFileAction extends Action {

    private static final int CANCEL = -1;

    private static final int APPEND = 0;

    private static final int OVERRIDE = 1;

    private static String lastUsedFile;

    public SaveToFileAction(String text) {
        super(text);
    }

    public void run() {

        IPreferenceStore prefs = JDepend4EclipsePlugin.getDefault().getPreferenceStore();
        boolean shouldAsk = prefs.getBoolean(JDependConstants.SAVE_TO_SHOW_OPTIONS);

        /*
         * Show dialog if prefs is set, asking for open in editor
         */
        boolean saveAsXml = prefs.getBoolean(JDependConstants.SAVE_AS_XML);
        if (shouldAsk) {
            MessageDialogWithToggle dialogWithToggle = MessageDialogWithToggle
                    .openYesNoCancelQuestion(getShell(), "Save JDepend output",
                            "Save JDepend output as XML (and not as plain text)?",
                            "Remember and do not ask me again", false, prefs,
                            JDependConstants.SAVE_TO_SHOW_OPTIONS);

            int returnCode = dialogWithToggle.getReturnCode();
            if (returnCode != IDialogConstants.YES_ID
                    && returnCode != IDialogConstants.NO_ID) {
                return;
            }
            saveAsXml = returnCode == IDialogConstants.YES_ID;
            prefs.setValue(JDependConstants.SAVE_AS_XML, saveAsXml);
        }

        /*
         * open file selection dialog (external)
         */
        File file = getFileFromUser(saveAsXml);
        if (file == null) {
            return;
        }

        /*
         * if selected file exists, ask for override/append/another file
         */
        int overrideOrAppend = checkForExisting(file);
        if (overrideOrAppend == CANCEL) {
            return;
        }

        IFile iFile = getWorkspaceFile(file);
        /*
         * if selected file is in the workspace, checkout it or show error message
         */
        if (iFile != null && !checkout(iFile, overrideOrAppend)) {
            return;
        }

        /*
         * save it
         */
        doSave(file, overrideOrAppend);
    }

    private static IFile getWorkspaceFile(File file) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IPath location = new Path(file.getAbsolutePath()); // Path.fromOSString();
        IFile[] files = workspace.getRoot().findFilesForLocation(location);
        List filesList = filterNonExistentFiles(files);
        if (filesList == null || filesList.size() != 1) {
            return null;
        }
        return (IFile) filesList.get(0);
    }

    private static List filterNonExistentFiles(IFile[] files) {
        if (files == null) {
            return null;
        }

        int length = files.length;
        ArrayList existentFiles = new ArrayList(length);
        for (int i = 0; i < length; i++) {
            if (files[i].exists()) {
                existentFiles.add(files[i]);
            }
        }
        return existentFiles;
    }

    private Shell getShell() {
        return JDepend4EclipsePlugin.getDefault().getWorkbench()
                .getActiveWorkbenchWindow().getShell();
    }

    private void doSave(final File file, int overrideOrAppend) {

        final FileWriter fw;
        try {
            fw = new FileWriter(file, overrideOrAppend == APPEND);
        } catch (IOException e) {
            errorDialog("Couldn't open file for writing: " + file, e);
            return;
        }

        Job job = new Job("Save JDepend output to file") {
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    write(fw);
                } catch (Exception e) {
                    errorDialog("Error during writing to file: " + file, e);
                } finally {
                    try {
                        fw.close();
                    } catch (IOException e) {
                        JDepend4EclipsePlugin.logError(e, "Couldn't close file: " + file);
                    }
                }
                return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
            }
        };
        job.schedule();
    }

    protected void write(FileWriter fw) {
        // do nothing
    }

    private void errorDialog(String string, Exception e) {
        String message = e == null? "" : e.getMessage();
        MessageDialog.openError(null, string, "Error: " + message);
    }

    /**
     * @param file
     *            non null
     * @param overrideOrAppend
     * @return true if file doesn't exist and was created or writable
     */
    private boolean checkout(IFile file, int overrideOrAppend) {
        if (file.getLocation() == null) {
            File file2 = new File(file.getFullPath().toOSString());
            if (!file2.exists()) {
                try {
                    file2.createNewFile();
                } catch (IOException e) {
                    errorDialog("Couldn't create file: " + file, e);
                    return false;
                }
            }
            boolean canWrite = file2.canWrite();
            if (!canWrite) {
                errorDialog("File is read-only: " + file, null);
            }
            return canWrite;
        }
        try {
            if (overrideOrAppend == APPEND && file.exists()) {
                file.appendContents(new ByteArrayInputStream(new byte[0]), true, true,
                        new NullProgressMonitor());
            } else {
                if (file.exists()) {
                    file.delete(true, new NullProgressMonitor());
                }
                file.create(new ByteArrayInputStream(new byte[0]), true,
                        new NullProgressMonitor());
            }
        } catch (CoreException e) {
            errorDialog("File is read-only: " + file, e);
            return false;
        }
        return true;
    }

    /**
     * @param file
     *            non null
     * @return OVERRIDE if file not exists or exists and may be overriden, APPEND if it
     *         exists and should be reused, CANCEL if action should be cancelled
     */
    private int checkForExisting(File file) {
        if (file.exists()) {
            MessageDialog md = new MessageDialog(getShell(),
                    "Warning: file already exist", null, "Warning: file already exist",
                    MessageDialog.WARNING,
                    new String[] { "Append", "Override", "Cancel" }, 0);
            int result = md.open();
            switch (result) {
            case APPEND: // Append btn index
                return APPEND;
            case OVERRIDE: // Override btn index
                return OVERRIDE;
            default:
                return CANCEL;
            }
        }
        return OVERRIDE;
    }

    private File getFileFromUser(boolean asXml) {
        FileDialog fd = new FileDialog(getShell());
        if (lastUsedFile == null) {
            String property = System.getProperty("user.home");
            fd.setFilterPath(property);
        } else {
            fd.setFileName(lastUsedFile);
        }
        fd.setFilterExtensions(new String[] { asXml? "*.xml" : "*.txt" });
        String fileStr = fd.open();
        if (fileStr != null) {
            if(new Path(fileStr).getFileExtension() == null){
                fileStr += asXml? ".xml" : ".txt";
            }
            lastUsedFile = fileStr;
            return new File(fileStr);
        }
        return null;
    }

}
