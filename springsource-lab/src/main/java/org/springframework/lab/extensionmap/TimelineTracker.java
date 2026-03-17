package org.springframework.lab.extensionmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局时间线记录器 —— 记录每个扩展点的触发顺序与阶段归属
 * <p>
 * 三阶段标记: 定义 / 实例 / 运行 / 销毁
 */
public class TimelineTracker {

	private static final String LINE = String.join("", Collections.nCopies(105, "="));
	private static final String DASH = String.join("", Collections.nCopies(100, "-"));
	private static final List<String[]> EVENTS = new ArrayList<>();
	private static final AtomicInteger SEQ = new AtomicInteger(0);

	public static void record(String phase, String point, String detail) {
		int n = SEQ.incrementAndGet();
		EVENTS.add(new String[]{String.valueOf(n), phase, point, detail});
		System.out.printf("  [%02d] [%s] %-50s -> %s%n", n, phase, point, detail);
	}

	public static void separator(String label) {
		System.out.println("\n  --- " + label + " ---");
	}

	public static void printTimeline() {
		System.out.println();
		System.out.println(LINE);
		System.out.println("  Spring IoC Extension Points Timeline");
		System.out.println(LINE);

		String lastPhase = "";
		for (String[] e : EVENTS) {
			String phase = e[1];
			if (!phase.equals(lastPhase)) {
				if (!lastPhase.isEmpty()) {
					System.out.println("  " + DASH);
				}
				lastPhase = phase;
			}
			System.out.printf("  [%2s] [%s] %-50s -> %s%n", e[0], e[1], e[2], e[3]);
		}

		System.out.println(LINE);
		System.out.println("  Total: " + EVENTS.size() + " extension point events");
		System.out.println(LINE);
	}

	public static void reset() {
		EVENTS.clear();
		SEQ.set(0);
	}
}
