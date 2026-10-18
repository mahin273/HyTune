package com.mahin.hytune.domain.safety.model

sealed interface WhitelistResult {
    data class Approved(val canonicalPath: String) : WhitelistResult
    data class Rejected(val path: String, val reason: String) : WhitelistResult

    val isApproved: Boolean
        get() = this is Approved
}
