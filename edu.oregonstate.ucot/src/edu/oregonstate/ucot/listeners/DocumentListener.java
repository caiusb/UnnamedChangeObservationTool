package edu.oregonstate.ucot.listeners;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.internal.UIPlugin;

import edu.oregonstate.edu.recorders.Recorder;

@SuppressWarnings("restriction")
public class DocumentListener implements IDocumentListener {

	private Recorder recorder;

	public void setRecorder(Recorder recorder) {
		this.recorder = recorder;
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		IEditorPart activeEditor = UIPlugin.getDefault().getWorkbench()
				.getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		IEditorInput editorInput = activeEditor.getEditorInput();
		IPathEditorInput input = (IPathEditorInput) editorInput;
		String changedFile = input.getPath().toPortableString();
		recorder.record(changedFile, event.fOffset, event.fOffset, event.fText);
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// TODO Auto-generated method stub
	}
}