/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.codingtracker.recording;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import edu.illinois.codingtracker.listeners.document.FileDocumentListener;

/**
 * 
 * @author Stas Negara
 * @author Mohsen Vakilian - Added the logging methods.
 * 
 */
public class Activator implements BundleActivator {

	// The plug-in ID
	public static final String PLUGIN_ID= "edu.illinois.codingtracker.recording";

	private static Activator plugin;

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		plugin= this;
		
		UIJob job = new UIJob("Loading listeners") {
			
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				regiserListenersForOpenEditors();
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private void regiserListenersForOpenEditors() {
		IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IEditorReference[] editorReferences = activeWindow.getActivePage().getEditorReferences();
		for (IEditorReference editorReference : editorReferences) {
			IDocument document = getDocumentForEditor(editorReference);
			try {
				IEditorInput editorInput= editorReference.getEditorInput();
				IFileEditorInput fileEditorInput= null;
				if (editorInput instanceof IFileEditorInput) {
					fileEditorInput= (IFileEditorInput) editorInput;
				}
				IFile editorFile= fileEditorInput.getFile();
				document.addDocumentListener(new FileDocumentListener(editorFile));
			} catch (PartInitException e) {
			}
		}
	}

	private IDocument getDocumentForEditor(IEditorReference editorReference) {
		IEditorPart editorPart = editorReference.getEditor(true);
		ISourceViewer sourceViewer = (ISourceViewer) editorPart.getAdapter(ITextOperationTarget.class);
		IDocument document = sourceViewer.getDocument();
		return document;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		plugin= null;
	}

	public static Activator getDefault() {
		return plugin;
	}

	private ILog getLog() {
		return Platform.getLog(Platform.getBundle(PLUGIN_ID));
	}

	public static Status createInfoStatus(String message) {
		return new Status(Status.INFO, PLUGIN_ID, message);
	}

	public static Status createErrorStatus(String message, Exception e) {
		return new Status(Status.ERROR, PLUGIN_ID, message, e);
	}

	public void log(Status status) {
		getLog().log(status);
	}

}
