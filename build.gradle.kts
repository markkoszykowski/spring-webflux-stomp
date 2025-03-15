import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
	id("java-library")
	id("checkstyle")
	id("idea")
	alias(libs.plugins.versions)
}

idea {
	module {
		isDownloadJavadoc = true
		isDownloadSources = true
	}
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

checkstyle {
	toolVersion = libs.versions.checkstyle.get()
}

group = "com.github.stomp"
version = "1.0-SNAPSHOT"


// https://github.com/ben-manes/gradle-versions-plugin

fun isNonStable(version: String): Boolean {
	val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
	val regex = "^[0-9,.v-]+(-r)?$".toRegex()
	val isStable = stableKeyword || regex.matches(version)
	return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
	resolutionStrategy {
		componentSelection {
			all {
				if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
					reject("Release candidate")
				}
			}
		}
	}
}


repositories {
	mavenCentral()
}

dependencies {
	annotationProcessor(platform(libs.spring.dependencies))
	implementation(platform(libs.spring.dependencies))

	annotationProcessor(libs.lombok)
	compileOnly(libs.lombok)

	implementation(libs.bundles.spring.webflux.websocket)
	implementation(libs.agrona)

	testImplementation(platform(libs.cucumber.dependencies))
	testImplementation(platform(libs.junit.dependencies))
	testImplementation(libs.bundles.testing)
	testRuntimeOnly(libs.junit.launcher)

	testAnnotationProcessor(libs.lombok)
	testCompileOnly(libs.lombok)
}

tasks.test {
	jvmArgs("--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")

	useJUnitPlatform()
}
