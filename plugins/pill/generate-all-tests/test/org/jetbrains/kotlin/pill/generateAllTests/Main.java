/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.pill.generateAllTests;

import org.jetbrains.kotlin.generators.tests.GenerateJava8TestsKt;
import org.jetbrains.kotlin.generators.tests.GenerateJsTestsKt;
import org.jetbrains.kotlin.generators.tests.GenerateRuntimeDescriptorTestsKt;
import org.jetbrains.kotlin.generators.tests.GenerateTestsKt;
import org.jetbrains.kotlin.test.generators.GenerateCompilerTestsKt;

import static org.jetbrains.kotlin.generators.InconsistencyChecker.Companion;

public class Main {
    public static void main(String[] args) {
        Companion.withAssertAllGenerated(args, dryRun -> {
            GenerateCompilerTestsKt.main(args);
            GenerateTestsKt.main(args);
            GenerateJsTestsKt.main(args);
            GenerateJava8TestsKt.main(args);
            GenerateRuntimeDescriptorTestsKt.main(args);
        });
    }
}
