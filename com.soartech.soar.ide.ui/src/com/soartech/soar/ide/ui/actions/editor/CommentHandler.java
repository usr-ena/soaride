package com.soartech.soar.ide.ui.actions.editor;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;

public class CommentHandler implements IHandler {

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// TODO Auto-generated method stub
		System.out.println("CommentHandler execute");
		return null;
	}

	@Override
	public boolean isEnabled() {
		// TODO Auto-generated method stub
		System.out.println("CommentHandler isEnabled");
		return true;
	}

	@Override
	public boolean isHandled() {
		// TODO Auto-generated method stub
		System.out.println("CommentHandler isHandled");
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {
		// TODO Auto-generated method stub

	}

}
