package com.mahin.hytune.domain.memory.model

enum class DropCacheType(val mode: Int) {
    PAGE_CACHE(1),
    DENTRIES_INODES(2),
    ALL(3)
}
