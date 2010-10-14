/*
 *Copyright (c) 2009, Soar Technology, Inc.
 *All rights reserved.
 *
 *Redistribution and use in source and binary forms, with or without modification,   *are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  * Neither the name of Soar Technology, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY  *EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED   *WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.   *IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,   *INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT   *NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR   *PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,    *WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)   *ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE    *POSSIBILITY OF SUCH *DAMAGE. 
 *
 * 
 */
package com.soartech.soar.ide.ui.views.explorer;

import java.awt.event.KeyEvent;
import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import com.soartech.soar.ide.core.SoarCorePlugin;
import com.soartech.soar.ide.core.sql.SoarDatabaseConnection;
import com.soartech.soar.ide.core.sql.ISoarDatabaseEventListener;
import com.soartech.soar.ide.core.sql.SoarDatabaseEvent;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow;
import com.soartech.soar.ide.core.sql.SoarDatabaseRowFolder;
import com.soartech.soar.ide.core.sql.StatementWrapper;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.JoinType;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.Table;
import com.soartech.soar.ide.ui.actions.dragdrop.SoarDatabaseExplorerDragAdapter;
import com.soartech.soar.ide.ui.actions.dragdrop.SoarDatabaseExplorerDropAdapter;
import com.soartech.soar.ide.ui.actions.explorer.AddAgentActionDelegate;
import com.soartech.soar.ide.ui.actions.explorer.AddChildRowAction;
import com.soartech.soar.ide.ui.actions.explorer.AddSubstateAction;
import com.soartech.soar.ide.ui.actions.explorer.ChangeJoinTypeAction;
import com.soartech.soar.ide.ui.actions.explorer.CheckRuleAgainstDatamapAction;
import com.soartech.soar.ide.ui.actions.explorer.DeleteDatabaseRowAction;
import com.soartech.soar.ide.ui.actions.explorer.ExportSoarDatabaseRowAction;
import com.soartech.soar.ide.ui.actions.explorer.GenerateDatamapAction;
import com.soartech.soar.ide.ui.actions.explorer.GenerateDatamapFromVisualSoarFileAction;
import com.soartech.soar.ide.ui.actions.explorer.ManageTagsAction;
import com.soartech.soar.ide.ui.actions.explorer.MarkProblemSpaceRootAction;
import com.soartech.soar.ide.ui.actions.explorer.OpenDatabaseRowInEditorAction;
import com.soartech.soar.ide.ui.actions.explorer.RemoveJoinFromParentAction;
import com.soartech.soar.ide.ui.actions.explorer.RenameDatabaseRowAction;
import com.soartech.soar.ide.ui.actions.soarmenu.NewSoarProjectActionDelegate;
import com.soartech.soar.ide.ui.actions.soarmenu.OpenSoarDatabaseProjectActionDelegate;
import com.soartech.soar.ide.ui.views.SoarDatabaseRowDoubleClickListener;
import com.soartech.soar.ide.ui.views.SoarLabelProvider;

import edu.umich.soar.debugger.jmx.SoarCommandLineMXBean;

/**
 * Implementation of a ViewPart representing the Package Explorer for 
 * the Soar IDE.
 * 
 * @author aron
 */
public class SoarExplorerView extends ViewPart 
							  implements ISoarDatabaseEventListener
{
    public static final String ID = "com.soartech.soar.ide.ui.views.SoarExplorerView";
    
	private TreeViewer tree;
	
	private ILabelProvider databaseLabelProvider = SoarLabelProvider.createFullLabelProvider();
	
	SoarExplorerContentProvider contentProvider = new SoarExplorerContentProvider();
	
	/**
	 * Constructor.
	 */
	public SoarExplorerView() 
	{
		super();
	}

	@Override
	public void createPartControl(Composite parent) 
	{
		tree = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		
		tree.addDoubleClickListener(new SoarDatabaseRowDoubleClickListener());
        //tree.setUseHashlookup(true); // this significantly improves update performance
		
		tree.setContentProvider(contentProvider);
		tree.setLabelProvider(databaseLabelProvider);
		SoarCorePlugin input = SoarCorePlugin.getDefault();
		input.getDatabaseConnection().addListener(this);
        tree.setInput(input);
        getSite().setSelectionProvider(tree);
        
        createContextMenu();
        makeActions();
        tree.addDragSupport(DND.DROP_MOVE, new Transfer[] {LocalSelectionTransfer.getTransfer()}, new SoarDatabaseExplorerDragAdapter());
        tree.addDropSupport(DND.DROP_MOVE, new Transfer[] {LocalSelectionTransfer.getTransfer()}, new SoarDatabaseExplorerDropAdapter(tree));
        
        //getViewSite().getActionBars().getToolBarManager().add(new FilterContributionItem(this, true));
        getViewSite().getActionBars().getToolBarManager().add(new FilterContributionItem(this, false));
   	}
	
	private void addActionsForRow(SoarDatabaseRow row, TreeSelection selection, IMenuManager manager) {
		Table table = row.getTable();
		
		manager.add(new OpenDatabaseRowInEditorAction(row));
		manager.add(new RenameDatabaseRowAction(row));
		manager.add(new DeleteDatabaseRowAction(row));
		if (table != Table.TAGS) {
			manager.add(new ManageTagsAction(row));
		}
		
		manager.add(new Separator());
		
		if (table == Table.AGENTS) {
			//ret.add(new GenerateAgentStructureActionDelegate(row));
		}
		if (table == Table.PROBLEM_SPACES) {
			manager.add(new MarkProblemSpaceRootAction(row, tree));

			manager.add(new Separator());

			manager.add(new AddChildRowAction(row, Table.RULES, row, tree, true));
			manager.add(new AddChildRowAction(row, Table.OPERATORS, row, tree, true));
			//ret.add(new AddOperatorTemplateChildrenAction(row, tree));
			//ret.add(new AddRuleTemplateChildrenAction(row, tree));
			
			// Add new impasse
			MenuManager newImpasseMenu = new MenuManager("Add Impasse");
			manager.add(newImpasseMenu);
			JoinType[] childImpasses = new JoinType[] {
					JoinType.TIE_IMPASSE,
					JoinType.CONFLICT_IMPASSE,
					JoinType.CONTRAINT__FAILURE_IMPASSE,
					JoinType.NO__CHANGE_IMPASSE,
					JoinType.STATE_NO__CHANGE_IMPASSE,
					JoinType.OPERATOR_NO__CHANGE_IMPASSE};
			for (JoinType type : childImpasses) {
				newImpasseMenu.add(new AddSubstateAction(row, false, tree, type));
			}
			
			// Change impasse type
			SoarDatabaseRow parentRow = null;
			TreePath[] paths = selection.getPathsFor(row);
			if (paths.length > 0) {
				TreePath path = paths[0];
				int pathLength = path.getSegmentCount();
				if (pathLength > 2) {
					Object parentObj = path.getSegment(pathLength - 2);
					if (parentObj instanceof SoarDatabaseRow) {
						parentRow = (SoarDatabaseRow) parentObj;
						Table parentTable = parentRow.getTable();
						if (!(parentTable == Table.PROBLEM_SPACES || parentTable == Table.OPERATORS)) {
							parentRow = null;
						}
					}
				}
			}
			if (parentRow != null) {
				JoinType[] impasses;
				if (parentRow.getTable() == Table.PROBLEM_SPACES) {
					impasses = new JoinType[] {
							JoinType.TIE_IMPASSE,
							JoinType.CONFLICT_IMPASSE,
							JoinType.CONTRAINT__FAILURE_IMPASSE,
							JoinType.NO__CHANGE_IMPASSE,
							JoinType.STATE_NO__CHANGE_IMPASSE};
				} else {
					impasses = new JoinType[] {JoinType.OPERATOR_NO__CHANGE_IMPASSE};
				}
				MenuManager sub = new MenuManager("Change Impasse Type");
				manager.add(sub);
				for (JoinType joinType : impasses) {
					sub.add(new ChangeJoinTypeAction(parentRow, row, joinType));
				}
			}
			
			//ret.add(new GenerateDatamapAction(row));
			manager.add(new GenerateDatamapAction(row, false));
			manager.add(new GenerateDatamapFromVisualSoarFileAction(row));
		}
		if (table == Table.OPERATORS) {
			manager.add(new AddChildRowAction(row, Table.RULES, row, tree, true));
			manager.add(new AddSubstateAction(row, false, tree, JoinType.OPERATOR_NO__CHANGE_IMPASSE));
			//ret.add(new AddRuleTemplateChildrenAction(row, tree));
		}
		if (table == Table.RULES) {
			manager.add(new CheckRuleAgainstDatamapAction(row));
		}
		RemoveJoinFromParentAction remove = new RemoveJoinFromParentAction(selection);
		if (remove.isRunnable()) {
			manager.add(remove);
		}
		
		manager.add(new Action("PRINT FREQUENCIES") {
			@Override
			public void run() {
				SoarDatabaseConnection.printFrequencies();
			}
		});

		
		manager.add(new Separator());
		
		manager.add(new ExportSoarDatabaseRowAction(row));
		SoarCommandLineMXBean proxy = SoarCorePlugin.getDefault().getCommandLineProxy(); 
		if (proxy != null) {
			manager.add(new ExportSoarDatabaseRowAction(row, proxy));
		}
	}
	
	private ArrayList<Action> actionsForFolder(SoarDatabaseRowFolder folder) {
		ArrayList<Action> ret = new ArrayList<Action>();
		ret.add(new AddChildRowAction(folder.getRow(), folder.getTable(), folder, tree, true));
		return ret;
	}
	
    private void createContextMenu()
    {
    	MenuManager manager = new MenuManager();
        manager.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager manager) {
				manager.removeAll();
				ISelection selection = tree.getSelection();
				if (selection instanceof TreeSelection) {
					TreeSelection ts = (TreeSelection) selection;
					Object obj = ts.getFirstElement();
					if (obj instanceof SoarDatabaseRow) {
						SoarDatabaseRow row = (SoarDatabaseRow) obj;
						addActionsForRow(row, ts, manager);
					}
					if (obj instanceof SoarDatabaseRowFolder) {
						SoarDatabaseRowFolder folder = (SoarDatabaseRowFolder) obj;
						ArrayList<Action> actions = actionsForFolder(folder);
						for (Action action : actions) {
							manager.add(action);
						}
					}
					if (obj == null) {
						manager.add(new AddAgentActionDelegate(SoarExplorerView.this));
					}
				}
			}
        	
        });
        Menu menu = manager.createContextMenu(tree.getControl());
        tree.getControl().setMenu(menu);
        getSite().registerContextMenu(manager, tree);
    }
    
    private void makeActions() {

		tree.getControl().addKeyListener(new org.eclipse.swt.events.KeyListener() {

			@Override
			public void keyReleased(org.eclipse.swt.events.KeyEvent event) {

			}

			@Override
			public void keyPressed(org.eclipse.swt.events.KeyEvent event) {
				ISelection selection = tree.getSelection();
				if (selection instanceof TreeSelection) {
					TreeSelection ts = (TreeSelection) selection;
					if (event.keyCode == KeyEvent.VK_DELETE && event.stateMask == (event.stateMask | SWT.CONTROL)) {
						boolean deleteAll = false;
						for (Object element : ts.toArray()) {
							if (element instanceof SoarDatabaseRow) {
								if (new DeleteDatabaseRowAction((SoarDatabaseRow) element).run(!deleteAll, true)) {
									deleteAll = true;
								}
							}
						}
					}
					
					else if (event.keyCode == KeyEvent.VK_DELETE) {
						for (Object element : ts.toArray()) {
							if (element instanceof SoarDatabaseRow) {
								RemoveJoinFromParentAction action = new RemoveJoinFromParentAction(ts);
								if (action.isRunnable()) {
									action.run();
								}
							}
						}
					}
				}
			}
		});
	}
    
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException 
	{
		super.init(site, memento);
		updateTitle(SoarCorePlugin.getDefault());
	}

	@Override
	public void saveState(IMemento memento) 
	{
		IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
		IContributionItem[] items = toolbarManager.getItems();
		
		for(IContributionItem item:items)
		{
			//save the state of all the action items in the toolbar
			if(item instanceof ActionContributionItem)
			{
				ActionContributionItem actionItem = (ActionContributionItem) item;
				
				String id = actionItem.getId();
				IAction action = actionItem.getAction();
				
				boolean checked = action.isChecked();
				
				if(checked)
				{
					memento.putString(id, "true");
				}
				else
				{
					memento.putString(id, "false");
				}
			}
		}
		
		super.saveState(memento);
	}
	
	@Override
	public void dispose() 
	{
        SoarCorePlugin.getDefault().getDatabaseConnection().removeListener(this);
		super.dispose();
	}

	/**
     * Notifies this listener that the menu is about to be shown by
     * the given menu manager.
     *
     * @param manager the menu manager
     */
    void fillContextMenu(IMenuManager manager) {
//        MenuManager newMenu = new MenuManager("Ne&w");
//        manager.add(newMenu);
//        newMenu.add(newWizardMenu);
        
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS + "-end"));
        manager.add(new Separator());
    }
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
	 */
	@Override
	public void setFocus() 
	{
		if(tree != null)
        {
            tree.getControl().setFocus();
        }
	}
	
	private void updateTitle(SoarCorePlugin input) {
		if (input.getDatabaseConnection().isSavedToDisk()) {
			this.setPartName("Soar Explorer: " + input.getDatabaseConnection().getPath());
		} else {
			this.setPartName("Soar Explorer: Unsaved Project");
		}
	}

	@Override
	public void onEvent(SoarDatabaseEvent event, SoarDatabaseConnection db) {
		
		if (event.type == SoarDatabaseEvent.Type.DATABASE_PATH_CHANGED) {
			SoarCorePlugin input = SoarCorePlugin.getDefault();
	        tree.setInput(input);
	        updateTitle(input);
		}
		
		//Object[] elements = tree.getExpandedElements();
		//TreePath[] treePaths = tree.getExpandedTreePaths();
		Display display = Display.getDefault();
		if (display != null) {
			display.asyncExec(new Runnable() {
				@Override
				public void run() {
					tree.refresh();
				};
			});
		}
		/*
		Display.getCurrent().asyncExec(new Runnable() {
			@Override
			public void run() {
		        tree.refresh();
			}
		});
		*/
		//tree.setExpandedElements(elements);
		//tree.setExpandedTreePaths(treePaths);
	}
	
	public TreeViewer getTreeViewer() {
		return tree;
	}

	public void setFilterString(String text) {
		contentProvider.setFilter(text);
		tree.refresh();
	}
}
