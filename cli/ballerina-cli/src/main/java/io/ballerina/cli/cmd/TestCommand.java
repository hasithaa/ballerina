/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.cli.cmd;

import io.ballerina.cli.TaskExecutor;
import io.ballerina.cli.task.CleanTargetDirTask;
import io.ballerina.cli.task.CompileTask;
import io.ballerina.cli.task.CreateBaloTask;
import io.ballerina.cli.task.CreateTargetDirTask;
import io.ballerina.cli.task.ListTestGroupsTask;
import io.ballerina.cli.task.RunTestsTask;
import io.ballerina.cli.utils.FileUtils;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.BuildOptionsBuilder;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.directory.SingleFileProject;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.runtime.api.constants.RuntimeConstants;
import io.ballerina.runtime.internal.launch.LaunchUtils;
import org.ballerinalang.tool.BLauncherCmd;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import static io.ballerina.cli.cmd.Constants.TEST_COMMAND;
import static io.ballerina.runtime.api.constants.RuntimeConstants.SYSTEM_PROP_BAL_DEBUG;

/**
 * This class represents the "ballerina test" command.
 *
 * @since 2.0.0
 */
@CommandLine.Command(name = TEST_COMMAND, description = "Test Ballerina modules")
public class TestCommand implements BLauncherCmd {

    private final PrintStream outStream;
    private final PrintStream errStream;
    private Path projectPath;
    private boolean exitWhenFinish;

    public TestCommand() {
        this.projectPath = Paths.get(System.getProperty(ProjectConstants.USER_DIR));
        this.outStream = System.out;
        this.errStream = System.err;
        this.exitWhenFinish = true;
    }

    public TestCommand(Path projectPath, boolean exitWhenFinish) {
        this.projectPath = projectPath;
        this.outStream = System.out;
        this.errStream = System.err;
        this.exitWhenFinish = exitWhenFinish;
    }

    public TestCommand(Path projectPath, PrintStream outStream, PrintStream errStream, boolean exitWhenFinish) {
        this.projectPath = projectPath;
        this.outStream = outStream;
        this.errStream = errStream;
        this.exitWhenFinish = exitWhenFinish;
    }

    @CommandLine.Option(names = {"--offline"}, description = "Builds/Compiles offline without downloading " +
            "dependencies.")
    private Boolean offline;

//    @CommandLine.Option(names = {"--skip-lock"}, description = "Skip using the lock file to resolve dependencies.")
//    private boolean skipLock;

    @CommandLine.Parameters
    private List<String> argList;

    @CommandLine.Option(names = {"--help", "-h"}, hidden = true)
    private boolean helpFlag;

    @CommandLine.Option(names = "--experimental", description = "Enable experimental language features.")
    private Boolean experimentalFlag;

    @CommandLine.Option(names = "--debug", description = "start in remote debugging mode")
    private String debugPort;

    @CommandLine.Option(names = "--list-groups", description = "list the groups available in the tests")
    private boolean listGroups;

    @CommandLine.Option(names = "--groups", split = ",", description = "test groups to be executed")
    private List<String> groupList;

    @CommandLine.Option(names = "--disable-groups", split = ",", description = "test groups to be disabled")
    private List<String> disableGroupList;

    @CommandLine.Option(names = "--test-report", description = "enable test report generation")
    private Boolean testReport;

    @CommandLine.Option(names = "--code-coverage", description = "enable code coverage")
    private Boolean coverage;

    @CommandLine.Option(names = "--observability-included", description = "package observability in the executable.")
    private Boolean observabilityIncluded;

    @CommandLine.Option(names = "--tests", split = ",", description = "Test functions to be executed")
    private List<String> testList;

    @CommandLine.Option(names = "--rerun-failed", description = "Rerun failed tests.")
    private boolean rerunTests;

    private static final String testCmd = "ballerina test [--offline] [--skip-tests]\n" +
            "                    [<ballerina-file> | <ballerina-project>] [(--key=value)...]";

    public void execute() {
        if (this.helpFlag) {
            String commandUsageInfo = BLauncherCmd.getCommandUsageInfo(TEST_COMMAND);
            this.errStream.println(commandUsageInfo);
            return;
        }

        String[] args;
        if (this.argList == null) {
            args = new String[0];
            this.projectPath = Paths.get(System.getProperty(ProjectConstants.USER_DIR));
        } else if (this.argList.get(0).startsWith(RuntimeConstants.BALLERINA_ARGS_INIT_PREFIX)) {
            args = argList.toArray(new String[0]);
            this.projectPath = Paths.get(System.getProperty(ProjectConstants.USER_DIR));
        } else {
            args = argList.subList(1, argList.size()).toArray(new String[0]);
            this.projectPath = Paths.get(argList.get(0));
        }

        String[] userArgs = LaunchUtils.getUserArgs(args, new HashMap<>());
        // check if there are too many arguments.
        if (userArgs.length > 0) {
            CommandUtil.printError(this.errStream, "too many arguments.", testCmd, false);
            CommandUtil.exitError(this.exitWhenFinish);
            return;
        }

        // load project
        Project project;

        // Skip code coverage for single bal files if option is set
        if (FileUtils.hasExtension(this.projectPath)) {
            if (coverage != null && coverage) {
                this.outStream.println("Code coverage is not yet supported with single bal files. Ignoring the flag " +
                        "and continuing the test run...");
            }
            coverage = false;
        }
        BuildOptions buildOptions = constructBuildOptions();
        boolean isSingleFile = false;
        if (FileUtils.hasExtension(this.projectPath)) {
            try {
                project = SingleFileProject.load(this.projectPath, buildOptions);
            } catch (ProjectException e) {
                CommandUtil.printError(this.errStream, e.getMessage(), null, false);
                CommandUtil.exitError(this.exitWhenFinish);
                return;
            }
            isSingleFile = true;
        } else {
            try {
                project = BuildProject.load(this.projectPath, buildOptions);
            } catch (ProjectException e) {
                CommandUtil.printError(this.errStream, e.getMessage(), null, false);
                CommandUtil.exitError(this.exitWhenFinish);
                return;
            }
        }

        // Sets the debug port as a system property, which will be used when setting up debug args before running tests.
        if (this.debugPort != null) {
            System.setProperty(SYSTEM_PROP_BAL_DEBUG, this.debugPort);
        }

        TaskExecutor taskExecutor = new TaskExecutor.TaskBuilder()
                .addTask(new CleanTargetDirTask(), isSingleFile)   // clean the target directory(projects only)
                .addTask(new CreateTargetDirTask()) // create target directory
//                .addTask(new ResolveMavenDependenciesTask()) // resolve maven dependencies in Ballerina.toml
                .addTask(new CompileTask(outStream, errStream)) // compile the modules
                .addTask(new CreateBaloTask(outStream), isSingleFile || listGroups) // create the BALO (projects only)
//                .addTask(new CopyResourcesTask(), listGroups) // merged with CreateJarTask
                .addTask(new ListTestGroupsTask(outStream), !listGroups) // list the available test groups
                .addTask(new RunTestsTask(outStream, errStream, args, rerunTests, groupList, disableGroupList,
                        testList), listGroups)
                .build();

        taskExecutor.executeTasks(project);
        if (this.exitWhenFinish) {
            Runtime.getRuntime().exit(0);
        }
    }

    private BuildOptions constructBuildOptions() {
        return new BuildOptionsBuilder()
                .codeCoverage(coverage)
                .experimental(experimentalFlag)
                .offline(offline)
                .skipTests(false)
                .testReport(testReport)
                .observabilityIncluded(observabilityIncluded)
                .build();
    }

    @Override
    public String getName() {
        return TEST_COMMAND;
    }

    @Override
    public void printLongDesc(StringBuilder out) {
        out.append("Test a Ballerina project or a standalone Ballerina file. \n");
    }

    @Override
    public void printUsage(StringBuilder out) {
        out.append(testCmd + "\n");
    }

    @Override
    public void setParentCmdParser(CommandLine parentCmdParser) {
    }

}
