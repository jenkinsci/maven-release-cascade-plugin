/**
 * Copyright (C) 2013 Barchart, Inc. <http://www.barchart.com/>
 *
 * All rights reserved. Licensed under the OSI BSD License.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package com.barchart.jenkins.cascade;

import static com.barchart.jenkins.cascade.PluginUtilities.*;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.ModuleName;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;

/**
 * Release logic.
 * 
 * @author Andrei Pozolotin
 */
public class CascadeLogic {

	static final String RELEASE = "release:prepare release:perform --define localCheckout=true --define resume=false";

	/**
	 * <p>
	 * Do not use wildcards.
	 */
	static final String SCM_CHECKIN = "scm:checkin --define includes=pom.xml --define message=cascade";

	static final String SCM_CHECKOUT = "scm:checkout";

	static final String SNAPSHOT = "-SNAPSHOT";

	static final String VALIDATE = "validate";

	/**
	 * needs include filter
	 */
	static final String VERSION_DEPENDENCY = "versions:use-latest-versions "
			+ "--define generateBackupPoms=false "
			+ "--define excludeReactor=false "
			+ "--define allowMajorUpdates=false "
			+ "--define allowMinorUpdates=false "
			+ "--define allowIncrementalUpdates=true ";

	/**
	 * <pre>
	 * if release is 1.0.25:
	 * and parent is 1.0.26-SNAPSHOT
	 * 	mvn versions:update-parent
	 * will revert to 1.0.25
	 * 
	 * workaround:
	 * 	mvn versions:update-parent -DparentVersion="[1.0.26,)"
	 * will either use 1.0.26+ or will keep SNAPSHOT
	 * </pre>
	 */
	static final String VERSION_PARENT = "versions:update-parent"
			+ "--define generateBackupPoms=false ";

	public static MemberUserCause cascadeCause(
			final BuildContext<CascadeBuild> context) {
		final CascadeBuild build = context.build();
		final MemberUserCause cause = build.getCause(MemberUserCause.class);
		return cause;
	}

	public static String cascadeProjectName(
			final BuildContext<CascadeBuild> context) {
		final CascadeBuild build = context.build();
		final MemberBuildAction action = build
				.getAction(MemberBuildAction.class);
		return action.getCascadeName();
	}

	public static boolean checkin(final BuildContext context,
			final MavenModuleSet project) throws Exception {

		final SCM scm = project.getScm();

		if (scm instanceof GitSCM) {

			final GitSCM gitScm = (GitSCM) scm;

			final String gitExe = gitScm.getGitExe(
					context.build().getBuiltOn(), context.listener());

		}

		return false;
	}

	public static boolean checkout(final BuildContext context,
			final MavenModuleSet project) throws Exception {

		final AbstractBuild<?, ?> build = context.build();
		final Launcher launcher = new Launcher.LocalLauncher(context.listener());
		final FilePath workspace = project.getWorkspace();
		final BuildListener listener = context.listener();
		final File changelogFile = new File(build.getRootDir(), "changelog.xml");

		final SCM scm = project.getScm();

		return scm
				.checkout(build, launcher, workspace, listener, changelogFile);

	}

	public static boolean hasCascadeCause(
			final BuildContext<CascadeBuild> context) {
		return null != cascadeCause(context);
	}

	public static boolean isFailure(final Result result) {
		return Result.SUCCESS != result;
	}

	public static boolean isSuccess(final Result result) {
		return Result.SUCCESS == result;
	}

	public static String layoutProjectName(
			final BuildContext<CascadeBuild> context) {
		final CascadeBuild build = context.build();
		final MemberBuildAction action = build
				.getAction(MemberBuildAction.class);
		return action.getLayoutName();
	}

	public static void logActions(final BuildContext<CascadeBuild> context,
			final List<Action> actionList) {
		for (final Action action : actionList) {
			final String text = action.toString();
			context.log("\t" + action.getClass().getName());
			context.log("\t\t" + action.toString());
			// final String[] termArray = text.split("\\s+");
			// for (final String term : termArray) {
			// context.log("\t\t" + term.trim());
			// }
		}
	}

	public static void logDependency(final BuildContext<CascadeBuild> context,
			final List<Dependency> dependencyList) {
		for (final Dependency dependency : dependencyList) {
			context.log("\t" + dependency);
		}
	}

	public static List<Action> mavenAnyGoals(final String... options) {
		final List<Action> list = new ArrayList<Action>();
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(options);
		list.add(goals);
		return list;

	}

	public static List<Action> mavenCheckinGoals(final String... options) {
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(SCM_CHECKIN);
		goals.append(options);
		final List<Action> list = new ArrayList<Action>();
		list.add(new CheckoutSkipAction());
		list.add(goals);
		return list;
	}

	/**
	 * Update selected dependency only.
	 * 
	 * See <a href=
	 * "http://mojo.codehaus.org/versions-maven-plugin/use-latest-versions-mojo.html#includesList"
	 * >includesList</a>
	 */
	public static String mavenDependencyFilter(final Dependency item) {
		final String groupId = item.getGroupId();
		final String artifactId = item.getArtifactId();
		final String expression = groupId + ":" + artifactId;
		return "--define includes=" + expression;
	}

	/**
	 * Update selected dependency only.
	 * 
	 * See <a href=
	 * "http://mojo.codehaus.org/versions-maven-plugin/use-latest-versions-mojo.html#includesList"
	 * >includesList</a>
	 */
	public static String mavenDependencyFilter(final List<Dependency> list) {
		final StringBuilder text = new StringBuilder();
		for (final Dependency item : list) {
			final String groupId = item.getGroupId();
			final String artifactId = item.getArtifactId();
			final String expression = groupId + ":" + artifactId;
			if (text.length() == 0) {
				text.append(expression);
			} else {
				text.append(",");
				text.append(expression);
			}
		}
		return "--define includes=" + text;
	}

	public static List<Action> mavenDependencyGoals(final String... options) {
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(VERSION_DEPENDENCY);
		goals.append(options);
		final List<Action> list = new ArrayList<Action>();
		list.add(new CheckoutSkipAction());
		list.add(goals);
		return list;
	}

	/**
	 * Update parent version only up from snapshot.
	 * 
	 * See <a href=
	 * "http://mojo.codehaus.org/versions-maven-plugin/update-parent-mojo.html#parentVersion"
	 * >parentVersion</a>
	 */
	public static String mavenParentFilter(final Parent item) {
		String version = item.getVersion();
		version = version.replaceAll(SNAPSHOT, "");
		return "--define parentVersion=[" + version + ",)";
	}

	public static List<Action> mavenParentGoals(final String... options) {
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(VERSION_PARENT);
		goals.append(options);
		final List<Action> list = new ArrayList<Action>();
		list.add(new CheckoutSkipAction());
		list.add(goals);
		return list;
	}

	public static List<Action> mavenReleaseGoals(final String... options) {
		final List<Action> list = new ArrayList<Action>();
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(RELEASE);
		goals.append(options);
		list.add(goals);
		return list;
	}

	public static List<Action> mavenValidateGoals(final String... options) {
		final List<Action> list = new ArrayList<Action>();
		final MavenGoalsAction goals = new MavenGoalsAction();
		goals.append(VALIDATE);
		goals.append(options);
		list.add(goals);
		return list;
	}

	public static String memberProjectName(
			final BuildContext<CascadeBuild> context) {
		final CascadeBuild build = context.build();
		final MemberBuildAction action = build
				.getAction(MemberBuildAction.class);
		return action.getMemberName();
	}

	/**
	 * Cascade entry point.
	 */
	public static Result process(final BuildContext<CascadeBuild> context)
			throws Exception {

		if (!hasCascadeCause(context)) {
			context.err("Unknown build cause.");
			context.err("Cascade builds expect invocation form member projects.");
			return Result.NOT_BUILT;
		}

		final String projectName = memberProjectName(context);

		context.log("Cascade started: " + projectName);

		final MavenModuleSet memberProject = mavenProject(projectName);

		final MavenModule rootModule = memberProject.getRootModule();

		if (rootModule == null) {
			context.err("Maven module undefined.");
			context.err("This happens when a new project is created but is never built.");
			return Result.NOT_BUILT;
		}

		final ModuleName memberName = rootModule.getModuleName();

		final Result result = process(0, context, memberName);

		context.log("Cascade finished: " + result);

		return result;

	}

	/**
	 * Recursively release projects.
	 */
	public static Result process(final int level,
			final BuildContext<CascadeBuild> context,
			final ModuleName memberName) throws Exception {

		context.log("");
		context.log("Level: " + level);
		context.log("Maven Module: " + memberName);

		final MavenModuleSet memberProject = project(context, memberName);

		if (memberProject == null) {
			context.err("Jenkins project not found.");
			return Result.FAILURE;
		}

		context.log("Jenkins project: " + memberProject.getAbsoluteUrl());

		/** Update jenkins/maven module metadata. */
		process(context, memberName, mavenValidateGoals());

		if (isRelease(mavenModel(memberProject))) {
			context.err("Project is a release.");
			context.err("Please update project version to a snapshot.");
			return Result.FAILURE;
		}

		/** Process parent. */
		PARENT: {

			/** Update to next release, if present. */
			{
				final Parent parent = mavenParent(memberProject);
				if (parent == null) {
					break PARENT;
				}
				if (isRelease(parent)) {
					break PARENT;
				}
				context.log("Parent needs an update: " + parent);
				if (isFailure(process(context, memberName,
						mavenParentGoals(mavenParentFilter(parent))))) {
					return Result.FAILURE;
				}
			}

			/** Need to release a parent, do it now. */
			{
				final Parent parent = mavenParent(memberProject);
				if (isRelease(parent)) {
					break PARENT;
				}
				context.log("Parent needs a release: " + parent);
				final ModuleName parentName = moduleName(parent);
				if (isFailure(process(level + 1, context, parentName))) {
					return Result.FAILURE;
				}
			}

			/** Refresh parent after the release. */
			{
				final Parent parent = mavenParent(memberProject);
				if (isRelease(parent)) {
					break PARENT;
				}
				context.log("Parent needs a refresh: " + parent);
				if (isFailure(process(context, memberName,
						mavenParentGoals(mavenParentFilter(parent))))) {
					return Result.FAILURE;
				}
			}

			/** Verify parent version after release/update. */
			{
				final Parent parent = mavenParent(memberProject);
				if (isRelease(parent)) {
					break PARENT;
				}
				context.err("Can not release parent.");
				return Result.FAILURE;
			}

		}

		/** Process dependencies. */
		DEPENDENCY: {

			/** Dependency update. */
			{
				final List<Dependency> snapshots = mavenDependencies(
						memberProject, MATCH_SNAPSHOT);
				if (snapshots.isEmpty()) {
					break DEPENDENCY;
				}
				context.log("Dependency needs an update: " + snapshots.size());
				logDependency(context, snapshots);
				if (isFailure(process(context, memberName,
						mavenDependencyGoals(mavenDependencyFilter(snapshots))))) {
					return Result.FAILURE;
				}
			}

			/** Dependency release. */
			{
				final List<Dependency> snapshots = mavenDependencies(
						memberProject, MATCH_SNAPSHOT);
				if (snapshots.isEmpty()) {
					break DEPENDENCY;
				}
				for (final Dependency dependency : snapshots) {
					context.log("Dependency needs a release: " + dependency);
					final ModuleName dependencyName = moduleName(dependency);
					if (isFailure(process(level + 1, context, dependencyName))) {
						return Result.FAILURE;
					}
				}
			}

			/** Dependency refresh. */
			{
				final List<Dependency> snapshots = mavenDependencies(
						memberProject, MATCH_SNAPSHOT);
				if (snapshots.isEmpty()) {
					break DEPENDENCY;
				}
				context.log("Dependency needs a refresh: " + snapshots.size());
				logDependency(context, snapshots);
				if (isFailure(process(context, memberName,
						mavenDependencyGoals(mavenDependencyFilter(snapshots))))) {
					return Result.FAILURE;
				}
			}

			/** Verify dependency. */
			{
				final List<Dependency> snapshots = mavenDependencies(
						memberProject, MATCH_SNAPSHOT);
				if (snapshots.isEmpty()) {
					break DEPENDENCY;
				}
				context.err("Failed to release dependency: " + snapshots.size());
				logDependency(context, snapshots);
				return Result.FAILURE;
			}

		}

		/** Publish pom.xml changes, if any. */
		process(context, memberName, mavenCheckinGoals());

		/** Process artifact. */
		{

			if (isFailure(process(context, memberName, mavenReleaseGoals()))) {
				return Result.FAILURE;
			}

			context.log("Project released: " + memberName);

			return Result.SUCCESS;

		}

	}

	/**
	 * Invoke module maven build, wait for completion.
	 */
	public static Result process(final BuildContext<CascadeBuild> context,
			final ModuleName moduleName, final List<Action> goals)
			throws Exception {

		context.log("");
		context.log("Module name: " + moduleName);
		logActions(context, goals);

		final MavenModuleSet project = project(context, moduleName);

		if (project == null) {
			context.err("Jenkins project not found.");
			return Result.FAILURE;
		}

		final MemberUserCause cause = cascadeCause(context);

		final QueueTaskFuture<MavenModuleSetBuild> buildFuture = project
				.scheduleBuild2(0, cause, goals);

		final Future<MavenModuleSetBuild> startFuture = buildFuture
				.getStartCondition();

		/** Block till build started. */
		final MavenModuleSetBuild memberBuild = startFuture.get();

		context.log("Module console: " + memberBuild.getAbsoluteUrl()
				+ "console");

		/** Block till build complete. */
		buildFuture.get();

		final Result result = memberBuild.getResult();

		context.log("Module result: " + result);

		return result;

	}

	/**
	 * Find member project of a cascade.
	 */
	public static MavenModuleSet project(
			final BuildContext<CascadeBuild> context,
			final ModuleName moudleName) {

		for (final MavenModuleSet project : mavenProjectList()) {

			final MemberProjectProperty property = project
					.getProperty(MemberProjectProperty.class);

			if (property == null) {
				continue;
			}

			final boolean isCascadeMatch = context.build().getProject()
					.getName().equals(property.getCascadeName());

			final MavenModule rootModule = project.getRootModule();

			if (rootModule == null) {
				continue;
			}

			final boolean isModuleMatch = rootModule.getModuleName().equals(
					moudleName);

			if (isCascadeMatch && isModuleMatch) {
				return project;
			}

		}

		return null;
	}

	private CascadeLogic() {

	}

}
