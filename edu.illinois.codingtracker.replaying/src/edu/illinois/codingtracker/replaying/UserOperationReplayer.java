/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.illinois.codingtracker.replaying;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IEditorPart;

import edu.illinois.codingtracker.helpers.Configuration;
import edu.illinois.codingtracker.compare.helpers.EditorHelper;
import edu.illinois.codingtracker.helpers.ResourceHelper;
import edu.illinois.codingtracker.helpers.ViewerHelper;
import edu.illinois.codingtracker.operations.JavaProjectsUpkeeper;
import edu.illinois.codingtracker.operations.OperationDeserializer;
import edu.illinois.codingtracker.operations.UserOperation;
import edu.illinois.codingtracker.operations.files.EditedFileOperation;
import edu.illinois.codingtracker.operations.files.EditedUnsychronizedFileOperation;
import edu.illinois.codingtracker.operations.files.SavedFileOperation;
import edu.illinois.codingtracker.operations.files.snapshoted.SnapshotedFileOperation;
import edu.illinois.codingtracker.operations.resources.CreatedResourceOperation;
import edu.illinois.codingtracker.operations.resources.ReorganizedResourceOperation;
import edu.illinois.codingtracker.operations.resources.ResourceOperation;
import edu.illinois.codingtracker.operations.textchanges.TextChangeOperation;

/**
 * 
 * @author Stas Negara
 * 
 */
public class UserOperationReplayer {

	private enum ReplayPace {
		FAST, SIMULATE, CUSTOM
	}

	private final OperationSequenceView operationSequenceView;

	private IAction loadAction;

	private IAction resetAction;

	private IAction findAction;

	private final Collection<IAction> replayActions= new LinkedList<IAction>();

	private List<UserOperation> userOperations;

	private Iterator<UserOperation> userOperationsIterator;

	private UserOperation currentUserOperation;

	private Collection<UserOperation> breakpoints;

	private Thread userOperationExecutionThread;

	private volatile boolean forcedExecutionStop= false;

	private IEditorPart currentEditor= null;

	public UserOperationReplayer(OperationSequenceView operationSequenceView) {
		this.operationSequenceView= operationSequenceView;
	}

	void addToolBarActions() {
		IToolBarManager toolBarManager= operationSequenceView.getToolBarManager();
		toolBarManager.add(createLoadOperationSequenceAction());
		toolBarManager.add(createResetOperationSequenceAction());
		toolBarManager.add(new Separator());
		toolBarManager.add(createReplayAction(newReplaySingleOperationAction(), "Step", "Replay the current user operation", false));
		toolBarManager.add(createReplayAction(newReplayOperationSequenceAction(ReplayPace.CUSTOM), "Custom", "Replay the remaining user operations at a custom pace", true));
		toolBarManager.add(createReplayAction(newReplayOperationSequenceAction(ReplayPace.SIMULATE), "Simulate", "Simulate the remaining user operations at the user pace", true));
		toolBarManager.add(createReplayAction(newReplayOperationSequenceAction(ReplayPace.FAST), "Fast", "Fast replay of the remaining user operations", true));
		toolBarManager.add(createReplayAction(newJumpToAction(), "Jump", "Jump as close as possible to a given timestamp", false));
		toolBarManager.add(new Separator());
		toolBarManager.add(createFindOperationAction());
		toolBarManager.add(new Separator());
	}

	private UserOperation findUserOperationClosestToTimestamp(long searchedTimestamp) {
		UserOperation foundUserOperation= null;
		long minimumDeltaTime= Long.MAX_VALUE;
		for (UserOperation userOperation : userOperations) {
			long currentDeltaTime= Math.abs(userOperation.getTime() - searchedTimestamp);
			if (currentDeltaTime < minimumDeltaTime) {
				minimumDeltaTime= currentDeltaTime;
				foundUserOperation= userOperation;
				if (minimumDeltaTime == 0) {
					//Found the exact match, no need to proceed.
					break;
				}
			}
		}
		return foundUserOperation;
	}

	private IAction createFindOperationAction() {
		findAction= new Action() {
			@Override
			public void run() {
				TimestampDialog dialog= new TimestampDialog(operationSequenceView.getShell(), "Find operation");
				if (dialog.open() == Window.OK) {
					long searchedTimestamp= dialog.getTimestamp();
					UserOperation foundUserOperation= findUserOperationClosestToTimestamp(searchedTimestamp);
					if (foundUserOperation == null) {
						showMessage("There are no operations near timestamp " + searchedTimestamp);
					} else {
						long deltaTime= Math.abs(searchedTimestamp - foundUserOperation.getTime());
						if (deltaTime == 0) {
							showMessage("Found the exact match!");
						} else {
							showMessage("Found the closest operation, delta = " + deltaTime + " ms.");
						}
						operationSequenceView.setSelection(new StructuredSelection(foundUserOperation));
						if (!operationSequenceView.getOperationSequenceFilter().isShown(foundUserOperation)) {
							showMessage("Operation with timestamp " + foundUserOperation.getTime() + " is filtered out");
						}
					}
				}
			}
		};
		ViewerHelper.initAction(findAction, "Find", "Find operation by its timestamp", false, false, false);
		return findAction;
	}

	private IAction createLoadOperationSequenceAction() {
		loadAction= new Action() {
			@Override
			public void run() {
				FileDialog fileDialog= new FileDialog(operationSequenceView.getShell(), SWT.OPEN);
				String selectedFilePath= fileDialog.open();
				if (selectedFilePath != null) {
					String operationsRecord= ResourceHelper.readFileContent(new File(selectedFilePath));
					try {
						userOperations= OperationDeserializer.getUserOperations(operationsRecord);
					} catch (RuntimeException e) {
						showMessage("Wrong format. Could not load user operations from file: " + selectedFilePath);
						throw e;
					}
					if (userOperations.size() > 0) {
						resetAction.setEnabled(true);
						findAction.setEnabled(true);
					}
					breakpoints= new HashSet<UserOperation>();
					prepareForReplay();
					System.out.println("Loaded " + userOperations.size() + " operations.");
				}
			}

		};
		ViewerHelper.initAction(loadAction, "Load", "Load operation sequence from a file", true, false, false);
		return loadAction;
	}

	private IAction createResetOperationSequenceAction() {
		resetAction= new Action() {
			@Override
			public void run() {
				JavaProjectsUpkeeper.clearWorkspace();
				prepareForReplay();
			}
		};
		ViewerHelper.initAction(resetAction, "Reset", "Reset operation sequence", false, false, false);
		return resetAction;
	}

	private void prepareForReplay() {
		initializeReplay();
		advanceCurrentUserOperation(null);
		operationSequenceView.setTableViewerInput(userOperations);
		updateReplayActionsStateForCurrentUserOperation();
	}

	private void initializeReplay() {
		UserOperation.isReplayedRefactoring= false;
		currentEditor= null;
		userOperationsIterator= userOperations.iterator();
	}

	private IAction createReplayAction(IAction action, String actionText, String actionToolTipText, boolean isToggable) {
		ViewerHelper.initAction(action, actionText, actionToolTipText, false, isToggable, false);
		replayActions.add(action);
		return action;
	}

	private IAction newReplaySingleOperationAction() {
		return new Action() {
			@Override
			public void run() {
				try {
					replayAndAdvanceCurrentUserOperation(null);
				} catch (RuntimeException e) {
					showReplayExceptionMessage();
					throw e;
				}
				updateReplayActionsStateForCurrentUserOperation();
			}
		};
	}

	private IAction newReplayOperationSequenceAction(final ReplayPace replayPace) {
		return new Action() {
			@Override
			public void run() {
				if (!this.isChecked()) {
					forcedExecutionStop= true;
					this.setEnabled(false);
					userOperationExecutionThread.interrupt();
				} else {
					replayUserOperationSequence(this, replayPace);
				}
			}
		};
	}

	private IAction newJumpToAction() {
		return new Action() {
			private final Map<String, UserOperation> snapshotsBeforeJumpToTimestamp= new HashMap<String, UserOperation>();

			private final Set<String> snapshotsAfterJumpToTimestamp= new HashSet<String>();

			private final Set<String> ensureSnapshots= new HashSet<String>();

			private ResourceOperation lastEditBeforeJumpToTimestamp= null;

			private long jumpToTimestamp= -1;

			private boolean shouldConsiderLastEditBeforeJumpTo= false;

			private boolean metFirstEditAfterJumpTo= false;

			@Override
			public void run() {
				TimestampDialog dialog= new TimestampDialog(operationSequenceView.getShell(), "Jump to timestamp");
				if (dialog.open() == Window.OK) {
					jumpToTimestamp= dialog.getTimestamp();
					while (true) {
						initializeAction();
						for (UserOperation userOperation : userOperations) {
							if (userOperation instanceof ResourceOperation) {
								handleResourceOperation((ResourceOperation)userOperation);
							}
							if (doesChangeFileContent(userOperation) && isAfterJumpToTimestamp(userOperation) && !metFirstEditAfterJumpTo) {
								shouldConsiderLastEditBeforeJumpTo= true;
							}
						}
						UserOperation startOperation= getStartOperation();
						if (ensureSnapshots.size() == 0) {
							jumpTo(startOperation);
							break;
						} else {
							jumpToTimestamp= startOperation.getTime();
						}
					}
				}
			}

			private void initializeAction() {
				shouldConsiderLastEditBeforeJumpTo= false;
				metFirstEditAfterJumpTo= false;
				snapshotsBeforeJumpToTimestamp.clear();
				snapshotsAfterJumpToTimestamp.clear();
				ensureSnapshots.clear();
				lastEditBeforeJumpToTimestamp= null;
			}

			private void handleResourceOperation(ResourceOperation resourceOperation) {
				if (resourceOperation instanceof ReorganizedResourceOperation) {
					ReorganizedResourceOperation reorganizedResourceOperation= (ReorganizedResourceOperation)resourceOperation;
					UserOperation snapshotOperation= snapshotsBeforeJumpToTimestamp.get(reorganizedResourceOperation.getResourcePath());
					if (snapshotOperation != null) {
						snapshotsBeforeJumpToTimestamp.put(reorganizedResourceOperation.getDestinationPath(), snapshotOperation);
					}
					if (snapshotsAfterJumpToTimestamp.contains(reorganizedResourceOperation.getResourcePath())) {
						snapshotsAfterJumpToTimestamp.add(reorganizedResourceOperation.getDestinationPath());
					}
					return;
				}
				if (isAfterJumpToTimestamp(resourceOperation)) {
					if (doesCreateNewFileContent(resourceOperation)) {
						snapshotsAfterJumpToTimestamp.add(resourceOperation.getResourcePath());
					}
					if (doesEditFile(resourceOperation)) {
						metFirstEditAfterJumpTo= true;
						if (!snapshotsAfterJumpToTimestamp.contains(resourceOperation.getResourcePath())) {
							ensureSnapshots.add(resourceOperation.getResourcePath());
						}
					}
				} else {
					if (doesCreateNewFileContent(resourceOperation)) {
						snapshotsBeforeJumpToTimestamp.put(resourceOperation.getResourcePath(), resourceOperation);
					}
					if (doesEditFile(resourceOperation)) {
						lastEditBeforeJumpToTimestamp= resourceOperation;
					}
				}
			}

			private boolean isAfterJumpToTimestamp(UserOperation userOperation) {
				//Note that equals is also after since this will be replayed after jump as well.
				return userOperation.getTime() >= jumpToTimestamp;
			}

			private boolean doesCreateNewFileContent(ResourceOperation resourceOperation) {
				return resourceOperation instanceof SnapshotedFileOperation || resourceOperation instanceof CreatedResourceOperation;
			}

			private boolean doesEditFile(ResourceOperation resourceOperation) {
				return resourceOperation instanceof EditedFileOperation || resourceOperation instanceof EditedUnsychronizedFileOperation;
			}

			private boolean doesChangeFileContent(UserOperation userOperation) {
				return userOperation instanceof TextChangeOperation || userOperation instanceof SavedFileOperation;
			}

			private UserOperation getStartOperation() {
				UserOperation startOperation= null;
				//Ensure that the last edited file before the "jump to" timestamp is snapshoted to account for jumps inside such edits.
				if (lastEditBeforeJumpToTimestamp != null && shouldConsiderLastEditBeforeJumpTo) {
					ensureSnapshots.add(lastEditBeforeJumpToTimestamp.getResourcePath());
				}
				if (ensureSnapshots.size() == 0) {
					startOperation= findUserOperationClosestToTimestamp(jumpToTimestamp);
				} else {
					for (String fileToEnsureSnapshot : ensureSnapshots) {
						UserOperation snapshotOperation= snapshotsBeforeJumpToTimestamp.get(fileToEnsureSnapshot);
						if (snapshotOperation == null) {
							showMessage("A file edited after jump to timestamp was not snapshoted before it: " + fileToEnsureSnapshot);
							break;
						}
						if (startOperation == null || startOperation.getTime() > snapshotOperation.getTime()) {
							startOperation= snapshotOperation;
						}
					}
				}
				return startOperation;
			}

			private void jumpTo(UserOperation userOperation) {
				initializeReplay();
				UserOperation oldUserOperation= currentUserOperation;
				currentUserOperation= userOperation;
				while (userOperationsIterator.hasNext()) {
					if (currentUserOperation == userOperationsIterator.next()) {
						break;
					}
				}
				updateSequenceView(oldUserOperation);
			}
		};
	}

	private void replayUserOperationSequence(IAction executionAction, ReplayPace replayPace) {
		if (replayPace == ReplayPace.CUSTOM) {
			CustomDelayDialog dialog= new CustomDelayDialog(operationSequenceView.getShell());
			if (dialog.open() == Window.CANCEL) {
				executionAction.setChecked(false);
				return;
			}
		}
		forcedExecutionStop= false;
		loadAction.setEnabled(false);
		resetAction.setEnabled(false);
		findAction.setEnabled(false);
		toggleReplayActions(false);
		executionAction.setEnabled(true);
		userOperationExecutionThread= new UserOperationExecutionThread(executionAction, replayPace, CustomDelayDialog.getDelay());
		userOperationExecutionThread.start();
	}

	private void replayAndAdvanceCurrentUserOperation(ReplayPace replayPace) {
		try {
			if (!Configuration.isInTestMode && currentEditor != null && currentEditor != EditorHelper.getActiveEditor()) {
				if (userOperationExecutionThread != null && userOperationExecutionThread.isAlive()) {
					forcedExecutionStop= true;
					userOperationExecutionThread.interrupt();
				}
				showMessage("The current editor is wrong. Should be: \"" + currentEditor.getTitle() + "\"");
				return;
			}
			currentUserOperation.replay();
			currentEditor= EditorHelper.getActiveEditor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		advanceCurrentUserOperation(replayPace);
	}

	private void advanceCurrentUserOperation(ReplayPace replayPace) {
		UserOperation oldUserOperation= currentUserOperation;
		if (userOperationsIterator.hasNext()) {
			currentUserOperation= userOperationsIterator.next();
		} else {
			currentUserOperation= null;
		}
		if (replayPace != ReplayPace.FAST) { //Do not display additional info during a fast replay.
			updateSequenceView(oldUserOperation);
		}
	}

	private void updateSequenceView(UserOperation oldUserOperation) {
		operationSequenceView.removeSelection();
		if (oldUserOperation != null) {
			operationSequenceView.updateTableViewerElement(oldUserOperation);
		}
		operationSequenceView.displayInOperationTextPane(currentUserOperation);
		operationSequenceView.updateTableViewerElement(currentUserOperation);
	}

	private void updateReplayActionsStateForCurrentUserOperation() {
		toggleReplayActions(currentUserOperation != null);
	}

	private void toggleReplayActions(boolean state) {
		for (IAction action : replayActions) {
			action.setEnabled(state);
		}
	}

	boolean isBreakpoint(Object object) {
		return breakpoints.contains(object);
	}

	boolean isCurrentUserOperation(Object object) {
		return currentUserOperation == object;
	}

	void toggleBreakpoint(UserOperation userOperation) {
		if (breakpoints.contains(userOperation)) {
			breakpoints.remove(userOperation);
		} else {
			breakpoints.add(userOperation);
		}
	}

	private void showMessage(String message) {
		MessageBox messageBox= new MessageBox(operationSequenceView.getShell());
		messageBox.setMessage(message);
		messageBox.open();
	}

	private void showReplayExceptionMessage() {
		showMessage("An exception occured while executing the current user operation");
	}

	private class UserOperationExecutionThread extends Thread {

		private final IAction executionAction;

		private final ReplayPace replayPace;

		private final int customDelayTime;

		private boolean stoppedDueToException= false;

		private final UserOperation firstUserOperation;

		private UserOperationExecutionThread(IAction executionAction, ReplayPace replayPace, int customDelayTime) {
			this.executionAction= executionAction;
			this.replayPace= replayPace;
			this.customDelayTime= customDelayTime;
			firstUserOperation= currentUserOperation;
		}

		@Override
		public void run() {
			final long startReplayTime= System.currentTimeMillis();
			try {
				do {
					long executedOperationTime= currentUserOperation.getTime();
					long startTime= System.currentTimeMillis();
					executeUserOperationInUIThread();
					if (shouldStopExecution()) {
						break;
					} else {
						if (replayPace == ReplayPace.SIMULATE) {
							long nextOperationTime= currentUserOperation.getTime();
							long delayTime= nextOperationTime - executedOperationTime;
							simulateDelay(delayTime, startTime);
						} else if (replayPace == ReplayPace.CUSTOM) {
							simulateDelay(customDelayTime, startTime);
						}
						if (forcedExecutionStop) {
							break;
						}
					}
				} while (true);
			} finally {
				operationSequenceView.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						long replayTime= System.currentTimeMillis() - startReplayTime;
						if (stoppedDueToException) {
							showReplayExceptionMessage();
						}
						showMessage("Replay time: " + replayTime + " ms");
					}
				});
				updateToolBarActions();
			}
		}

		private void executeUserOperationInUIThread() {
			operationSequenceView.getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					try {
						replayAndAdvanceCurrentUserOperation(replayPace);
					} catch (RuntimeException e) {
						stoppedDueToException= true;
						throw e;
					}
				}
			});
		}

		private boolean shouldStopExecution() {
			return currentUserOperation == null || breakpoints.contains(currentUserOperation);
		}

		private void simulateDelay(long delayTime, long startTime) {
			long finishTime= System.currentTimeMillis();
			long sleepTime= delayTime - (finishTime - startTime);
			if (sleepTime > 0) {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					//ignore
				}
			}
		}

		private void updateToolBarActions() {
			executionAction.setChecked(false);
			loadAction.setEnabled(true);
			resetAction.setEnabled(true);
			findAction.setEnabled(true);
			updateReplayActionsStateForCurrentUserOperation();
			if (replayPace == ReplayPace.FAST) { //Update the view after the fast replay is over.
				operationSequenceView.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						updateSequenceView(firstUserOperation);
					}
				});
			}
		}
	}

}
