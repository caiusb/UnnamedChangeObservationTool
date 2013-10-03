package edu.oregonstate.ucot;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	private final class DocumentListener implements
			IDocumentListener {
		@Override
		public void documentChanged(DocumentEvent event) {
			System.out.println("Document changed " + event.getDocument().toString());
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// TODO Auto-generated method stub
			
		}
	}

	private static BundleContext context;

	static BundleContext getContext() {
		return context;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		UIJob job = new UIJob("Loading things") {
			
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
			IEditorPart editorPart = editorReference.getEditor(true);
			ISourceViewer sourceViewer = (ISourceViewer) editorPart.getAdapter(ITextOperationTarget.class);
			IDocument document = sourceViewer.getDocument();
			document.addDocumentListener(new DocumentListener());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;
	}

}
