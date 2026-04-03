/*
 * Copyright 2002-2023 the original author or authors.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>ConfigurationClassPostProcessor 的"解析引擎"——递归拆解配置类的核心递归器！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.annotation.ConfigurationClassParser}</li>
 * <li><b>中文名</b>：配置类解析器 —— 递归解析 @Configuration 的"拆弹专家"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 annotation 包（注意！annotation 包 = 注解驱动编程模型的大本营！
 * 本类是 ConfigurationClassPostProcessor 的"内核引擎"——CCPP 负责调度，本类负责<b>真正的解析逻辑</b>。
 * 一句话：<b>CCPP 是指挥官，本类是冲在前线的突击队长！</b>）</li>
 * <li><b>类性质</b>：包级可见（非 public），是框架内部的核心实现类</li>
 * </ul>
 *
 * <h3>💡 为什么需要单独的 Parser？——"解析"和"注册"必须分离！</h3>
 * <p>配置类的处理分为两个阶段：</p>
 * <ol>
 * <li><b>解析阶段（本类负责）</b>：递归扫描配置类上的所有注解（@ComponentScan、@Import、@Bean、@PropertySource、
 * @ImportResource），构建出 {@link ConfigurationClass} 模型对象集合</li>
 * <li><b>注册阶段（ConfigurationClassBeanDefinitionReader 负责）</b>：把解析出的模型对象转换为 BeanDefinition 并注册到容器</li>
 * </ol>
 * <p>这种分离的好处：解析逻辑可以独立测试和演化，不会和 BD 注册逻辑纠缠在一起。</p>
 *
 * <h3>🧬 递归解析的主线——doProcessConfigurationClass() 的 8 步拆解</h3>
 * <pre>
 * parse(configCandidates)                                    ← 入口：遍历所有候选配置类
 *   └── processConfigurationClass(configClass)               ← 单个配置类处理（含 @Conditional 检查）
 *         └── doProcessConfigurationClass(sourceClass)       ← 核心递归方法，8 步拆解：
 *               ├── ① 处理 @Component 的内部类（递归解析成员类）
 *               ├── ② 处理 @PropertySource（加载外部属性文件到 Environment）
 *               ├── ③ 处理 @ComponentScan（立即触发扫描！扫到的类如果是配置类，递归解析）
 *               ├── ④ 处理 @Import（三种类型分流：ImportSelector / ImportBeanDefinitionRegistrar / 普通类）
 *               │     ├── ImportSelector → 调 selectImports() → 递归处理返回的类名
 *               │     ├── DeferredImportSelector → 延迟到所有配置类解析完毕后再处理（Spring Boot 的核心！）
 *               │     ├── ImportBeanDefinitionRegistrar → 记录下来，延迟到注册阶段调用
 *               │     └── 普通类 → 当作配置类递归解析
 *               ├── ⑤ 处理 @ImportResource（记录 XML 资源路径，延迟到注册阶段加载）
 *               ├── ⑥ 处理 @Bean 方法（收集所有 @Bean 方法，封装为 BeanMethod 对象）
 *               ├── ⑦ 处理接口默认方法上的 @Bean（Java 8+ 特性）
 *               └── ⑧ 处理父类（如果有父类且不是 java.* 开头，递归回到步骤 ①）
 * </pre>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>ASM 读取代替反射——不加载类就能解析注解</b><br/>
 * 本类使用 ASM 字节码读取技术（通过 MetadataReader）解析 .class 文件中的注解信息，
 * <b>不需要把类加载到 JVM</b>。这避免了类加载的副作用（静态代码块执行、ClassNotFoundException 等），
 * 大幅提升了解析性能和安全性。<br/>
 * <b>业务借鉴</b>：当你需要扫描大量类做元数据分析时（如 API 文档生成、注解统计），
 * 考虑用 ASM/Javassist 等字节码工具代替反射，避免不必要的类加载。</li>
 *
 * <li><b>DeferredImportSelector——"延迟处理"的精妙设计</b><br/>
 * 普通 ImportSelector 立即执行，但 DeferredImportSelector 会被收集起来，
 * 等<b>所有配置类都解析完毕</b>后才执行。<br/>
 * Spring Boot 的自动配置（@EnableAutoConfiguration → AutoConfigurationImportSelector）
 * 就是 DeferredImportSelector——它必须等用户自定义的 Bean 都注册完，
 * 才能判断哪些自动配置该生效（@ConditionalOnMissingBean 依赖此时序）。<br/>
 * <b>业务借鉴</b>：当你的插件系统需要"先加载用户配置，再决定哪些默认配置生效"时，
 * 可以借鉴这种"延迟处理"机制。</li>
 *
 * <li><b>@ComponentScan 的特殊性——"立即执行"而非延迟</b><br/>
 * 在 8 步中，只有 @ComponentScan 是<b>立即触发扫描并注册 BD</b>的（步骤 ③）。
 * 因为扫描出来的类可能本身就是配置类，需要立即递归解析。
 * 而 @Bean/@ImportResource 等都是"记录下来，延迟到注册阶段处理"。</li>
 * </ol>
 *
 * <h3>🧬 与 CCPP 的协作关系</h3>
 * <pre>
 * ConfigurationClassPostProcessor（指挥官）
 *   ├── 持有 ConfigurationClassParser（突击队长） ← 👈 你在这里！
 *   │     └── 负责解析：配置类 → ConfigurationClass 模型
 *   └── 持有 ConfigurationClassBeanDefinitionReader（后勤官）
 *         └── 负责注册：ConfigurationClass 模型 → BeanDefinition → Registry
 * </pre>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>ConfigurationClassParser 的核心价值：<b>递归解析配置类上的所有注解
 * （@ComponentScan/@Import/@Bean/@PropertySource/@ImportResource），
 * 构建出完整的 ConfigurationClass 模型</b>。<br/>
 * 它是 Spring 注解驱动的"解析引擎"——用 ASM 高效读取注解、用递归处理配置类嵌套、
 * 用 DeferredImportSelector 支持延迟处理。掌握了 doProcessConfigurationClass 的 8 步，
 * 就理解了 Spring 如何把一个 @Configuration 类"展开"成一棵完整的 Bean 定义树。</p>
 *
 * <hr/>
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	private final ImportStack importStack = new ImportStack();

	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * <br>
	 * <h3>架构巅峰：智能分发调度台与 Spring Boot 的心脏 🎛️</h3>
	 * <p>
	 * 如果说 {@code ConfigurationClassParser} 是一台极其复杂的超大型机器，那么这段代码，
	 * 就是这台机器的<b>“智能分发流水线（调度台）”</b>。
	 * </p>
	 * <p>
	 * 它本身不干具体的脏活，核心职责是：识别进来的图纸到底是什么“材质”，然后分发给下面对应
	 * 的专用处理车间，并在最后呼叫一位“神秘大咖”出场。
	 * </p>
	 */
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		/*
		 * 🎯 步骤一：履带启动，逐个甄别 (for 循环)
		 * ---------------------------------------------------------
		 * [车间大白话] 上一道工序 (海选) 把那些贴着 @Configuration、@Component 的图纸都装在
		 * configCandidates 这个筐里送过来了。现在，分发台的机械臂把筐里的图纸一张张拿出来，准备深度解析。
		 */
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			/*
			 * 🔀 步骤二：智能材质识别 (核心 if-else 分发逻辑)
			 * ---------------------------------------------------------
			 * [原理解析] Spring 的 BeanDefinition (图纸) 有很多实现类，来源不同，长得也不一样。
			 * 这里就是根据图纸的“材质”，调用底层真正干活的重载 parse() 方法。
			 */
			try {
				/*
				 * [材质 1：纯注解图纸] AnnotatedBeanDefinition (最常见！)
				 * 如果你是用 AnnotationConfigApplicationContext 纯注解启动，传入的 AppConfig.class 或者被包扫描扫出来的类，绝大多数都是 AnnotatedBeanDefinition 这种材质。
				 * 👉 好处：此时提取出它的元数据 (Metadata) 去解析，还不需要把类真正加载进  JVM 内存，避免了类加载过早引发的各种玄学 Bug，极其轻量！
				 */
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				/*
				 * [材质 2：已经加载了 Class 对象的传统图纸] AbstractBeanDefinition
				 * 如果图纸是硬编码 new 出来的，或者通过老旧 XML 转换来的，且它的 Class 对象
				 * 已经存在了内存中，就直接把 Class 对象传给底层的 parse。
				 */
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				/*
				 * [材质 3：极其骨感的字符串图纸]
				 * 如果连 Class 对象都没有，只知道一个类名字符串 (如 "com.demo.AppConfig")，
				 * 那就传字符串到底层，底层车间会用反射去获取。
				 */
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			/*
			 * 🛡️ 步骤三：严密的异常捕获 (Fail-Fast 机制)
			 * ---------------------------------------------------------
			 * [车间大白话] 如果在深度解析某张图纸时发生致命错误（如类找不到、注解自相矛盾），
			 * 立刻拉响防空警报抛出异常，停止整个工厂的启动，绝不带着残缺的图纸强行开工。
			 */
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

		/*
		 * 👑 终极 Boss 登场：处理延迟导入 (Spring Boot 自动装配的基石！)
		 * ---------------------------------------------------------
		 * [原理解析] 当上面的 for 循环结束，所有的用户自定义配置类都解析完之后，偷偷执行了这行极其伟大的代码。
		 * deferred 的意思是“延迟的”。在解析配置类时，如果遇到实现了 DeferredImportSelector 接口的类，
		 * Spring 不会立刻解析，而是暂存起来。等所有正常的业务配置类解析完毕后，最后集中处理它们。
		 *
		 *  [高频面试点] 为什么这是 Spring Boot 的心脏？
		 * Spring Boot 的 @EnableAutoConfiguration 底层注册的正是 DeferredImportSelector 的实现类！<br>
		 * 为什么要延迟？因为 Spring Boot 讲究“用户自定义优先”。它必须先把上面 for 循环里你写的代码
		 * 全解析完，看看你有没有自己配置 DataSource 或 RedisTemplate。看完之后，最后再执行这行代码
		 * (引入默认的自动装配)。如果发现你已经配了，它就不加载默认的了。这就是 @ConditionalOnMissingBean
		 * 能够完美生效的底层物理前提！
		 */
		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * <h3>架构巅峰：图纸安检与档案室查重 🗄️</h3>
	 * <p>
	 * 这个方法的核心使命只有一个：<b>“把一张候选的图纸，经过严格的安检、查重、深层扫描后，
	 * 确认为一张合格的正式配置图纸。”</b>
	 * </p>
	 * <p>
	 * 让我们把这两段代码切分成 5 个极其严密的工序，逐段对应讲解：
	 * </p>
	 */
	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		/*
		 * 📦 工序一：前台接待与“档案袋”封装
		 * ---------------------------------------------------------
		 * [原理解析] 这是进入车间前的小动作。Spring 不喜欢零散地传递参数，所以它把类的元数据
		 * (里面包含了这个类身上的所有注解信息) 和类的名字，统一塞进了一个叫 ConfigurationClass 的对象里。
		 *
		 * [车间大白话] 你可以把它当成一个标准的“档案袋”。把原材料和名字装好，然后送进主力车间
		 * (processConfigurationClass)，并附带一个默认的排除过滤器。
		 * 后续所有的查重、解析、盖章，都是针对这个档案袋来进行的。
		 */
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}


	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		/*
		 * 🛑 工序二：残酷的安检门 —— @Conditional 拦截
		 * ---------------------------------------------------------
		 * [原理解析] 写在类上的 @Conditional（如 @ConditionalOnClass、@Profile）就是在这里生效的！
		 *
		 * [车间大白话] 安检员拿着档案袋，掏出扫描仪 (conditionEvaluator) 对准它。如果图纸上写着
		 * “只有当系统里有 Redis 驱动时才能解析我”，而当前系统没有，安检员直接把档案袋扔进碎纸机 (return)。
		 * 🚨 这是 Spring Boot 能够实现“按需加载、极致提速”的第一道绝对防线！
		 */
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		/*
		 * 🗄️ 工序三：极其复杂的“档案室查重与冲突解决” (全段最烧脑)
		 * ---------------------------------------------------------
		 * [背景提要] 如果安检通过了，接下来要去系统的档案柜里查查，这张图纸以前是不是已经来过了？
		 * 因为配置类可以互相 @Import，导致同一个类可能会被系统发现多次。Spring 制定了严谨的“主权优先级”：
		 */
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		if (existingClass != null) {// 发现档案柜里已经有这个类了！
			//如果真的重复了，Spring 制定了非常严谨的**“主权优先级”**规则：

			/*
			 * 🤝 情况 A：你这次是被别人“推荐 (Import)”进来的
			 *
			 * [原理解析] 一个类可以被多个配置类同时 @Import。mergeImportedBy 的作用是把这些
			 * “推荐人”都记录下来。以防未来某个推荐人因 @Conditional 被干掉后，Spring 还能知道有谁保着它。
			 * [核心逻辑] 只要你这次是被推荐进来的，不管旧档案是推荐的还是亲自登门的，都忽略本次解析 (return)。
			 */
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					existingClass.mergeImportedBy(configClass); // 合并“推荐人”名单
				}
				// 忽略新的 imported 配置类；现有的图纸优先级更高。
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				return;
			}
			else {
				/*
				 * 👑 情况 B：你是“亲自登门 (显式注册/包扫描)”进来的
				 * [车间大白话] 你这次是光明正大通过 @ComponentScan 扫出来的，或者是写在 context.register() 里的，
				 * 而柜子里的那份是以前别人 @Import 进来的。这叫“正主驾到”！
				 *
				 * [核心逻辑] 显式注册的优先级永远高于被动导入的！管理员立刻把旧档案从柜子里撕掉 (remove)，
				 * 准备用你现在这份全新的档案去覆盖它！
				 */
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		/*
		 * 🧬 工序四：追根溯源的“血统扫描机” (核磁共振仪)
		 * ---------------------------------------------------------
		 * [原理解析] 冲突解决完毕，开始真正的全身检查。这里又是一个神奇的 do-while 循环！
		 * [灵魂拷问] 为什么要循环？
		 * 假设 MyConfig 继承了 BaseConfig (里面有 @Bean 方法)。
		 * ① 第一次扫描 MyConfig，底层方法会返回它的父类 BaseConfig (不为 null，循环继续)。
		 * ② 第二次扫描父类 BaseConfig，把你写在父类里的 @Bean 也全部解析出来。
		 * ③ 扫完后，再往上是 Object (返回 null，循环结束)。
		 * 👉 结论：这保证了不管注解写在当前类，还是所有祖宗身上，统统都会被解析，一个都跑不掉！
		 */
		// Recursively process the configuration class and its superclass hierarchy.
		SourceClass sourceClass = asSourceClass(configClass, filter);// 把档案袋转换为 SourceClass（方便进行类层级的向上遍历）
		do {
			// 💥 真正的核心干活机器！开始读取 @PropertySource、@ComponentScan、@Bean 等等
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		/*
		 * 🏷️ 工序五：盖章入库，档案落锁
		 * ---------------------------------------------------------
		 * [车间大白话] 经历完安检、查重，以及连同祖宗十八代的深层扫描后，这张配置图纸终于成了“正果”。
		 * 盖上“解析完毕”的钢印，放进档案柜 (configurationClasses)，防止以后再被重复解析。
		 */
		this.configurationClasses.put(configClass, configClass);

		/*
		 * 🔮 [下一步的高能预警]
		 * 这个车间最核心的活儿，全交给了那台在 do-while 循环里的机器 —— doProcessConfigurationClass。
		 * 这是整个 Spring 框架中，所有注解解析的“终极大魔王”！
		 * 在这台机器内部，是一排排整齐的代码，分别处理：
		 * @PropertySource (加载外部 properties 文件)
		 * @ComponentScan (真正去包里扫描业务类的底层逻辑)
		 * @Import (引入其他组件)
		 * @Bean (提取你写的方法)
		 */
	}

	/**
	 * <br>
	 * <h3>架构巅峰：终极解析机器的“八道严格工序” 🏭</h3>
	 * <p>
	 * 这段代码定义了 Spring 解析一个配置类时<b>绝对不可更改的 8 道工序顺序</b>。
	 * 这里的“顺序即正义”：必须先加载环境变量，再去扫包，再去处理导入... 弄懂了这套履带的运转逻辑，
	 * 以后遇到任何 Spring 注解不生效、覆盖冲突的问题，你都能瞬间从底层原理上秒杀它！
	 * </p>
	 *
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

		/*
		 * 🏭 第一道工序：处理“内部嵌套类” (Member Classes)
		 * ---------------------------------------------------------
		 * [原理解析] 检查当前类（如 AppConfig）是否带有 @Component 注解（@Configuration 底层就是 @Component）。
		 * 如果是，立刻去检查它内部有没有写嵌套的配置类（如 static class InnerConfig { ... }）。
		 *
		 * [深度释疑] 为什么放第一步？
		 * 在 Spring 架构中，内部配置类通常是为了给外部主配置类做“局部补充”或“参数覆盖”的。
		 * 根据局部优先原则，必须把它们先揪出来，扔进车间去递归解析（让内部类也走一遍这 8 道工序），
		 * 确保内部定义的 Bean 和配置能及早进入工厂的视线。
		 */
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
			processMemberClasses(configClass, sourceClass, filter);
		}

		/*
		 * 🏭 第二道工序：加载环境变量 @PropertySource
		 * ---------------------------------------------------------
		 * [原理解析] attributesForRepeatable(...) 去类上寻找 @PropertySource 注解（支持写多个）。
		 * 如果找到，processPropertySource(...) 立马读取对应的配置文件（比如 classpath:application.propertie），并将其键值对硬塞进 Spring 的全局 Environment （环境大管家）里。
		 *
		 * [深度释疑] 为什么放第二步？
		 * 因为后面的工序（如扫包路径 @ComponentScan("com.demo.${env}")，或 @Value 注入），
		 * 极有可能会用到 ${...} 占位符！如果这一步不提前把属性读到内存里，后面遇到占位符直接原地爆炸。
		 */
		// Process any @PropertySource annotations
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource); // 真正的读取属性文件动作
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		/*
		 * 🏭 第三道工序：执行核弹级扫包 @ComponentScan (核心中的核心)
		 * ---------------------------------------------------------
		 * [原理解析] 这里是 Spring 启动变慢的最大“元凶”，也是最强大的地方。this.componentScanParser.parse(...) 会去底层去读取 .class 文件，
		 * 把业务 Bean 提取成 BeanDefinition 图纸。
		 *
		 * [裂变源头] 接下来的 for 循环极其关键。它会仔细端详刚扫出来的这些图纸，如果发现某个被扫出
		 * 来的类自己也是个配置类（比如扫出了一个 RedisConfig），系统绝不含糊，立马递归调用 parse()，
		 * 让 RedisConfig 也从外围的工序一重新跑一遍，确保持续裂变，直到榨干所有注解。
		 */
		// Process any @ComponentScan annotations
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		// 如果有 @ComponentScan 注解，且没有被 @Conditional 拦截掉
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {

				// 【动作 1】开动扫包机器！去你的包路径下把 @Service, @Controller 全扫出来变成图纸
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());

				// 【动作 2】检查刚扫出来的图纸，看看里面有没有隐藏的配置类？
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}

					// 检查这张图纸头上有没有 @Configuration 或 @Component 等注解
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						// 【动作3】如果有，大裂变开始！把它当作一个全新的配置类，调用外层的 parse 方法，重新送进流水线！
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		/*
		 * 🏭 第四道工序：处理引入的兄弟 @Import (Spring Boot 魔法的基石)
		 * ---------------------------------------------------------
		 * [原理解析] 寻找 @Import 注解。processImports 内部的逻辑极其复杂且精妙，
		 * 它将导入的类严格分为三种“材质”并分别处理：
		 * ① 普通配置类：直接当作新的配置类去递归解析。
		 * ② ImportSelector：动态选择器。执行它的方法，返回一批类名，然后再去解析这批类。（这也是前面提到的 DeferredImportSelector 延迟自动装配的接口父类）
		 * ③ ImportBeanDefinitionRegistrar：拥有最高权限的“特派员”。直接给它图纸注册表 (Registry)，让它自己写代码去底层手动注册图纸。
		 * 👉 Spring Boot 的各种 @EnableXXX (如 @EnableAsync) 底层全都是靠这三种材质玩转的！
		 */
		// Process any @Import annotations
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		/*
		 * 🏭 第五道工序：兼容老旧 XML 配置 @ImportResource
		 * ---------------------------------------------------------
		 * [深度释疑] 为什么这里只是存路径，不立刻解析 XML？
		 * 如果你用了 @ImportResource("classpath:spring.xml")，它只是把 XML 路径存进 configClass
		 * 这个档案袋里。因为必须要等所有的纯 Java 注解全解析完之后，再统一去解析 XML，
		 * 从而在架构层面保证：Java 注解配置的优先级始终高于老旧的 XML 配置！
		 */
		// Process any @ImportResource annotations
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				// 完美利用第二道工序加载的环境变量，解析占位符 (如把 "classpath:config-${env}.xml" 变成 "classpath:config-dev.xml")
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				// 把 XML 路径记录在当前配置类的模型里， 暂不解析
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		/*
		 * 🏭 第六道工序：提取手写的 @Bean 方法
		 * ---------------------------------------------------------
		 * [原理解析] 用反射遍历当前配置类里的所有方法，把带有 @Bean 的方法挑出来。
		 * [深度释疑] 注意防坑！这里绝对没有调用你的方法（没有 new 对象）！
		 * 这里只是提取了方法的元数据（方法名、返回值类型等），包装成 BeanMethod 零件挂在档案袋上。
		 * 等以后真正开始实例化 Bean 时，Spring 才会去反射调用这些方法。
		 */
		// Process individual @Bean methods
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			// 把每个 @Bean 方法包装成 BeanMethod 零件，挂在 configClass 档案袋上
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		/*
		 * 🏭 第七道工序：提取接口上的默认方法 (Java 8 特性支持)
		 * ---------------------------------------------------------
		 * [原理解析] 如果你的配置类实现了一个接口，且该接口里写了一个 default 方法并贴了
		 * @Bean 注解，Spring 会在这里顺藤摸瓜把它提取出来，同样挂在档案袋上。
		 */
		// Process default methods on interfaces
		processInterfaces(configClass, sourceClass);

		/*
		 * 🏭 第八道工序：追根溯源，挖出父类 (配合外层 do-while 循环)
		 * ---------------------------------------------------------
		 * [原理解析] 检查配置类是否继承了其他类 (如 AppConfig extends BaseConfig)。
		 * 只要父类存在，且不是 JDK 自带的基础类 (java.* 开头)，且之前没处理过，
		 * 它就把这个父类 return 回去。
		 * * [深度释疑] 外层的 do-while 循环拿到这个返回的父类后，一看不是 null，就会立马把父类
		 * 重新送进第一道工序，让父类也把这 8 道工序再跑一遍！这就实现了极致的深度类继承体系解析。
		 */
		// Process superclass, if any
		if (sourceClass.getMetadata().hasSuperClass()) {
			// 获取父类的名字
			String superclass = sourceClass.getMetadata().getSuperClassName();
			// 如果父类存在，且不是 JDK 自带的类 (java.* 开头)，且之前没处理过
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);// 记录一下
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();// 找到父类，直接 return 给外层的 do-while 循环去继续递归！
			}
		}

		// 彻底没有父类了 (或者父类是 Object)，全套工序完毕，下线！
		// No superclass -> processing is complete
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					this.importStack.push(configClass);
					try {
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		AnnotationMetadata original = sourceClass.getMetadata();
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> candidateMethods = new LinkedHashSet<>(beanMethods);
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (Iterator<MethodMetadata> it = candidateMethods.iterator(); it.hasNext();) {
							MethodMetadata beanMethod = it.next();
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								it.remove();
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		for (String location : locations) {
			try {
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		}
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		if (importCandidates.isEmpty()) {
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			this.importStack.push(configClass);
			try {
				for (SourceClass candidate : importCandidates) {
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						if (selector instanceof DeferredImportSelector) {
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	@SuppressWarnings("deprecation")
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new org.springframework.core.NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					deferredImports.forEach(handler::register);
					handler.processGroupImports();
				}
			}
			finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		public void processGroupImports() {
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		@SuppressWarnings("deprecation")
		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new org.springframework.core.NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
