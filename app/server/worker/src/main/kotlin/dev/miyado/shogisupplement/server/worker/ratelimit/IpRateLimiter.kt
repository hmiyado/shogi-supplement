package dev.miyado.shogisupplement.server.worker.ratelimit

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface IpRateLimiter {
    /** true = 許可。false = 上限超過（呼び出し側は429を返す）。 */
    fun tryAcquire(ip: String): Boolean
}

/**
 * IPごとの固定窓レート制限（多層防御。主たる防御は128bitのk_auth自体のエントロピー）。
 *
 * Why not 分散カウンタ（Redis等）: Cloud Runの複数インスタンスに跨ると実際の上限は
 * インスタンス数倍に緩むが、総当たり耐性は既にk_authのエントロピーで足りているため、
 * インスタンスをまたぐ厳密な合算まではここでは求めない。
 */
class InMemoryIpRateLimiter(
    private val limit: Int,
    private val windowMs: Long,
    private val clock: Clock = Clock.systemUTC(),
) : IpRateLimiter {

    private data class Window(val start: Instant, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    override fun tryAcquire(ip: String): Boolean {
        val now = clock.instant()
        var allowed = false
        windows.compute(ip) { _, existing ->
            if (existing == null || Duration.between(existing.start, now).toMillis() >= windowMs) {
                allowed = true
                Window(start = now, count = 1)
            } else if (existing.count < limit) {
                allowed = true
                existing.copy(count = existing.count + 1)
            } else {
                allowed = false
                existing
            }
        }
        return allowed
    }
}
