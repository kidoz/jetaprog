package su.kidoz.jetaprog.app.project

import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Service for creating new projects from templates.
 */
public class ProjectCreator(
    private val fileSystem: FileSystem,
) {
    /**
     * Creates a new project from the given configuration.
     */
    public suspend fun createProject(config: ProjectConfig): Result<String> =
        try {
            // Create project directory
            fileSystem.createDirectory(config.projectPath).getOrThrow()

            // Generate files based on template
            when (config.template) {
                is KotlinGradleTemplate -> createKotlinGradleProject(config)
                is KotlinMavenTemplate -> createKotlinMavenProject(config)
                is JavaGradleTemplate -> createJavaGradleProject(config)
                is JavaMavenTemplate -> createJavaMavenProject(config)
                is RustCargoTemplate -> createRustCargoProject(config)
                is CppMesonTemplate -> createCppMesonProject(config)
                is ValaMesonTemplate -> createValaMesonProject(config)
            }

            // Create common files
            if (config.createReadme) {
                createReadme(config)
            }

            if (config.license != License.NONE) {
                createLicense(config)
            }

            createGitignore(config)

            // Initialize Git repository if requested
            if (config.initGit) {
                initGitRepository(config)
            }

            Result.success(config.projectPath)
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun createKotlinGradleProject(config: ProjectConfig) {
        val projectPath = config.projectPath
        val packagePath = config.packageName.replace('.', '/')

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src/main/kotlin/$packagePath")
        fileSystem.createDirectory("$projectPath/src/test/kotlin/$packagePath")
        fileSystem.createDirectory("$projectPath/gradle/wrapper")

        // settings.gradle.kts
        writeFile(
            "$projectPath/settings.gradle.kts",
            """
            rootProject.name = "${config.name}"
            """.trimIndent(),
        )

        // build.gradle.kts
        val kotlinVersion = config.sdkVersion.ifEmpty { "2.1.21" }
        writeFile(
            "$projectPath/build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                application
            }

            group = "${config.packageName}"
            version = "1.0-SNAPSHOT"

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation(kotlin("test"))
            }

            tasks.test {
                useJUnitPlatform()
            }

            kotlin {
                jvmToolchain(25)
            }

            application {
                mainClass.set("${config.packageName}.MainKt")
            }
            """.trimIndent(),
        )

        // gradle.properties
        writeFile(
            "$projectPath/gradle.properties",
            """
            kotlin.code.style=official
            """.trimIndent(),
        )

        // Main.kt
        writeFile(
            "$projectPath/src/main/kotlin/$packagePath/Main.kt",
            """
            package ${config.packageName}

            fun main() {
                println("Hello, ${config.name}!")
            }
            """.trimIndent(),
        )

        // gradle-wrapper.properties
        writeFile(
            "$projectPath/gradle/wrapper/gradle-wrapper.properties",
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.1-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent(),
        )
    }

    private suspend fun createKotlinMavenProject(config: ProjectConfig) {
        val projectPath = config.projectPath
        val packagePath = config.packageName.replace('.', '/')

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src/main/kotlin/$packagePath")
        fileSystem.createDirectory("$projectPath/src/test/kotlin/$packagePath")

        val kotlinVersion = config.sdkVersion.ifEmpty { "2.1.21" }

        // pom.xml
        writeFile(
            "$projectPath/pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>${config.packageName}</groupId>
                <artifactId>${config.name}</artifactId>
                <version>1.0-SNAPSHOT</version>
                <packaging>jar</packaging>

                <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <kotlin.version>$kotlinVersion</kotlin.version>
                    <kotlin.code.style>official</kotlin.code.style>
                    <maven.compiler.source>21</maven.compiler.source>
                    <maven.compiler.target>21</maven.compiler.target>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-test-junit5</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>

                <build>
                    <sourceDirectory>src/main/kotlin</sourceDirectory>
                    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>${'$'}{kotlin.version}</version>
                            <executions>
                                <execution>
                                    <id>compile</id>
                                    <phase>compile</phase>
                                    <goals>
                                        <goal>compile</goal>
                                    </goals>
                                </execution>
                                <execution>
                                    <id>test-compile</id>
                                    <phase>test-compile</phase>
                                    <goals>
                                        <goal>test-compile</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                        <plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>exec-maven-plugin</artifactId>
                            <version>3.1.0</version>
                            <configuration>
                                <mainClass>${config.packageName}.MainKt</mainClass>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent(),
        )

        // Main.kt
        writeFile(
            "$projectPath/src/main/kotlin/$packagePath/Main.kt",
            """
            package ${config.packageName}

            fun main() {
                println("Hello, ${config.name}!")
            }
            """.trimIndent(),
        )
    }

    private suspend fun createJavaGradleProject(config: ProjectConfig) {
        val projectPath = config.projectPath
        val packagePath = config.packageName.replace('.', '/')

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src/main/java/$packagePath")
        fileSystem.createDirectory("$projectPath/src/test/java/$packagePath")
        fileSystem.createDirectory("$projectPath/gradle/wrapper")

        val javaVersion = config.sdkVersion.ifEmpty { "21" }

        // settings.gradle.kts
        writeFile(
            "$projectPath/settings.gradle.kts",
            """
            rootProject.name = "${config.name}"
            """.trimIndent(),
        )

        // build.gradle.kts
        writeFile(
            "$projectPath/build.gradle.kts",
            """
            plugins {
                java
                application
            }

            group = "${config.packageName}"
            version = "1.0-SNAPSHOT"

            repositories {
                mavenCentral()
            }

            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of($javaVersion))
                }
            }

            dependencies {
                testImplementation(platform("org.junit:junit-bom:5.10.0"))
                testImplementation("org.junit.jupiter:junit-jupiter")
            }

            tasks.test {
                useJUnitPlatform()
            }

            application {
                mainClass.set("${config.packageName}.Main")
            }
            """.trimIndent(),
        )

        // gradle.properties
        writeFile(
            "$projectPath/gradle.properties",
            """
            org.gradle.parallel=true
            """.trimIndent(),
        )

        // Main.java
        writeFile(
            "$projectPath/src/main/java/$packagePath/Main.java",
            """
            package ${config.packageName};

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, ${config.name}!");
                }
            }
            """.trimIndent(),
        )

        // gradle-wrapper.properties
        writeFile(
            "$projectPath/gradle/wrapper/gradle-wrapper.properties",
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent(),
        )
    }

    private suspend fun createJavaMavenProject(config: ProjectConfig) {
        val projectPath = config.projectPath
        val packagePath = config.packageName.replace('.', '/')

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src/main/java/$packagePath")
        fileSystem.createDirectory("$projectPath/src/test/java/$packagePath")

        val javaVersion = config.sdkVersion.ifEmpty { "21" }

        // pom.xml
        writeFile(
            "$projectPath/pom.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>${config.packageName}</groupId>
                <artifactId>${config.name}</artifactId>
                <version>1.0-SNAPSHOT</version>
                <packaging>jar</packaging>

                <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <maven.compiler.source>$javaVersion</maven.compiler.source>
                    <maven.compiler.target>$javaVersion</maven.compiler.target>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.0</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.11.0</version>
                        </plugin>
                        <plugin>
                            <groupId>org.codehaus.mojo</groupId>
                            <artifactId>exec-maven-plugin</artifactId>
                            <version>3.1.0</version>
                            <configuration>
                                <mainClass>${config.packageName}.Main</mainClass>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent(),
        )

        // Main.java
        writeFile(
            "$projectPath/src/main/java/$packagePath/Main.java",
            """
            package ${config.packageName};

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, ${config.name}!");
                }
            }
            """.trimIndent(),
        )
    }

    private suspend fun createRustCargoProject(config: ProjectConfig) {
        val projectPath = config.projectPath

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src")

        // Cargo.toml
        writeFile(
            "$projectPath/Cargo.toml",
            """
            [package]
            name = "${config.name}"
            version = "0.1.0"
            edition = "2021"
            authors = []
            description = "A Rust project"

            [dependencies]

            [dev-dependencies]
            """.trimIndent(),
        )

        // main.rs
        writeFile(
            "$projectPath/src/main.rs",
            """
            fn main() {
                println!("Hello, ${config.name}!");
            }
            """.trimIndent(),
        )

        // rust-toolchain.toml (optional, for specifying Rust version)
        val rustVersion = config.sdkVersion.ifEmpty { "1.83" }
        writeFile(
            "$projectPath/rust-toolchain.toml",
            """
            [toolchain]
            channel = "$rustVersion"
            """.trimIndent(),
        )
    }

    private suspend fun createCppMesonProject(config: ProjectConfig) {
        val projectPath = config.projectPath

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src")
        fileSystem.createDirectory("$projectPath/include")

        // Determine C++ standard from sdkVersion
        val cppStandard =
            when (config.sdkVersion) {
                "C++23" -> "c++23"
                "C++20" -> "c++20"
                "C++17" -> "c++17"
                "C++14" -> "c++14"
                else -> "c++20"
            }

        // meson.build
        writeFile(
            "$projectPath/meson.build",
            """
            project('${config.name}', 'cpp',
                version: '1.0.0',
                meson_version: '>= 0.58.0',
                default_options: [
                    'warning_level=3',
                    'cpp_std=$cppStandard'
                ]
            )

            ${config.name}_sources = [
                'src/main.cpp',
            ]

            executable('${config.name}',
                ${config.name}_sources,
                install: true,
            )
            """.trimIndent(),
        )

        // main.cpp
        writeFile(
            "$projectPath/src/main.cpp",
            """
            #include <iostream>

            int main() {
                std::cout << "Hello, ${config.name}!" << std::endl;
                return 0;
            }
            """.trimIndent(),
        )
    }

    private suspend fun createValaMesonProject(config: ProjectConfig) {
        val projectPath = config.projectPath

        // Create directory structure
        fileSystem.createDirectory("$projectPath/src")

        // meson.build
        val valaVersion = config.sdkVersion.ifEmpty { "0.56" }
        writeFile(
            "$projectPath/meson.build",
            """
            project('${config.name}', 'vala', 'c',
                version: '1.0.0',
                meson_version: '>= 0.58.0',
                default_options: ['warning_level=2']
            )

            gnome = import('gnome')

            ${config.name}_sources = [
                'src/main.vala',
            ]

            ${config.name}_deps = [
                dependency('glib-2.0'),
                dependency('gobject-2.0'),
            ]

            executable('${config.name}',
                ${config.name}_sources,
                dependencies: ${config.name}_deps,
                install: true,
            )
            """.trimIndent(),
        )

        // main.vala
        val namespace = config.packageName.split('.').lastOrNull() ?: config.name
        writeFile(
            "$projectPath/src/main.vala",
            """
            namespace $namespace {
                public static int main(string[] args) {
                    stdout.printf("Hello, ${config.name}!\n");
                    return 0;
                }
            }
            """.trimIndent(),
        )
    }

    private suspend fun createReadme(config: ProjectConfig) {
        val languageName = config.template.language.displayName

        val buildInstructions =
            when (config.template) {
                is KotlinGradleTemplate, is JavaGradleTemplate -> {
                    """
                    ## Build

                    ```bash
                    ./gradlew build
                    ```

                    ## Run

                    ```bash
                    ./gradlew run
                    ```
                    """.trimIndent()
                }

                is KotlinMavenTemplate, is JavaMavenTemplate -> {
                    """
                    ## Build

                    ```bash
                    mvn compile
                    ```

                    ## Run

                    ```bash
                    mvn exec:java
                    ```
                    """.trimIndent()
                }

                is RustCargoTemplate -> {
                    """
                    ## Build

                    ```bash
                    cargo build
                    ```

                    ## Run

                    ```bash
                    cargo run
                    ```
                    """.trimIndent()
                }

                is CppMesonTemplate, is ValaMesonTemplate -> {
                    """
                    ## Build

                    ```bash
                    meson setup build
                    meson compile -C build
                    ```

                    ## Run

                    ```bash
                    ./build/${config.name}
                    ```
                    """.trimIndent()
                }
            }

        writeFile(
            "${config.projectPath}/README.md",
            """
            # ${config.name}

            A $languageName project.

            $buildInstructions

            ## License

            ${if (config.license != License.NONE) "This project is licensed under the ${config.license.displayName}." else ""}
            """.trimIndent(),
        )
    }

    private suspend fun createLicense(config: ProjectConfig) {
        val licenseText =
            when (config.license) {
                License.MIT -> mitLicenseText()
                License.APACHE_2_0 -> apache2LicenseText()
                License.GPL_3_0 -> gpl3LicenseText()
                License.NONE -> return
            }

        writeFile("${config.projectPath}/LICENSE", licenseText)
    }

    private suspend fun createGitignore(config: ProjectConfig) {
        val content =
            when (config.template) {
                is KotlinGradleTemplate, is JavaGradleTemplate -> {
                    """
                    # Gradle
                    .gradle/
                    build/
                    !gradle/wrapper/gradle-wrapper.jar

                    # IDE
                    .idea/
                    *.iml
                    .vscode/

                    # OS
                    .DS_Store
                    Thumbs.db

                    # Compiled
                    *.class
                    """.trimIndent()
                }

                is KotlinMavenTemplate, is JavaMavenTemplate -> {
                    """
                    # Maven
                    target/

                    # IDE
                    .idea/
                    *.iml
                    .vscode/

                    # OS
                    .DS_Store
                    Thumbs.db

                    # Compiled
                    *.class
                    """.trimIndent()
                }

                is RustCargoTemplate -> {
                    """
                    # Rust/Cargo
                    /target/
                    Cargo.lock

                    # IDE
                    .idea/
                    .vscode/

                    # OS
                    .DS_Store
                    Thumbs.db
                    """.trimIndent()
                }

                is CppMesonTemplate, is ValaMesonTemplate -> {
                    """
                    # Meson
                    build/
                    subprojects/

                    # IDE
                    .idea/
                    .vscode/

                    # OS
                    .DS_Store
                    Thumbs.db

                    # Compiled
                    *.o
                    *.so
                    *.a
                    """.trimIndent()
                }
            }

        writeFile("${config.projectPath}/.gitignore", content)
    }

    private suspend fun initGitRepository(config: ProjectConfig) {
        // Create empty .git marker - actual git init would require process execution
        // For now, we just ensure the .gitignore is created
        // In a full implementation, we would use ProcessExecutor to run 'git init'
    }

    private suspend fun writeFile(
        path: String,
        content: String,
    ) {
        fileSystem.writeBytes(path, content.toByteArray(Charsets.UTF_8))
    }

    private fun mitLicenseText(): String =
        """
        MIT License

        Copyright (c) ${java.time.Year.now().value}

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
        """.trimIndent()

    private fun apache2LicenseText(): String =
        """
        Apache License
        Version 2.0, January 2004
        http://www.apache.org/licenses/

        TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

        [Full Apache 2.0 license text would go here]

        See https://www.apache.org/licenses/LICENSE-2.0 for the full license text.
        """.trimIndent()

    private fun gpl3LicenseText(): String =
        """
        GNU GENERAL PUBLIC LICENSE
        Version 3, 29 June 2007

        Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>

        [Full GPL 3.0 license text would go here]

        See https://www.gnu.org/licenses/gpl-3.0.html for the full license text.
        """.trimIndent()
}
