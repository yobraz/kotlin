/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "SameThreadMarkAndSweep.hpp"

#include <cinttypes>

#include "CompilerConstants.hpp"
#include "GlobalData.hpp"
#include "Logging.hpp"
#include "MarkAndSweepUtils.hpp"
#include "Memory.h"
#include "RootSet.hpp"
#include "Runtime.h"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadSuspension.hpp"

using namespace kotlin;

namespace {

struct MarkTraits {
    static bool IsMarked(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::SameThreadMarkAndSweep>::NodeRef::From(object).GCObjectData();
        return objectData.color() == gc::SameThreadMarkAndSweep::ObjectData::Color::kBlack;
    }

    static bool TryMark(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::SameThreadMarkAndSweep>::NodeRef::From(object).GCObjectData();
        if (objectData.color() == gc::SameThreadMarkAndSweep::ObjectData::Color::kBlack) return false;
        objectData.setColor(gc::SameThreadMarkAndSweep::ObjectData::Color::kBlack);
        return true;
    };
};

struct SweepTraits {
    using ObjectFactory = mm::ObjectFactory<gc::SameThreadMarkAndSweep>;

    static bool TryResetMark(ObjectFactory::NodeRef node) noexcept {
        auto& objectData = node.GCObjectData();
        if (objectData.color() == gc::SameThreadMarkAndSweep::ObjectData::Color::kWhite) return false;
        objectData.setColor(gc::SameThreadMarkAndSweep::ObjectData::Color::kWhite);
        return true;
    }
};

struct FinalizeTraits {
    using ObjectFactory = mm::ObjectFactory<gc::SameThreadMarkAndSweep>;
};

} // namespace

void gc::SameThreadMarkAndSweep::ThreadData::SafePointFunctionEpilogue() noexcept {
    SafePointRegular(1);
}

void gc::SameThreadMarkAndSweep::ThreadData::SafePointLoopBody() noexcept {
    SafePointRegular(1);
}

void gc::SameThreadMarkAndSweep::ThreadData::SafePointExceptionUnwind() noexcept {
    SafePointRegular(1);
}

void gc::SameThreadMarkAndSweep::ThreadData::SafePointAllocation(size_t size) noexcept {
    size_t allocationOverhead =
            gc_.GetAllocationThresholdBytes() == 0 ? allocatedBytes_ : allocatedBytes_ % gc_.GetAllocationThresholdBytes();
    if (threadData_.suspensionData().suspendIfRequested()) {
        allocatedBytes_ = 0;
    } else if (allocationOverhead + size >= gc_.GetAllocationThresholdBytes()) {
        RuntimeLogDebug({"gc"}, "Attempt to GC at SafePointAllocation size=%zu", size);
        allocatedBytes_ = 0;
        PerformFullGC();
    }
    allocatedBytes_ += size;
}

void gc::SameThreadMarkAndSweep::ThreadData::PerformFullGC() noexcept {
    mm::ObjectFactory<gc::SameThreadMarkAndSweep>::FinalizerQueue finalizerQueue;
    {
        // Switch state to native to simulate this thread being a GC thread.
        // As a bonus, if we failed to suspend threads (which means some other thread asked for a GC),
        // we will automatically suspend at the scope exit.
        // TODO: Cannot use `threadData_` here, because there's no way to transform `mm::ThreadData` into `MemoryState*`.
        ThreadStateGuard guard(ThreadState::kNative);
        finalizerQueue = gc_.PerformFullGC();
    }

    // Finalizers are run after threads are resumed, because finalizers may request GC themselves, which would
    // try to suspend threads again. Also, we run finalizers in the runnable state, because they may be executing
    // kotlin code.

    // TODO: These will actually need to be run on a separate thread.
    // TODO: Cannot use `threadData_` here, because there's no way to transform `mm::ThreadData` into `MemoryState*`.
    AssertThreadState(ThreadState::kRunnable);
    RuntimeLogDebug({"gc"}, "Starting to run finalizers");
    auto timeBeforeUs = konan::getTimeMicros();
    finalizerQueue.Finalize();
    auto timeAfterUs = konan::getTimeMicros();
    RuntimeLogInfo({"gc"}, "Finished running finalizers in %" PRIu64 " microseconds", timeAfterUs - timeBeforeUs);
}

void gc::SameThreadMarkAndSweep::ThreadData::OnOOM(size_t size) noexcept {
    RuntimeLogDebug({"gc"}, "Attempt to GC on OOM");
    PerformFullGC();
}

void gc::SameThreadMarkAndSweep::ThreadData::SafePointRegular(size_t weight) noexcept {
    size_t counterOverhead = gc_.GetThreshold() == 0 ? safePointsCounter_ : safePointsCounter_ % gc_.GetThreshold();
    if (threadData_.suspensionData().suspendIfRequested()) {
        safePointsCounter_ = 0;
    } else if (counterOverhead + weight >= gc_.GetThreshold() && konan::getTimeMicros() - timeOfLastGcUs_ >= gc_.GetCooldownThresholdUs()) {
        RuntimeLogDebug({"gc"}, "Attempt to GC at SafePointRegular weight=%zu", weight);
        timeOfLastGcUs_ = konan::getTimeMicros();
        safePointsCounter_ = 0;
        PerformFullGC();
    }
    safePointsCounter_ += weight;
}

gc::SameThreadMarkAndSweep::SameThreadMarkAndSweep() noexcept {
    if (compiler::gcAggressive()) {
        // TODO: Make it even more aggressive and run on a subset of backend.native tests.
        threshold_ = 1000;
        allocationThresholdBytes_ = 10000;
        cooldownThresholdUs_ = 0;
    }
}

mm::ObjectFactory<gc::SameThreadMarkAndSweep>::FinalizerQueue gc::SameThreadMarkAndSweep::PerformFullGC() noexcept {
    RuntimeLogDebug({"gc"}, "Attempt to suspend threads by thread %d", konan::currentThreadId());
    auto timeStartUs = konan::getTimeMicros();
    bool didSuspend = mm::SuspendThreads();
    auto timeSuspendUs = konan::getTimeMicros();
    if (!didSuspend) {
        RuntimeLogDebug({"gc"}, "Failed to suspend threads");
        // Somebody else suspended the threads, and so ran a GC.
        // TODO: This breaks if suspension is used by something apart from GC.
        return {};
    }
    RuntimeLogDebug({"gc"}, "Suspended all threads in %" PRIu64 " microseconds", timeSuspendUs - timeStartUs);

    RuntimeLogInfo({"gc"}, "Started GC epoch %zu. Time since last GC %" PRIu64 " microseconds", epoch_, timeStartUs - lastGCTimestampUs_);
    KStdVector<ObjHeader*> graySet;
    for (auto& thread : mm::GlobalData::Instance().threadRegistry().LockForIter()) {
        // TODO: Maybe it's more efficient to do by the suspending thread?
        thread.Publish();
        size_t stack = 0;
        size_t tls = 0;
        for (auto value : mm::ThreadRootSet(thread)) {
            if (!isNullOrMarker(value.object)) {
                graySet.push_back(value.object);
                switch (value.source) {
                    case mm::ThreadRootSet::Source::kStack:
                        ++stack;
                        break;
                    case mm::ThreadRootSet::Source::kTLS:
                        ++tls;
                        break;
                }
            }
        }
        RuntimeLogDebug({"gc"}, "Collected root set for thread stack=%zu tls=%zu", stack, tls);
    }
    mm::StableRefRegistry::Instance().ProcessDeletions();
    size_t global = 0;
    size_t stableRef = 0;
    for (auto value : mm::GlobalRootSet()) {
        if (!isNullOrMarker(value.object)) {
            graySet.push_back(value.object);
            switch (value.source) {
                case mm::GlobalRootSet::Source::kGlobal:
                    ++global;
                    break;
                case mm::GlobalRootSet::Source::kStableRef:
                    ++stableRef;
                    break;
            }
        }
    }
    auto timeRootSetUs = konan::getTimeMicros();
    RuntimeLogDebug({"gc"}, "Collected global root set global=%zu stableRef=%zu", global, stableRef);

    // Can be unsafe, because we've stopped the world.
    auto objectsCountBefore = mm::GlobalData::Instance().objectFactory().GetSizeUnsafe();

    RuntimeLogDebug({"gc"}, "Collected root set of size=%zu in %" PRIu64 " microseconds", graySet.size(), timeRootSetUs - timeSuspendUs);
    gc::Mark<MarkTraits>(std::move(graySet));
    auto timeMarkUs = konan::getTimeMicros();
    RuntimeLogDebug({"gc"}, "Marked in %" PRIu64 " microseconds", timeMarkUs - timeRootSetUs);
    auto finalizerQueue = gc::Sweep<SweepTraits>(mm::GlobalData::Instance().objectFactory());
    auto timeSweepUs = konan::getTimeMicros();
    RuntimeLogDebug({"gc"}, "Sweeped in %" PRIu64 " microseconds", timeSweepUs - timeMarkUs);

    // Can be unsafe, because we've stopped the world.
    auto objectsCountAfter = mm::GlobalData::Instance().objectFactory().GetSizeUnsafe();

    mm::ResumeThreads();
    auto timeResumeUs = konan::getTimeMicros();

    RuntimeLogDebug({"gc"}, "Resumed threads in %" PRIu64 " microseconds.", timeResumeUs - timeSweepUs);

    auto finalizersCount = finalizerQueue.size();
    auto collectedCount = objectsCountBefore - objectsCountAfter - finalizersCount;

    RuntimeLogInfo(
            {"gc"},
            "Finished GC epoch %zu. Collected %zu objects, to be finalized %zu objects, %zu objects remain. Total pause time %" PRIu64
            " microseconds",
            epoch_, collectedCount, finalizersCount, objectsCountAfter, timeResumeUs - timeStartUs);
    ++epoch_;
    lastGCTimestampUs_ = timeResumeUs;

    return finalizerQueue;
}
