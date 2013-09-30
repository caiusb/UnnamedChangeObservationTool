/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.codingtracker.replaying;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * 
 * @author Stas Negara
 * 
 */
public class TimestampDialog extends Dialog {

	private static long timestamp= 0;

	private final String message;

	private Text text;

	protected TimestampDialog(Shell parentShell, String message) {
		super(parentShell);
		this.message= message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(message);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout(2, false);
		layout.horizontalSpacing= 5;
		composite.setLayout(layout);
		Label label= new Label(composite, SWT.NONE);
		label.setLayoutData(new GridData(140, 15));
		label.setText("Provide timestamp:");
		text= new Text(composite, SWT.BORDER);
		text.setLayoutData(new GridData(85, 15));
		text.setText(String.valueOf(timestamp));
		return composite;

	}

	@Override
	protected void okPressed() {
		try {
			timestamp= Long.valueOf(text.getText().trim());
		} catch (NumberFormatException ex) {
			timestamp= 0;
		}
		super.okPressed();
	}

}
