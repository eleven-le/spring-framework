/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.NativeDetector;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Spring 注解驱动的"一号大将"——解析 @Configuration 的核心引擎，图纸大爆发的总指挥！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.ConfigurationClassPostProcessor}</li>
 * <li><b>中文名</b>：配置类后置处理器 —— 注解驱动世界的"总参谋长"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 本类是这个包里<b>最核心、最复杂、也最重要</b>的类——它把 @Configuration、@Bean、@Import、
 * @ComponentScan、@PropertySource 等注解全部"翻译"成 BeanDefinition，是注解驱动的大脑。
 * 一句话：<b>没有这个类，所有注解配置都是摆设！</b>）</li>
 * <li><b>身份</b>：{@code BeanDefinitionRegistryPostProcessor}（BDRPP）+ {@code PriorityOrdered}</li>
 * </ul>
 *
 * <h3>💡 为什么它是"一号大将"？——注解驱动的所有魔法都从这里开始！</h3>
 * <p>回忆一下 Spring 容器启动的关键时间线：</p>
 * <pre>
 * refresh()
 *   ├── invokeBeanFactoryPostProcessors()   ← 在这一步，本类被激活！
 *   │     ├── 1. 先调所有 BDRPP（按 PriorityOrdered → Ordered → 普通 分批执行）
 *   │     │     └── ConfigurationClassPostProcessor ← 作为唯一的 PriorityOrdered BDRPP，第一个执行！
 *   │     │           ├── postProcessBeanDefinitionRegistry()  ← 第一阶段：解析配置类，注册新 BD
 *   │     │           │     └── ConfigurationClassParser.parse()  → 递归解析 @Import/@Bean/@ComponentScan
 *   │     │           └── postProcessBeanFactory()             ← 第二阶段：增强 @Configuration 类（CGLIB 代理）
 *   │     └── 2. 再调所有 BFPP
 *   ├── registerBeanPostProcessors()
 *   └── finishBeanFactoryInitialization()
 * </pre>
 *
 * <h3>🧬 两大阶段——BDRPP 的"一鱼两吃"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>阶段</th><th>方法</th><th>核心动作</th><th>产出</th></tr>
 * <tr><td><b>第一阶段</b></td><td>postProcessBeanDefinitionRegistry()</td>
 *     <td>① 从现有 BD 中找出 @Configuration 候选类<br/>
 *         ② 用 ConfigurationClassParser 递归解析（@Import → @Bean → @ComponentScan → @PropertySource）<br/>
 *         ③ 用 ConfigurationClassBeanDefinitionReader 将解析结果注册为 BD</td>
 *     <td>容器中的 BD 数量从个位数暴增到几十甚至几百个——<b>"图纸大爆发"！</b></td></tr>
 * <tr><td><b>第二阶段</b></td><td>postProcessBeanFactory()</td>
 *     <td>① 对 Full @Configuration 类生成 CGLIB 增强子类<br/>
 *         ② 替换 BD 中的 beanClass 为增强类<br/>
 *         ③ 注册 ImportAwareBeanPostProcessor</td>
 *     <td>@Bean 方法的"单例语义"得到保证（多次调用 @Bean 方法返回同一实例）</td></tr>
 * </table>
 *
 * <h3>🧬 为什么是 PriorityOrdered？——"必须第一个执行"的铁律！</h3>
 * <p>本类实现了 PriorityOrdered（最高优先级排序接口），确保它在所有 BDRPP 中<b>最先执行</b>。<br/>
 * 原因很简单：其他 BFPP/BPP 自身的 BD 可能就是通过 @Bean 或 @Import 声明的——
 * 如果本类不先执行把这些 BD 注册出来，后续的处理器连"出生"的机会都没有！</p>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>BDRPP 的"图纸扩展"能力</b><br/>
 * 普通 BFPP 只能修改已有 BD 的属性。而 BDRPP 可以<b>新增 BD</b>——这是 Spring 扩展性的核心机制。
 * @ComponentScan 扫描出来的 BD、@Import 导入的 BD、@Bean 方法声明的 BD，都是通过本类新增的。<br/>
 * <b>业务借鉴</b>：当你的框架需要"插件式扩展"时，可以设计类似 BDRPP 的机制——
 * 让插件在启动时向注册表新增配置项，而不是只能修改已有配置。</li>
 *
 * <li><b>Full vs Lite 配置类的区分</b><br/>
 * 有 @Configuration 注解的是 Full 配置类（会被 CGLIB 增强，@Bean 方法调用保证单例）；
 * 只有 @Component/@Import 等的是 Lite 配置类（不增强，@Bean 方法调用返回新实例）。<br/>
 * 本类在第二阶段会检查并增强 Full 配置类——这就是为什么 @Configuration(proxyBeanMethods=false)
 * 能跳过 CGLIB 增强，提升启动速度。</li>
 *
 * <li><b>"循环解析"的处理——配置类可能导入新的配置类</b><br/>
 * @Import 可能导入新的 @Configuration 类，新类上又有 @ComponentScan 扫描出更多类……
 * 本类通过"解析→注册→检查是否有新候选→再解析"的循环，确保所有配置类都被递归处理完毕。</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanDefinitionRegistryPostProcessor（BDRPP）
 * └── extends BeanFactoryPostProcessor（BFPP）
 *
 * ConfigurationClassPostProcessor  ← 👈 你在这里！
 *   ├── implements BeanDefinitionRegistryPostProcessor  （第一阶段：解析配置类、注册 BD）
 *   ├── implements PriorityOrdered                       （保证最先执行）
 *   ├── implements ResourceLoaderAware                   （获取资源加载器）
 *   ├── implements ApplicationStartupAware               （启动性能追踪）
 *   ├── implements BeanClassLoaderAware                   （获取类加载器）
 *   └── implements EnvironmentAware                       （获取环境配置）
 * </pre>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ConfigurationClassPostProcessor 的核心价值：<b>把 @Configuration/@Bean/@Import/@ComponentScan
 * 等注解全部"翻译"成 BeanDefinition，实现从"注解声明"到"容器注册"的核心飞跃</b>。<br/>
 * 它是 Spring 注解驱动的"大脑"——没有它，所有注解配置都无法生效。
 * 它在 refresh 的 invokeBeanFactoryPostProcessors 阶段第一个执行，
 * 完成"图纸大爆发"后，后续的 BPP 注册、单例实例化才能正常推进。</p>
 *
 * <hr/>
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other {@link BeanFactoryPostProcessor}.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean @Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@code BeanFactoryPostProcessor} executes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
		PriorityOrdered, ResourceLoaderAware, ApplicationStartupAware, BeanClassLoaderAware, EnvironmentAware {

	/**
	 * A {@code BeanNameGenerator} using fully qualified class names as default bean names.
	 * <p>This default for configuration-level import purposes may be overridden through
	 * {@link #setBeanNameGenerator}. Note that the default for component scanning purposes
	 * is a plain {@link AnnotationBeanNameGenerator#INSTANCE}, unless overridden through
	 * {@link #setBeanNameGenerator} with a unified user-level bean name generator.
	 * @since 5.2
	 * @see #setBeanNameGenerator
	 */
	public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR =
			FullyQualifiedAnnotationBeanNameGenerator.INSTANCE;

	private static final String IMPORT_REGISTRY_BEAN_NAME =
			ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	private boolean localBeanNameGeneratorSet = false;

	/* Using short class names as default bean names by default. */
	private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	/* Using fully qualified class names as default bean names by default. */
	private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * Set the {@link BeanNameGenerator} to be used when triggering component scanning
	 * from {@link Configuration} classes and when registering {@link Import}'ed
	 * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
	 * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
	 * and a variant thereof for imported configuration classes (using unique fully-qualified
	 * class names instead of standard component overriding).
	 * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
	 * <p>This setter is typically only appropriate when configuring the post-processor as a
	 * standalone bean definition in XML, e.g. not using the dedicated {@code AnnotationConfig*}
	 * application contexts or the {@code <context:annotation-config>} element. Any bean name
	 * generator specified against the application context will take precedence over any set here.
	 * @since 3.1.1
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		this.applicationStartup = applicationStartup;
	}

	/**
	 * <br>
	 * <h3>架构深度解析：首席设计师的打卡与发威 👑</h3>
	 * <p>
	 * 这就是咱们千呼万唤始出来的<b>【元老 1】（ConfigurationClassPostProcessor，首席图纸设计师）</b>
	 * 正式开始干活的入口！
	 * </p>
	 * <p>
	 * 这段入口代码虽然不长，但每一行都透着严谨。让我们马上来拆解这位“首席设计师”
	 * 上班打卡时的标准动作与防线：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Derive further bean definitions from the configuration classes in the registry.
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		/*
		 * 🛡️ 第一道防线：物理级别的“指纹打卡” (获取 Registry ID)
		 * ---------------------------------------------------------
		 * [原理解析] 为什么不用 registry.hashCode()，非要用 System.identityHashCode？
		 * 因为普通的 hashCode() 方法是可以被子类重写 (Override) 的，有被伪造或碰撞的风险。
		 * 而 System.identityHashCode 是直接根据对象在 JVM 内存里的物理地址算出来的值，绝对无法伪造！
		 *
		 * [车间大白话] 首席设计师说：“我认大管家只认他本人的物理肉体，绝不认什么工牌，
		 * 坚决防止有人拿个假冒的 Registry 过来骗我干活！”
		 */
		int registryId = System.identityHashCode(registry);
		/*
		 * 🛡️ 第二/三道防线：极其严格的“防重与顺序校验”
		 * ---------------------------------------------------------
		 * [背景知识] 还记得咱们上一轮说的吗？ 【1号大将】是个“双面人”，既实现了 BeanDefinitionRegistryPostProcessor (新增图纸)，又继承了 BeanFactoryPostProcessor (审核图纸)。
		 *
		 * [防重检查] registriesPostProcessed.contains
		 * “我今天是不是已经给这个工厂画过图纸了？” 如果画过了，立刻报错 (快速失败)。
		 *
		 * [顺序检查] factoriesPostProcessed.contains (高能细节)
		 * “我今天是不是已经给这个工厂审核过图纸了？” 如果是，那更要报错！因为在 Spring 的铁律中，
		 * “画图纸 (Registry)”必须发生在“审核图纸 (Factory)”之前。顺序反了说明系统调度出了大 Bug。
		 *
		 * [打卡记录] this.registriesPostProcessed.add(registryId)
		 * 校验通过，把这个工厂的物理 ID 记在自己的小本本上，证明“我今天已经在这干过活了”。
		 */
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		this.registriesPostProcessed.add(registryId);

		/*
		 * 🚀 终极大招：开启全自动包扫描！(核心业务流)
		 * ---------------------------------------------------------
		 * [这是什么] 整个注解驱动体系中最、最、最核心的一行代码！前面的校验都只是前戏，
		 * 这一行才是真正的“狂风暴雨”。
		 *
		 * [它要干嘛] 点进这个方法，你会看到首席设计师真正开始发威。它会把你传进来的
		 * AppConfig.class 当作种子：
		 * ① 看到 @ComponentScan ➡️ 顺藤摸瓜扒开包路径，把所有 @Component、@Service 全变成图纸。
		 * ② 看到 @Bean ➡️ 把对应的方法解析成图纸。
		 * ③ 看到 @Import ➡️ 把你导入的其他配置类也拉进来一起解析。
		 */
		processConfigBeanDefinitions(registry);
	}

	/**
	 * <h3>架构巅峰：图纸审核员与 CGLIB 终极魔法 🪄</h3>
	 * <p>
	 * 前面我们花了大量时间研究的，是它作为“首席图纸设计师”(BeanDefinitionRegistryPostProcessor) 去扫包、画图纸的过程。
	 * 而现在这段代码，是它换上了第二套制服——作为<b>“图纸审核员”(BeanFactoryPostProcessor)</b> 来执行的最终审核逻辑！
	 * </p>
	 * <p>
	 * 这段代码虽然短，但它藏着 Spring 面试中一道绝杀题的底层答案：
	 * <b>“为什么标了 @Configuration 的类里面，@Bean 方法互相调用也能保证是单例？”</b>
	 * 让我们继续按照“一行一行绝不遗漏”的最高标准，拆解这位审核员的收尾工作：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Prepare the Configuration classes for servicing bean requests at runtime
	 * by replacing them with CGLIB-enhanced subclasses.
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/*
		🛡️ 工序一：物理防伪打卡与防重拦截
		原理解析：跟之前画图纸时的逻辑一模一样。
		车间大白话：审核员拿大管家（beanFactory）的物理肉身地址算了个 ID。先查查打卡记录：“我今天是不是已经给这个工厂审核过图纸了？”如果是，立马报错（防重复执行）。如果没审核过，就把工厂 ID 记在小本本上，准备开干。
		 */
		/*
		 * 🛡️ 工序一：物理防伪打卡与防重拦截
		 * ---------------------------------------------------------
		 * [原理解析] 审核员拿大管家(beanFactory)的物理肉身地址算了个 ID。先查查打卡记录：
		 * “我今天是不是已经给这个工厂审核过图纸了？”如果是，立马报错（防重复执行）。
		 * 如果没审核过，就把工厂 ID 记在小本本上，准备开干。
		 */
		int factoryId = System.identityHashCode(beanFactory);
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		/*
		 * 🪂 工序二：极其严谨的“兜底机制” (Fallback)
		 * ---------------------------------------------------------
		 * [原理解析] 正常情况下，画图纸(postProcessBeanDefinitionRegistry)一定会先于审核图纸(postProcessBeanFactory)执行。所以这个 if 里面的代码一般是不会走的。
		 * [车间大白话] 审核员摸了摸口袋：“诶？我之前怎么没在这个工厂的设计师打卡记录里签过到？
		 * 难道是工厂启动的方式太奇葩，跳过了画图纸的步骤？”
		 * [兜底动作] Spring 极其谨慎。如果发现真没画过图纸，现场补票，先调大魔王方法
		 * processConfigBeanDefinitions 把图纸画完，再继续往下审核。
		 */
		if (!this.registriesPostProcessed.contains(factoryId)) {
			// BeanDefinitionRegistryPostProcessor hook apparently not supported...
			// Simply call processConfigurationClasses lazily at this point then.
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}

		/*
		 * 🪄 工序三：终极魔法 —— CGLIB 动态代理增强 (全段最核心 🔥)
		 * ---------------------------------------------------------
		 * [原理解析] 这就是 @Configuration (Full 模式) 和 @Component (Lite 模式) 的根本区别！
		 * [魔法施展] 审核员动用 enhanceConfigurationClasses 魔法，利用 CGLIB 技术，
		 * 给所有标了 @Configuration 的图纸套上了一层“智能代理外壳”。
		 * [代理作用] 以后当 beanA() 试图内部调用 beanB() 时，这层 CGLIB 外壳会瞬间拦截调用，
		 * 并去单例池（缓存）里查：“如果有现成的 BeanB，直接返回；如果没有，才真正去执行
		 * beanB() 的逻辑。” 这就完美捍卫了 Spring 的单例铁律！
		 */
		enhanceConfigurationClasses(beanFactory);


		/*
		 * 🕵️‍♂️ 工序四：招募并安插“专属质检员”
		 * ---------------------------------------------------------
		 * [原理解析] 审核员在离开车间前，往流水线上安插了一个 ImportAwareBeanPostProcessor 质检员。
		 * 如果你的 Bean 实现了 ImportAware 接口，当 Bean 实例化时，质检员就会凑上去，
		 * 把“是谁（哪个配置类）通过 @Import 把你引进来的”线索偷偷塞给你的 Bean。
		 * （这是开发 Spring Boot Starter 时感知环境属性的高级内功）
		 */
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));

/*
到这里，【1号大将】（ConfigurationClassPostProcessor）的全部使命，已经彻底、圆满地结束了！ 鞠躬下台！
总结它的一生：
作为设计师：疯狂扫包、解析 @Bean、@Import，把它们变成图纸。（之前啃的 processConfigBeanDefinitions）
作为审核员：利用 CGLIB 魔法给 @Configuration 图纸穿上代理铠甲，保证单例语义不出错。（就是你现在看的这个方法）
至此，refresh() 方法第 5 步 invokeBeanFactoryPostProcessors 正式宣告完结！大管家的仓库里，装满了完美无瑕的图纸。
*/

/*
@Configuration (Full 模式) 和 @Component (Lite 模式)

审核员给 @Configuration 图纸套上了一层 CGLIB 动态代理的铠甲。这其实就是在开启所谓的 Full 模式。
很多开发者写了好几年 Spring，都以为 @Configuration 和 @Component 是一回事，反正都能把 Bean 扫进容器。但实际上，它们在底层的运作机制有着天壤之别！
为了让你一次性彻底搞懂，我们直接上真实场景，看看这两种模式在“工厂车间”里到底有什么区别：

🛡️ Full 模式 (全副武装的“全量模式”)
触发条件：类头上标了 @Configuration（且没有显式设置 proxyBeanMethods = false）。
底层真身：经过 CGLIB 动态代理增强的代理子类。
车间大白话：在这个模式下，这张图纸变成了一张**“智能图纸”。图纸上的每一个 @Bean 方法，都被安装了“拦截监控探头”**。

【案发现场】 假设我们有这样一段代码：
@Configuration
public class AppConfig {
    @Bean
    public UserService userService() {
        System.out.println("创建 UserService...");
        return new UserService();
    }

    @Bean
    public OrderService orderService() {
        // 🚨 注意这里！直接调用了上面的方法！
        UserService user1 = userService();
        return new OrderService(user1);
    }
}

在 Full 模式下，会发生什么？
Spring 启动，先调用 userService()，打印“创建 UserService...”，把 UserService 存入大管家的单例池仓库。
接着 Spring 调用 orderService()。
核心魔法来了！ 代码运行到 userService() 时，CGLIB 的监控探头瞬间拦截了这个调用！
探头去仓库里查：“哎？UserService 之前是不是已经造出来过一个了？”
仓库回答：“有的！”
探头立刻阻断真实的函数执行，直接把仓库里那个已经存在的 UserService 返回给了你！
最终结果：控制台只打印了一次“创建 UserService...”。orderService 里注入的那个 User，和容器里单例的 User 是同一个绝对实例。单例铁律被完美捍卫！

🪶 Lite 模式 (轻装上阵的“轻量模式”)
触发条件：类头上标了 @Component（或者 @Service、@Controller），亦或者写了 @Configuration(proxyBeanMethods = false)。
底层真身：就是个原生的普通 Java 类 (Plain Java Object)，没有被代理过。
车间大白话：这张图纸就是一张**“死图纸”**。图纸上的方法就是普普通通的 Java 方法，没有任何监控探头。
【案发现场】 我们把上面的代码换成 @Component：
@Component // 🚨 换成了 Component
public class AppConfig {
    @Bean
    public UserService userService() {
        System.out.println("创建 UserService...");
        return new UserService();
    }

    @Bean
    public OrderService orderService() {
        // 🚨 再次调用
        UserService user1 = userService();
        return new OrderService(user1);
    }
}

在 Lite 模式下，会发生什么？
Spring 启动，调用 userService()，打印“创建 UserService...”，存入仓库。（这是容器管理的那个 Bean）。
接着 Spring 调用 orderService()。
运行到 userService() 时，因为没有 CGLIB 拦截探头，这就是一次极其普通的 Java 方法调用！
代码老老实实地走进了 userService() 的方法体，再次执行了 new UserService()！
最终结果：控制台打印了两次“创建 UserService...”。orderService 里注入的那个 User，是你刚刚手动 new 出来的一个全新对象，它根本不受 Spring 容器的管理（没有走过依赖注入、AOP 等后续流程）。单例铁律被无情打破！

📊 厂长终极对决表
对比项,@Configuration (Full 模式),@Component (Lite 模式)
底层实现,CGLIB 代理的子类,普通的 Java 原生类
@Bean 方法互调,安全！ 会被拦截并去单例池取，保证是同一个 Bean,危险！ 就是普通的方法调用，会 new 出新的多例对象
启动速度与内存,稍慢。 需要在启动时动态生成 CGLIB 字节码，占用更多内存。,极快！ 零代理开销，启动飞快，省内存。
适用场景,你的 @Bean 方法之间有复杂的互相调用、依赖注入。,你的 @Bean 都是独立存在的，互相不调用。


💡 编码助手的高级实战提示：
其实在最新的 Spring Boot（2.2 版本以后）里，官方极其推崇 Lite 模式！
如果你去翻看 Spring Boot 源码，你会发现里面几乎所有的自动装配类都加上了 @Configuration(proxyBeanMethods = false)。
为什么？因为在庞大的微服务架构下，成百上千个配置类如果全用 CGLIB 去生成代理，启动会非常慢，而且极其吃内存。只要我们写代码时不去直接调用别的方法（而是通过方法参数把依赖传进来），完全可以抛弃 CGLIB，用 Lite 模式换取极速的启动体验！
		 */

/*
要搞懂这个 ImportAwareBeanPostProcessor 质检员到底是干嘛的，我们必须先弄明白 “什么是 ImportAware”，以及 “为什么我们需要它”。

❓ 痛点场景：被“推荐”入厂的员工，怎么知道主子的喜好？
假设你现在正在开发一个牛逼的第三方组件（比如你自己写的一个 Redis Starter 组件）。你希望别人引入你的组件时，用一个优雅的注解来开启，并能传递参数，比如：
// 这是业务线小王写的代码，他想用你的组件
@Configuration
@EnableMyRedis(host = "192.168.1.100", port = 6379) // 贴上你给的开启注解
public class AppConfig {
}

而在你写的 @EnableMyRedis 注解底层，你通过 @Import 把你真正的配置类 MyRedisConfig 导入了进去：
// 这是你写的注解定义
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MyRedisConfig.class) // 核心：通过 Import 导入真正的配置类！
public @interface EnableMyRedis {
    String host();
    int port();
}

🚨 痛点来了！
当你的 MyRedisConfig 被 Spring 实例化时，它只知道自己被创建了。但是，它该怎么去获取小王写在 AppConfig 头上的那个 192.168.1.100 呢？
它根本不知道自己是被谁 @Import 进来的！这就好比一个员工被推荐进了工厂，但他连推荐人是谁、推荐信上写了什么期望薪资都不知道！
*
💡 破局方案：ImportAware 接口登场
为了解决这个问题，Spring 提供了 ImportAware 接口。只要你的 MyRedisConfig 实现了这个接口，奇迹就会发生：
*
// 这是你写的真正干活的配置类
@Configuration
public class MyRedisConfig implements ImportAware {

    private String host;
    private int port;

    // 💥 核心魔法方法！Spring 会自动调用这个方法！
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        // importMetadata 里面装的，就是把这个类导入进来的那个“推荐人”（也就是 AppConfig）的所有信息！

        // 1. 去推荐人身上找 @EnableMyRedis 注解的属性
        Map<String, Object> attributes = importMetadata.getAnnotationAttributes(EnableMyRedis.class.getName());

        // 2. 完美拿到小王配置的参数！
        this.host = (String) attributes.get("host");
        this.port = (Integer) attributes.get("port");

        System.out.println("我感知到了！主子让我连接的 Redis 是：" + host + ":" + port);
    }

    @Bean
    public RedisClient redisClient() {
        return new RedisClient(this.host, this.port);
    }
}

👷‍♂️ 闭环解析：ImportAwareBeanPostProcessor 到底在干嘛？
现在我们回到你提问的那行代码：
beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));

车间大白话还原全过程：
审核员的远见：【1号大将】（也就是之前那个方法）在解析图纸时，其实已经把“是谁 @Import 了谁”的关系网记在了本子上。但他马上就要下班了，而现在真正的 Bean（比如 MyRedisConfig）还没开始造呢。
招募专属质检员：为了确保将来造对象的时候，有人能把这个情报传递下去。【1号大将】在下班前，特意招募了 ImportAwareBeanPostProcessor 这个质检员，让他站在流水线旁边等着。
质检员发威（在未来的第 11 步）：等工厂真正开始 new MyRedisConfig() 的时候，这个质检员就会凑上去看一眼：“哎哟？你实现了 ImportAware 接口啊？来来来，我有情报交给你。把你导入进来的那个大哥是 AppConfig，这是他头上的注解信息（AnnotationMetadata），你赶紧拿着（调用 setImportMetadata）！”
一句话总结：ImportAwareBeanPostProcessor 就是一个**“情报快递员”**。它负责在组件被创建时，把“推荐人（引入者）”的注解属性，精准地投递给实现了 ImportAware 的组件，从而实现了 Spring Boot 中极其优雅的 @EnableXXX 参数透传机制！
*/
	}

	/**
	 * <br>
	 * <h3>架构巅峰：注解驱动的“核反应堆” ☢️</h3>
	 * <p>
	 * 欢迎来到 Spring 注解驱动真正的<b>“核反应堆”</b>！<br>
	 * 这段 {@code processConfigBeanDefinitions} 方法，就是大名鼎鼎的【元老 1】
	 * （首席图纸设计师）的核心办公桌。你在开发中用到的 {@code @ComponentScan} 自动扫包、
	 * {@code @Import} 导入组件、{@code @Bean} 声明方法，全部都是在这段代码里被解析并转化成底层图纸的！
	 * </p>
	 * <p>
	 * 这段长代码本质上是一个<b>“查找配置类 ➡️ 解析配置类 ➡️ 注册新图纸 ➡️ 检查是否产生新配置类（循环）”</b>
	 * 的完整生产线。它是 Spring 中最能体现“递归”与“模型化”思维的杰作。
	 * 让我们戴上安全帽，把这个“核反应堆”分成 4 个关键工序来拆解：
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 *
	 * Build and validate a configuration model based on the registry of
	 * {@link Configuration} classes.
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		/*
		 * 🕵️‍♂️ 步骤一：全厂海选“配置类候选人”
		 * ---------------------------------------------------------
		 * [场景带入] 工厂刚启动时，仓库 (registry) 里只有少数几个内置组件和你的主配置类
		 * (如 AppConfig)。第一步要做的，就是把这些“总设计图”找出来。
		 *
		 * [原理解析] checkConfigurationClassCandidate 这个方法非常关键。它会甄别图纸：
		 * ① 全注解配置，Full 模式：打上了 @Configuration 注解的类。
		 * ② 轻量级配置，Lite 模式：打上了 @Component、@ComponentScan、@Import、@Bean 的类。
		 * 只要符合条件，统统抓进 configCandidates 列表里准备解析。
		 */
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		String[] candidateNames = registry.getBeanDefinitionNames();//1.1 获取当前仓库里所有图纸的名字

		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			//1.2 如果之前已经处理过了，就跳过（防重复解析）
			if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			//1.3 【核心甄别】检查它是不是一个配置类！
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				//1.4 如果是，打包成 Holder，加入“候选人名单”
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}

		//1.5 如果一个配置类都没找到，直接下班
		// Return immediately if no @Configuration classes were found
		if (configCandidates.isEmpty()) {
			return;
		}

		/*
		 * ⚖️ 步骤二：排队与组装“超级解析机”
		 * ---------------------------------------------------------
		 * [场景带入] 候选人找好了，接下来要讲规矩按顺序解析，并且要把解析的工具准备好。
		 *
		 * [原理解析] 这里实例化了 ConfigurationClassParser。这台“超级机器”接下来将负责
		 * 读取 .class 字节码，读懂你写的 @ComponentScan 等注解，它是 Spring 注解解析的真正引擎。
		 */

		// 2.1 根据 @Order 注解排个序，决定谁先被解析
		// Sort by previously determined @Order value, if applicable
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		// (检测自定义的 BeanName 生成策略...)
		// Detect any custom bean name generation strategy supplied through the enclosing application context
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(
						AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}

		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// 2.2 组装一台超级解析机：ConfigurationClassParser
		// Parse each @Configuration class
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		/*
		 * 🌪️ 步骤三：核爆核心 —— 神奇的 do-while 裂变循环
		 * ---------------------------------------------------------
		 * [最高机密] 这是整个方法最精妙的设计！配置类的解析不是一次性结束的，而是一个可能
		 * 引发“无限裂变”的循环。
		 *
		 * [动作 A：深度解析] 机器读取 AppConfig，看到 @ComponentScan("com.demo")，
		 * 冲进目录扫出了一个 DatabaseConfig.class (里面带了 @Bean)。
		 * [动作 B：转化为图纸] 将 DatabaseConfig 里的 @Bean 方法正式转化为 BeanDefinition 并入库。
		 * [动作 C：裂变侦测] 对比仓库发现图纸变多了！检查刚才扫出来的 DatabaseConfig，
		 * 发现它也是个配置类！把它加入 candidates，再循环一次！直到扫出来的所有类都不再是
		 * 配置类为止。这就是你可以随意嵌套配置类的底层原因。
		 */

		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
			StartupStep processConfig = this.applicationStartup.start("spring.context.config-classes.parse");
			// 💥 3.1 动作 A 开动解析机，深度阅读图纸 (解析 @ComponentScan, @Import, @Bean 等)
			parser.parse(candidates);
			parser.validate();

			// 3.2 把解析出来的模型提取出来，剔除掉已经解析过的
			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			configClasses.removeAll(alreadyParsed);

			// 💥 3.3 动作 B 根据解析结果，真正去创建图纸并入库！
			// Read the model and create bean definitions based on its content
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
			//3.4 此时，@Bean 等方法才真正变成了 BeanDefinition 塞进了仓库！
			this.reader.loadBeanDefinitions(configClasses);
			alreadyParsed.addAll(configClasses); // 记录已解析的类
			processConfig.tag("classCount", () -> String.valueOf(configClasses.size())).end();

			// 准备进入裂变检查，清空当前批次的候选人
			candidates.clear();

			// 💥 3.5 动作 C 裂变侦测！
			// 如果现在的仓库图纸总数 > 解析前的图纸总数，说明刚才的解析（扫描）产生了新的图纸！
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				Set<String> alreadyParsedClasses = new HashSet<>();
				// 3.6 记录已解析名称的代码
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				// 3.7 遍历所有新增加的图纸
				for (String candidateName : newCandidateNames) {
					if (!oldCandidateNames.contains(candidateName)) {
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						//3.8 检查这些新图纸中，有没有新的配置类？
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							//3.9 如果有，把它加到 candidates 里！准备进入下一次 do-while 循环！
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames; // 3.10 更新对比基准
			}
		}
		while (!candidates.isEmpty()); // 3.11 只要发现了新的配置类，就继续循环解析！

		/*
		 * 🧹 步骤四：清理与收尾
		 * ---------------------------------------------------------
		 * 既然图纸已经裂变、解析完毕，剩下的就是打扫战场了。
		 */

		// 4.1 把 ImportRegistry 作为一个单例 Bean 注册进容器，供将来解析 ImportAware 接口时使用
		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		// 4.2 清除工厂里缓存的类元数据（因为解析工作已经做完，留着只会白占内存）
		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
	 * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
	 * Candidate status is determined by BeanDefinition attribute metadata.
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		StartupStep enhanceConfigClasses = this.applicationStartup.start("spring.context.config-classes.enhance");
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			AnnotationMetadata annotationMetadata = null;
			MethodMetadata methodMetadata = null;
			if (beanDef instanceof AnnotatedBeanDefinition) {
				AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDef;
				annotationMetadata = annotatedBeanDefinition.getMetadata();
				methodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
			}
			if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
				// Configuration class (full or lite) or a configuration-derived @Bean method
				// -> eagerly resolve bean class at this point, unless it's a 'lite' configuration
				// or component class without @Bean methods.
				AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
				if (!abd.hasBeanClass()) {
					boolean liteConfigurationCandidateWithoutBeanMethods =
							(ConfigurationClassUtils.CONFIGURATION_CLASS_LITE.equals(configClassAttr) &&
								annotationMetadata != null && !ConfigurationClassUtils.hasBeanMethods(annotationMetadata));
					if (!liteConfigurationCandidateWithoutBeanMethods) {
						try {
							abd.resolveBeanClass(this.beanClassLoader);
						}
						catch (Throwable ex) {
							throw new IllegalStateException(
									"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
						}
					}
				}
			}
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				}
				else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		if (configBeanDefs.isEmpty() || NativeDetector.inNativeImage()) {
			// nothing to enhance -> return immediately
			enhanceConfigClasses.end();
			return;
		}

		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// If a @Configuration class gets proxied, always proxy the target class
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			// Set enhanced subclass of the user-specified bean class
			Class<?> configClass = beanDef.getBeanClass();
			Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
			if (configClass != enhancedClass) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
							"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
				}
				beanDef.setBeanClass(enhancedClass);
			}
		}
		enhanceConfigClasses.tag("classCount", () -> String.valueOf(configBeanDefs.keySet().size())).end();
	}


	private static class ImportAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessProperties method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			if (bean instanceof ImportAware) {
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
				if (importingClass != null) {
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
