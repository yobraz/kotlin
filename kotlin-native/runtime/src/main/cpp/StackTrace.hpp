/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_STACK_TRACE_H
#define RUNTIME_STACK_TRACE_H

#include <array>
#include <cstddef>
#include <cstdint>

#include "SourceInfo.h"
#include "Types.h"
#include "Utils.hpp"

#if USE_GCC_UNWIND
#elif NO_UNWIND
#else
#define USE_LIBC_UNWIND 1
#endif

namespace kotlin {

namespace internal {

#if (__MINGW32__ || __MINGW64__)
// Skip the stack frames related to `StackTrace` ctor, `CollectStackTrace` and `_Unwind_Backtrace`.
static constexpr size_t kSkipFrames = 2;
#else
// Skip the stack frame related to the `StackTrace` ctor and `CollectStackTrace`.
static constexpr size_t kSkipFrames = 1;
#endif

// TODO: This API is asking for a span.
void PrettyPrintSymbol(void* address, const char* name, SourceInfo sourceInfo, char* buffer, size_t bufferSize) noexcept;

NO_INLINE size_t CollectStackTrace(void* buffer[], size_t capacity) noexcept;
NO_INLINE void CollectStackTrace(KStdVector<void*>* buffer) noexcept;

} // namespace internal

static constexpr size_t kDynamicCapacity = std::numeric_limits<size_t>::max();

template<size_t Capacity>
class StackTrace {
public:
    explicit ALWAYS_INLINE StackTrace(size_t skipFrames = 0) noexcept : skipFrames_(skipFrames + internal::kSkipFrames) {
        size_ = internal::CollectStackTrace(buffer_.data(), buffer_.size());
    }

    // TODO: This API is asking for a span.

    size_t size() const noexcept { return skipFrames_ >= size_ ? 0 : size_ - skipFrames_; }

    void* const* data() const noexcept { return buffer_.data() + skipFrames_; }

private:
    size_t size_ = 0;
    size_t skipFrames_;
    // Increase the buffer size by kSkipFrames to make API more predictable.
    // Otherwise creating a StackTrace<32> would result in only 30 or 31 stack entries.
    std::array<void*, Capacity + internal::kSkipFrames> buffer_ = {nullptr};
};

template<>
class StackTrace<kDynamicCapacity> {
public:
    explicit ALWAYS_INLINE StackTrace(size_t skipFrames = 0) noexcept : skipFrames_(skipFrames + internal::kSkipFrames) {
        internal::CollectStackTrace(&buffer_);
    }

    size_t size() const noexcept { return skipFrames_ >= buffer_.size() ? 0 : buffer_.size() - skipFrames_; }

    void* const* data() const noexcept { return buffer_.data() + skipFrames_; }

private:
    size_t skipFrames_;
    KStdVector<void*> buffer_;
};

class SymbolicStackTrace : private MoveOnly {
public:
    class Symbol {
    public:
        Symbol(const SymbolicStackTrace& owner, size_t index) noexcept;

        void* Address() const noexcept { return address_; }
        const char* Name() const noexcept;

        std::array<char, 1024> PrettyPrint(bool allowSourceInfo = true) const noexcept;

    private:
        void* address_;
#if USE_LIBC_UNWIND
        char* name_ = nullptr;
#elif USE_GCC_UNWIND
        std::array<char, 512> name_;
#endif
    };

    class Iterator {
    public:
        Iterator(const SymbolicStackTrace& owner, size_t index) noexcept : owner_(owner), index_(index) {}

        Iterator& operator++() noexcept {
            ++index_;
            return *this;
        }

        Symbol operator*() const noexcept { return Symbol(owner_, index_); }

        bool operator==(const Iterator& rhs) const noexcept { return index_ == rhs.index_; }

        bool operator!=(const Iterator& rhs) const noexcept { return !(*this == rhs); }

    private:
        const SymbolicStackTrace& owner_;
        size_t index_ = 0;
    };

    // TODO: This argument should be a span.
    SymbolicStackTrace(void* const* addresses, size_t size) noexcept;

    template<size_t Capacity>
    explicit SymbolicStackTrace(const StackTrace<Capacity>& stackTrace) noexcept
        : SymbolicStackTrace(stackTrace.data(), stackTrace.size()) {}

    SymbolicStackTrace(SymbolicStackTrace&& rhs) noexcept;
    SymbolicStackTrace& operator=(SymbolicStackTrace&& rhs) noexcept;

    ~SymbolicStackTrace();

    void swap(SymbolicStackTrace& rhs) noexcept;

    Iterator begin() const noexcept { return Iterator(*this, 0); }
    Iterator end() const noexcept { return Iterator(*this, size_); }

    size_t size() const noexcept { return size_; }

    Symbol operator[](size_t index) const noexcept { return Symbol(*this, index); }

private:
    void* const* addresses_;
    size_t size_;
#if USE_LIBC_UNWIND
    char** symbols_ = nullptr;
#endif
};

void PrintStackTraceStderr(bool allowSourceInfo = true) noexcept;

} // namespace kotlin

#endif // RUNTIME_STACK_TRACE_H
