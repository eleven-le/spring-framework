package com.leilei.lab.laboratory.l01.l01_01;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.SpringVersion;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 📖 知识点：[[L01-01-模块地图与生态定位#🏭 生产怎么用对]]（Spring 模块依赖地图）
 * 🎯 作用：把「一次商品详情查询穿过的 Spring 层」做成一张活的模块地图——
 *         自上而下取每一层的真实核心抽象，反查其归属模块（jar）与 manifest 实现版本。
 * 🔗 业务场景：古茗产品中心商品查询热路径（Web → 消息转换 → AOP → 事务 → JDBC → 容器 → 装配 → 内核），
 *         排查「这套抽象到底由哪个 spring-xxx 提供、版本几何」时的取证工具。
 *
 * <p>实现要点：{@link Package#getImplementationVersion()} 读的是 jar 的 MANIFEST「Implementation-Version」。
 * 本实验工程用 project 依赖直连源码（exploded class 目录，无 manifest），因此版本会是 {@code null}——
 * 这恰好印证 {@link SpringVersion} 注释里那句「some ClassLoaders do not expose the package metadata」。
 * 真实 jar 部署下该值即 {@code 5.3.39}。
 */
public final class L0101_01_ModuleMapProbe {

	/** 商品查询热路径上某一层的模块坐标：层名 / 模块 / 核心抽象 / 实现版本（可能 null）。 */
	public static final class ModuleCoordinate {

		private final String layer;
		private final String module;
		private final String abstractionFqn;
		private final String implementationVersion;

		ModuleCoordinate(String layer, String module, String abstractionFqn, String implementationVersion) {
			this.layer = layer;
			this.module = module;
			this.abstractionFqn = abstractionFqn;
			this.implementationVersion = implementationVersion;
		}

		public String getLayer() {
			return layer;
		}

		public String getModule() {
			return module;
		}

		public String getAbstractionFqn() {
			return abstractionFqn;
		}

		/** manifest 实现版本；源码直连时为 null。 */
		public String getImplementationVersion() {
			return implementationVersion;
		}

		@Override
		public String toString() {
			return String.format("%-10s│ %-15s│ %-52s│ %s",
					layer, module, abstractionFqn, implementationVersion == null ? "—(源码直连)" : implementationVersion);
		}
	}

	/**
	 * 探测一次商品详情查询自上而下穿过的 8 层及其归属模块。
	 * 顺序即真实请求穿透顺序：Web 入口 → 消息转换 → AOP 织入 → 事务边界 → JDBC 落库 → 容器 → 装配 → 内核。
	 */
	public List<ModuleCoordinate> probeProductQueryStack() {
		List<ModuleCoordinate> stack = new ArrayList<>();
		stack.add(probe("Web", "spring-webmvc", DispatcherServlet.class));
		stack.add(probe("消息转换", "spring-web", HttpMessageConverter.class));
		stack.add(probe("AOP", "spring-aop", ProxyFactory.class));
		stack.add(probe("事务", "spring-tx", PlatformTransactionManager.class));
		stack.add(probe("JDBC", "spring-jdbc", JdbcTemplate.class));
		stack.add(probe("容器", "spring-context", ApplicationContext.class));
		stack.add(probe("装配", "spring-beans", BeanFactory.class));
		stack.add(probe("内核", "spring-core", SpringVersion.class));
		return Collections.unmodifiableList(stack);
	}

	/** 用任一真实类作锚点，反查其包归属版本。 */
	private ModuleCoordinate probe(String layer, String module, Class<?> anchor) {
		Package pkg = anchor.getPackage();
		String version = (pkg != null ? pkg.getImplementationVersion() : null);
		return new ModuleCoordinate(layer, module, anchor.getName(), version);
	}

	/** 渲染成等宽表格，章节文档与启动日志直接贴。 */
	public String renderTable() {
		StringBuilder sb = new StringBuilder();
		sb.append("商品查询热路径 · 模块地图（codebase=")
				.append(SpringVersion.getVersion() == null ? "源码直连" : SpringVersion.getVersion()).append(")\n");
		sb.append(String.format("%-10s│ %-15s│ %-52s│ %s%n", "层", "模块", "核心抽象", "实现版本"));
		for (ModuleCoordinate coordinate : probeProductQueryStack()) {
			sb.append(coordinate).append('\n');
		}
		return sb.toString();
	}

}
