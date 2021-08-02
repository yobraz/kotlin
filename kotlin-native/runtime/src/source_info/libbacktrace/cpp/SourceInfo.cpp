/*
 * Copyright 2010-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "SourceInfo.h"
#include "backtrace.h"

#include <cstring>

extern "C" int Kotlin_getSourceInfo_libbacktrace(void* addr, SourceInfo *result, int result_size) {
    /**
     * This is hack for better traces.
     * backtrace function returns address after call instruction, and address detection need call instruction itself
     * For honest solution, we should distinguish backtrace symbols got from signal handlers frames, ordinary frames,
     * and addresses got from somewhere else. But for now, we assume all addresses are ordinary backtrace frames.
     */
    addr = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(addr) - 1);
    auto ignore_error = [](void*, const char*, int){};
    static auto state = backtrace_create_state(nullptr, 1, ignore_error, nullptr);
    if (!state) return 0;
    struct callback_arg_t {
        SourceInfo *result;
        int result_ptr;
        int result_size;
    } callback_arg;
    callback_arg.result = result;
    callback_arg.result_ptr = 0;
    callback_arg.result_size = result_size;
    auto process_line = [](void *data, uintptr_t pc, const char *filename, int lineno, int column, const char *function) -> int {
        auto &callback_arg = *static_cast<callback_arg_t*>(data);
        if (callback_arg.result_ptr < callback_arg.result_size) {
            auto &info = callback_arg.result[callback_arg.result_ptr];
            info.setFilename(filename);
            info.lineNumber = lineno;
            info.column = column;
            callback_arg.result_ptr++;
        }
        return callback_arg.result_ptr == callback_arg.result_size;
    };
    backtrace_pcinfo(state, reinterpret_cast<uintptr_t>(addr), process_line, ignore_error, &callback_arg);
    return callback_arg.result_ptr;
}