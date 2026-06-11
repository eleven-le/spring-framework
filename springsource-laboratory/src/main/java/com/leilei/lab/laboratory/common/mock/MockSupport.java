package com.leilei.lab.laboratory.common.mock;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：按 {@link MockProfile} 执行一次「资源访问模拟」：睡延迟、掷慢调用、掷故障
 * 🔗 业务场景：所有 Mock DAO / RPC 的统一入口，让实验代码的耗时曲线接近生产
 */
public final class MockSupport {

    private MockSupport() {
    }

    /**
     * 模拟一次资源访问。
     *
     * @param profile  行为画像
     * @param resource 资源名（如 mysql:inventory），用于异常信息定位
     * @throws MockAccessException 命中故障注入或线程被中断时
     */
    public static void simulate(MockProfile profile, String resource) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long latency = profile.getBaseLatencyMs();
        if (profile.getJitterMs() > 0) {
            latency += random.nextLong(profile.getJitterMs() + 1);
        }
        if (profile.getSlowRate() > 0 && random.nextDouble() < profile.getSlowRate()) {
            latency += profile.getSlowLatencyMs();
        }
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MockAccessException(resource + " 访问被中断", ex);
            }
        }
        if (profile.getFaultRate() > 0 && random.nextDouble() < profile.getFaultRate()) {
            throw new MockAccessException(resource + " 故障注入命中（faultRate=" + profile.getFaultRate() + "）");
        }
    }
}
