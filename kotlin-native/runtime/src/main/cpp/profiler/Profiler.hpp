/*
* Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once

#include <unordered_map>
#include <unordered_set>
#include <sstream>
#include <numeric>
#include <variant>

#include "IterableHelpers.hpp"
#include "ProfileReporter.hpp"
#include "ProfilerEvents.hpp"
#include "Porting.h"
#include "Utils.hpp"
#include "StackTrace.hpp"
#include "Logging.hpp"
#include "KString.h"

namespace kotlin::profiler::internal {

template <typename EventTraits>
struct EventRecord {
    using Event = typename EventTraits::Event;
    using Backtrace = StackTrace<>;

    EventRecord(const Event& event, const Backtrace& backtrace) : event_(event), backtrace_(backtrace) {}

    // Either the event itself or a backtrace frame
    using ContextEntry = std::variant<Event, void*>;

    auto operator==(const EventRecord& other) const noexcept {
        return event_ == other.event_ && backtrace_ == other.backtrace_;
    }
    auto operator!=(const EventRecord& other) const noexcept { return !operator==(other); }

    [[nodiscard]] auto entries() const noexcept {
        // FIXME a view instead
        std::vector<ContextEntry> elems;
        elems.push_back(event_);
        elems.insert(elems.end(), backtrace_.begin(), backtrace_.end());
        return elems;
    }

    auto hash() const noexcept {
        return CombineHash(hashOf(event_), hashOf(backtrace_));
    }

private:
    Event event_;
    Backtrace backtrace_;
};

} // namespace kotlin::profiler::internal

template <typename EventTraits>
struct std::hash<kotlin::profiler::internal::EventRecord<EventTraits>> {
    std::size_t operator()(const kotlin::profiler::internal::EventRecord<EventTraits>& x) const noexcept {
        return x.hash();
    }
};

namespace kotlin::profiler {

template <typename EventTraits>
class Profiler : Pinned {
public:
    using Event = typename EventTraits::Event;
    using EventRecord = typename internal::EventRecord<EventTraits>;
    using EventCounts = typename std::unordered_map<EventRecord, std::size_t>;

    class ThreadData : Pinned {
    public:
        explicit ThreadData(Profiler& profiler) : profiler_(profiler) {}

        ~ThreadData() {
            publish();
        }

        NO_INLINE void hitImpl(Event event, std::size_t skipFrames = 0) {
            RuntimeAssert(EventTraits::enabled(), "Registered hit with a disabled profiler");
            // FIXME make it collect exactly the requested amount of frames
            EventRecord record{event, EventRecord::Backtrace::current(skipFrames + 1, EventTraits::backtraceDepth())};
            ++inThreadEventCounts_[record];
        }

        void publish() {
            if (EventTraits::enabled()) {
                profiler_.aggregate(inThreadEventCounts_);
                inThreadEventCounts_.clear();
            }
        }

    private:
        Profiler& profiler_;
        EventCounts inThreadEventCounts_{};
    };

    ~Profiler() {
        report();
    }

    void aggregate(EventCounts& data) {
        std::unique_lock lockGuard(aggregatedStorageMutex_);
        for (const auto& [key, value] : data) {
            aggregatedEventCounts_[key] += value;
        }
    }

    void report() const noexcept {
        if (EventTraits::enabled()) {
            std::unique_lock lockGuard(aggregatedStorageMutex_);

            auto reporter = internal::ProfileReporter<EventTraits>{aggregatedEventCounts_};
            reporter.report();
        }
    }

private:
    EventCounts aggregatedEventCounts_{};
    mutable std::mutex aggregatedStorageMutex_;
};


class Profilers {
public:
    class ThreadData {
    public:
        explicit ThreadData(Profilers& profilers)
            : allocationProfilerData_(profilers.allocationProfiler_)
            , safePointProfilerData_(profilers.safePointProfiler_)
            {}

        auto& allocation() noexcept { return allocationProfilerData_; }
        auto& safePoint() noexcept { return safePointProfilerData_; }

        void publish() {
            allocationProfilerData_.publish();
            safePointProfilerData_.publish();
        }
    private:
        Profiler<AllocationEventTraits>::ThreadData allocationProfilerData_;
        Profiler<SafePointEventTraits>::ThreadData safePointProfilerData_;
    };

    void report() {
        allocationProfiler_.report();
        safePointProfiler_.report();
    }
private:
    Profiler<AllocationEventTraits> allocationProfiler_{};
    Profiler<SafePointEventTraits> safePointProfiler_{};
};

} // namespace kotlin::profiler

#define ProfilerHit(EventTraits, profilerThreadData, event, ...) \
    do { \
        if (EventTraits::enabled()) { \
            profilerThreadData.hitImpl(event, ##__VA_ARGS__); \
        } \
    } while (false)
