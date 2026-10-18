package com.mahin.hytune.domain.appmanager

import com.mahin.hytune.data.local.db.dao.FreezeGroupDao
import com.mahin.hytune.data.local.db.entity.FreezeGroupEntity
import com.mahin.hytune.data.local.db.entity.FreezeGroupPackageCrossRef
import com.mahin.hytune.data.local.db.entity.FreezeGroupWithPackages
import com.mahin.hytune.domain.appmanager.model.AppOperation
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import com.mahin.hytune.domain.appmanager.model.GroupOperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreezeGroupRepository @Inject constructor(
    private val dao: FreezeGroupDao,
    private val operationEngine: AppOperationEngine,
    private val safetyGuard: PackageSafetyGuard,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getAllGroups(): Flow<List<FreezeGroup>> =
        dao.getAllGroupsWithPackages()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    fun getGroupById(groupId: Long): Flow<FreezeGroup?> =
        dao.getGroupWithPackagesById(groupId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    suspend fun getAutoFreezeGroups(): List<FreezeGroup> = withContext(ioDispatcher) {
        dao.getAutoFreezeGroups().map { it.toDomain() }
    }

    suspend fun saveGroup(group: FreezeGroup): Long = withContext(ioDispatcher) {
        val entity = FreezeGroupEntity(
            id = group.id,
            name = group.name.trim(),
            description = group.description.trim(),
            autoFreezeOnScreenOff = group.autoFreezeOnScreenOff,
            autoFreezeDelaySeconds = group.autoFreezeDelaySeconds.coerceAtLeast(0),
            createdAtEpochMs = group.createdAtEpochMs
        )

        dao.saveGroupWithPackages(entity, group.packages.map { it.trim() }.distinct())
    }

    suspend fun deleteGroup(groupId: Long) = withContext(ioDispatcher) {
        dao.deleteGroup(groupId)
    }

    suspend fun addPackageToGroup(groupId: Long, packageName: String) = withContext(ioDispatcher) {
        dao.insertPackageCrossRef(FreezeGroupPackageCrossRef(groupId, packageName.trim()))
    }

    suspend fun removePackageFromGroup(groupId: Long, packageName: String) = withContext(ioDispatcher) {
        dao.deletePackageFromGroup(groupId, packageName.trim())
    }

    suspend fun freezeGroup(groupId: Long): Result<GroupOperationResult> = withContext(ioDispatcher) {
        executeGroupOperation(groupId, AppOperation.FREEZE)
    }

    suspend fun unfreezeGroup(groupId: Long): Result<GroupOperationResult> = withContext(ioDispatcher) {
        executeGroupOperation(groupId, AppOperation.UNFREEZE)
    }

    private suspend fun executeGroupOperation(
        groupId: Long,
        operation: AppOperation
    ): Result<GroupOperationResult> {
        val target = dao.getGroupWithPackagesByIdSync(groupId)
            ?: return Result.failure(NoSuchElementException("Freeze group with id $groupId not found"))

        val domainGroup = target.toDomain()
        val failures = mutableMapOf<String, String>()
        var successCount = 0

        for (pkg in domainGroup.packages) {
            if (operation.isDestructive && safetyGuard.isCritical(pkg)) {
                failures[pkg] = "Package is protected by safety guard"
                continue
            }

            val result = operationEngine.executeOperation(pkg, operation)
            result.onSuccess { opResult ->
                if (opResult.success) {
                    successCount++
                } else {
                    failures[pkg] = opResult.message ?: "Operation failed"
                }
            }.onFailure { err ->
                failures[pkg] = err.message ?: "Operation failed"
            }
        }

        return Result.success(
            GroupOperationResult(
                groupId = groupId,
                groupName = domainGroup.name,
                operation = operation,
                totalPackages = domainGroup.packages.size,
                successCount = successCount,
                failureCount = failures.size,
                failures = failures
            )
        )
    }

    private fun FreezeGroupWithPackages.toDomain(): FreezeGroup {
        return FreezeGroup(
            id = group.id,
            name = group.name,
            description = group.description,
            autoFreezeOnScreenOff = group.autoFreezeOnScreenOff,
            autoFreezeDelaySeconds = group.autoFreezeDelaySeconds,
            packages = packages.map { it.packageName },
            createdAtEpochMs = group.createdAtEpochMs
        )
    }
}
