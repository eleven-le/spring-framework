package com.leilei.lab.laboratory.common.bench;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：简易并发压测器：N 线程 barrier 齐射 × 每线程 M 次迭代，统计吞吐与延迟分位数
 * 🔗 业务场景：大促秒杀扣库存、缓存击穿回源风暴等实验的标准压测入口；
 *             task 抛出任何异常计为失败但不中断压测
 */
public final class ConcurrentBench {

    private ConcurrentBench() {
    }

    /**
     * 同步压测：阻塞直到全部线程完成，返回报告。
     *
     * @param name                场景名（线程名前缀 + 报告标题）
     * @param threads             并发线程数
     * @param iterationsPerThread 每线程迭代次数
     * @param task                被压测任务（抛异常计失败）
     */
    public static BenchReport run(String name, int threads, int iterationsPerThread, Runnable task) {
        if (threads <= 0 || iterationsPerThread <= 0) {
            throw new IllegalArgumentException("threads/iterationsPerThread must be positive");
        }
        final AtomicInteger threadSeq = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "bench-" + name + "-" + threadSeq.incrementAndGet());
            }
        });
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong success = new AtomicLong();
        AtomicLong fail = new AtomicLong();
        final long[][] latencies = new long[threads][iterationsPerThread];

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            long begin = System.nanoTime();
                            try {
                                task.run();
                                success.incrementAndGet();
                            }
                            catch (Throwable ex) {
                                fail.incrementAndGet();
                            }
                            latencies[threadIndex][i] = System.nanoTime() - begin;
                        }
                    }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    finally {
                        done.countDown();
                    }
                }
            });
        }

        try {
            ready.await();
            long wallBegin = System.nanoTime();
            start.countDown();
            done.await();
            long wallNanos = System.nanoTime() - wallBegin;
            return summarize(name, threads, iterationsPerThread, latencies,
                    success.get(), fail.get(), wallNanos);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("压测被中断: " + name, ex);
        }
        finally {
            pool.shutdownNow();
        }
    }

    private static BenchReport summarize(String name, int threads, int iterationsPerThread,
            long[][] latencies, long success, long fail, long wallNanos) {
        int total = threads * iterationsPerThread;
        long[] all = new long[total];
        int index = 0;
        for (long[] perThread : latencies) {
            System.arraycopy(perThread, 0, all, index, perThread.length);
            index += perThread.length;
        }
        Arrays.sort(all);
        long sum = 0;
        for (long latency : all) {
            sum += latency;
        }
        long wallMillis = Math.max(1, wallNanos / 1_000_000);
        double opsPerSecond = total * 1_000_000_000.0 / Math.max(1L, wallNanos);
        return new BenchReport(name, threads, total, success, fail, wallMillis, opsPerSecond,
                toMillis((double) sum / total),
                toMillis(percentile(all, 0.50)),
                toMillis(percentile(all, 0.95)),
                toMillis(percentile(all, 0.99)),
                toMillis(all[all.length - 1]));
    }

    private static double percentile(long[] sorted, double quantile) {
        int index = (int) Math.ceil(quantile * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }

    private static double toMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
