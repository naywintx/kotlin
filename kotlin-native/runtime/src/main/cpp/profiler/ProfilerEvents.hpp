/*
* Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once

#include "KString.h"
namespace kotlin::profiler {

struct AllocationEventTraits {
    struct Allocation {
        auto operator==(const Allocation& other) const noexcept {
            return typeInfo_ == other.typeInfo_ && arrayLength_ == other.arrayLength_;
        }
        auto operator!=(const Allocation& other) const noexcept { return !operator==(other); }

        auto hash() const noexcept {
            return kotlin::CombineHash(hashOf(typeInfo_), hashOf(arrayLength_));
        }

        const TypeInfo* typeInfo_;
        std::size_t arrayLength_ = 0;
    };

    using Event = Allocation;

    static constexpr int kContextBacktraceDepth = 2; // TODO make configurable

    static auto str(const Allocation& alloc) -> std::string {
        auto pkg = to_string(alloc.typeInfo_->packageName_);
        auto cls = to_string(alloc.typeInfo_->relativeName_);
        auto fqName = pkg.empty() ? cls : pkg + "." + cls;
        if (alloc.typeInfo_->IsArray()) {
            return fqName + "[" + std::to_string(alloc.arrayLength_) +"]";
        }
        return fqName;
    }
};

}

template<>
struct std::hash<kotlin::profiler::AllocationEventTraits::Allocation> {
    std::size_t operator()(const typename kotlin::profiler::AllocationEventTraits::Allocation& x) const noexcept {
        return x.hash();
    }
};
