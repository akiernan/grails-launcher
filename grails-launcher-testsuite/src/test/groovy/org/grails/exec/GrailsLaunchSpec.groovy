/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.launcher

import spock.lang.*

abstract class GrailsLaunchSpec extends Specification {
    
    def command = null
    def args = null
    def props = null
    
    def project = null
    def baseDir = ""
    def grailsHome = null
        
    def stdout = ""
    def stderr = ""
    
    private getSystemProperty(name) {
        System.getProperty("grails.launcher.testsuite.$name")
    }
    
    protected getGrailsVersion() {
        getSystemProperty("grailsVersion")
    }
    
    protected commandRequiresRuntimeClasspathForBootstrap(command) {
        command in ["run-app", "test-app"]
    }
    
    protected getBootstrapClasspath() {
        getClasspath(commandRequiresRuntimeClasspathForBootstrap(command) ? "bootstrapRuntime" : "bootstrap")
    }
    
    Collection<File> getClasspath(type) {
        getSystemProperty("classpath.$type").split(":").collect { new File(it) }
    }
    
    def getWorkspacePath() {
        getSystemProperty("workspace")
    }
    
    def getBaseDirPath() {
        project ? "$workspacePath/$project" : project
    }

    def getProjectPath() {
        assert project : "need a current project"
        baseDirPath
    }
    
    def getProjectFile() {
        new File(projectPath)
    }
    
    def projectFile(subpath) {
        new File(getProjectFile(), subpath)
    }
    
    def getRootLoader() {
        new RootLoader(bootstrapClasspath*.toURL() as URL[])
    }
    
    def getLauncher() {
        def launcher = new GrailsLauncher(rootLoader, grailsHome, baseDirPath)

        launcher.compileDependencies = getClasspath("compile")
        launcher.testDependencies = getClasspath("test")
        launcher.runtimeDependencies = getClasspath("runtime")
        
        launcher.projectWorkDir = projectFile("target")
        launcher.classesDir = projectFile("target/classes")
        launcher.testClassesDir = projectFile("target/test-classes")
        launcher.resourcesDir = projectFile("target/resources")
        launcher.projectPluginsDir = projectFile("target/plugins")
        launcher.testReportsDir = projectFile("target/test-results")
        
        launcher
    }
    
    def launch(command, args = null, props = null) {
        this.command = command
        this.args = args
        this.props = props
        
        launch()
    }
    
    def launch() {
        if (command == "test-app") {
            installGrails7296Fix()
        }
        
        println ""
        println "-- Executing $command $props $args (@ $project)"
        println ""
        
        def ioSwapper = new SystemOutAndErrSwapper(true, true)
        ioSwapper.swapIn()
        
        try {
            launcher.launch(NameUtils.toScriptName(command), args, props)
        } finally {
            (stdout, stderr) = ioSwapper.swapOut().collect { new String(it.toByteArray()) }
        }
    }

    void newProject(projectName) {
        project = projectName
        if (projectFile.exists()) {
            throw new IllegalStateException("already a test project with name '$projectName', use something else")
        }
        launch("init")
    }
    
    void cleanBaseDir() {
        if (baseDir) {
            assert !baseDir.exists() || baseDir.deleteDir()
        }
    }
    
    private installGrails7296Fix() {
        projectFile("scripts/_Events.groovy") << """
            // Override to workaround GRAILS-7296
            org.codehaus.groovy.grails.test.support.GrailsTestTypeSupport.metaClass.getSourceDir = { ->
                println "testSourceDir: \$delegate.buildBinding.grailsSettings.testSourceDir"
                new File(delegate.buildBinding.grailsSettings.testSourceDir, delegate.relativeSourcePath)
            }
        """
    }
}