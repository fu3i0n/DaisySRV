import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktlint by configurations.creating
plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.8"
}

group = "cat.daisy"
version = "1.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

val versions =
    mapOf(
        "ktlint" to "1.7.1",
    )
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
    }
    ktlint("com.pinterest.ktlint:ktlint-cli:${versions["ktlint"]}") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}
val ktlintCheck by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("**/src/**/*.kt", "**.kts", "!**/build/**")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
            jvmTarget = targetJavaVersion.toString()
        }
    }
}

tasks {
    check {
        dependsOn(ktlintCheck)
    }
    // Configure the build task to depend on shadowJar and be finalized by printJarSize
    build {
        dependsOn("shadowJar")
        finalizedBy("printJarSize")
    }
    register<Copy>("copyDependencies") {
        from(configurations.runtimeClasspath)
        into("$buildDir/libs")
    }
    register<JavaExec>("ktlintFormat") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Check Kotlin code style and format"
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args("-F", "**/src/**/*.kt", "**.kts", "!**/build/**")
    }

    processResources {
        val versionValue = project.version.toString()
        inputs.property("version", versionValue)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(mapOf("version" to versionValue))
        }
    }
    withType<ShadowJar> {
        // Set a classifier to distinguish from regular JAR
        archiveClassifier.set("shaded")

        // Maven Central dependencies will be downloaded by the server via plugin.yml

        // Enable minimization to remove unused classes
        minimize {
            // Exclude certain packages from minimization if they cause issues
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*:.*"))
        }

        // Exclude unnecessary files to reduce JAR size
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/maven/**")
        exclude("META-INF/versions/**")
        exclude("META-INF/services/javax.*")
        exclude("**/*.html")
        exclude("**/*.txt")
        exclude("**/*.properties")
        exclude("**/*.kotlin_module")
        exclude("**/*.kotlin_metadata")
        exclude("**/*.kotlin_builtins")

        // Merge service files instead of overwriting
        mergeServiceFiles()

        // Configure the JAR manifest
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Built-By" to System.getProperty("user.name"),
                // Date is omitted to avoid import issues
            )
        }
    }

    // Task to print the size of the final JAR file
    register("printJarSize") {
        dependsOn("shadowJar")
        doLast {
            // Look for JAR files in the build/libs directory
            val libsDir = file("$buildDir/libs")
            val jarFiles =
                libsDir.listFiles { file ->
                    file.name.endsWith("-shaded.jar")
                }

            if (jarFiles != null && jarFiles.isNotEmpty()) {
                val jarFile = jarFiles.first()
                val sizeInMB = jarFile.length() / (1024.0 * 1024.0)
                println("Final JAR size: ${String.format("%.2f", sizeInMB)} MB")
                println("JAR location: ${jarFile.absolutePath}")
            } else {
                println("No shaded JAR files found in ${libsDir.absolutePath}")
            }
        }
    }
}
