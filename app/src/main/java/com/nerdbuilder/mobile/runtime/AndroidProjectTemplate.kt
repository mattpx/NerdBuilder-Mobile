package com.nerdbuilder.mobile.runtime

import java.io.File

object AndroidProjectTemplate {
    fun packageName(name: String): String {
        val raw = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "app" }
        val suffix = if (raw.first().isLetter()) raw else "app_$raw"
        return "com.nerdbuilder.mobile.$suffix"
    }

    fun generate(destination: File, appName: String) {
        val staging = File(destination.parentFile, ".${destination.name}.template")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Cannot stage template" }
        try {
            val ns = packageName(appName)
            write(staging, "settings.gradle.kts", """
                pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
                dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
                rootProject.name = "$appName"
                include(":app")
            """)
            write(staging, "build.gradle.kts", """
                plugins {
                    id("com.android.application") version "8.5.0" apply false
                    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
                }
            """)
            write(staging, "app/build.gradle.kts", """
                plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
                android {
                    namespace = "$ns"
                    compileSdk = 34
                    defaultConfig {
                        applicationId = "$ns"
                        minSdk = 28
                        targetSdk = 34
                        versionCode = 1
                        versionName = "1.0"
                    }
                    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
                    kotlinOptions { jvmTarget = "17" }
                }
                dependencies { implementation("androidx.core:core-ktx:1.12.0") }
            """)
            val pkg = ns.replace('.', '/')
            write(staging, "app/src/main/AndroidManifest.xml", """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="$appName">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """)
            write(staging, "app/src/main/java/$pkg/MainActivity.kt", """
                package $ns
                import android.os.Bundle
                import android.widget.TextView
                import androidx.appcompat.app.AppCompatActivity
                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(TextView(this).apply { text = "Built with NerdProject" })
                    }
                }
            """)
            if (destination.exists()) {
                check(destination.listFiles().isNullOrEmpty()) { "Folder not empty" }
                destination.delete()
            }
            check(staging.renameTo(destination)) { "Cannot finish template" }
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    private fun write(root: File, rel: String, content: String) {
        File(root, rel).apply { parentFile?.mkdirs(); writeText(content.trimIndent().trim() + "\n") }
    }
}
