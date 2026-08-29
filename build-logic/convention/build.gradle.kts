plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.53.1")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.1.0-1.0.29")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}
