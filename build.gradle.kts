plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.github.hyxf.projectmanager"

// 尝试从 Gradle 属性中获取 pluginVersion，如果没有则使用默认值 "1.0.0-SNAPSHOT"
val buildVersion = properties["pluginVersion"] as? String ?: "1.0.0-SNAPSHOT"
version = buildVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdeaCommunity("2023.2")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    test {
        useJUnitPlatform()
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            create("IC", "2023.2")
        }
    }

    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("232")
            // Updated to support newer IDE versions (e.g. 2025.x)
        }
    }

    signing {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
