package com.magi.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.magi.app.v6.V6FinalPort
import com.magi.app.v6.copy2D
import com.magi.app.v6.toIntArray2D
import com.magi.app.model.MagiState
import com.magi.app.model.StateParser
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Background optimization (改善仕様書 §6.2). Runs the V6 engine off the UI process's main
 * thread, publishes live progress to [OptimizationRepository], persists the result there, and
 * posts a completion notification. Enqueued as expedited work (with non-expedited fallback).
 */
class OptimizationWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // [C1] kill後にWorkManagerが再起動した場合、同一プロセス参照(request)は失われている。
        // 入力をファイルから復元して続行する（参照が無くても完走する）。
        val req = OptimizationRepository.request ?: loadInputFromFile(ctx) ?: return Result.failure()
        ensureChannel()
        // [C1] 入力をファイルへ退避（現在は参照渡し）。kill後の再起動でここから復元できる。
        runCatching { inputFile(ctx).writeText(StateParser.serialize(req.first, req.second)) }
        OptimizationRepository.setRunning(true)
        // [#4] 前景サービス化: 5分のCPUジョブをOSに止めさせない（FGS不可な環境では通常実行へフォールバック）。
        runCatching { setForeground(getForegroundInfo()) }
        var lastSnapMs = 0L
        return try {
            val res = V6FinalPort.handleOptimize(
                state = req.first,
                schedule = req.second.copy2D(),
                secondsRaw = OptimizationRepository.seconds,
                workers = OptimizationRepository.workers,
                allowImpossible = true,
            ) { phase, report, iters, elapsed ->
                if (report != null) {
                    OptimizationRepository.publishProgress(
                        OptimizationRepository.BgProgress(phase, report.hard, report.soft, report.total, iters, elapsed),
                    )
                    // [#4/C1] 途中最良解を定期スナップショット → kill されても「途中結果から再開」できる。
                    if (elapsed - lastSnapMs > 8_000L) {
                        lastSnapMs = elapsed
                        com.magi.app.v6.V6NativeOptimizer.liveBest?.let { live ->
                            runCatching { snapshotFile(ctx).writeText(StateParser.serialize(req.first, live.toIntArray2D())) }
                        }
                    }
                }
            }
            OptimizationRepository.publishResult(
                OptimizationRepository.BgResult(res.schedule, res.report, res.phase),
            )
            notifyDone(res.report.hard, res.report.total)
            // [C1] 完了結果を耐久保存。UI不在(プロセス再起動でWorkerだけ走った)でも次回起動で反映できる。
            runCatching { resultFile(ctx).writeText(StateParser.serialize(req.first, res.schedule)) }
            runCatching { inputFile(ctx).delete() }
            runCatching { snapshotFile(ctx).delete() }   // [#4] 完了でスナップショット破棄
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notify("最適化に失敗しました", e.message ?: "原因不明")
            Result.failure()
        } finally {
            OptimizationRepository.setRunning(false)
        }
    }

    /** Required for expedited work running as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("勤務表を最適化中")
            .setContentText("バックグラウンドで計算しています…")
            .setOngoing(true)
            .build()
        // minSdk 35: foregroundServiceType is always required.
        return ForegroundInfo(NID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notifyDone(hard: Int, total: Int) {
        val msg = if (hard == 0) "配布できます（必須違反0・合計$total）" else "未解決$hard 件（合計$total）"
        notify("最適化が完了しました", msg)
    }

    private fun notify(title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NID_DONE, n) }
    }

    private fun ensureChannel() {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "勤務表の最適化", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val UNIQUE = "magi_bg_optimize"
        private const val CHANNEL = "magi_optimize"
        private const val NID_PROGRESS = 4101
        private const val NID_DONE = 4102

        // [C1] kill耐性: 入力・完了結果・途中最良解のファイル退避先（filesDir、UIと共有）
        fun inputFile(ctx: Context): File = ctx.filesDir.resolve("magi_bg_input.json")
        fun resultFile(ctx: Context): File = ctx.filesDir.resolve("magi_bg_result.json")
        fun snapshotFile(ctx: Context): File = ctx.filesDir.resolve("magi_bg_best.json")   // [#4] 途中最良解

        fun clearFiles(ctx: Context) {
            runCatching { inputFile(ctx).takeIf { it.exists() }?.delete() }
            runCatching { resultFile(ctx).takeIf { it.exists() }?.delete() }
            runCatching { snapshotFile(ctx).takeIf { it.exists() }?.delete() }
        }

        private fun loadInputFromFile(ctx: Context): Pair<MagiState, Array<IntArray>>? {
            val f = inputFile(ctx)
            if (!f.exists()) return null
            return runCatching {
                val st = StateParser.parse(f.readText())
                st to st.schedule.toIntArray2D()
            }.getOrNull()
        }
    }
}
