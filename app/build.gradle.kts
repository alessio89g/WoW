plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "net.alessio.wow"
    compileSdk = 35
    defaultConfig { applicationId = "net.alessio.wow"; minSdk = 26; targetSdk = 35; testInstrumentationRunner = "net.alessio.wow.SshSmokeInstrumentation"; versionCode = 10; versionName = "0.7.0" }
    buildFeatures { compose = true }
    packaging { resources.excludes += setOf("META-INF/versions/**", "META-INF/AL2.0", "META-INF/LGPL2.1") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("com.github.mwiede:jsch:2.28.8")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    testImplementation("org.json:json:20250517")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.5.0-alpha03")
    implementation("androidx.compose.ui:ui:1.8.2")
    implementation("androidx.compose.foundation:foundation:1.8.2")
    testImplementation("junit:junit:4.13.2")
}
