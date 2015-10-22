/*******************************************************************************
 * Copyright (c) 2013, 2015 Red Hat, Inc. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.thym.core.internal.util;

import java.io.File;
import java.util.Arrays;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.thym.core.HybridCore;
/**
 * Utilities for calling and processing the output from external executables.
 * 
 * @author Gorkem Ercan
 *
 */
public class ExternalProcessUtility {


	/**
	 * Executes the given commands asynchronously.
	 *
	 * <p>
	 * If the workingDirectory is null, the current directory for process is used.
	 * </p>
	 * @param command the command line can not be null or empty
	 * @param workingDirectory working directory for the executed command can be null
	 * @param outStreamListener  listener for output, can be null
	 * @param errorStreamListene listener for error output, can be null
	 * @param envp environment variables to set in the process can be null
	 * @throws CoreException if execution fails
	 * @throws IllegalArgumentException 
	 *        <ul>
	 *        <li>If command is null or empty</li>
	 *        <li>If specified workingDirectory does not exist or not a directory</li>
	 *        </ul> 
	 */
	public void execAsync (String [] command, File workingDirectory, 
			IStreamListener outStreamListener,
			IStreamListener errorStreamListener, String[] envp) throws CoreException{
	
		checkCommands(command);
		checkWorkingDirectory(workingDirectory);
		HybridCore.trace("Async Execute command line: "+Arrays.toString(command));
		Process process =DebugPlugin.exec(command, workingDirectory, envp);
		
		
		Launch launch = new Launch(null, "run", null);
		IProcess prcs = DebugPlugin.newProcess(launch, process, "Eclipse Thym:  "+ command[0]);
		DebugPlugin.getDefault().getLaunchManager().addLaunch(launch);
		setTracing(command, outStreamListener, errorStreamListener, prcs);
		
	}
	
	/**
	 * Convenience method to specify command line as a string for {@link #execAsync(String[], File, IStreamListener, IStreamListener, String[])}
	 */
	public void execAsync ( String commandLine, File workingDirectory, 
			IStreamListener outStreamListener, 
			IStreamListener errorStreamListener, String[] envp) throws CoreException{
		checkCommandLine(commandLine);
		this.execAsync(DebugPlugin.parseArguments(commandLine),
				workingDirectory, outStreamListener, errorStreamListener, envp);
	}

	/**
	 * Executes the given commands synchronously.
	 *
	 * <p>
	 * If the workingDirectory is null, the current directory for process is used.
	 * </p>
	 * @param command the command line can not be null or empty
	 * @param workingDirectory working directory for the executed command, can be null
	 * @param outStreamListener  listener for output, can be null
	 * @param errorStreamListene listener for error output, can be null
	 * @param envp environment variables to set in the process, can be null
	 * @param launchConfiguration the launch to add as part of this call, can be null
	 * @return the exit code for the process
	 * @throws CoreException if the execution fails
	 */
	public int execSync ( String[] command, File workingDirectory, 
			IStreamListener outStreamListener, 
			IStreamListener errorStreamListener, IProgressMonitor monitor, String[] envp, ILaunchConfiguration launchConfiguration) throws CoreException{
		
		checkCommands(command);
		checkWorkingDirectory(workingDirectory);
		HybridCore.trace("Sync Execute command line: "+Arrays.toString(command));
		if(monitor == null ){
			monitor = new NullProgressMonitor();
		}
		Process process =DebugPlugin.exec(command, workingDirectory, envp);
		
		Launch launch = new Launch(launchConfiguration, "run", null);
		IProcess prcs = DebugPlugin.newProcess(launch, process, "Eclipse Thym:  "+ command[0]);
		if(launchConfiguration != null){
			DebugPlugin.getDefault().getLaunchManager().addLaunch(launch);
		}
		
		setTracing(command, outStreamListener, errorStreamListener, prcs);
		
		while (!prcs.isTerminated()) {
			try {
				if (monitor.isCanceled()) {
					prcs.terminate();
					break;
				}
				Thread.sleep(50);
			} catch (InterruptedException e) {
				HybridCore.log(IStatus.INFO, "Exception waiting for process to terminate", e);
			}
		}
		return prcs.getExitValue();
	}

	/**
	 * Convenience method to specify comandline as a String 
	 * for {@link #execSync(String[], File, IStreamListener, IStreamListener, IProgressMonitor, String[], ILaunchConfiguration)} 
	 */
	public int execSync ( String commandLine, File workingDirectory, 
			IStreamListener outStreamListener, 
			IStreamListener errorStreamListener, IProgressMonitor monitor, String[] envp, ILaunchConfiguration launchConfiguration) throws CoreException{
		String[] cmd = DebugPlugin.parseArguments(commandLine);
		return this.execSync(cmd, workingDirectory, outStreamListener, errorStreamListener, monitor, envp, launchConfiguration);
	}	
	
	private void checkWorkingDirectory(File workingDirectory) {
		if(workingDirectory != null && !workingDirectory.isDirectory()){
			throw new IllegalArgumentException(workingDirectory.toString()+ " is not a valid directory");
		}
	}

	private void setTracing(String[] command, IStreamListener outStreamListener, IStreamListener errorStreamListener,
			IProcess prcs) {
		if(HybridCore.DEBUG){
			HybridCore.trace("Creating TracingStreamListeners for " + Arrays.toString(command));
			outStreamListener = new TracingStreamListener(outStreamListener);
			errorStreamListener = new TracingStreamListener(outStreamListener);
		}
		
		if( outStreamListener != null ){
			prcs.getStreamsProxy().getOutputStreamMonitor().addListener(outStreamListener);
		}

		if( errorStreamListener != null ){
			prcs.getStreamsProxy().getErrorStreamMonitor().addListener(errorStreamListener);
		}
	}	

	private void checkCommandLine(String commandLine) {
		if(commandLine == null || commandLine.isEmpty()){
			throw new IllegalArgumentException("Missing command line");
		}
	}
	
	private void checkCommands(String[] command) {
		if(command == null || command.length <1 ){
			throw new IllegalArgumentException("Empty commands array");
		}
	}
}
