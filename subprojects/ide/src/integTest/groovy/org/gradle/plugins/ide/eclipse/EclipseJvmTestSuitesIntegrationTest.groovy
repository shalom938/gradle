/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants

class EclipseJvmTestSuitesIntegrationTest extends AbstractEclipseIntegrationSpec {

    def "sources and dependencies defined in jvm test suites are marked as test sources by default"() {
        settingsFile << "rootProject.name = 'eclipse-jvm-test-suites-integration-test'"
        buildFile << """
            plugins {
                id 'eclipse'
                id 'jvm-test-suite'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integration(JvmTestSuite) {
                        sources {
                            java {
                                srcDirs = ['src/integration/java']
                            }
                        }

                        dependencies {
                             implementation 'org.assertj:assertj-core:3.21.0'
                        }
                    }
                }
            }
        """
        file('src/integration/java').mkdirs()

        when:
        run 'eclipse'

        then:
        classpath.sourceDirs.find {it.path == 'src/integration/java' }.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        classpath.lib('assertj-core-3.21.0.jar').assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }
}
