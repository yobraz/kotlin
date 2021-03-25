/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "StackTrace.hpp"

#include <signal.h>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "Common.h"
#include "Porting.h"

using namespace kotlin;

namespace {

// Disable optimizations for these functions to avoid inlining and tail recursion optimization.
template <size_t Capacity>
[[clang::optnone]] StackTrace<Capacity> GetStackTrace1(size_t skipFrames) {
    return StackTrace<Capacity>(skipFrames);
}

template <size_t Capacity>
[[clang::optnone]] StackTrace<Capacity> GetStackTrace2(size_t skipFrames) {
    return GetStackTrace1<Capacity>(skipFrames);
}

template <size_t Capacity>
[[clang::optnone]] StackTrace<Capacity> GetDeepStackTrace(size_t depth) {
    if (depth <= 1) {
        return StackTrace<Capacity>(0);
    } else {
        return GetDeepStackTrace<Capacity>(depth - 1);
    }
}

NO_INLINE void AbortWithStackTrace(int unused) {
    PrintStackTraceStderr();
    konan::abort();
}

std::string Collect(const SymbolicStackTrace& stackTrace) {
    std::string result;
    for (const auto& symbol : stackTrace) {
        auto line = symbol.PrettyPrint();
        result += line.data();
    }
    return result;
}

std::string PrettyPrintSymbol(uintptr_t address, const char* name, const char* filename, int line, int column, size_t bufferSize) {
    std::vector<char> result(bufferSize, '\0');

    kotlin::internal::PrettyPrintSymbol(reinterpret_cast<void*>(address), name, SourceInfo{filename, line, column}, result.data(), result.size());

    return std::string(result.data());
}

template <size_t Capactiy>
struct StaticTestCase {
    static constexpr size_t capacity = Capactiy;

    static std::string getName() {
        return std::to_string(capacity);
    }
};

struct DynamicTestCase {
    static constexpr size_t capacity = kotlin::kDynamicCapacity;

    static std::string getName() {
        return "Dynamic";
    }
};

class StackTraceCaseNames {
public:
    template <typename T>
    static std::string GetName(int i) {
        return T::getName();
    }
};

} // namespace

template <typename TestCase>
class StackTraceTest : public testing::Test {};
using StackTraceTestCases = ::testing::Types<StaticTestCase<32>, DynamicTestCase>;
TYPED_TEST_SUITE(StackTraceTest, StackTraceTestCases, StackTraceCaseNames);

TYPED_TEST(StackTraceTest, StackTrace) {
    auto stackTrace = GetStackTrace2<TypeParam::capacity>(0);
    SymbolicStackTrace symbolicStackTrace(stackTrace);
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_THAT(symbolicStackTrace[0].Name(), testing::HasSubstr("GetStackTrace1"));
}

TYPED_TEST(StackTraceTest, StackTraceWithSkip) {
    auto stackTrace = GetStackTrace2<TypeParam::capacity>(1);
    SymbolicStackTrace symbolicStackTrace(stackTrace);
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_THAT(symbolicStackTrace[0].Name(), testing::HasSubstr("GetStackTrace2"));
}

TYPED_TEST(StackTraceTest, DeepStackTrace) {
    constexpr size_t knownStackDepth = 100;
    auto stackTrace = GetDeepStackTrace<TypeParam::capacity>(knownStackDepth);
    SymbolicStackTrace symbolicStackTrace(stackTrace);
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_THAT(symbolicStackTrace[0].Name(), testing::HasSubstr("GetDeepStackTrace"));

    if (TypeParam::capacity == kotlin::kDynamicCapacity) {
        EXPECT_GT(symbolicStackTrace.size(), knownStackDepth);
        EXPECT_THAT(symbolicStackTrace[knownStackDepth - 1].Name(), testing::HasSubstr("GetDeepStackTrace"));
    } else {
        EXPECT_EQ(symbolicStackTrace.size(), TypeParam::capacity);
        EXPECT_THAT(symbolicStackTrace[TypeParam::capacity - 1].Name(), testing::HasSubstr("GetDeepStackTrace"));
    }
}

TEST(StackTraceTest, SingleElementBuffer) {
    auto stackTrace = GetStackTrace1<1>(0);
    SymbolicStackTrace symbolicStackTrace(stackTrace);
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_EQ(symbolicStackTrace.size(), 1ul);
    EXPECT_THAT(symbolicStackTrace[0].Name(), testing::HasSubstr("GetStackTrace1"));
}

TEST(StackTraceTest, EmptySingleElementBuffer) {
    auto stackTrace = GetStackTrace1<1>(1);
    SymbolicStackTrace symbolicStackTrace(stackTrace);
    EXPECT_EQ(symbolicStackTrace.size(), 0ul);
}

TYPED_TEST(StackTraceTest, Iteration) {
    auto stackTrace = GetStackTrace1<TypeParam::capacity>(0);
    SymbolicStackTrace symbolicStackTrace(stackTrace);

    std::string result = Collect(symbolicStackTrace);
    EXPECT_THAT(result, testing::AllOf(testing::HasSubstr("GetStackTrace1"), testing::HasSubstr("StackTraceTest_Iteration_Test")));
}

TEST(StackTraceTest, IterationOnEmpty) {
    SymbolicStackTrace symbolicStackTrace(nullptr, 0);
    ASSERT_THAT(symbolicStackTrace.size(), 0ul);

    std::string result = Collect(symbolicStackTrace);
    EXPECT_THAT(result, std::string());
}

TYPED_TEST(StackTraceTest, MoveConstructSymbolicStackTrace) {
    auto stackTrace = GetStackTrace1<TypeParam::capacity>(0);
    SymbolicStackTrace symbolicStackTrace1(stackTrace);
    SymbolicStackTrace symbolicStackTrace2(std::move(symbolicStackTrace1));

    std::string result1 = Collect(symbolicStackTrace1);
    std::string result2 = Collect(symbolicStackTrace2);

    EXPECT_THAT(result1, std::string());
    EXPECT_THAT(result2, testing::HasSubstr("GetStackTrace1"));
}

TYPED_TEST(StackTraceTest, MoveAssignSymbolicStackTrace) {
    auto stackTrace1 = GetStackTrace1<TypeParam::capacity>(0);
    auto stackTrace2 = GetStackTrace2<TypeParam::capacity>(0);
    SymbolicStackTrace symbolicStackTrace1(stackTrace1);
    SymbolicStackTrace symbolicStackTrace2(stackTrace2);
    symbolicStackTrace2 = std::move(symbolicStackTrace1);

    std::string result1 = Collect(symbolicStackTrace1);
    std::string result2 = Collect(symbolicStackTrace2);

    EXPECT_THAT(result1, std::string());
    EXPECT_THAT(result2, testing::AllOf(testing::HasSubstr("GetStackTrace1"), testing::Not(testing::HasSubstr("GetStackTrace2"))));
}

TEST(StackTraceDeathTest, PrintStackTrace) {
    EXPECT_DEATH(
            { AbortWithStackTrace(0); },
            testing::AllOf(
                    testing::HasSubstr("AbortWithStackTrace"), testing::HasSubstr("StackTraceDeathTest_PrintStackTrace_Test"),
                    testing::Not(testing::HasSubstr("PrintStackTraceStderr"))));
}

TEST(StackTraceDeathTest, PrintStackTraceInSignalHandler) {
    EXPECT_DEATH(
            {
                signal(SIGINT, &AbortWithStackTrace);
                raise(SIGINT);
            },
            testing::AllOf(
                    testing::HasSubstr("AbortWithStackTrace"),
                    testing::HasSubstr("StackTraceDeathTest_PrintStackTraceInSignalHandler_Test"),
                    testing::Not(testing::HasSubstr("PrintStackTraceStderr"))));
}

TEST(StackTraceTest, PrettyPrintSymbol) {
#if KONAN_WINDOWS
    EXPECT_THAT(PrettyPrintSymbol(0xa, "SymbolName", "SomeFile", 42, 13, 1024), "SymbolName 000000000000000a (SomeFile:42:13)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, "SymbolName", "SomeFile", 42, 13, 23), "SymbolName 00000000000");
    EXPECT_THAT(PrettyPrintSymbol(0xa, "SymbolName", "SomeFile", -1, 13, 1024), "SymbolName 000000000000000a (SomeFile:<unknown>)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, "SymbolName", nullptr, 42, 13, 1024), "SymbolName 000000000000000a ");
    EXPECT_THAT(PrettyPrintSymbol(0xa, nullptr, "SomeFile", 42, 13, 1024), "000000000000000a (SomeFile:42:13)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, nullptr, nullptr, 42, 13, 1024), "000000000000000a ");
#else
#if USE_LIBC_UNWIND
    const char* kSymbolName = "SymbolName 0xa";
#else
    const char* kSymbolName = "SymbolName";
#endif
    EXPECT_THAT(PrettyPrintSymbol(0xa, kSymbolName, "SomeFile", 42, 13, 1024), "SymbolName 0xa (SomeFile:42:13)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, kSymbolName, "SomeFile", 42, 13, 23), "SymbolName 0xa (SomeFi");
    EXPECT_THAT(PrettyPrintSymbol(0xa, kSymbolName, "SomeFile", -1, 13, 1024), "SymbolName 0xa (SomeFile:<unknown>)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, kSymbolName, nullptr, 42, 13, 1024), "SymbolName 0xa ");
    EXPECT_THAT(PrettyPrintSymbol(0xa, nullptr, "SomeFile", 42, 13, 1024), "0xa (SomeFile:42:13)");
    EXPECT_THAT(PrettyPrintSymbol(0xa, nullptr, nullptr, 42, 13, 1024), "0xa ");
#endif
}
