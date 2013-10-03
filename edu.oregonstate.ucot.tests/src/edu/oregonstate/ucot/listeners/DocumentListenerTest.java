package edu.oregonstate.ucot.listeners;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Before;

public class DocumentListenerTest {
	
	@Before
	public void setUp() {
		IProject project = createNewEmptyProject();
		setUpProject(project);
	}

	private IProject createNewEmptyProject() {
		IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspace.getProject("TestProject");
		if(project.exists()){}
			//fail("Could not create test project");
		
		try {
			project.create(new NullProgressMonitor());
		} catch (CoreException e) {
		}
		
		return project;
	}
	
	private void setUpProject(IProject project) {
		
	}
}
