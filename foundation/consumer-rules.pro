#
# This source file is part of the Grove open-source project
#
# SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
#
# SPDX-License-Identifier: MIT
#

# typeReference<T>() captures its generic argument from the signature of the anonymous
# TypeReferenceImpl subclass it creates. Those subclasses are structurally identical, so R8 merges
# them into their superclass and every TypeReference then reports the same type -- which collapses
# every DependenciesGraph key onto one another. TypeReferenceImpl cross-checks the captured
# signature against the erasure so non-generic keys stay correct regardless, but distinguishing
# List<String> from List<Int> needs the subclasses to survive as distinct classes.
-keep,allowobfuscation,allowshrinking class org.grovealliance.foundation.TypeReferenceImpl
-keep,allowobfuscation,allowshrinking class * extends org.grovealliance.foundation.TypeReferenceImpl

# ValueRepository and AccountKeyType.instance() resolve a knowledge source from its KClass through
# kotlin-reflect (KClass.objectInstance, falling back to companionObjectInstance). Nothing in the
# bytecode reads an object's INSTANCE field that way, so R8 renames or drops it, objectInstance
# returns null, and the lookup fails with "must be an object or have a companion object" -- in the
# app, the first time the account sign-up sheet resolves UserIdKey.
#
# As for DefaultInitializer in core: let R8 rename the classes, since kotlin-reflect follows the
# rewritten metadata, but keep them from being removed or merged and keep INSTANCE by its name.
# The Companion field is already kept by core's rules.
-keep,allowobfuscation class * implements org.grovealliance.foundation.KnowledgeSource
-keepclassmembers class * implements org.grovealliance.foundation.KnowledgeSource {
    public static ** INSTANCE;
}
