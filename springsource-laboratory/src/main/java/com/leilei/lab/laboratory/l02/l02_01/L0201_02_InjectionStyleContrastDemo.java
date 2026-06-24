package com.leilei.lab.laboratory.l02.l02_01;

import java.lang.reflect.Field;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;
import com.leilei.lab.laboratory.common.mock.MockProfile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * 📖 知识点：[[L02-01-注入方式选型-构造器Setter字段#2. 🏭 生产怎么用对]]（构造器 / Setter / 字段三种注入选型与可测性）
 * 🎯 作用：把同一个依赖（{@link MockPriceRuleDao}）分别用构造器 / Setter / 字段三种风格注入，
 *         在「<b>能否脱离容器实例化</b>」「<b>漏依赖何时暴露</b>」「<b>依赖能否被偷改</b>」三个维度上做对照实验。
 *         先在容器外裸 {@code new} 看三者的本质差异，再丢进真实 {@link AnnotationConfigApplicationContext}
 *         证明「在容器里三者都能注进去」——差异不在『能不能用』，而在『可测性与健壮性』。
 * 🔗 业务场景：产品中心里到处是「服务依赖 DAO/RPC」的装配。选错注入风格不会立刻报错，
 *         但会在写单测时寸步难行、在漏配依赖时把 NPE 推迟到大促首个请求——这就是选型的代价。
 */
public final class L0201_02_InjectionStyleContrastDemo {

	private L0201_02_InjectionStyleContrastDemo() {
	}

	public static void main(String[] args) {
		System.out.println("==================== 一、容器外裸 new：三种风格的本质差异 ====================");
		contrastOutsideContainer();
		System.out.println();
		System.out.println("==================== 二、丢进 ApplicationContext：三者都能注进去 ====================");
		contrastInsideContainer();
	}

	/** 容器外手搓对象——这正是「写单元测试」时的场景：能不能不靠 Spring 就把依赖喂进去？ */
	private static void contrastOutsideContainer() {
		MockDataSet dataSet = MockDataFactory.seed(9, 3);
		MockPriceRuleDao dao = new MockPriceRuleDao(dataSet, MockProfile.inMemory());

		// 构造器：依赖是构造入参，一行 new 即装配完成；漏传依赖在「诞生那一刻」就 fail-fast。
		ConstructorStyle ctor = new ConstructorStyle(dao);
		System.out.println("[构造器] new ConstructorStyle(dao) 直接可用，dao 已就位 = " + ctor.daoReady());
		try {
			new ConstructorStyle(null);
		}
		catch (IllegalArgumentException ex) {
			System.out.println("[构造器] 漏依赖 fail-fast（实例化即炸，不会潜伏到运行期）：" + ex.getMessage());
		}

		// Setter：能空参 new，但忘了 setter 就埋雷——依赖 null 直到被调用才 NPE。
		SetterStyle setter = new SetterStyle();
		System.out.println("[Setter] new 之后未调 setter，dao 就位 = " + setter.daoReady() + "（忘了注入也不报错，埋雷）");
		setter.setPriceRuleDao(dao);
		System.out.println("[Setter] 手动 setPriceRuleDao 后，dao 就位 = " + setter.daoReady());

		// 字段：私有字段无任何注入入口——容器外只能靠反射硬塞，这就是字段注入「最难测」的根因。
		FieldStyle field = new FieldStyle();
		System.out.println("[字段] new 之后字段为 private 且无 setter，dao 就位 = " + field.daoReady()
				+ "（没有容器/反射根本注不进）");
		injectByReflection(field, dao);
		System.out.println("[字段] 被迫用 ReflectionUtils 反射强塞后，dao 就位 = " + field.daoReady()
				+ "（写单测要么起容器要么反射，仪式感拉满）");
	}

	/** 丢进真实容器：三种风格 Spring 都兜得住——所以争论焦点从来不是『能不能用』，而是可测性。 */
	private static void contrastInsideContainer() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(InjectionLabConfig.class)) {
			System.out.println("[构造器] 容器注入后 dao 就位 = " + ctx.getBean(ConstructorStyle.class).daoReady());
			System.out.println("[Setter] 容器注入后 dao 就位 = " + ctx.getBean(SetterStyle.class).daoReady());
			System.out.println("[字段]   容器注入后 dao 就位 = " + ctx.getBean(FieldStyle.class).daoReady()
					+ "（容器靠反射注入，对字段注入照样生效——『能用』掩盖了『难测』）");
		}
	}

	private static void injectByReflection(FieldStyle target, MockPriceRuleDao dao) {
		Field f = ReflectionUtils.findField(FieldStyle.class, "priceRuleDao");
		Assert.notNull(f, "字段 priceRuleDao 必须存在");
		ReflectionUtils.makeAccessible(f);
		ReflectionUtils.setField(f, target, dao);
	}

	/** 构造器注入：final 依赖、单构造器隐式装配、漏依赖 fail-fast、可脱离容器 new。 */
	static final class ConstructorStyle {

		private final MockPriceRuleDao priceRuleDao;

		ConstructorStyle(MockPriceRuleDao priceRuleDao) {
			Assert.notNull(priceRuleDao, "priceRuleDao 不能为空");
			this.priceRuleDao = priceRuleDao;
		}

		boolean daoReady() {
			return this.priceRuleDao != null;
		}
	}

	/** Setter 注入：依赖可空参 new，但非 final、可被重复改写，漏 setter 时静默埋雷。 */
	static final class SetterStyle {

		private MockPriceRuleDao priceRuleDao;

		@Autowired
		void setPriceRuleDao(MockPriceRuleDao priceRuleDao) {
			this.priceRuleDao = priceRuleDao;
		}

		boolean daoReady() {
			return this.priceRuleDao != null;
		}
	}

	/** 字段注入：最省代码，但容器外无注入入口，单测必须起容器或反射，且依赖被隐藏。 */
	static final class FieldStyle {

		@Autowired
		private MockPriceRuleDao priceRuleDao;

		boolean daoReady() {
			return this.priceRuleDao != null;
		}
	}

	/** 实验容器配置：把确定性沙盘 + Mock DAO + 三种风格的 Bean 一起装配起来。 */
	@Configuration
	static class InjectionLabConfig {

		@Bean
		MockDataSet mockDataSet() {
			return MockDataFactory.seed(9, 3);
		}

		@Bean
		MockPriceRuleDao priceRuleDao(MockDataSet mockDataSet) {
			return new MockPriceRuleDao(mockDataSet, MockProfile.inMemory());
		}

		@Bean
		ConstructorStyle constructorStyle(MockPriceRuleDao priceRuleDao) {
			return new ConstructorStyle(priceRuleDao);
		}

		@Bean
		SetterStyle setterStyle() {
			return new SetterStyle();
		}

		@Bean
		FieldStyle fieldStyle() {
			return new FieldStyle();
		}
	}

}
