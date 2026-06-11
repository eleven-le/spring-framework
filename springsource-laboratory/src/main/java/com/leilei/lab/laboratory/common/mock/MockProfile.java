package com.leilei.lab.laboratory.common.mock;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：模拟资源的行为画像（基础延迟 + 抖动 + 慢调用占比 + 故障率），不可变，wither 派生
 * 🔗 业务场景：给 Mock DAO / RPC 配置「像真的一样」的 MySQL / Redis / RPC 延迟与故障，
 *             演示缓存击穿、超时雪崩、事务内慢调用等事故
 */
public final class MockProfile {

    private final long baseLatencyMs;
    private final long jitterMs;
    private final double faultRate;
    private final double slowRate;
    private final long slowLatencyMs;

    private MockProfile(long baseLatencyMs, long jitterMs, double faultRate, double slowRate, long slowLatencyMs) {
        this.baseLatencyMs = baseLatencyMs;
        this.jitterMs = jitterMs;
        this.faultRate = faultRate;
        this.slowRate = slowRate;
        this.slowLatencyMs = slowLatencyMs;
    }

    /** 零延迟零故障：纯内存语义，压测容器本身开销时用 */
    public static MockProfile inMemory() {
        return new MockProfile(0, 0, 0, 0, 0);
    }

    /** 典型 MySQL 单条查询：3ms ± 2ms */
    public static MockProfile mysql() {
        return new MockProfile(3, 2, 0, 0, 0);
    }

    /** 典型 Redis：1ms ± 1ms */
    public static MockProfile redis() {
        return new MockProfile(1, 1, 0, 0, 0);
    }

    /** 典型同机房 RPC：15ms ± 10ms */
    public static MockProfile rpc() {
        return new MockProfile(15, 10, 0, 0, 0);
    }

    public MockProfile withLatency(long baseLatencyMs, long jitterMs) {
        return new MockProfile(baseLatencyMs, jitterMs, faultRate, slowRate, slowLatencyMs);
    }

    /** 故障注入：每次访问以 faultRate 概率抛 {@link MockAccessException} */
    public MockProfile withFaultRate(double faultRate) {
        return new MockProfile(baseLatencyMs, jitterMs, faultRate, slowRate, slowLatencyMs);
    }

    /** 慢调用注入：以 slowRate 概率额外增加 slowLatencyMs（演示长尾 / 超时） */
    public MockProfile withSlow(double slowRate, long slowLatencyMs) {
        return new MockProfile(baseLatencyMs, jitterMs, faultRate, slowRate, slowLatencyMs);
    }

    public long getBaseLatencyMs() {
        return baseLatencyMs;
    }

    public long getJitterMs() {
        return jitterMs;
    }

    public double getFaultRate() {
        return faultRate;
    }

    public double getSlowRate() {
        return slowRate;
    }

    public long getSlowLatencyMs() {
        return slowLatencyMs;
    }

    @Override
    public String toString() {
        return "MockProfile{latency=" + baseLatencyMs + "±" + jitterMs + "ms, faultRate=" + faultRate
                + ", slowRate=" + slowRate + "(+" + slowLatencyMs + "ms)}";
    }
}
