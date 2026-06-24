package com.leilei.lab.laboratory.l01.l01_01;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.leilei.lab.laboratory.l01.l01_01.L0101_01_ModuleMapProbe.ModuleCoordinate;

/**
 * 📖 知识点：[[L01-01-模块地图与生态定位#🧨 事故与避坑]]（Spring 模块版本一致性 / 依赖地狱）
 * 🎯 作用：启动期依赖体检——扫描商品查询热路径各模块的实现版本，发现版本漂移（混用多个 Spring 版本）即否决。
 *         模拟 spring-boot-dependencies 用 BOM 锁版本所守的那条线，没有 Boot 托管时的手动防线。
 * 🔗 业务场景：古茗某次因第三方 starter 拖入 spring-web 5.2、与主工程 spring-webmvc 5.3 混用，
 *         上线后偶发 NoSuchMethodError——本 guard 在 refresh 早期跑一遍，把这类故障拦在启动而非线上。
 *
 * <p>判定口径：取热路径上全部非 null 的实现版本去重；多于一个 ⇒ 不一致 ⇒ 抛错。
 * 源码直连（无 manifest）时版本全为 null，guard 给出「无法判定」而非误报通过——诚实优先于绿灯。
 */
public final class L0101_02_StartupDependencyConsistencyGuard {

	private final L0101_01_ModuleMapProbe probe;

	public L0101_02_StartupDependencyConsistencyGuard() {
		this(new L0101_01_ModuleMapProbe());
	}

	public L0101_02_StartupDependencyConsistencyGuard(L0101_01_ModuleMapProbe probe) {
		this.probe = probe;
	}

	/** 体检结论：是否可判定、是否一致、出现的版本集合、人类可读结论。 */
	public static final class Verdict {

		private final boolean determinable;
		private final boolean consistent;
		private final Set<String> distinctVersions;
		private final String message;

		Verdict(boolean determinable, boolean consistent, Set<String> distinctVersions, String message) {
			this.determinable = determinable;
			this.consistent = consistent;
			this.distinctVersions = distinctVersions;
			this.message = message;
		}

		/** manifest 是否暴露了版本（源码直连为 false）。 */
		public boolean isDeterminable() {
			return determinable;
		}

		/** 可判定且全部版本一致才为 true。 */
		public boolean isConsistent() {
			return consistent;
		}

		public Set<String> getDistinctVersions() {
			return distinctVersions;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public String toString() {
			return message;
		}
	}

	/** 只体检不抛错，返回结论供日志/监控上报。 */
	public Verdict inspect() {
		List<ModuleCoordinate> stack = probe.probeProductQueryStack();
		Set<String> versions = new LinkedHashSet<>();
		for (ModuleCoordinate coordinate : stack) {
			if (coordinate.getImplementationVersion() != null) {
				versions.add(coordinate.getImplementationVersion());
			}
		}
		if (versions.isEmpty()) {
			return new Verdict(false, false, versions,
					"⚠️ 依赖体检无法判定：热路径 " + stack.size() + " 个模块均无 manifest 版本（源码直连/exploded class，属预期）");
		}
		if (versions.size() == 1) {
			return new Verdict(true, true, versions,
					"✅ 依赖体检通过：热路径 " + stack.size() + " 个模块版本统一为 " + versions.iterator().next());
		}
		return new Verdict(true, false, versions,
				"❌ 依赖体检失败：热路径出现混用版本 " + versions + "，存在 NoSuchMethodError/NoClassDefFoundError 风险");
	}

	/** 体检不一致即抛错，挂到容器启动早期（如 BeanFactoryPostProcessor）可阻断带病上线。 */
	public Verdict assertConsistentOrThrow() {
		Verdict verdict = inspect();
		if (verdict.isDeterminable() && !verdict.isConsistent()) {
			throw new IllegalStateException(verdict.getMessage());
		}
		return verdict;
	}

}
