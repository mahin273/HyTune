package com.mahin.hytune.domain.engine.model

data class ApplyProfileResult(
    val isSuccess: Boolean,
    val profileId: String,
    val appliedDiff: ProfileDiff,
    val verifiedChangesCount: Int,
    val failedAction: String? = null,
    val rolledBack: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun success(profileId: String, diff: ProfileDiff, verifiedCount: Int): ApplyProfileResult {
            return ApplyProfileResult(
                isSuccess = true,
                profileId = profileId,
                appliedDiff = diff,
                verifiedChangesCount = verifiedCount
            )
        }

        fun failure(
            profileId: String,
            diff: ProfileDiff,
            failedAction: String,
            rolledBack: Boolean,
            errorMessage: String
        ): ApplyProfileResult {
            return ApplyProfileResult(
                isSuccess = false,
                profileId = profileId,
                appliedDiff = diff,
                verifiedChangesCount = 0,
                failedAction = failedAction,
                rolledBack = rolledBack,
                errorMessage = errorMessage
            )
        }
    }
}
