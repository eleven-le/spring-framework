package com.leilei.lab.laboratory.common.bench;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：一次并发压测的结果快照（吞吐 + 延迟分布），prettyPrint 输出人类可读报告
 * 🔗 业务场景：秒杀扣库存 / 缓存击穿等实验的量化口径，章节文档贴报告说话
 */
public class BenchReport {

    private final String name;
    private final int threads;
    private final long totalOps;
    private final long successOps;
    private final long failOps;
    private final long wallMillis;
    private final double opsPerSecond;
    private final double avgMillis;
    private final double p50Millis;
    private final double p95Millis;
    private final double p99Millis;
    private final double maxMillis;

    public BenchReport(String name, int threads, long totalOps, long successOps, long failOps,
            long wallMillis, double opsPerSecond, double avgMillis,
            double p50Millis, double p95Millis, double p99Millis, double maxMillis) {
        this.name = name;
        this.threads = threads;
        this.totalOps = totalOps;
        this.successOps = successOps;
        this.failOps = failOps;
        this.wallMillis = wallMillis;
        this.opsPerSecond = opsPerSecond;
        this.avgMillis = avgMillis;
        this.p50Millis = p50Millis;
        this.p95Millis = p95Millis;
        this.p99Millis = p99Millis;
        this.maxMillis = maxMillis;
    }

    public String prettyPrint() {
        return String.format(
                "┌─ 压测报告: %s%n"
                        + "│ 线程=%d  总请求=%d  成功=%d  失败=%d%n"
                        + "│ 总耗时=%dms  吞吐=%.0f ops/s%n"
                        + "│ 延迟(ms): avg=%.2f  p50=%.2f  p95=%.2f  p99=%.2f  max=%.2f%n"
                        + "└─",
                name, threads, totalOps, successOps, failOps,
                wallMillis, opsPerSecond, avgMillis, p50Millis, p95Millis, p99Millis, maxMillis);
    }

    public String getName() {
        return name;
    }

    public int getThreads() {
        return threads;
    }

    public long getTotalOps() {
        return totalOps;
    }

    public long getSuccessOps() {
        return successOps;
    }

    public long getFailOps() {
        return failOps;
    }

    public long getWallMillis() {
        return wallMillis;
    }

    public double getOpsPerSecond() {
        return opsPerSecond;
    }

    public double getAvgMillis() {
        return avgMillis;
    }

    public double getP50Millis() {
        return p50Millis;
    }

    public double getP95Millis() {
        return p95Millis;
    }

    public double getP99Millis() {
        return p99Millis;
    }

    public double getMaxMillis() {
        return maxMillis;
    }

    @Override
    public String toString() {
        return prettyPrint();
    }
}
