/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithDiamondDependencyHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore
import spock.lang.Unroll

@Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
class LibraryDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            allprojects {
                apply plugin: "cpp"
                // Allow static libraries to be linked into shared
                binaries.withType(StaticLibraryBinary) {
                    if (toolChain in Gcc || toolChain in Clang) {
                        cppCompiler.args '-fPIC'
                    }
                }
            }
"""
    }

    def "can use map notation to reference library in same project"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'hello'
            libraries {
                hello {}
            }
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can use map notation to reference library dependency of binary"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            executables {
                main {
                    binaries.all { binary ->
                        binary.lib library: 'hello'
                    }
                }
            }
            libraries {
                hello {}
            }
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can use map notation to reference static library in same project"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            executables {
                main {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'static'
            libraries {
                hello {}
            }
        """

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    @Ignore("Fails due to model rules evaluating before script when project.evaluate() is called")
    @Unroll
    def "can use map notation to reference library in different project#label"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("exe/src/main"))
        app.library.writeSources(file("lib/src/hello"))

        and:
        settingsFile.text = "include ':lib', ':exe'"
        buildFile << """
        project(":exe") {
            ${explicitEvaluation}
            executables {
                main {}
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        project(":lib") {
            libraries {
                hello {}
            }
        }
        """

        when:
        if (configureOnDemand) {
            executer.withArgument('--configure-on-demand')
        }
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput

        where:
        label                       | configureOnDemand | explicitEvaluation
        ""                          | false             | ""
        " with configure-on-demand" | true              | ""
        " with evaluationDependsOn" | false             | "evaluationDependsOn(':lib')"
        " with afterEvaluate"       | false             | """
project.afterEvaluate {
    binaries*.libs*.linkFiles.files.each { println it }
}
"""
    }

    def "can use map notation to transitively reference libraries in different projects"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("greet/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib', ':greet'"
        buildFile << """
        project(":exe") {
            executables {
                main {}
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        project(":lib") {
            libraries {
                hello {}
            }
            sources.hello.cpp.lib project: ':greet', library: 'greetings', linkage: 'static'
        }
        project(":greet") {
            libraries {
                greetings {}
            }
        }
        """

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can have component graph with project dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.writeSources(file("exe/src/main"), file("lib/src/hello"), file("exe/src/greetings"))

        and:
        settingsFile.text = "include ':exe', ':lib'"
        buildFile << """
        project(":exe") {
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                greetings {}
            }
            sources.main.cpp.lib project: ':lib', library: 'hello'
        }
        project(":lib") {
            apply plugin: "cpp"
            libraries {
                hello {}
            }
            sources.hello.cpp.lib project: ':exe', library: 'greetings', linkage: 'static'
        }
        """

        when:
        succeeds ":exe:installMainExecutable"

        then:
        installation("exe/build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can have component graph with diamond dependency"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.static
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        and:
        notExecuted ":greetingsSharedLibrary"
        sharedLibrary("build/binaries/greetingsSharedLibrary/greetings").assertDoesNotExist()
    }

    def "can have component graph with both static and shared variants of same library"() {
        given:
        def app = new ExeWithDiamondDependencyHelloWorldApp()
        app.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib libraries.hello.shared
            sources.main.cpp.lib libraries.greetings.shared
            sources.hello.cpp.lib libraries.greetings.static
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        and:
        executedAndNotSkipped ":greetingsSharedLibrary", ":greetingsStaticLibrary"
        sharedLibrary("build/binaries/greetingsSharedLibrary/greetings").assertExists()
        staticLibrary("build/binaries/greetingsStaticLibrary/greetings").assertExists()

        // TODO:DAZ Investigate this output and parse to ensure that greetings is dynamically linked into mainExe but not helloShared
        and:
        println executable("build/binaries/mainExecutable/main").binaryInfo.listLinkedLibraries()
        println sharedLibrary("build/binaries/helloSharedLibrary/hello").binaryInfo.listLinkedLibraries()
    }

    @Unroll
    def "library requires api of another library via #notationName notation"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))

        app.library.headerFiles*.writeToDir(file("src/helloApi"))
        app.library.sourceFiles*.writeToDir(file("src/hello"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                helloApi {}
                hello {}
            }
            sources.main.cpp.lib ${notation}
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib ${notation}
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput

        where:
        notationName | notation
        "direct"     | "libraries.helloApi.api"
        "map"        | "library: 'helloApi', linkage: 'api'"
    }

    def "can use api linkage for component graph with library dependency cycle"() {
        given:
        def app = new ExeWithLibraryUsingLibraryHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        app.greetingsHeader.writeToDir(file("src/hello"))
        app.greetingsSources*.writeToDir(file("src/greetings"))

        and:
        buildFile << """
            executables {
                main {}
            }
            libraries {
                hello {}
                greetings {}
            }
            sources.main.cpp.lib library: 'hello'
            sources.hello.cpp.lib library: 'greetings', linkage: 'static'
            sources.greetings.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/mainExecutable").exec().out == app.englishOutput
    }

    def "can compile but not link when executable depends on api of library required for linking"() {
        given:
        def app = new CppHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))

        and:
        buildFile << """
            apply plugin: "cpp"
            executables {
                main {}
            }
            libraries {
                hello {}
            }
            sources.main.cpp.lib library: 'hello', linkage: 'api'
        """

        when:
        fails "mainExecutable"

        then:
        failure.assertHasDescription("Execution failed for task ':linkMainExecutable'.")
    }
}
