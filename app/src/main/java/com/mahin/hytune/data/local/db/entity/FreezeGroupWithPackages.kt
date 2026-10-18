package com.mahin.hytune.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class FreezeGroupWithPackages(
    @Embedded
    val group: FreezeGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "group_id"
    )
    val packages: List<FreezeGroupPackageCrossRef>
)
