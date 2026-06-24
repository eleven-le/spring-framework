package com.leilei.lab.laboratory.l02.l02_09;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * 📖 知识点：[[L02-09-容器基础设施-Resource-SpEL-MessageSource-父子容器#2.1 Resource / ResourceLoader：统一资源寻址]]
 * 🎯 作用：演示 Spring 资源抽象的三件事——
 *         ① {@code ResourceLoader#getResource("classpath:...")} 把「内置兜底价规则」当统一句柄读取（Resource 只是句柄，不存在也不抛异常，靠 {@code exists()} 判定）；
 *         ② {@code ResourcePatternResolver#getResources("classpath*:.../region/*.properties")} 用 {@code classpath*:} 通配<b>跨 jar 聚合</b>多个区域配置模块贡献的同名资源；
 *         ③ {@code "file:"} 前缀直读运营「价格热更目录」里的盘上文件，与 classpath 资源同一套 API。
 * 🔗 业务场景：古茗 C 端定价的资源寻址——全国基础价随构建打包（classpath）、各大区覆盖价由区域模块贡献（classpath* 聚合）、
 *         运营临时改价落在盘上热更目录（file:），三类来源用一套 Resource 抽象统一加载，切换协议只改前缀。
 */
public final class L0209_01_ResourceLoaderDemo {

	private static final String PKG = "com/leilei/lab/laboratory/l02/l02_09";

	private L0209_01_ResourceLoaderDemo() {
	}

	public static void main(String[] args) throws IOException {
		// ApplicationContext 本身就是 ResourceLoader（继承 DefaultResourceLoader）+ ResourcePatternResolver
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
			ctx.refresh();

			System.out.println("==================== 1. classpath: 把内置兜底价当统一句柄读取 ====================");
			Resource baseline = ctx.getResource("classpath:" + PKG + "/baseline-price-rules.properties");
			System.out.println("baseline 实现类型 = " + baseline.getClass().getSimpleName()
					+ "，exists = " + baseline.exists());
			Properties baseProps = load(baseline);
			System.out.println("全国基础价 sku.1001.price = " + baseProps.getProperty("sku.1001.price"));

			System.out.println();
			System.out.println("==================== 2. Resource 是「句柄」：不存在也不抛异常，靠 exists() 判定 ====================");
			Resource missing = ctx.getResource("classpath:" + PKG + "/not-exist.properties");
			System.out.println("getResource 拿到句柄（未真正读取）= " + (missing != null)
					+ "，missing.exists() = " + missing.exists());

			System.out.println();
			System.out.println("==================== 3. classpath*: 通配跨模块聚合多个区域价覆盖 ====================");
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(ctx);
			Resource[] regionFiles = resolver.getResources(
					ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + PKG + "/region/*.properties");
			System.out.println("classpath*: 枚举到的区域配置文件数 = " + regionFiles.length);
			List<String> regions = new ArrayList<>();
			for (Resource r : regionFiles) {
				Properties p = load(r);
				regions.add(p.getProperty("region") + "(sku.1001=" + p.getProperty("sku.1001.price") + ")");
			}
			regions.sort(String::compareTo);
			System.out.println("各大区覆盖价 = " + regions);
			System.out.println("说明：单个 classpath: 只命中第一份；classpath*: 才会扫描全部类路径根做聚合（fat-jar 多模块同名资源全靠它）。");

			System.out.println();
			System.out.println("==================== 4. file: 前缀直读运营价格热更目录 ====================");
			File hotfix = File.createTempFile("price-hotfix-", ".properties");
			hotfix.deleteOnExit();
			Files.write(hotfix.toPath(),
					"sku.1001.price=9.90\n# 运营 9.9 秒杀临时价\n".getBytes(StandardCharsets.UTF_8));
			Resource hotfixRes = ctx.getResource("file:" + hotfix.getAbsolutePath());
			System.out.println("hotfix 实现类型 = " + hotfixRes.getClass().getSimpleName()
					+ "，exists = " + hotfixRes.exists());
			System.out.println("运营热更秒杀价 sku.1001.price = " + load(hotfixRes).getProperty("sku.1001.price"));
			System.out.println("结论：classpath / classpath* / file 三类来源同一套 Resource API，切换协议只改前缀。");
		}
	}

	private static Properties load(Resource resource) throws IOException {
		Properties props = new Properties();
		try (InputStream in = resource.getInputStream()) {
			props.load(in);
		}
		return props;
	}

}
