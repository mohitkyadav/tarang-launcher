# Tarang Launcher — ProGuard/R8 rules.
# Release builds run R8 (see app/build.gradle.kts). The app uses no reflection: the manifest keeps
# the activity, services and the FileProvider, and AndroidX ships its own consumer rules. Add a keep
# rule here only for code that is loaded by name.
