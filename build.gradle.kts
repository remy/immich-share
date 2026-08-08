// Root build file. Plugins are declared here with `apply false` so their
// versions resolve once for the whole build; modules apply them by alias.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
