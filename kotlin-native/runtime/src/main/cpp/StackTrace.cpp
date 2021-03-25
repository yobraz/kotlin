/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "StackTrace.hpp"

#include <cstdlib>
#include <cstring>

#include "Porting.h"
#include "Types.h"

// GCC unwinder for backtrace.
// Use it for the !USE_LIBC_UNWIND case too, to count the stack depth.
#include <unwind.h>
#if USE_GCC_UNWIND
// AddressToSymbol mapping.
#include "ExecFormat.h"
#elif USE_LIBC_UNWIND
// Glibc backtrace() function.
#include <execinfo.h>
#endif

using namespace kotlin;

namespace {

#if (USE_GCC_UNWIND)
_Unwind_Ptr getUnwindPtr(_Unwind_Context* context) {
#if (__MINGW32__ || __MINGW64__)
    return _Unwind_GetRegionStart(context);
#else
    return _Unwind_GetIP(context);
#endif
}
#endif

class StringBuilder {
public:
    StringBuilder(char* buffer, size_t size) noexcept : buffer_(buffer), size_(size) {}

    void Append(char c) noexcept {
        if (size_ <= 1) {
            return;
        }

        buffer_[0] = c;
        buffer_[1] = '\0';
        ++buffer_;
        --size_;
    }

    void Append(const char* string) {
        if (size_ <= 1) {
            return;
        }

        size_t stringLength = konan::strnlen(string, size_ - 1);
        // `stringLength` < `size_`
        strncpy(buffer_, string, stringLength);
        buffer_[stringLength] = '\0';
        buffer_ += stringLength;
        size_ -= stringLength;
    }

    template <typename... Args>
    void Append(const char* format, Args&&... args) noexcept {
        if (size_ <= 1) {
            return;
        }

        int rv = konan::snprintf(buffer_, size_, format, std::forward<Args>(args)...);
        if (rv < 0) {
            return;
        }

        size_t stringLength = std::min(static_cast<size_t>(rv), size_ - 1);
        buffer_ += stringLength;
        size_ -= stringLength;
    }

private:
    char* buffer_;
    size_t size_;
};

} // namespace

void kotlin::internal::PrettyPrintSymbol(void* address, const char* name, SourceInfo sourceInfo, char* buffer, size_t bufferSize) noexcept {
    bool needsAddress = true;

    StringBuilder builder(buffer, bufferSize);

    if (name != nullptr) {
#if USE_LIBC_UNWIND
        // With libc's `backtrace` address is already included in the symbol name.
        needsAddress = false;
#endif
        builder.Append(name);
        builder.Append(' ');
    }

    if (needsAddress) {
        builder.Append("%p ", address);
    }

    if (sourceInfo.fileName == nullptr) return;

    builder.Append("(%s:", sourceInfo.fileName);

    if (sourceInfo.lineNumber == -1) {
        builder.Append("<unknown>)");
    } else {
        builder.Append("%d:%d)", sourceInfo.lineNumber, sourceInfo.column);
    }
}

// TODO: CollectStackTrace implementations are just a hack, e.g. the result is inexact;
// however it is better to have an inexact stacktrace than not to have any.
NO_INLINE size_t kotlin::internal::CollectStackTrace(void* buffer[], size_t capacity) noexcept {
#if USE_GCC_UNWIND
    struct TraceHolder {
        void** buffer;
        size_t capacity;
        size_t size;
    };

    TraceHolder result { .buffer = buffer, .capacity = capacity, .size = 0 };

    _Unwind_Trace_Fn unwindCallback = [](_Unwind_Context* context, void* arg) {
        auto* trace = static_cast<TraceHolder*>(arg);
        // We do not allocate a dynamic storage for the stacktrace so store only first `kBufferCapacity` elements.
        if (trace->size >= trace->capacity) {
            return _URC_NO_REASON;
        }
        _Unwind_Ptr ptr = getUnwindPtr(context);
        trace->buffer[trace->size++] = reinterpret_cast<void*>(ptr);
        return _URC_NO_REASON;
    };
    _Unwind_Backtrace(unwindCallback, &result);
    return result.size;
#elif USE_LIBC_UNWIND
    return backtrace(buffer, capacity);
#endif
}

NO_INLINE void kotlin::internal::CollectStackTrace(KStdVector<void*>* buffer) noexcept {
#if USE_GCC_UNWIND
    _Unwind_Trace_Fn unwindCallback = [](_Unwind_Context* context, void* arg) {
        auto* buffer = static_cast<KStdVector<void*>*>(arg);
        _Unwind_Ptr ptr = getUnwindPtr(context);
        buffer->push_back(reinterpret_cast<void*>(ptr));
        return _URC_NO_REASON;
    };
    _Unwind_Backtrace(unwindCallback, buffer);
#elif USE_LIBC_UNWIND
    // Count the stack depth using the _Unwind_Backtrace API.
    _Unwind_Trace_Fn unwindCallback = [](_Unwind_Context* context, void* arg) {
        auto* stackDepth = static_cast<size_t*>(arg);
        (*stackDepth)++;
        return _URC_NO_REASON;
    };

    size_t stackDepth = 0;
    _Unwind_Backtrace(unwindCallback, &stackDepth);
    buffer->resize(stackDepth);
    backtrace(buffer->data(), buffer->size());
#endif
}

SymbolicStackTrace::Symbol::Symbol(const SymbolicStackTrace& owner, size_t index) noexcept : address_(owner.addresses_[index]) {
#if USE_GCC_UNWIND
    if (!AddressToSymbol(address_, name_.data(), name_.size())) {
        // Make empty string:
        name_[0] = '\0';
    }
#elif USE_LIBC_UNWIND
    // Can be null if we failed to allocated dynamic memory for the symbols. In which case - fine,
    // we just avoid printing the symbols
    if (owner.symbols_ != nullptr) {
        name_ = owner.symbols_[index];
    }
#endif
}

const char* SymbolicStackTrace::Symbol::Name() const noexcept {
#if NO_UNWIND
    return nullptr;
#elif USE_GCC_UNWIND
    return name_.data();
#elif USE_LIBC_UNWIND
    return name_;
#endif
}

std::array<char, 1024> SymbolicStackTrace::Symbol::PrettyPrint(bool allowSourceInfo) const noexcept {
    void* address = address_;
    const char* name = Name();
    SourceInfo sourceInfo = allowSourceInfo ? Kotlin_getSourceInfo(address) : SourceInfo{nullptr, -1, -1};
    std::array<char, 1024> buffer;
    internal::PrettyPrintSymbol(address, name, sourceInfo, buffer.data(), buffer.size());
    return buffer;
}

SymbolicStackTrace::SymbolicStackTrace(void* const* addresses, size_t size) noexcept : addresses_(addresses), size_(size) {
#if USE_LIBC_UNWIND
    if (size_ > 0) {
        symbols_ = backtrace_symbols(addresses_, size_);
    }
#endif
}

SymbolicStackTrace::SymbolicStackTrace(SymbolicStackTrace&& rhs) noexcept : addresses_(rhs.addresses_), size_(rhs.size_) {
    rhs.size_ = 0;
    rhs.addresses_ = nullptr;
#if USE_LIBC_UNWIND
    symbols_ = rhs.symbols_;
    rhs.symbols_ = nullptr;
#endif
}

SymbolicStackTrace& SymbolicStackTrace::operator=(SymbolicStackTrace&& rhs) noexcept {
    SymbolicStackTrace copy(std::move(rhs));
    swap(copy);
    return *this;
}

SymbolicStackTrace::~SymbolicStackTrace() {
#if USE_LIBC_UNWIND
    // Not `konan::free`. Used to free memory allocated in `backtrace_symbols` where `malloc` is used.
    free(symbols_);
#endif
}

void SymbolicStackTrace::swap(SymbolicStackTrace& rhs) noexcept {
    std::swap(addresses_, rhs.addresses_);
    std::swap(size_, rhs.size_);
#if USE_LIBC_UNWIND
    std::swap(symbols_, rhs.symbols_);
#endif
}

NO_INLINE void kotlin::PrintStackTraceStderr(bool allowSourceInfo) noexcept {
    // Skip this function in the stack trace.
    StackTrace<32> stackTrace(1);
    SymbolicStackTrace symbolicStackTrace(stackTrace);

    for (const auto& symbol : symbolicStackTrace) {
        auto line = symbol.PrettyPrint(allowSourceInfo);
        konan::consoleErrorUtf8(line.data(), konan::strnlen(line.data(), line.size()));
        konan::consoleErrorUtf8("\n", 1);
    }
}
