package com.leilei.lab.laboratory.l02.l02_04;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * 📖 知识点：[[L02-04-Environment与属性绑定#2.1 PropertySource 优先级：同 key 多来源谁赢]]（Environment / PropertySource 优先级）
 * 🎯 作用：把「同一个配置 key 由多个来源提供时，到底谁生效」坐实成可运行实验——
 *         {@link ConfigurableEnvironment#getPropertySources()} 是一个**有序链**，
 *         {@link org.springframework.core.env.PropertySourcesPropertyResolver#getProperty} 从头到尾遍历、
 *         **第一个命中的 PropertySource 即返回（first-wins），后面的同 key 值被静默忽略**；
 *         {@code StandardEnvironment} 默认把 {@code systemProperties}（JVM -D 启动参数）排在
 *         {@code systemEnvironment}（OS 环境变量）之前；
 *         {@link MutablePropertySources#addFirst}/{@link MutablePropertySources#addLast}/
 *         {@code addBefore}/{@code addAfter} 用来精确插队，决定覆盖关系。
 * 🔗 业务场景：秒杀限流 QPS、价格保护阈值、缓存 TTL 这类高危阈值，生产上同时存在三个来源——
 *         启动参数（应急覆盖）> 配置中心 Nacos（日常调参）> 本地默认（兜底）。
 *         排错时「为什么我改了 Nacos 没生效」十次有八次是被启动参数或更高优先级源盖了；
 *         真出大促事故要紧急降级，靠 {@code addFirst} 注入一个 hotfix 源把所有来源全盖掉，是最快的止血手段。
 */
public final class L0204_01_PropertySourcePriorityDemo {

	private static final String QPS_KEY = "ratelimit.seckill.qps";
	private static final String TTL_KEY = "cache.price.ttlSeconds";
	private static final String BANNER_KEY = "promo.banner.text";

	private L0204_01_PropertySourcePriorityDemo() {
	}

	public static void main(String[] args) {
		// 模拟「启动参数 -Dratelimit.seckill.qps=8000」：写进 JVM 系统属性，会落在 StandardEnvironment 的 systemProperties 源
		System.setProperty(QPS_KEY, "8000");
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ConfigurableEnvironment env = ctx.getEnvironment();
			MutablePropertySources sources = env.getPropertySources();

			System.out.println("==================== 1. 默认 PropertySource 顺序：systemProperties 优先于 systemEnvironment ====================");
			System.out.println("[PropertySource 顺序] " + sourceNames(sources));
			System.out.println();

			// 配置中心 Nacos：日常调参，放在系统源之后（启动参数能盖过它）
			Map<String, Object> nacos = new LinkedHashMap<>();
			nacos.put(QPS_KEY, 5000);          // 与启动参数同 key → 会被启动参数盖掉
			nacos.put(TTL_KEY, 600);           // 仅 nacos + local 有
			sources.addLast(new MapPropertySource("nacosConfig", nacos));

			// 本地默认：最低优先级兜底
			Map<String, Object> local = new HashMap<>();
			local.put(QPS_KEY, 2000);
			local.put(TTL_KEY, 300);
			local.put(BANNER_KEY, "默认营销文案");
			sources.addLast(new MapPropertySource("localDefault", local));

			System.out.println("==================== 2. 三来源同 key：启动参数(systemProperties) > 配置中心(nacos) > 本地默认 → first-wins ====================");
			System.out.println("[PropertySource 顺序] " + sourceNames(sources));
			printResolved(env, sources, QPS_KEY, "三处都有(8000/5000/2000)");
			printResolved(env, sources, TTL_KEY, "仅 nacos+local(600/300)");
			printResolved(env, sources, BANNER_KEY, "仅 local");
			System.out.println();

			// 大促紧急降级：addFirst 注入 hotfix 源，盖过包括启动参数在内的所有来源
			Map<String, Object> hotfix = new HashMap<>();
			hotfix.put(QPS_KEY, 500);
			sources.addFirst(new MapPropertySource("hotfix", hotfix));

			System.out.println("==================== 3. 紧急降级：addFirst 注入 hotfix 源，盖过最高优先级的启动参数 ====================");
			System.out.println("[PropertySource 顺序] " + sourceNames(sources));
			printResolved(env, sources, QPS_KEY, "已加 hotfix=500");
		}
		finally {
			System.clearProperty(QPS_KEY);
		}
	}

	private static void printResolved(ConfigurableEnvironment env, MutablePropertySources sources, String key, String note) {
		// 统一按 String 取，避免把文案类 value 误转 Integer；数值 key 的 String 形态同样直观
		String value = env.getProperty(key);
		System.out.println("[" + key + "] " + note + " → 命中 = " + value + " (来源 " + winningSource(sources, key) + ")");
	}

	/** 复刻 PropertySourcesPropertyResolver 的 first-wins：返回第一个含该 key 的源名。 */
	private static String winningSource(MutablePropertySources sources, String key) {
		for (PropertySource<?> source : sources) {
			if (source.containsProperty(key)) {
				return source.getName();
			}
		}
		return "<未命中>";
	}

	private static String sourceNames(MutablePropertySources sources) {
		StringBuilder sb = new StringBuilder("[");
		boolean first = true;
		for (PropertySource<?> source : sources) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(source.getName());
			first = false;
		}
		return sb.append("]").toString();
	}

}
