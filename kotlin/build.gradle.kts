plugins {
    java
    kotlin("jvm") version "1.4.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("com.google.android", "android", "4.1.1.4")
    testImplementation("junit", "junit", "4.12")
}
