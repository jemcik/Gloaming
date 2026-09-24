# R8 rules for the release build, on top of proguard-android-optimize.txt and
# each library's own consumer rules. No keep rule is needed yet: the comment on
# `release` in app/build.gradle.kts says why, and when to add one here.

# Line numbers in crash traces. The mapping file travels inside the bundle, so
# Play can put the names back; a line the dex no longer records it cannot.
-keepattributes SourceFile,LineNumberTable
