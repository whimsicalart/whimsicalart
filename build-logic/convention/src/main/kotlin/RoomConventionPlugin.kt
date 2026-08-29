import org.gradle.api.Plugin
import org.gradle.api.Project

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
            }

            dependencies {
                add("implementation", "androidx.room:room-runtime:2.6.1")
                add("implementation", "androidx.room:room-ktx:2.6.1")
                add("ksp", "androidx.room:room-compiler:2.6.1")
            }
        }
    }
}
