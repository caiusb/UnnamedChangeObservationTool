package edu.oregonstate.ucot.listeners;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;

public class DocumentListener implements IDocumentListener {
	@Override
	public void documentChanged(DocumentEvent event) {
		System.out.println("Document changed " + event.getDocument().toString());
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// TODO Auto-generated method stub
		
	}
}