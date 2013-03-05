/**
 * Copyright (C) 2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.jenkins.cascade;

import hudson.maven.MavenArgumentInterceptorAction;
import hudson.maven.MavenModuleSetBuild;
import hudson.util.ArgumentListBuilder;

/**
 * Provides replacement arguments for maven builds.
 * 
 * @author Andrei Pozolotin
 */
public class MavenInterceptorAction extends AdapterAction implements
		MavenArgumentInterceptorAction {

	private final String goalsAndOptions;

	public MavenInterceptorAction(final String goalsAndOptions) {
		this.goalsAndOptions = goalsAndOptions;
	}

	public String getGoalsAndOptions(final MavenModuleSetBuild build) {
		return goalsAndOptions;
	}

	public ArgumentListBuilder intercept(final ArgumentListBuilder mavenargs,
			final MavenModuleSetBuild build) {
		return null;
	}

}