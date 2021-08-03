/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Exceptions.h"

#include <csignal>
#include <memory>
#include <future>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "Memory.h"
#include "TestSupport.hpp"
#include "TestSupportCompilerGenerated.hpp"

using namespace kotlin;
using namespace testing;
using namespace kotlin::test_support;

namespace {

using NativeHandlerMock = NiceMock<MockFunction<void(void)>>;
using OnUnhandledExceptionMock = NiceMock<MockFunction<void(KRef)>>;

KStdUniquePtr<NativeHandlerMock> gNativeHandlerMock = nullptr;
KStdUniquePtr<ScopedMockFunction<void(KRef), /* Strict = */ false>> gOnUnhandledExceptionMock = nullptr;

// Google Test's death tests do not fail in case of a failed EXPECT_*/ASSERT_* check in a death statement.
// To workaround it, manually check the conditions to be asserted, log all failed conditions and then
// validate that there were no failure messages.
void loggingAssert(bool condition, const char* message) noexcept {
    if (!condition) {
        std::cerr << "FAIL: " << message << std::endl;
    }
}

void log(const char* message) noexcept {
    std::cerr << message << std::endl;
}

NativeHandlerMock& setNativeTerminateHandler() noexcept {
    gNativeHandlerMock = make_unique<NativeHandlerMock>();
    std::set_terminate([]() {
        gNativeHandlerMock->Call();
        std::abort();
    });
    return *gNativeHandlerMock;
}

OnUnhandledExceptionMock& setKotlinTerminationHandler() noexcept {
    gOnUnhandledExceptionMock =
            make_unique<ScopedMockFunction<void(KRef), /* Strict = */ false>>(ScopedOnUnhandledExceptionMock</* Strict = */ false>());
    SetKonanTerminateHandler();
    return gOnUnhandledExceptionMock->get();
}

void setupMocks(bool expectRegisteredThread = true) noexcept {
    auto& nativeHandlerMock = setNativeTerminateHandler();
    ON_CALL(nativeHandlerMock, Call)
            .WillByDefault([expectRegisteredThread]() {
                if (expectRegisteredThread) {
                    loggingAssert(mm::GetMemoryState() != nullptr, "Expected registered thread in the native handler");
                    loggingAssert(GetThreadState() == ThreadState::kNative, "Expected kNative thread state in the native handler");
                }
                log("Native handler");
            });

    auto& onUnhandledExceptionMock = setKotlinTerminationHandler();
    ON_CALL(onUnhandledExceptionMock, Call)
            .WillByDefault([]() {
                loggingAssert(GetThreadState() == ThreadState::kRunnable, "Expected kRunnable state in the Kotlin handler");
                log("Kotlin handler");
            });
}

} // namespace

#define EXPERIMENTAL_MM_ONLY()                                        \
    do {                                                              \
        if (CurrentMemoryModel != MemoryModel::kExperimental) {       \
            GTEST_SKIP() << "This test requires the Experimental MM"; \
        }                                                             \
    } while(false)

#define ASSERTS_PASSED AllOf(Not(HasSubstr("FAIL")), Not(HasSubstr("runtime assert")))
#define KOTLIN_HANDLER_RAN HasSubstr("Kotlin handler")
#define NATIVE_HANDLER_RAN HasSubstr("Native handler")

TEST(TerminationHandlerDeathTest, TerminationInRunnableState) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        ScopedMemoryInit init;
        loggingAssert(GetThreadState() == ThreadState::kRunnable, "Expected kRunnable thread state before std::terminate");
        std::terminate();
    };

    EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, TerminationInNativeState) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        ScopedMemoryInit init;
        ThreadStateGuard stateGuard(ThreadState::kNative);
        loggingAssert(GetThreadState() == ThreadState::kNative, "Expected native thread state before std::terminate");
        std::terminate();
    };

     EXPECT_DEATH(testBlock(),
                AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, TerminationInForeignThread) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks(/* expectRegisteredThread = */ false);

        loggingAssert(mm::GetMemoryState() == nullptr, "Expected unregistered thread before std::terminate");
        std::terminate();
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, UnhandledKotlinExceptionInRunnableState) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        // Do not use RunInNewThread because the termination handler will check initiliazation
        // of the whole runtime while RunInNewThread initializes the memory only.
        std::thread thread([]() {
            Kotlin_initRuntimeIfNeeded();
            SwitchThreadState(mm::GetMemoryState(), ThreadState::kRunnable);

            loggingAssert(GetThreadState() == ThreadState::kRunnable, "Expected kRunanble thread state before throwing");
            ObjHeader exception{};
            ExceptionObjHolder::Throw(&exception);
        });
        thread.join();
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, KOTLIN_HANDLER_RAN, Not(NATIVE_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, UnhandledKotlinExceptionInNativeState) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        // This situation is possible if a Kotlin exception thrown by a Kotlin callback is re-thrown in
        // another thread which is attached to the Kotlin runtime but has the kNative state.

        // Do not use RunInNewThread because the termination handler will check initiliazation
        // of the whole runtime while RunInNewThread initializes the memory only.
        std::thread thread([]() {
            Kotlin_initRuntimeIfNeeded();

            loggingAssert(GetThreadState() == ThreadState::kNative, "Expected kNative thread state before throwing");
            ObjHeader exception{};
            ExceptionObjHolder::Throw(&exception);
        });
        thread.join();
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, KOTLIN_HANDLER_RAN, Not(NATIVE_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, UnhandledKotlinExceptionInForeignThread) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks(/* expectRegisteredThread = */ false);

        // It is possible if a Kotlin exception thrown by a Kotlin callback is re-thrown in
        // another thread which is not attached to the Kotlin runtime at all.
        std::thread foreignThread([]() {
            loggingAssert(mm::GetMemoryState() == nullptr, "Expected unregistered thread before throwing");

            auto future = std::async(std::launch::async, []() {
                // Initial Kotlin exception throwing requires the runtime to be initialized.
                // Do not use ScopedMemoryInit because it clears the stable ref queue
                // of the current thread on deinitialization. After that the ExceptionObjHolder
                // will contain a dangling pointer to the stable ref queue entry.
                Kotlin_initRuntimeIfNeeded();
                ObjHeader exception{};
                ExceptionObjHolder::Throw(&exception);
            });
            // Re-throw the Kotlin exception in a foreign thread.
            future.get();
        });
        foreignThread.join();
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, KOTLIN_HANDLER_RAN, Not(NATIVE_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, UnhandledForeignExceptionInNativeState) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        RunInNewThread([](MemoryState* thread) {
            SwitchThreadState(thread, ThreadState::kNative);
            loggingAssert(GetThreadState(thread) == ThreadState::kNative, "Expected kNative thread state before throwing");

            throw std::runtime_error("Foreign exception");
        });
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}

TEST(TerminationHandlerDeathTest, UnhandledForeignExceptionInForeignThread) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks(/* expectRegisteredThread = */ false);

        std::thread foreignThread([]() {
            loggingAssert(mm::GetMemoryState() == nullptr, "Expected unregistered thread before throwing");
            throw std::runtime_error("Foreign exception");
        });
        foreignThread.join();
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}

// Model a filtering exception handler which terminates the program if an interop call throws a foreign exception.
TEST(TerminationHandlerDeathTest, TerminationInForeignExceptionCatch) {
    EXPERIMENTAL_MM_ONLY();
    auto testBlock = []() {
        setupMocks();

        ScopedMemoryInit init;
        loggingAssert(GetThreadState(init.memoryState()) == ThreadState::kRunnable, "Expected kRunnable state before catching");

        try {
            throw std::runtime_error("Foreign exception");
        } catch(...) {
            std::terminate();
        }
    };

     EXPECT_DEATH(testBlock(), AllOf(ASSERTS_PASSED, NATIVE_HANDLER_RAN, Not(KOTLIN_HANDLER_RAN)));
}