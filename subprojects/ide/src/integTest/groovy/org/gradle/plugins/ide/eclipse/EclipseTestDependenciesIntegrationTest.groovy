/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.ide.eclipse

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import spock.lang.IgnoreRest

class EclipseTestDependenciesIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "project dependency does not leak test sources"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }

    @ToBeFixedForConfigurationCache
    def "project dependency pointing to test fixture project exposes test sources"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
                id 'java-test-fixtures'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }

    @ToBeFixedForConfigurationCache
    def "can configure test sources via eclipse classpath"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = true
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    @ToBeFixedForConfigurationCache
    def "classpath configuration has precedence for test dependencies"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-test-fixtures'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = false
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }

    static int testProjectDependenciesIteration = 1

    def "classpath contains test flags for project dependencies coming from test scopes(:a[sourceSet=src/#fromSourceSet/java] --(#dependency)--> :b[plugin=#toProjectType,sourceSet=#toSourceSet])"() {
        when:
        def okExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-project-dependencies/ok")
        def badExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-project-dependencies/bad")
        if (!okExtDir.exists()) {
            okExtDir.mkdirs()
        }
        if (!badExtDir.exists()) {
            badExtDir.mkdir()
        }
        def okSettings = new File(okExtDir, 'settings.gradle')
        def badSettings = new File(badExtDir, 'settings.gradle')
        if (!okSettings.exists()) {
            okSettings.text = ' '
        }
        if (!badSettings.exists()) {
            badSettings.text = ' '
        }
        def rootProjectName = "${testProjectDependenciesIteration++}_${fromSourceSet}_${dependency.replace(' ', '-')}_${toProjectType}_${toSourceSet}"
        settingsFile << """
            include 'a', 'b'
            rootProject.name = "$rootProjectName"
        """
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
                ${fromSourceSet == 'testFixtures' ? "id 'java-test-fixtures'" : "" }
            }

            dependencies {
                $dependency(project(":b"))
            }
        """
        file("a/src/$fromSourceSet/java/A.java") << """
            public class A {
                public B b;
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
                ${toProjectType == 'java-test-fixtures' ? "id 'java-test-fixtures'" : "" }
            }
        """
        file("b/src/$toSourceSet/java/B.java") << """
            public class B {
            }
        """

        then:
        if (expectedBuildOutcome in ['OK', 'compilation-failure']) {
            run 'eclipse'
            expectedDependency(classpath('a').projects.find {it.name == 'b' })
        }

        and:
        if (expectedBuildOutcome == 'OK') {
            run 'build'
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(okExtDir, rootProjectName))
            okSettings.text = okSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'compilation-failure') {
            runAndFail('build').assertHasCause('Compilation failed; see the compiler error output for details')
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(badExtDir, rootProjectName))
            badSettings.text = badSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'invalid-configuration') {
            runAndFail('build').assertHasCause('Could not resolve all task dependencies for configuration')
        } else {
            throw new RuntimeException("Invalid expected result: $expectedBuildOutcome")
        }

        where:
        fromSourceSet  | dependency                         | toProjectType        | toSourceSet    | expectedBuildOutcome    | expectedDependency
        'main'         | 'implementation'                   | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'implementation'                   | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'implementation'                   | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'implementation'                   | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'implementation'                   | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'implementation'                   | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-library'       | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-library'       | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-library'       | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation'               | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation testFixtures'  | 'java-library'       | 'main'         | 'invalid-configuration' | null
        'main'         | 'testImplementation testFixtures'  | 'java-library'       | 'test'         | 'invalid-configuration' | null
        'main'         | 'testImplementation testFixtures'  | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        'main'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'main'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'test'         | 'implementation'                   | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'implementation'                   | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'implementation'                   | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'implementation'                   | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        'test'         | 'implementation'                   | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'test'         | 'implementation'                   | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'test'         | 'testImplementation'               | 'java-library'       | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'testImplementation'               | 'java-library'       | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'testImplementation'               | 'java-library'       | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'testImplementation'               | 'java-test-fixtures' | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'test'         | 'testImplementation'               | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'test'         | 'testImplementation'               | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'test'         | 'testImplementation testFixtures'  | 'java-library'       | 'main'         | 'invalid-configuration' | null
        'test'         | 'testImplementation testFixtures'  | 'java-library'       | 'test'         | 'invalid-configuration' | null
        'test'         | 'testImplementation testFixtures'  | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        'test'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'test'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'test'         | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        'testFixtures' | 'implementation'                   | 'java-library'       | 'main'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'implementation'                   | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'testFixtures' | 'implementation'                   | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'testFixtures' | 'implementation'                   | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'implementation'                   | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'implementation'                   | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation'               | 'java-library'       | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation'               | 'java-library'       | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'testFixtures' | 'testImplementation'               | 'java-library'       | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation'               | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation'               | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation'               | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation testFixtures'  | 'java-library'       | 'main'         | 'invalid-configuration' | null
        'testFixtures' | 'testImplementation testFixtures'  | 'java-library'       | 'test'         | 'invalid-configuration' | null
        'testFixtures' | 'testImplementation testFixtures'  | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        'testFixtures' | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testImplementation testFixtures'  | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES // no compilation failure in Eclipse
    }

    static int testProjectMultiDependenciesIteration = 1

    @IgnoreRest
    def "(#scenario) classpath contains test flags for project multi dependencies coming from test scopes(:a[sourceSet=src/#fromSourceSet/java] --(#dependency1,#dependency2)--> :b[plugin=#toProjectType,sourceSet=#toSourceSet])"() {
        when:
        def okExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-project-multi-dependencies/ok")
        def badExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-project-multi-dependencies/bad")
        if (!okExtDir.exists()) {
            okExtDir.mkdirs()
        }
        if (!badExtDir.exists()) {
            badExtDir.mkdir()
        }
        def okSettings = new File(okExtDir, 'settings.gradle')
        def badSettings = new File(badExtDir, 'settings.gradle')
        if (!okSettings.exists()) {
            okSettings.text = ' '
        }
        if (!badSettings.exists()) {
            badSettings.text = ' '
        }
        def rootProjectName = "${testProjectMultiDependenciesIteration++}_${fromSourceSet}_${dependency1.replace(' ', '-')}_${fromSourceSet}_${dependency2.replace(' ', '-')}_${toProjectType}_${toSourceSet}"
        settingsFile << """
            include 'a', 'b'
            rootProject.name = "$rootProjectName"
        """
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
                ${fromSourceSet == 'testFixtures' ? "id 'java-test-fixtures'" : "" }
            }

            dependencies {
                $dependency1(project(":b"))
                $dependency2(project(":b"))
            }
        """
        file("a/src/$fromSourceSet/java/A.java") << """
            public class A {
                public B b;
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
                ${toProjectType == 'java-test-fixtures' ? "id 'java-test-fixtures'" : "" }
            }
        """
        file("b/src/$toSourceSet/java/B.java") << """
            public class B {
            }
        """

        then:
        if (expectedBuildOutcome in ['OK', 'compilation-failure']) {
            run 'eclipse'
            expectedDependency(classpath('a').projects.find {it.name == 'b' })
        }

        and:
        if (expectedBuildOutcome == 'OK') {
            run 'build'
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(okExtDir, rootProjectName))
            okSettings.text = okSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'compilation-failure') {
            runAndFail('build').assertHasCause('Compilation failed; see the compiler error output for details')
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(badExtDir, rootProjectName))
            badSettings.text = badSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'invalid-configuration') {
            runAndFail('build').assertHasCause('Could not resolve all task dependencies for configuration')
        } else {
            throw new RuntimeException("Invalid expected result: $expectedBuildOutcome")
        }

        where:
        scenario | fromSourceSet  | dependency1          | dependency2                       | toProjectType        | toSourceSet    | expectedBuildOutcome    | expectedDependency
        1        | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        2        | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        3        | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        4        | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        5        | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        6        | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        7        | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        8        | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        9        | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        10       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        11       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        12       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        13       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        14       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        15       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        16       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        17       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        18       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        19       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        20       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | DEPENDENCY_WITHOUT_TEST_SOURCES
        21       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration'   | null
        22       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        23       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        24       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        25       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        26       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        27       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        28       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        29       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        30       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        31       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        32       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        33       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        34       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        35       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        36       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        37       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        38       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        39       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        40       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        41       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        42       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        43       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        44       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        45       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        46       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        47       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        48       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        49       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        50       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        51       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        52       | 'main'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        53       | 'main'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        54       | 'main'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        55       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        56       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        57       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        58       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        59       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        60       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        61       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        62       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        63       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        64       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        65       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        66       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        67       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        68       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        69       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        70       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        71       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        72       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        73       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        74       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | DEPENDENCY_WITHOUT_TEST_SOURCES
        75       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        76       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        77       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        78       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        79       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        80       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        81       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        82       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        83       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        84       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        85       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        86       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        87       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        88       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        89       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        90       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        91       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        92       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        93       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        94       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        95       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        96       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        97       | 'test'         | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        98       | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        99       | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        100      | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        101      | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        102      | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        103      | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        104      | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        105      | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        106      | 'test'         | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        107      | 'test'         | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | DEPENDENCY_WITH_TEST_SOURCES
        108      | 'test'         | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'OK'                    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        109      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        110      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        111      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        112      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        113      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        114      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        115      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        116      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        117      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        118      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        119      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        120      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        121      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        122      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        123      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        124      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        125      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        126      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        127      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        128      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | DEPENDENCY_WITHOUT_TEST_SOURCES
        129      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        130      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        131      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        132      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        133      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        134      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        135      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        136      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        137      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        138      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        139      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        140      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        141      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        142      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        143      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        144      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        145      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'main'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        146      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        147      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'main'         | 'invalid-configuration' | null
        148      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'test'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        149      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        150      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'test'         | 'invalid-configuration' | null
        151      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-library'       | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        152      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        153      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-library'       | 'testFixtures' | 'invalid-configuration' | null
        154      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'main'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        155      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         |'compilation-failure'    | DEPENDENCY_WITH_TEST_SOURCES
        156      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'main'         |'compilation-failure'    | TEST_DEPENDENCY_WITH_TEST_SOURCES
        157      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        158      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        159      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'test'         | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
        160      | 'testFixtures' | 'implementation'     | 'testImplementation'              | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        161      | 'testFixtures' | 'implementation'     | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | DEPENDENCY_WITH_TEST_SOURCES
        162      | 'testFixtures' | 'testImplementation' | 'testImplementation testFixtures' | 'java-test-fixtures' | 'testFixtures' | 'compilation-failure'   | TEST_DEPENDENCY_WITH_TEST_SOURCES
    }

    static binaryDependenciesIteration = 1

    def "binary dependencies(:a[sourceSet=src/#fromSourceSet/java] --(#dependency)--> junit)"() {
        when:
        def okExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-binary-dependencies/ok")
        def badExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-binary-dependencies/bad")
        if (!okExtDir.exists()) {
            okExtDir.mkdirs()
        }
        if (!badExtDir.exists()) {
            badExtDir.mkdir()
        }
        def okSettings = new File(okExtDir, 'settings.gradle')
        def badSettings = new File(badExtDir, 'settings.gradle')
        if (!okSettings.exists()) {
            okSettings.text = ' '
        }
        if (!badSettings.exists()) {
            badSettings.text = ' '
        }
        def rootProjectName = "${binaryDependenciesIteration++}_${fromSourceSet}_${dependency.replace(' ', '-')}"
        settingsFile << """
            rootProject.name = "$rootProjectName"
        """
        buildFile << """
            plugins {
                id 'java-library'
                id 'eclipse'
                ${fromSourceSet == 'testFixtures' ? "id 'java-test-fixtures'" : "" }
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                $dependency 'junit:junit:4.12'
            }
        """
        file("src/$fromSourceSet/java/A.java") << """
            public class A {
                public org.junit.Test test;
            }
        """

        then:
        if (expectedBuildOutcome in ['OK', 'compilation-failure']) {
            run 'eclipse'
            expectedDependency(classpath.libs.find {it.jarName == 'junit-4.12.jar' })
        }

        and:
        if (expectedBuildOutcome == 'OK') {
            run 'build'
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(okExtDir, rootProjectName))
            okSettings.text = okSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'compilation-failure') {
            runAndFail('build').assertHasCause('Compilation failed; see the compiler error output for details')
            run 'cleanEclipse'
            FileUtils.copyDirectory(testDirectory, new File(badExtDir, rootProjectName))
            badSettings.text = badSettings.text + "\n includeBuild('$rootProjectName')"
        } else if (expectedBuildOutcome == 'invalid-configuration') {
            runAndFail('build').assertHasCause('Could not find method testFixturesImplementation() for arguments')
        } else {
            throw new RuntimeException("Invalid expected result: $expectedBuildOutcome")
        }

        where:
        fromSourceSet  | dependency                   | expectedBuildOutcome    | expectedDependency
        'main'         | 'implementation'             | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'testImplementation'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'main'         | 'testFixturesImplementation' | 'invalid-configuration' | null
        'test'         | 'implementation'             | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'testImplementation'         | 'OK'                    | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
        'test'         | 'testFixturesImplementation' | 'invalid-configuration' | null
        'testFixtures' | 'implementation'             | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES
        'testFixtures' | 'testImplementation'         | 'compilation-failure'   | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
        'testFixtures' | 'testFixturesImplementation' | 'OK'                    | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
    }

      static binaryMultiDependenciesIteration = 1

      def "binary multi dependencies(:a[sourceSet=src/#fromSourceSet/java] --(#dependency1, #dependency2)--> junit)"() {
          when:
          def okExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-multi-binary-dependencies/ok")
          def badExtDir = new File("/Users/donat/Development/projects/ide-experience/eclipse-test-multi-binary-dependencies/bad")
          if (!okExtDir.exists()) {
              okExtDir.mkdirs()
          }
          if (!badExtDir.exists()) {
              badExtDir.mkdir()
          }
          def okSettings = new File(okExtDir, 'settings.gradle')
          def badSettings = new File(badExtDir, 'settings.gradle')
          if (!okSettings.exists()) {
              okSettings.text = ' '
          }
          if (!badSettings.exists()) {
              badSettings.text = ' '
          }
          def rootProjectName = "${binaryMultiDependenciesIteration++}_${fromSourceSet}_${dependency1.replace(' ', '-')}_${dependency2.replace(' ', '-')}"
          settingsFile << """
              rootProject.name = "$rootProjectName"
          """
          buildFile << """
              plugins {
                  id 'java-library'
                  id 'eclipse'
                  ${fromSourceSet == 'testFixtures' ? "id 'java-test-fixtures'" : "" }
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  $dependency1 'junit:junit:4.12'
                  $dependency2 'junit:junit:4.12'
              }
          """
          file("src/$fromSourceSet/java/A.java") << """
              public class A {
                  public org.junit.Test test;
              }
          """

          then:
          if (expectedBuildOutcome in ['OK', 'compilation-failure']) {
              run 'eclipse'
              expectedDependency(classpath.libs.find {it.jarName == 'junit-4.12.jar' })
          }

          and:
          if (expectedBuildOutcome == 'OK') {
              run 'build'
              run 'cleanEclipse'
              FileUtils.copyDirectory(testDirectory, new File(okExtDir, rootProjectName))
              okSettings.text = okSettings.text + "\n includeBuild('$rootProjectName')"
          } else if (expectedBuildOutcome == 'compilation-failure') {
              runAndFail('build').assertHasCause('Compilation failed; see the compiler error output for details')
              run 'cleanEclipse'
              FileUtils.copyDirectory(testDirectory, new File(badExtDir, rootProjectName))
              badSettings.text = badSettings.text + "\n includeBuild('$rootProjectName')"
          } else if (expectedBuildOutcome == 'invalid-configuration') {
              runAndFail('build').assertHasCause('Could not find method testFixturesImplementation()')
          } else {
              throw new RuntimeException("Invalid expected result: $expectedBuildOutcome")
          }

          where:
          fromSourceSet  | dependency1          | dependency2                  | expectedBuildOutcome    | expectedDependency
          'main'         | 'implementation'     | 'testImplementation'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
          'main'         | 'implementation'     | 'testFixturesImplementation' | 'invalid-configuration' | null
          'main'         | 'testImplementation' | 'testFixturesImplementation' | 'invalid-configuration' | null
          'test'         | 'implementation'     | 'testImplementation'         | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
          'test'         | 'implementation'     | 'testFixturesImplementation' | 'invalid-configuration' | null
          'test'         | 'testImplementation' | 'testFixturesImplementation' | 'invalid-configuration' | null
          'testFixtures' | 'implementation'     | 'testImplementation'         | 'compilation-failure'   | DEPENDENCY_WITHOUT_TEST_SOURCES // no compilation failure in Eclipse
          'testFixtures' | 'implementation'     | 'testFixturesImplementation' | 'OK'                    | DEPENDENCY_WITHOUT_TEST_SOURCES
          'testFixtures' | 'testImplementation' | 'testFixturesImplementation' | 'OK'                    | TEST_DEPENDENCY_WITHOUT_TEST_SOURCES
      }

    def "can configure test dependencies"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('c/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            configurations {
                integration
            }

            dependencies {
                integration project(':a')
                testImplementation project(':b')
            }

            eclipse {
                classpath {
                    plusConfigurations += [configurations.integration]
                    testConfigurations = [configurations.integration]
                }
            }
        """

        when:
        run 'eclipse'

        then:
        def dependencyA = classpath('c').projects.find { it.name == 'a' }
        def dependencyB = classpath('c').projects.find { it.name == 'b' }

        dependencyA.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        dependencyB.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }

    private static final Closure DEPENDENCY_WITH_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure DEPENDENCY_WITHOUT_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure TEST_DEPENDENCY_WITH_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure TEST_DEPENDENCY_WITHOUT_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }
}
