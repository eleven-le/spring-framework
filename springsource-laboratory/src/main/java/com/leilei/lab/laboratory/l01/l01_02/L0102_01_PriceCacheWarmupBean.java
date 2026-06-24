package com.leilei.lab.laboratory.l01.l01_02;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leilei.lab.laboratory.common.dao.MockPriceRuleDao;
import com.leilei.lab.laboratory.common.domain.PriceRule;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 📖 知识点：[[L01-02-容器心智模型与核心抽象#2. 🏭 生产怎么用对]]（BeanFactory vs ApplicationContext / Aware 回调时机）
 * 🎯 作用：价格规则缓存预热 Bean——把「容器启动时把热点 SKU 的价格规则灌进本地缓存」做成一个
 *         实现了 {@link InitializingBean} / {@link BeanNameAware} / {@link ApplicationContextAware}
 *         的单例。它是 L0102_02 对照实验的「探针」：同一个 BeanDefinition 丢进裸 BeanFactory 与
 *         ApplicationContext，预热在「什么时候跑」「Aware 回调谁来兜」会出现决定性差异。
 * 🔗 业务场景：古茗 C 端商品详情页价格展示——价格多维匹配规则若不在启动期预热，首屏请求会撞上冷缓存
 *         回源 MySQL，叠加大促瞬时高并发即缓存击穿。预热应当在「容器就绪、对外放流量之前」完成。
 */
public class L0102_01_PriceCacheWarmupBean implements InitializingBean, BeanNameAware, ApplicationContextAware {

	/** 本地价格规则缓存：skuId -> 候选规则。预热即把热点 SKU 的规则提前灌进来。 */
	private final Map<Long, List<PriceRule>> priceCache = new HashMap<>();

	// —— 用于对照实验观测的状态位（生产代码不会保留这些观测字段，这里是教学探针）——

	/** afterPropertiesSet 是否已执行（= 这个单例是否已被实例化并初始化）。 */
	private volatile boolean warmedUp = false;

	/** 预热加载的规则条数，证明预热真的访问了 DAO 而非空跑。 */
	private volatile int warmedRuleCount = 0;

	/** 预热耗时（含 MockPriceRuleDao 注入的 MySQL 级延迟），凸显「为什么不能拖到首个请求才做」。 */
	private volatile long warmupCostMillis = -1;

	/** BeanNameAware 是否被回调——裸 BeanFactory 也会回调它（初始化流程内建）。 */
	private volatile String injectedBeanName = null;

	/** ApplicationContextAware 是否被回调——只有 ApplicationContext 会（靠 ApplicationContextAwareProcessor 这个 BPP）。 */
	private volatile boolean applicationContextInjected = false;

	@Override
	public void setBeanName(String name) {
		this.injectedBeanName = name;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContextInjected = (applicationContext != null);
	}

	/**
	 * 单例初始化阶段执行预热：访问价格规则 DAO（带 MySQL 级延迟），把候选规则灌进本地缓存。
	 * <p>关键认知：ApplicationContext.refresh() 会在 finishBeanFactoryInitialization 阶段
	 * 对所有非 lazy 单例执行 preInstantiateSingletons，于是本方法在「放流量之前」就跑完；
	 * 裸 DefaultListableBeanFactory 不做这一步，本方法被推迟到第一次 getBean 时才触发。
	 */
	@Override
	public void afterPropertiesSet() {
		long begin = System.currentTimeMillis();
		MockDataSet dataSet = MockDataFactory.seed(9, 3);
		MockPriceRuleDao priceRuleDao = new MockPriceRuleDao(dataSet);
		int count = 0;
		for (Long skuId : dataSet.getSkus().keySet()) {
			List<PriceRule> rules = priceRuleDao.listBySkuId(skuId);
			this.priceCache.put(skuId, rules);
			count += rules.size();
		}
		this.warmedRuleCount = count;
		this.warmupCostMillis = System.currentTimeMillis() - begin;
		this.warmedUp = true;
	}

	public boolean isWarmedUp() {
		return this.warmedUp;
	}

	public int getWarmedRuleCount() {
		return this.warmedRuleCount;
	}

	public long getWarmupCostMillis() {
		return this.warmupCostMillis;
	}

	public String getInjectedBeanName() {
		return this.injectedBeanName;
	}

	public boolean isApplicationContextInjected() {
		return this.applicationContextInjected;
	}

	public int cachedSkuCount() {
		return this.priceCache.size();
	}

}
