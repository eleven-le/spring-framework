package org.springframework.lab.processortour;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 报表服务 — 演示纯 BPP 路线（@Scheduled）。
 *
 * <p>@Scheduled 由 ScheduledAnnotationBeanPostProcessor 处理，
 * 它只在 postProcessAfterInitialization 扫描方法并注册到 TaskScheduler，
 * 不创建代理 — bean 保持原始类型。
 */
@Service
public class ReportService {

	/**
	 * 每 60 秒打印一次（仅演示注册，实际在 ctx.close() 前最多触发一次）。
	 */
	@Scheduled(fixedRate = 60_000)
	public void generateDailyReport() {
		System.out.println("    [ReportService#generateDailyReport] 生成报表... (thread="
				+ Thread.currentThread().getName() + ")");
	}
}
