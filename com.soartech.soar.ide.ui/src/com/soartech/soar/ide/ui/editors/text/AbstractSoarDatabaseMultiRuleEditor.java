package com.soartech.soar.ide.ui.editors.text;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.PlatformUI;

import com.soartech.soar.ide.core.SoarProblem;
import com.soartech.soar.ide.core.sql.ISoarDatabaseEventListener;
import com.soartech.soar.ide.core.sql.ISoarDatabaseTreeItem;
import com.soartech.soar.ide.core.sql.SoarDatabaseConnection;
import com.soartech.soar.ide.core.sql.SoarDatabaseEvent;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow;
import com.soartech.soar.ide.core.sql.SoarDatabaseEvent.Type;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.Table;

public abstract class AbstractSoarDatabaseMultiRuleEditor extends AbstractSoarDatabaseTextEditor implements ISoarDatabaseTextEditor, ISoarDatabaseEventListener {
	
	@Override
	public void doSave(IProgressMonitor progressMonitor) {
		super.doSave(progressMonitor);
		if (input != null) {
			final HashMap<String, SoarDatabaseRow> rulesByName = new HashMap<String, SoarDatabaseRow>();
			final HashSet<SoarDatabaseRow> childRules = new HashSet<SoarDatabaseRow>();
			final SoarDatabaseRow row = input.getRow();
			ArrayList<SoarDatabaseRow> rules = row.getJoinedRowsFromTable(Table.RULES);
			for (SoarDatabaseRow rule : rules) {
				String ruleName = rule.getName();
				rulesByName.put(ruleName, rule);
			}
			
			IDocument doc = getDocumentProvider().getDocument(input);
			final ArrayList<String> rulesText = getRulesFromText(doc.get());
			boolean eventsWereSuppressed = row.getDatabaseConnection().getSupressEvents();
			row.getDatabaseConnection().setSupressEvents(true);
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

			try {
				new ProgressMonitorDialog(shell).run(true, true, new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						monitor.beginTask("Saving Rules for \"" + getInput().getName() + "\"", rulesText.size());
						for (String ruleText : rulesText) {
							String ruleName = getNameOfRules(ruleText);
							SoarDatabaseRow rule = rulesByName.get(ruleName);
							if (rule == null) {
								rule = row.getTopLevelRow().createChild(Table.RULES, ruleName);
								addRow(rule);
							}
							rule.save(ruleText, input, null);
							childRules.add(rule);
							monitor.worked(1);
						}
						monitor.done();
					}
				});
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			for (SoarDatabaseRow rule : row.getJoinedRowsFromTable(Table.RULES)) {
				if (!childRules.contains(rule)) {
					// TODO prompt user to delete rule
					SoarDatabaseRow.unjoinRows(row, rule, row.getDatabaseConnection());
					String ruleName = rule.getName();
					String rowName = row.getName();
					MessageDialog dialog = new MessageDialog(shell,
							"Delete rule \"" + ruleName + "\"?",
							null,
							"The rule \"" + ruleName + "\" no longer appears in \"" + rowName + "\" (it may have been renamed). Delete \"" + ruleName + "\" or just remove it from \"" + rowName + "\"?",
							MessageDialog.QUESTION,
							new String[] { "Remove", "Delete" },
							0);
					int result = dialog.open();
					if (result == 1) { // Delete
						rule.deleteAllChildren(true, null);
					}
				}
			}
			
			row.getDatabaseConnection().setSupressEvents(eventsWereSuppressed);
			row.getDatabaseConnection().fireEvent(new SoarDatabaseEvent(Type.DATABASE_CHANGED));
			
			input.clearProblems();
			clearAnnotations();
			//SoarDatabaseRow row = input.getSoarDatabaseStorage().getRow();
			//row.save(doc, input);
			ArrayList<SoarProblem> problems = input.getProblems();
			for (SoarProblem problem : problems) {
				SoarDatabaseTextAnnotation annotation = new SoarDatabaseTextAnnotation();
				Position position = new Position(problem.start, problem.length);
				addAnnotation(annotation, position);
			}
			getVerticalRuler().update();
			/*
			if (!hasRule) {
				this.doRevertToSaved();
			}
			*/
		}
	}
	
	protected abstract void addRow(SoarDatabaseRow newRow);
	
	private ArrayList<String> getRulesFromText(String doc) {
		ArrayList<String> ret = new ArrayList<String>();
		int braceDepth = 0;
		StringBuffer buff = new StringBuffer();
		boolean string = false;
		boolean comment = false;
		for (char c : doc.toCharArray()) {
			buff.append(c);
			if (comment && c == '\n') comment = false;
			if (!string && c == '#') comment = true;
			if (!comment && c == '|') string = !string;
			if (comment) continue;
			if (c == '{') ++braceDepth;
			if (c == '}') --braceDepth;
			if (c < 0) c = 0;
			if (c == '}' && braceDepth == 0) {
				ret.add(buff.toString().trim());
				buff = new StringBuffer();
			}
		}
		return ret;
	}
	
	private String getBodyOfRule(String rule) {
		int start = rule.indexOf('{');
		int end = rule.lastIndexOf('}');
		if (start == -1 || end == -1 || end < start) return "";
		return rule.substring(start, end + 1);
	}
	
	private String getNameOfRules(String rule) {
		int nameBeginIndex = rule.indexOf('{') + 1;
		int nameEndIndex1 = rule.indexOf('(');
		int nameEndIndex2 = rule.indexOf("-->");
		int nameEndIndex3 = rule.lastIndexOf('}');
		if (nameEndIndex1 == -1)
			nameEndIndex1 = Integer.MAX_VALUE;
		if (nameEndIndex2 == -1)
			nameEndIndex2 = Integer.MAX_VALUE;
		if (nameEndIndex3 == -1)
			nameEndIndex3 = Integer.MAX_VALUE; // shouldn't happen with matching regex
		int nameEndIndex = Math.min(nameEndIndex1, Math.min(nameEndIndex2, nameEndIndex3));
		String ruleName = rule.substring(nameBeginIndex, nameEndIndex).trim();
		return ruleName;
	}
	
	private String replaceRuleBodiesWithBody(String oldRule, String newBody) {
		StringBuffer buff = new StringBuffer();
		int index = 0;
		boolean added = false;
		
		buildBuffer:
		while (true) {
			int startIndex = oldRule.indexOf('{', index);
			if (startIndex == -1) {
				buff.append(oldRule.substring(index));
				break buildBuffer;
			}
			int endIndex = 0;
			int braceDepth = 1;
			
			findBody:
			for ( ; endIndex < oldRule.length(); ++endIndex) {
				char c = oldRule.charAt(endIndex);
				if (c == '{') ++braceDepth;
				if (c == '}') --braceDepth;
				if (braceDepth == 0) {
					break findBody;
				}
			}
			buff.append(oldRule.subSequence(index, startIndex));
			buff.append(newBody);
			added = true;
			index = endIndex;
		}
		if (!added) {
			buff.append("sp " + newBody);
		}
		return buff.toString();
	}
	
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		if (this.input != null) {
			this.input.getRow().getDatabaseConnection().addListener(this);
		}
	}

	@Override
	public void onEvent(SoarDatabaseEvent event, SoarDatabaseConnection db) {
		if (event.type == Type.DATABASE_CHANGED) {
			
			/* This wasn't working beause if you delete a rule from an operator this won't fire
			SoarDatabaseRow row = this.input.getRow();
			ArrayList<ISoarDatabaseTreeItem> rules = TraversalUtil.getRelatedRules(row);
			if (event.row != null && rules.contains(event.row) || event.row == row) {
				this.doRevertToSaved();
			}
			*/
			
			// Fire every time
			doRevertToSaved();
		}
	}
}
