package dev.opencab.feature.roster

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.opencab.core.data.throttle.PrimaryAcquireResult
import dev.opencab.core.data.throttle.ThrottleSession
import dev.opencab.core.model.RosterLoco
import javax.inject.Inject
import javax.inject.Singleton

/** The tiny acquisition boundary the roster surfaces depend on. */
interface LocoAcquirer {
    fun acquire(loco: RosterLoco): PrimaryAcquireResult
}

@Singleton
class SessionLocoAcquirer @Inject constructor(
    private val session: ThrottleSession,
) : LocoAcquirer {
    override fun acquire(loco: RosterLoco): PrimaryAcquireResult = session.acquirePrimary(loco)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RosterModule {
    @Binds
    @Singleton
    abstract fun bindLocoAcquirer(implementation: SessionLocoAcquirer): LocoAcquirer
}
