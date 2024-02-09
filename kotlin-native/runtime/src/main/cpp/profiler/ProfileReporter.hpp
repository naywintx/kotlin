/*
* Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once

#include "Profiler.hpp"

#include "Porting.h"
#include "StackTrace.hpp"

namespace kotlin::profiler {

template<typename>
class Profiler;

namespace internal {

template <typename EventTraits>
class ProfileReporter {
public:
    using EventCounts = typename Profiler<EventTraits>::EventCounts;
    using EventRecord = typename Profiler<EventTraits>::EventRecord;

    explicit ProfileReporter(const EventCounts& eventCounts) : eventCounts_(eventCounts) {}

    void report() {
        auto records = mapToVector(eventCounts_, [](const auto& p) { return p.first; });
        printRecordEventsOnLevel(records);
    }

private:
    using Event = typename EventRecord::Event;
    using ContextEntry = typename EventRecord::ContextEntry; // FIXME  RecordEntry?

    void printRecordEventsOnLevel(const std::vector<EventRecord>& records, std::size_t level = 0) {
        auto entriesOnLevel = uniq(mapToVector(records, [&](const auto& r) { return r.entries()[level]; }));
        auto recordsOfEntry = groupBy(records,
                                     [&](const auto& r) { return r.entries()[level]; },
                                     [](const auto& r) { return r; });

        auto accumulatedHitsForEntry = associateWith(entriesOnLevel, [&](const auto& entry) {
            auto& entryRecords = recordsOfEntry[entry];
            auto hitCounts = mapToVector(entryRecords, [&](const auto& record) { return eventCounts_.at(record); });
            return std::accumulate(hitCounts.begin(), hitCounts.end(), std::size_t(0));
        });

        sortBy(entriesOnLevel, [&](const auto& e) -> std::size_t { return accumulatedHitsForEntry.at(e); }, true);

        for (auto& entry: entriesOnLevel) {
            printWithSubentries(entry, recordsOfEntry, accumulatedHitsForEntry.at(entry), level);
        }
    }

    void printWithSubentries( const ContextEntry& entry,
            const std::unordered_map<ContextEntry, std::vector<EventRecord>>& recordsOfEntry,
            std::size_t accumulatedHits, size_t level) {

        entryPrinter_.print(accumulatedHits, entry);

        const auto& subRecords = recordsOfEntry.at(entry);
        if (level < subRecords[0].entries().size() - 1) {
            entryPrinter_.incIdent();
            printRecordEventsOnLevel(subRecords, level + 1);
            entryPrinter_.decIdent();
        }
    }

    class IdentedEntryPrinter {
        static constexpr auto kIdentStep = 2;
    public:
        void incIdent() { baseIdent_ += kIdentStep; }
        void decIdent() { baseIdent_ -= kIdentStep; }

        void print(std::size_t hits, const ContextEntry& entry) {
            auto buf = std::stringstream{};
            buf << "[" << hits << "]";
            auto hitsStr = buf.str();

            buf = std::stringstream{};

            appendSpaces(buf, baseIdent_);
            buf << hitsStr << " ";

            if (std::holds_alternative<Event>(entry)) {
                buf << EventTraits::str(std::get<Event>(entry));
            } else {
                std::array backtraceFrames = {std::get<void*>(entry)};
                auto backtraceLines = GetStackTraceStrings(std_support::span(backtraceFrames));
                for (std::size_t i = 0; i < backtraceLines.size(); ++i) {
                    if (i > 0) {
                        buf << "\n";
                        appendSpaces(buf, baseIdent_ + hitsStr.size() + 1);
                    }
                    buf << backtraceLines[i];
                }
            }

            buf << "\n";
            auto str = buf.str();
            konan::consoleErrorUtf8(str.data(), str.size());
        }

    private:
        void appendSpaces(std::stringstream& buf, std::size_t count) {
            for (std::size_t i = 0; i < count; ++i) {
                buf << " ";
            }
        }

        std::size_t baseIdent_ = 0;
    };


    // FIXME padding

    const EventCounts& eventCounts_;
    IdentedEntryPrinter entryPrinter_;
};

}
}
