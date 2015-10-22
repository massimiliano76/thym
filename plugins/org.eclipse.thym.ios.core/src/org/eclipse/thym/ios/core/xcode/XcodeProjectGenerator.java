/*******************************************************************************
 * Copyright (c) 2013, 2014 Red Hat, Inc. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.thym.ios.core.xcode;

import static org.eclipse.thym.core.engine.HybridMobileLibraryResolver.VAR_APP_NAME;
import static org.eclipse.thym.core.internal.util.FileUtils.directoryCopy;
import static org.eclipse.thym.core.internal.util.FileUtils.fileCopy;
import static org.eclipse.thym.core.internal.util.FileUtils.templatedFileCopy;
import static org.eclipse.thym.core.internal.util.FileUtils.toURL;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.thym.core.HybridProject;
import org.eclipse.thym.core.config.Icon;
import org.eclipse.thym.core.config.ImageResourceBase;
import org.eclipse.thym.core.config.Splash;
import org.eclipse.thym.core.config.Widget;
import org.eclipse.thym.core.config.WidgetModel;
import org.eclipse.thym.core.engine.HybridMobileLibraryResolver;
import org.eclipse.thym.core.platform.AbstractProjectGeneratorDelegate;
import org.eclipse.thym.core.platform.PlatformConstants;
import org.eclipse.thym.ios.core.IOSCore;

import com.dd.plist.ASCIIPropertyListParser;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

public class XcodeProjectGenerator extends AbstractProjectGeneratorDelegate{
	
	public XcodeProjectGenerator(){
		super();
	}
	
	public XcodeProjectGenerator(IProject project, File generationFolder, String platform) {
		init(project, generationFolder, platform);
	}
	
	@Override
	protected void generateNativeFiles(HybridMobileLibraryResolver resolver) throws CoreException{
		
		try{
			HybridProject hybridProject = HybridProject.getHybridProject(getProject());
			if(hybridProject == null ){
				throw new CoreException(new Status(IStatus.ERROR, IOSCore.PLUGIN_ID, "Not a hybrid mobile project, can not generate files"));
			}

			File destinationDir = getDestination();
			Path destinationPath = new Path(destinationDir.toString());
			
			String name = hybridProject.getBuildArtifactAppName();
			IPath prjPath = destinationPath.append(name);
			Widget widgetModel = WidgetModel.getModel(hybridProject).getWidgetForRead();
			String packageName = widgetModel.getId();
			
			File prjdir = prjPath.toFile();
			if( !prjdir.exists() ){//create the project directory
				prjdir.mkdirs();
			}
			
			// /${project_name}
			directoryCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME)),toURL(prjdir));		
			//Delete these two files. Because we use them directly from the template location 
			// Cordova CLI renames them after copying, we end up with dangling template files 
			// which in some cases confuses the config file actions for plugin installation.
			FileUtils.deleteQuietly(new File(prjdir,"__PROJECT_NAME__-Info.plist"));
			FileUtils.deleteQuietly(new File(prjdir,"__PROJECT_NAME__-Prefix.pch"));
			
			handleIcons(widgetModel, hybridProject);
			handleSplashScreens(widgetModel, hybridProject);
			
			// cordova
			IPath cordovaScriptPath = destinationPath.append("cordova");
			directoryCopy(resolver.getTemplateFile(cordovaScriptPath.makeRelativeTo(destinationPath)), 
					toURL(cordovaScriptPath.toFile()));
			
			//Copy node_modules to cordova/node_modules.  cordova-ios >3.9.0 uses them during build
			IPath nodeModules = cordovaScriptPath.append("node_modules");
			directoryCopy(resolver.getTemplateFile(nodeModules.makeRelativeTo(destinationPath)), 
					toURL(nodeModules.toFile()));

			// cordova-ios >3.9.0 does not need need this anymore but uses node.js scripts.
			File wwwwCopyScript = cordovaScriptPath.append("lib").append("copy-www-build-step.sh").toFile();
			if(wwwwCopyScript.exists()){
				wwwwCopyScript.setExecutable(true);
			}
			
			HashMap<String, String > values = new HashMap<String, String>();
			values.put("__TESTING__", name);
			values.put("__PROJECT_NAME__", name); // replaced __TESTING__ after 3.4.0
			values.put("--ID--", packageName);
			
			// /${project_name}/${project_name}-Info.plist
			IPath templatePath = prjPath.append(name+"-Info.plist");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/"+VAR_APP_NAME+"-Info.plist")),
					toURL(templatePath.toFile()), 
					values);
			// /${project_name}/${project_name}-Prefix.pch
			templatePath = prjPath.append(name+"-Prefix.pch");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/"+VAR_APP_NAME+"-Prefix.pch")),
					toURL(templatePath.toFile()),
					values);
			
			// /${project_name}.xcodeproj/project.pbxproj
			IPath xcodeprojDirPath = destinationPath.append(name+".xcodeproj");
			File xcodeDir = xcodeprojDirPath.toFile();//create the xcodeproj folder first
			if(!xcodeDir.exists()){
				xcodeDir.mkdir();
			}
			IPath xcodeprojectFilePath = xcodeprojDirPath.append("project.pbxproj");
			File xcodeprojectFile = xcodeprojectFilePath.toFile();
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+".xcodeproj/project.pbxproj")),
					toURL(xcodeprojectFile), 
					values);
			
			// /${project_name}/Classes/AppDelegate.h
			IPath classesPath = prjPath.append("Classes");
			templatePath = classesPath.append("AppDelegate.h");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/Classes/AppDelegate.h")),
					toURL(templatePath.toFile()),
					values);
			// /${project_name}/Classes/AppDelegate.m
			templatePath = classesPath.append("AppDelegate.m");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/Classes/AppDelegate.m")),
					toURL(templatePath.toFile()),
					values);
			// /${project_name}/Classes/MainViewController.h
			templatePath = classesPath.append("MainViewController.h");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/Classes/MainViewController.h")),
					toURL(templatePath.toFile()),
					values);

			// /${project_name}/Classes/MainViewController.h
			templatePath = classesPath.append("MainViewController.m");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/Classes/MainViewController.m")),
					toURL(templatePath.toFile()),
					values);
			// /${project_name}/main.m
			templatePath = prjPath.append("main.m");
			templatedFileCopy(resolver.getTemplateFile(new Path(VAR_APP_NAME+"/main.m")),
					toURL(templatePath.toFile()),
					values);
			
			//CordovaLib	
			IPath cordovaLibDirectory = getCordovaLibPath();
			directoryCopy(resolver.getTemplateFile(new Path("CordovaLib")), toURL(cordovaLibDirectory.toFile()));

			updateCordovaSubProjectPath(xcodeprojectFile, "CordovaLib/CordovaLib.xcodeproj", "<group>");
		
			//iOS config.xml needs to be copied outside www to be used
			File configxml = hybridProject.getConfigFile().getLocation().toFile();
			fileCopy(toURL(configxml),toURL(new File(prjdir, "/"+PlatformConstants.FILE_XML_CONFIG)));
		}
		catch(IOException e ){
			throw new CoreException(new Status(IStatus.ERROR,IOSCore.PLUGIN_ID,"Error generating the native iOS project", e));
		}
		
	}
	
	private void handleSplashScreens(Widget widget, HybridProject hybridProject) throws CoreException{
		final List<Splash> splashes = widget.getSplashes();
		if(splashes == null || splashes.isEmpty()){
			return;
		}
		final PlatformSplash[] platformSplashes = getPlatformSplashes();
		final File platformHome = new File(getDestination(), hybridProject.getBuildArtifactAppName());
		for (Splash splash : splashes) {
			if(splash.getPlatform().equals(getTargetShortName())){
				IFile splashFile = hybridProject.getProject().getFile(splash.getSrc());
				if (!splashFile.exists()) {
					IOSCore.log(IStatus.ERROR, NLS.bind("Missing splash image {0}", splash.getSrc()), null);
					continue;
				}
				//find destination for the file
				for (PlatformSplash platformSplash : platformSplashes) {
					if( splash.getHeight() == platformSplash.height && 
							splash.getWidth() == platformSplash.width){
						try{
							fileCopy(toURL(splashFile.getLocation().toFile()), toURL(new File(platformHome, platformSplash.fileName)));
						}catch(IOException e){
							throw new CoreException(
									new Status(IStatus.ERROR, IOSCore.PLUGIN_ID, "Error whiile processing iOS splash screens", e));
						}
						break;
					}
				}
			}
		}
	}
	
	private PlatformSplash[] getPlatformSplashes(){
		return new PlatformSplash[] {
				new PlatformSplash("Resources/splash/Default~iphone.png", 320, 480),
				new PlatformSplash("Resources/splash/Default@2x~iphone.png", 640, 960),
				new PlatformSplash("Resources/splash/Default-Portrait~ipad.png", 768, 1024),
				new PlatformSplash("Resources/splash/Default-Portrait@2x~ipad.png", 1536, 2048),
				new PlatformSplash("Resources/splash/Default-Landscape~ipad.png", 1024, 768),
				new PlatformSplash("Resources/splash/Default-Landscape@2x~ipad.png", 2048, 1536),
				new PlatformSplash("Resources/splash/Default-568h@2x~iphone.png", 640, 1136),
				new PlatformSplash("Resources/splash/Default-667h.png", 750, 1334),
				new PlatformSplash("Resources/splash/Default-736h.png", 1242, 2208),
				new PlatformSplash("Resources/splash/Default-Landscape-736h.png", 2208, 1242)
		};
	}
	
	// A small class to hold platform splash screen values.
	private class PlatformSplash{
		int width;
		int height;
		String fileName;
		public PlatformSplash(String file, int w, int h) {
			fileName = file;
			width = w;
			height = h;	
		}		
	}

	private void handleIcons(Widget widgetModel, HybridProject project) throws CoreException{
		final List<Icon> icons = widgetModel.getIcons();
		if (icons == null || icons.isEmpty()) {
			return;// no icons to process.
		}
		final Map<Integer, String> platformIcons = getPlatformIcons();
		ImageResourceBase defaultIcon = null;
		final File iconsDir = new File(getDestination(), project.getBuildArtifactAppName()+"/Resources/icons");
		try {
			for (ImageResourceBase icon : icons) {
				if (icon.isDefault()) {
					defaultIcon = icon;
					continue;
				}
				if (icon.getPlatform().equals(getTargetShortName())) {
					IFile iconFile = project.getProject().getFile(icon.getSrc());
					if (!iconFile.exists()) {
						IOSCore.log(IStatus.ERROR, NLS.bind("Missing icon file {0}", icon.getSrc()), null);
						continue;
					}
					Integer size = Integer.valueOf(Math.max(icon.getHeight(), icon.getWidth()));
					String destinationPath = platformIcons.get(size);
					if (destinationPath != null) {
						fileCopy(toURL(iconFile.getLocation().toFile()), toURL(new File(iconsDir, destinationPath)));
						platformIcons.remove(size);
					}
				}
			}
			
			if (defaultIcon != null) {// use default for any remaining sizes.
				IFile iconFile = project.getProject().getFile(defaultIcon.getSrc());
				if (!iconFile.exists()) {
					IOSCore.log(IStatus.ERROR, NLS.bind("Missing icon file {0}", defaultIcon.getSrc()), null);
				} else {
					Collection<String> remaing = platformIcons.values();
					for (String string : remaing) {
						fileCopy(toURL(iconFile.getLocation().toFile()), toURL(new File(iconsDir,string)));
					}
				}
			}
		} catch (IOException e) {
			throw new CoreException(
					new Status(IStatus.ERROR, IOSCore.PLUGIN_ID, "Error whiile processing iOS icons", e));
		}

	}
	
	private  Map<Integer,String> getPlatformIcons(){
		HashMap<Integer, String> map = new HashMap<Integer, String>();
		map.put(Integer.valueOf(60),  "icon-60.png");
		map.put(Integer.valueOf(120), "icon-60@2x.png");
		map.put(Integer.valueOf(180), "icon-60@3x.png");
		map.put(Integer.valueOf(76),  "icon-76.png");
		map.put(Integer.valueOf(152), "icon-76@2x.png");
		map.put(Integer.valueOf(29),  "icon-small.png");
		map.put(Integer.valueOf(58),  "icon-small@2x.png");
		map.put(Integer.valueOf(40),  "icon-40.png");
		map.put(Integer.valueOf(80),  "icon-40@2x.png");
		map.put(Integer.valueOf(57),  "icon.png");
		map.put(Integer.valueOf(114), "icon@2x.png");
		map.put(Integer.valueOf(72),  "icon-72.png");
		map.put(Integer.valueOf(144), "icon-72@2x.png");
		map.put(Integer.valueOf(50),  "icon-50.png");
		map.put(Integer.valueOf(100), "icon-50@2x.png");
		return map;
	}
	
	private void updateCordovaSubProjectPath(File pbxprojfile, String path,
			String sourcetree) throws CoreException{
		try {
			NSDictionary dict = (NSDictionary)ASCIIPropertyListParser.parse(pbxprojfile);
			NSDictionary objects = (NSDictionary)dict.objectForKey("objects");
			HashMap<String, NSObject> hashmap =  objects.getHashMap();	
			Collection<NSObject> values = hashmap.values();
			for (NSObject nsObject : values) {
				NSDictionary obj = (NSDictionary) nsObject;
				NSString isa = (NSString) obj.objectForKey("isa");
				NSString pathObj = (NSString)obj.objectForKey("path");
				if(isa != null && isa.getContent().equals("PBXFileReference") && path != null && pathObj.getContent().contains("CordovaLib.xcodeproj")){
					obj.remove("path");
					obj.put("path", path);
					obj.remove("sourceTree");
					obj.put("sourceTree", sourcetree);
					if(!obj.containsKey("name")){
						obj.put("name","CordovaLib.xcodeproj");
					}
					PropertyListParser.saveAsASCII(dict, pbxprojfile);
					break;
				}
			}

		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, IOSCore.PLUGIN_ID, "Error updating CordovaLib subproject", e));
		}
	}
	
	private IPath getCordovaLibPath(){
		return new Path(getDestination().toString()).append("CordovaLib");
	}

	@Override
	protected void replaceCordovaPlatformFiles(HybridMobileLibraryResolver resolver) throws IOException{
		fileCopy(resolver.getTemplateFile(HybridMobileLibraryResolver.PATH_CORDOVA_JS), 
				toURL(new File(getPlatformWWWDirectory(), PlatformConstants.FILE_JS_CORDOVA)));
	}

	@Override
	protected File getPlatformWWWDirectory() {
		return XCodeProjectUtils.getPlatformWWWDirectory(getDestination());
	}

}
