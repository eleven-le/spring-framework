/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanDefinitionReaderUtils 是 BeanDefinition 读取流水线旁边的“装配工装台”——把散装图纸加工成可命名、可登记、可落库的标准工件。</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.BeanDefinitionReaderUtils}</li>
 * <li><b>中文名</b>：BeanDefinition 读取辅助工具类</li>
 * <li><b>所属模块</b>：{@code spring-beans}</li>
 * <li><b>包级定位</b>：位于 {@code org.springframework.beans.factory.support}，因为它服务的不是业务侧配置 API，而是 BeanDefinitionReader、Parser、Scanner、Registry 这条基础设施流水线；{@code support} 包正是 Spring 放置“骨架能力 + 内部协作工具”的车间。</li>
 * <li><b>接口层级与体量</b>：抽象工具类，不额外实现接口、不保存状态；对外集中暴露 <b>1 个常量</b> 与 <b>6 个静态方法</b>，把“建图纸、起名字、做唯一化、完成注册”拆成几个可复用的原子动作。</li>
 * <li><b>一句话角色</b>：它不是 BeanFactory 本身，而是 BeanDefinition 入库前的“前置加工台”。</li>
 * </ul>
 *
 * <h3>💡 为什么要设计这个类/接口？</h3>
 * <p>Spring 的 BeanDefinition 来源极多：XML、properties、注解扫描、编程式注册、第三方框架扩展都能往容器里送“图纸”。这些入口就像多个外协车间，原材料各不相同，但最后都要进入同一个档案馆 {@link BeanDefinitionRegistry}。如果没有这个工具类，每个 Reader 都得自己重复实现三件基础活：先把 class/parent/factory-bean 组装成标准图纸，再按统一规则生成名字，最后把主名和别名一起登记入库。</p>
 * <p>真正棘手的不是“代码会重复”，而是“规则会漂移”。某个 Reader 可能 eager load class，另一个只记 className；某个解析器给内部 bean 加 identity 后缀，另一个却拿全局名字去硬碰硬；某个注册流程记得补 alias，另一个却把别名丢在半路。BeanDefinitionReaderUtils 就像统一工装夹具，把这些入库前的共性动作收口，保证不同配置风格最终落到容器里的语义是一致的。</p>
 * <p>所以，这个类解决的本质问题是：<b>多入口配置模型下的规则治理与一致性复用</b>。没有它，Spring 依然能跑，但 Reader 体系会更散、更重，也更难维护“同一种配置结果必须得到同一种容器语义”的底线。</p>
 *
 * <h3>🧬 二、设计精髓·业务偷师</h3>
 * <ol>
 * <li><b>能力原子化</b>：{@code createBeanDefinition()}、{@code generateBeanName()}、{@code uniqueBeanName()}、{@code registerBeanDefinition()} 各做一件事，再由不同 Reader 组合成自己的处理流水线。这是典型的“能力积木化”设计，避免把所有逻辑塞进一个超级父类。</li>
 * <li><b>元数据阶段与运行阶段分离</b>：这个类只处理 BeanDefinition 和注册表，不碰 Bean 实例本身。先治理图纸，再进入实例化车间，是 Spring 容器极其关键的两阶段思维。</li>
 * <li><b>上下文感知的命名策略</b>：顶层 bean 与内部 bean 的命名策略不同。顶层 bean 要能被全局引用，内部 bean 只要求局部唯一。这说明“命名”不是字符串拼接，而是跟作用域、可见性、生命周期绑定的架构决策。</li>
 * <li><b>订单系统可借鉴</b>：把“订单流程定义的录入”与“订单实例的执行”拆开。先建立一个统一的 {@code OrderFlowDefinitionRegistry} 处理默认命名、唯一化和注册，再让运行引擎只消费定义，不要把“注册流程图”和“跑订单”揉成一个大服务。</li>
 * <li><b>风控平台可借鉴</b>：风控规则从控制台、数据库、灰度配置多源进入时，不要让每个入口各自发明规则编码。抽出类似 {@code RuleDefinitionUtils} 的统一工具，把规则装配、命名冲突消解、别名登记集中治理。</li>
 * <li><b>状态机/活动引擎可借鉴</b>：面向外部暴露的节点 ID 和引擎内部临时节点 ID 应分开设计。外部节点要稳定可追踪，内部节点只需局部唯一，避免全局命名空间被一堆临时对象挤满。</li>
 * </ol>
 *
 * <h3>🧬 三、继承体系定位</h3>
 * <pre>
 * Object
 * └── BeanDefinitionReaderUtils  ← 👈 你在这里！(无状态静态工具类：给 Reader/Parser/Scanner 提供统一入库动作)
 *
 * 典型下游协作者（非继承，协作关系）:
 * ├── PropertiesBeanDefinitionReader
 * ├── BeanDefinitionParserDelegate
 * ├── DefaultBeanDefinitionDocumentReader
 * ├── DefaultBeanNameGenerator
 * ├── AnnotatedBeanDefinitionReader
 * └── ClassPathBeanDefinitionScanner
 * </pre>
 *
 * <h3>🗂️ 四、战区划分·全局作战地图</h3>
 * <table border="1">
 * <tr><th>战区</th><th>使命</th><th>成员（具体的常量/方法名）</th></tr>
 * <tr><td>命名规约区</td><td>统一生成 beanName 的后缀规则，并负责名称唯一化，避免多个 Reader 各搞一套命名口径。</td><td>{@code GENERATED_BEAN_NAME_SEPARATOR}、{@link #generateBeanName(BeanDefinition, BeanDefinitionRegistry)}、{@link #generateBeanName(BeanDefinition, BeanDefinitionRegistry, boolean)}、{@link #uniqueBeanName(String, BeanDefinitionRegistry)}</td></tr>
 * <tr><td>图纸装配区</td><td>把 parent/class/classLoader 这些输入折叠成标准 {@link AbstractBeanDefinition}，为后续注册和实例化提供统一起点。</td><td>{@link #createBeanDefinition(String, String, ClassLoader)}</td></tr>
 * <tr><td>入库登记区</td><td>把主名称、别名和 BeanDefinition 一起落到注册表，或者先生成名称再完成注册，让“最终入库动作”具备统一语义。</td><td>{@link #registerBeanDefinition(BeanDefinitionHolder, BeanDefinitionRegistry)}、{@link #registerWithGeneratedName(AbstractBeanDefinition, BeanDefinitionRegistry)}</td></tr>
 * </table>
 *
 * <h3>🎯 五、战略复盘</h3>
 * <p>BeanDefinitionReaderUtils 看起来只是一个小工具类，实际上它把 Spring 配置读取阶段最容易分叉的动作做了标准化：图纸怎么建、名字怎么起、冲突怎么化解、别名怎么登记。正因为这些规则被收束在一个稳定的“工装台”上，XML 解析器、注解扫描器、Properties Reader 和各种编程式扩展才能共享同一套容器语义。Spring 能在多种配置风格并存的情况下依旧保持一致体验，靠的正是这种小而硬、无状态、可复用的基础设施设计。</p>
 *
 * <hr/>
 * Utility methods that are useful for bean definition reader implementations.
 * Mainly intended for internal use.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 1.1
 * @see PropertiesBeanDefinitionReader
 * @see org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader
 */
public abstract class BeanDefinitionReaderUtils {

	/**
	 * <h3>🧷 GENERATED_BEAN_NAME_SEPARATOR —— 生成式 beanName 的统一分隔符</h3>
	 * <p><b>一句话</b>：这是 Spring 给“容器代起名字”的 bean 贴后缀时使用的统一分隔符，默认来自 {@link BeanFactoryUtils}，通常就是 {@code #}。</p>
	 * <p>它的存在看似微小，实则是在给整条命名流水线定规矩。无论是顶层 bean 的顺序编号，还是内部 bean 的 identity 后缀，都会借助这个分隔符把“基础名”和“唯一化片段”拼接成最终 beanName。</p>
	 * <p>例如：顶层自动生成名可能长成 {@code com.example.OrderService#0}，内部 bean 则可能长成 {@code com.example.OrderService#7f3a1c2d}。统一分隔符意味着日志、诊断、排障、工具链分析都能按同一语义识别“这是系统生成的名字”。</p>
	 *
	 * <hr/>
	 * Separator for generated bean names. If a class name or parent name is not
	 * unique, "#1", "#2" etc will be appended, until the name becomes unique.
	 */
	public static final String GENERATED_BEAN_NAME_SEPARATOR = BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR;


	/**
	 * <h3>🏗️ createBeanDefinition(...) —— 把外部配置先折成一张标准图纸</h3>
	 * <p><b>核心能力</b>：根据 {@code parentName}、{@code className}、{@code classLoader} 组装出一个 {@link GenericBeanDefinition}。这是 BeanDefinitionReader 把 XML、properties 等外部描述翻译为 Spring 内部图纸时的第一道工序。</p>
	 * <ul>
	 * <li><b>parentName</b>：表示这张图纸是否要继承另一张“母图纸”，也就是 Spring 经典的 bean definition inheritance。</li>
	 * <li><b>className</b>：表示最终想造什么类型的 Bean；如果没有 class，而是走 parent/factory-bean 语义，也允许暂时为空。</li>
	 * <li><b>classLoader</b>：决定“现在就验明正身”还是“先登记名字，稍后再解析”。传入时会立刻把类加载成 {@link Class}；不传时只记录 beanClassName，把类加载延后到更合适的阶段。</li>
	 * </ul>
	 * <p>这个方法体现了 Spring 非常典型的工程取舍：<b>解析阶段尽量保持信息完整，但是否立即触发类加载可以按上下文决定</b>。这样既支持早失败，也支持延迟解析，给不同 Reader 留出弹性空间。</p>
	 * <blockquote>大白话就是：先把“原材料描述”压成一张标准工单，但这张工单此时还没贴编号，也还没进档案柜。</blockquote>
	 *
	 * <hr/>
	 * Create a new GenericBeanDefinition for the given parent name and class name,
	 * eagerly loading the bean class if a ClassLoader has been specified.
	 * @param parentName the name of the parent bean, if any
	 * @param className the name of the bean class, if any
	 * @param classLoader the ClassLoader to use for loading bean classes
	 * (can be {@code null} to just register bean classes by name)
	 * @return the bean definition
	 * @throws ClassNotFoundException if the bean class could not be loaded
	 */
	public static AbstractBeanDefinition createBeanDefinition(
			@Nullable String parentName, @Nullable String className, @Nullable ClassLoader classLoader) throws ClassNotFoundException {

		GenericBeanDefinition bd = new GenericBeanDefinition();
		bd.setParentName(parentName);
		if (className != null) {
			if (classLoader != null) {
				bd.setBeanClass(ClassUtils.forName(className, classLoader));
			}
			else {
				bd.setBeanClassName(className);
			}
		}
		return bd;
	}

	/**
	 * <h3>🪪 generateBeanName(definition, registry) —— 给顶层图纸生成默认名字</h3>
	 * <p><b>核心能力</b>：为一个即将进入 {@link BeanDefinitionRegistry} 的顶层 BeanDefinition 生成默认 beanName，并确保它符合 Spring 的生成式命名规则。</p>
	 * <p>这个重载是“常规入口”，默认把当前 BeanDefinition 视为 <b>top-level bean</b>。也就是说，它不会走内部 bean 的 identity 命名策略，而是委托给三参重载，以 {@code isInnerBean=false} 的方式执行。</p>
	 * <p>这类默认命名主要服务于“用户没显式给名字”的场景，例如 XML 中未写 {@code id}，或者某些 Reader 只拿到了 class 元数据。它让容器在缺省配置下仍能稳定落库，而不是要求所有入口都先人工取名。</p>
	 *
	 * <hr/>
	 * Generate a bean name for the given top-level bean definition,
	 * unique within the given bean factory.
	 * @param beanDefinition the bean definition to generate a bean name for
	 * @param registry the bean factory that the definition is going to be
	 * registered with (to check for existing bean names)
	 * @return the generated bean name
	 * @throws BeanDefinitionStoreException if no unique name can be generated
	 * for the given bean definition
	 * @see #generateBeanName(BeanDefinition, BeanDefinitionRegistry, boolean)
	 */
	public static String generateBeanName(BeanDefinition beanDefinition, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		return generateBeanName(beanDefinition, registry, false);
	}

	/**
	 * <h3>🧠 generateBeanName(definition, registry, isInnerBean) —— 统一命名中枢</h3>
	 * <p><b>核心能力</b>：根据 BeanDefinition 的内容推导出“基础名”，再结合 {@code isInnerBean} 决定采用哪一种唯一化策略，最终得到可注册的 beanName。</p>
	 * <ul>
	 * <li><b>基础名推导顺序</b>：先取 {@code beanClassName}；如果没有，再退回到 {@code parentName + "$child"}；再不行就退回到 {@code factoryBeanName + "$created"}。</li>
	 * <li><b>失败条件</b>：如果 class、parent、factory-bean 三条线都没有信息，就说明这张图纸连“叫什么基底名字”都说不清，直接抛出 {@link BeanDefinitionStoreException}，避免模糊状态进入容器。</li>
	 * <li><b>顶层 bean 策略</b>：走 {@link #uniqueBeanName(String, BeanDefinitionRegistry)}，得到类似 {@code com.example.OrderService#0}、{@code #1} 的全局可注册名字。</li>
	 * <li><b>内部 bean 策略</b>：直接拼上 {@link ObjectUtils#getIdentityHexString(Object) identity hash}，让名字只在当前宿主图纸上下文里保持唯一，例如 {@code com.example.OrderService#7f3a1c2d}。</li>
	 * </ul>
	 * <p>这里最值得偷师的是：<b>命名策略是上下文敏感的</b>。顶层 bean 需要稳定、可枚举、可重复生成；内部 bean 则更像匿名零件，只要别跟同一批装配件撞车即可，没必要占用全局命名资源。</p>
	 * <blockquote>它像档案馆的编号员：先看这张图纸本体是谁，如果是“临时内嵌零件”就贴局部流水号；如果是“正式入库图纸”，就走全局编号规则。</blockquote>
	 *
	 * <hr/>
	 * Generate a bean name for the given bean definition, unique within the
	 * given bean factory.
	 * @param definition the bean definition to generate a bean name for
	 * @param registry the bean factory that the definition is going to be
	 * registered with (to check for existing bean names)
	 * @param isInnerBean whether the given bean definition will be registered
	 * as inner bean or as top-level bean (allowing for special name generation
	 * for inner beans versus top-level beans)
	 * @return the generated bean name
	 * @throws BeanDefinitionStoreException if no unique name can be generated
	 * for the given bean definition
	 */
	public static String generateBeanName(
			BeanDefinition definition, BeanDefinitionRegistry registry, boolean isInnerBean)
			throws BeanDefinitionStoreException {

		String generatedBeanName = definition.getBeanClassName();
		if (generatedBeanName == null) {
			if (definition.getParentName() != null) {
				generatedBeanName = definition.getParentName() + "$child";
			}
			else if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new BeanDefinitionStoreException("Unnamed bean definition specifies neither " +
					"'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
		}

		if (isInnerBean) {
			// Inner bean: generate identity hashcode suffix.
			return generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
		}

		// Top-level bean: use plain class name with unique suffix if necessary.
		return uniqueBeanName(generatedBeanName, registry);
	}

	/**
	 * <h3>🔢 uniqueBeanName(beanName, registry) —— 把基础名加工成真正可落库的唯一名</h3>
	 * <p><b>核心能力</b>：为给定基础名追加统一计数后缀，直到在当前注册表中不与已有 BeanDefinition 冲突为止。</p>
	 * <p>有一个很容易被忽略的细节：这个方法不是“若冲突才加后缀”，而是<b>从一开始就走生成式命名路径</b>。也就是说，只要你调用它，返回值就会是 {@code beanName#0}、{@code beanName#1} 这一类格式，而不是原样返回 {@code beanName}。</p>
	 * <p>这样设计的好处是命名语义非常清晰：凡是通过该工具自动生成的顶层 beanName，都带着明显的“系统代起名”痕迹。它避免了“有些自动生成名像手写名，有些又像系统名”的混乱体验。</p>
	 * <blockquote>像给工单打流水号：不是先试试看裸名字能不能用，而是直接进入系统编号模式，从 {@code #0} 开始往后排，谁都别插队。</blockquote>
	 *
	 * <hr/>
	 * Turn the given bean name into a unique bean name for the given bean factory,
	 * appending a unique counter as suffix if necessary.
	 * @param beanName the original bean name
	 * @param registry the bean factory that the definition is going to be
	 * registered with (to check for existing bean names)
	 * @return the unique bean name to use
	 * @since 5.1
	 */
	public static String uniqueBeanName(String beanName, BeanDefinitionRegistry registry) {
		String id = beanName;
		int counter = -1;

		// Increase counter until the id is unique.
		String prefix = beanName + GENERATED_BEAN_NAME_SEPARATOR;
		while (counter == -1 || registry.containsBeanDefinition(id)) {
			counter++;
			id = prefix + counter;
		}
		return id;
	}

	/**
	 * <h3>📥 registerBeanDefinition(holder, registry) —— 把图纸连同主名和别名一起入库</h3>
	 * <p><b>核心能力</b>：将 {@link BeanDefinitionHolder} 中封装的主 beanName、BeanDefinition 以及 aliases 一次性登记到注册表中。</p>
	 * <ul>
	 * <li><b>第一步</b>：用主名称调用 {@link BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)}，先把“正档案”落进去。</li>
	 * <li><b>第二步</b>：遍历别名数组，逐个调用 {@link BeanDefinitionRegistry#registerAlias(String, String)}，把“曾用名、门牌别称”同步登记好。</li>
	 * </ul>
	 * <p>这段逻辑之所以值得专门抽成工具方法，是因为别名注册极容易在各类 Reader 中被遗漏。把“主名 + 别名”的登记打包成固定动作，就能让 XML、注解扫描、命名空间扩展在出厂语义上保持一致。</p>
	 * <blockquote>像档案馆办入职手续：先把员工正式档案按工号存档，再把英文名、花名一并录入，否则以后有人按别名找人就会扑空。</blockquote>
	 *
	 * <hr/>
	 * Register the given bean definition with the given bean factory.
	 * @param definitionHolder the bean definition including name and aliases
	 * @param registry the bean factory to register with
	 * @throws BeanDefinitionStoreException if registration failed
	 */
	public static void registerBeanDefinition(
			BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		// Register bean definition under primary name.
		String beanName = definitionHolder.getBeanName();
		registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

		// Register aliases for bean name, if any.
		String[] aliases = definitionHolder.getAliases();
		if (aliases != null) {
			for (String alias : aliases) {
				registry.registerAlias(beanName, alias);
			}
		}
	}

	/**
	 * <h3>🚀 registerWithGeneratedName(definition, registry) —— 起名与入库的一键联动</h3>
	 * <p><b>核心能力</b>：先为给定 {@link AbstractBeanDefinition} 生成一个顶层 beanName，然后立刻注册进 {@link BeanDefinitionRegistry}，最后把实际生成的名字回传给调用方。</p>
	 * <p>它适用于“调用方只关心把图纸送进容器，不想手动管理命名细节”的场景。相比先调 {@code generateBeanName()} 再调 {@code registerBeanDefinition()}，这个方法把两步压缩成一次原子化调用，减少 Reader 侧模板代码。</p>
	 * <p>注意它只处理主名称，不处理别名，因为这里只传入的是裸 {@link AbstractBeanDefinition}，而不是携带别名信息的 {@link BeanDefinitionHolder}。这也体现了 Spring API 设计的一贯风格：<b>能力只对拥有完整上下文的数据结构开放</b>。</p>
	 * <blockquote>像前台替你办手续：系统先自动分配工号，再立刻建档，最后把工号回执递给你。</blockquote>
	 *
	 * <hr/>
	 * Register the given bean definition with a generated name,
	 * unique within the given bean factory.
	 * @param definition the bean definition to generate a bean name for
	 * @param registry the bean factory to register with
	 * @return the generated bean name
	 * @throws BeanDefinitionStoreException if no unique name can be generated
	 * for the given bean definition or the definition cannot be registered
	 */
	public static String registerWithGeneratedName(
			AbstractBeanDefinition definition, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		String generatedName = generateBeanName(definition, registry, false);
		registry.registerBeanDefinition(generatedName, definition);
		return generatedName;
	}

}
