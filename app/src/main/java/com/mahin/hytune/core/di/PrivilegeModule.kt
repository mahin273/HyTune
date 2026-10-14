package com.mahin.hytune.core.di

import com.mahin.hytune.core.privilege.LibsuShellRunner
import com.mahin.hytune.core.privilege.PrivilegeBackend
import com.mahin.hytune.core.privilege.PrivilegeManager
import com.mahin.hytune.core.privilege.RikkaShizukuShellRunner
import com.mahin.hytune.core.privilege.RootPrivilegeBackend
import com.mahin.hytune.core.privilege.RootShellRunner
import com.mahin.hytune.core.privilege.ShizukuPrivilegeBackend
import com.mahin.hytune.core.privilege.ShizukuShellRunner
import com.mahin.hytune.core.privilege.UnprivilegedBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivilegeModule {

    @Provides
    @Singleton
    fun provideRootShellRunner(): RootShellRunner = LibsuShellRunner()

    @Provides
    @Singleton
    fun provideShizukuShellRunner(): ShizukuShellRunner = RikkaShizukuShellRunner()

    @Provides
    @Singleton
    @Named("root")
    fun provideRootBackend(rootShellRunner: RootShellRunner): PrivilegeBackend =
        RootPrivilegeBackend(rootShellRunner)

    @Provides
    @Singleton
    @Named("shizuku")
    fun provideShizukuBackend(shizukuShellRunner: ShizukuShellRunner): PrivilegeBackend =
        ShizukuPrivilegeBackend(shizukuShellRunner)

    @Provides
    @Singleton
    @Named("unprivileged")
    fun provideUnprivilegedBackend(): PrivilegeBackend =
        UnprivilegedBackend()

    @Provides
    @Singleton
    fun providePrivilegeManager(
        rootShellRunner: RootShellRunner,
        shizukuShellRunner: ShizukuShellRunner,
        @Named("root") rootBackend: PrivilegeBackend,
        @Named("shizuku") shizukuBackend: PrivilegeBackend,
        @Named("unprivileged") unprivilegedBackend: PrivilegeBackend
    ): PrivilegeManager = PrivilegeManager(
        rootShellRunner = rootShellRunner,
        shizukuShellRunner = shizukuShellRunner,
        rootBackend = rootBackend,
        shizukuBackend = shizukuBackend,
        unprivilegedBackend = unprivilegedBackend
    )

    @Provides
    fun provideActivePrivilegeBackend(privilegeManager: PrivilegeManager): PrivilegeBackend =
        privilegeManager.activeBackend.value
}
