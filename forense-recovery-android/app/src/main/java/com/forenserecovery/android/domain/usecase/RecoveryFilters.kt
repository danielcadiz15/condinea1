package com.forenserecovery.android.domain.usecase

import com.forenserecovery.android.domain.model.ItemFilter
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.RecoveryStatus
import com.forenserecovery.android.domain.model.RecoveryType

fun RecoveryItem.matchesFilter(filter: ItemFilter): Boolean = when (filter) {
    ItemFilter.ALL -> true
    ItemFilter.IMAGE -> type == RecoveryType.IMAGE
    ItemFilter.VIDEO -> type == RecoveryType.VIDEO
    ItemFilter.AUDIO -> type == RecoveryType.AUDIO
    ItemFilter.THUMBNAIL -> status == RecoveryStatus.THUMBNAIL
    ItemFilter.PARTIAL -> status == RecoveryStatus.PARTIAL
    ItemFilter.CORRUPT -> status == RecoveryStatus.CORRUPT
}
