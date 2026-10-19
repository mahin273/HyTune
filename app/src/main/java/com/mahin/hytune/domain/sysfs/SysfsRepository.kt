package com.mahin.hytune.domain.sysfs

import com.mahin.hytune.domain.sysfs.engine.SysfsBrowserEngine
import com.mahin.hytune.domain.sysfs.model.SysfsNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SysfsRepository @Inject constructor(
    private val browserEngine: SysfsBrowserEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun listDirectory(path: String): Result<List<SysfsNode>> = withContext(ioDispatcher) {
        browserEngine.listDirectory(path)
    }

    suspend fun readNode(path: String): Result<String> = withContext(ioDispatcher) {
        browserEngine.readNode(path)
    }

    suspend fun writeNode(path: String, value: String): Result<Unit> = withContext(ioDispatcher) {
        browserEngine.writeNode(path, value)
    }
}
