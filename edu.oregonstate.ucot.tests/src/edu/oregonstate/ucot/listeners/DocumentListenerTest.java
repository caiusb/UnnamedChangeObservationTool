package edu.oregonstate.ucot.listeners;

import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocumentExtension;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.UIPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class DocumentListenerTest {

	@Rule
	public TestName testName = new TestName();
	private IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
	private IProject project;
	private static final String testFile = "testFile.txt";

	@Before
	public void setUp() throws Exception {
		project = createNewEmptyProject();
		addProjectFile(project);
	}

	@After
	public void tearDown() throws Exception {
		project.delete(true, new NullProgressMonitor());
	}

	private void addProjectFile(IProject project) throws Exception {
		IFile file = project.getFile(testFile);
		if (file.exists())
			fail("Test file already exists");

		file.create(null, false, new NullProgressMonitor());
	}

	private IProject createNewEmptyProject() throws Exception {
		IProject project = workspace.getProject("TestProject");
		if (project.exists())
			fail("Project already exists");

		project.create(new NullProgressMonitor());
		project.open(new NullProgressMonitor());

		return project;
	}

	@Test
	@SuppressWarnings("restriction")
	public void testSimpleFile() throws Exception {
		File file = new File("TestProject/" + testFile);
		UIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().openEditor(null, "testFile");
	}
}
