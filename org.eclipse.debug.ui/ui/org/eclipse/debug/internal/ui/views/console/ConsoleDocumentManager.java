package org.eclipse.debug.internal.ui.views.console;

/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.debug.internal.ui.views.DebugUIViewsMessages;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;

/**
 * Creates documents for processes as they are registered with a launch. */
public class ConsoleDocumentManager implements ILaunchListener {

	/**
	 * Singleton console document manager
	 */
	private static ConsoleDocumentManager fgConsoleDocumentManager= null;
	
	/**
	 * The process that is/can provide output to the console
	 * view.
	 */
	private IProcess fCurrentProcess= null;
	
	/**
	 * Console document content provider extensions, keyed by extension id	 */
	private Map fContentProviders;
	
	/**
	 * Default document provider.	 */
	protected IDocumentProvider fDefaultDocumentProvider = new ConsoleDocumentProvider();
	
	public static ConsoleDocumentManager getDefault() {
		if (fgConsoleDocumentManager == null) {
			fgConsoleDocumentManager= new ConsoleDocumentManager();
		}	
		return fgConsoleDocumentManager;
	}
	
	/**
	 * Returns whether the singleton instance of the manager exists
	 */
	public static boolean defaultExists() {
		return fgConsoleDocumentManager != null;
	}
	
	private ConsoleDocumentManager() {
	}
	
	/**
	 * @see ILaunchListener#launchRemoved(ILaunch)
	 */
	public void launchRemoved(ILaunch launch) {
		removeLaunch(launch);
		DebugUIPlugin.getStandardDisplay().syncExec(new Runnable () {
			public void run() {
				notifyConsoleViews();
			}
		});
	}
	
	protected void removeLaunch(ILaunch launch) {
		IProcess currentProcess= getCurrentProcess();
		IProcess[] processes= launch.getProcesses();
		for (int i= 0; i < processes.length; i++) {
			IProcess iProcess = processes[i];
			IDocumentProvider provider = getDocumentProvider(iProcess);
			provider.disconnect(iProcess);
			if (iProcess.equals(currentProcess)) {
				setCurrentProcess(null);
			}
		}		
	}

	/**
	 * @see ILaunchListener#launchAdded(ILaunch)
	 */
	public void launchAdded(ILaunch launch) {
		launchChanged(launch);
	}

	/**
	 * @see ILaunchListener#launchChanged(ILaunch)
	 */
	public void launchChanged(final ILaunch launch) {
		IProcess newProcess= null;
		IDebugTarget target= launch.getDebugTarget();
		if (target != null) {
			newProcess= target.getProcess();
		} else {
			IProcess[] processes= launch.getProcesses();
			if (processes.length > 0) {
				newProcess= processes[processes.length - 1];
			}
		}
		setCurrentProcess(newProcess);
				
		DebugUIPlugin.getStandardDisplay().syncExec(new Runnable () {
			public void run() {
				IProcess[] processes= launch.getProcesses();
				for (int i= 0; i < processes.length; i++) {
					if (getConsoleDocument(processes[i]) == null) {
						IProcess process = processes[i];
						IDocumentProvider provider = getDocumentProvider(process);
						try {
							provider.connect(process);
						} catch (CoreException e) {
						}
					}
				}
				
				notifyConsoleViews();
			}
		});
	}

	/**
	 * Notify all existing console views of the current process.
	 * Must be called in the UI thread.
	 */
	private void notifyConsoleViews() {		
		IWorkbench workbench= PlatformUI.getWorkbench();
		IWorkbenchWindow[] windows= workbench.getWorkbenchWindows();
		for (int i = 0; i < windows.length; i++) {
			IWorkbenchWindow iWorkbenchWindow = windows[i];
			IWorkbenchPage[] pages= iWorkbenchWindow.getPages();
			for (int j = 0; j < pages.length; j++) {
				IWorkbenchPage iWorkbenchPage = pages[j];
				IViewPart part= iWorkbenchPage.findView(IDebugUIConstants.ID_CONSOLE_VIEW);
				if (part instanceof ConsoleView) {
					ConsoleView view= (ConsoleView)part;
					view.setViewerInputFromConsoleDocumentManager(getCurrentProcess());
				}
			}
		}
	}
	
	protected IProcess getCurrentProcess() {
		return fCurrentProcess;
	}

	protected void setCurrentProcess(IProcess currentProcess) {
		fCurrentProcess = currentProcess;
	}
	
	/**
	 * Returns the document for the process, or <code>null</code>
	 * if none.
	 */
	public IDocument getConsoleDocument(IProcess process) {
		IDocumentProvider provider = getDocumentProvider(process);
		return provider.getDocument(process);
	}
		
	/**
	 * Called by the debug ui plug-in on startup.
	 * The console document manager starts listening for
	 * launches to be registered and initializes if any launches
	 * already exist.
	 */
	public void startup() {
		ILaunchManager launchManager= DebugPlugin.getDefault().getLaunchManager();
		launchManager.addLaunchListener(this);	

		//set up the docs for launches already registered
		ILaunch[] launches= launchManager.getLaunches();
		for (int i = 0; i < launches.length; i++) {
			launchAdded(launches[i]);
		}
	}
	
	/**
	 * Called by the debug ui plug-in on shutdown.
	 * The console document manager de-registers as a 
	 * launch listener and kills all existing console documents.
	 */
	public void shutdown() throws CoreException {
		ILaunchManager launchManager= DebugPlugin.getDefault().getLaunchManager();
		ILaunch[] launches = launchManager.getLaunches();
		for (int i = 0; i < launches.length; i++) {
			ILaunch launch = launches[i];
			removeLaunch(launch);
		}
		launchManager.removeLaunchListener(this);
	}
	
	/**
	 * Notifies the console document manager that system err is about to be written
	 * to the console. The manager will open the console if the preference is
	 * set to show the console on system err.
	 */
	protected void aboutToWriteSystemErr(IDocument doc) {
		if (DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugPreferenceConstants.CONSOLE_OPEN_ON_ERR)) {
			showConsole(doc);
		}
	}
	
	/**
	 * Notifies the console document manager that system out is about to be written
	 * to the console. The manager will open the console if the preference is
	 * set to show the console on system out and the console document being written 
	 * is associated with the current process.
	 */	
	protected void aboutToWriteSystemOut(IDocument doc) {
		if (DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugPreferenceConstants.CONSOLE_OPEN_ON_OUT)) {
			showConsole(doc);
		}
	}
	
	protected IProcess getDebugViewProcess() {
		IProcess debugViewProcess= null;
		IWorkbenchWindow window= DebugUIPlugin.getActiveWorkbenchWindow();
		if (window != null) {	
			ISelection selection= window.getSelectionService().getSelection(IDebugUIConstants.ID_DEBUG_VIEW);
			if (selection instanceof IStructuredSelection) {
				Object element= ((IStructuredSelection)selection).getFirstElement();
				if (element instanceof IProcess) {
					debugViewProcess= (IProcess) element;
				} else if (element instanceof ILaunch) {
					IDebugTarget target= ((ILaunch) element).getDebugTarget();
					if (target != null) {
						debugViewProcess= target.getProcess();
					} else {
						IProcess[] processes= ((ILaunch) element).getProcesses();
						if ((processes != null) && (processes.length > 0)) {
							debugViewProcess= processes[0];
						}
					}
				} else if (element instanceof IDebugElement) {
					debugViewProcess= ((IDebugElement) element).getDebugTarget().getProcess();
				}
			}
		}
		return debugViewProcess;
	}
	
	/**
	 * Opens the console view. If the view is already open, it is brought to the front.
	 */
	protected void showConsole(final IDocument doc) {
		IProcess debugViewProcess= getDebugViewProcess();
		if (doc != null && debugViewProcess != null && !doc.equals(getConsoleDocument(debugViewProcess))) {
			return;
		}
		DebugUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
			public void run() {
				IWorkbenchWindow window= DebugUIPlugin.getActiveWorkbenchWindow();
				if (window != null) {
					IWorkbenchPage page= window.getActivePage();
					if (page != null) {
						try {
							IViewPart consoleView= page.findView(IDebugUIConstants.ID_CONSOLE_VIEW);
							if(consoleView == null) {
								IWorkbenchPart activePart= page.getActivePart();
								page.showView(IDebugUIConstants.ID_CONSOLE_VIEW);
								//restore focus stolen by the creation of the console
								page.activate(activePart);
							} else {
								page.bringToTop(consoleView);
							}
						} catch (PartInitException pie) {
							DebugUIPlugin.log(pie);
						}
					}
				}
			}
		});
	}
	
	/**
	 * Returns the document provider applicable for the given process.
	 * 	 * @param process	 * @return document provider	 */
	protected IDocumentProvider getDocumentProvider(IProcess process) {
		return fDefaultDocumentProvider;
	}
	
	/**
	 * Returns a new console document content provider extension with the
	 * given identifier, or <code>null</code> if none.
	 * 	 * @param identifier	 * @return IConsoleDocumentContentProvider	 */
	public IConsoleDocumentContentProvider getContentProvider(String identifier) {
		if (fContentProviders == null) {
			fContentProviders = new HashMap();
			IPluginDescriptor descriptor = DebugUIPlugin.getDefault().getDescriptor();
			IExtensionPoint extensionPoint = descriptor.getExtensionPoint(IDebugUIConstants.EXTENSION_POINT_CONSOLE_DOCUMENT_CONTENT_PROVIDERS);
			IConfigurationElement[] elements = extensionPoint.getConfigurationElements();
			for (int i = 0; i < elements.length; i++) {
				IConfigurationElement extension = elements[i];
				fContentProviders.put(extension.getAttributeAsIs("id"), extension);
			}
		}
		IConfigurationElement extension = (IConfigurationElement)fContentProviders.get(identifier);
		if (extension != null) {
			try {
				Object contentProvider = extension.createExecutableExtension("class"); //$NON-NLS-1$
				if (contentProvider instanceof IConsoleDocumentContentProvider) {
					return (IConsoleDocumentContentProvider)contentProvider;
				} else {
					DebugUIPlugin.logErrorMessage(MessageFormat.format(DebugUIViewsMessages.getString("ConsoleDocumentManager.Invalid_extension_{0}_-_class_must_be_an_instance_of_IConsoleDocumentContentProvider._1"),new String[]{extension.getDeclaringExtension().getUniqueIdentifier()} )); //$NON-NLS-1$
				}
			} catch (CoreException e) {
				DebugUIPlugin.log(e);
			}
		}
		return null;
	} 
}
