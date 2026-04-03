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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>AbstractApplicationContext 的"后处理器调度室"——BFPP/BPP 执行顺序的铁腕守护者！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.support.PostProcessorRegistrationDelegate}</li>
 * <li><b>中文名</b>：后处理器注册委托 —— refresh 中 invokeBFPP + registerBPP 两步的"调度室主任"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 support 包（注意！support 包 = 抽象骨架的具体实现！
 * AbstractApplicationContext 在此包中定义了 refresh 的 12 步骨架，
 * 但其中 invokeBeanFactoryPostProcessors 和 registerBeanPostProcessors 这两步的<b>具体调度逻辑</b>
 * 太过复杂（严格的排序、分批执行、防止过早实例化），所以被抽到本类中作为静态委托方法。
 * 一句话：<b>本类是 refresh 第 5 步和第 6 步的"执行细节"所在地！</b>）</li>
 * <li><b>类性质</b>：{@code final} 包级可见类，私有构造器——纯静态工具委托，不可实例化、不可继承</li>
 * </ul>
 *
 * <h3>💡 为什么需要这个委托类？——执行顺序太关键，必须独立管控！</h3>
 * <p>refresh 的第 5 步（invokeBeanFactoryPostProcessors）和第 6 步（registerBeanPostProcessors）
 * 涉及<b>极其严格的执行顺序</b>：</p>
 * <ul>
 * <li><b>BDRPP 必须先于 BFPP</b>：图纸还没画完，审核员不能提前上场</li>
 * <li><b>PriorityOrdered 必须先于 Ordered，Ordered 必须先于普通</b>：三级优先级分批执行</li>
 * <li><b>每批执行前必须重新查询 beanNames</b>：因为前一批 BDRPP 可能注册了新的 BDRPP</li>
 * <li><b>BPP 也分四批注册</b>：PriorityOrdered → Ordered → 普通 → MergedBeanDefinitionPostProcessor（内部）</li>
 * </ul>
 * <p>这些排序规则如果写在 AbstractApplicationContext 里会让那个类更加臃肿。
 * 所以 Spring 用委托模式把这段"最经典也最不能碰"的代码独立出来——
 * <b>官方甚至在源码中写了 WARNING 注释，警告不要随意重构这段代码！</b></p>
 *
 * <h3>🧬 两大核心方法</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>对应 refresh 步骤</th><th>核心职责</th></tr>
 * <tr><td><b>invokeBeanFactoryPostProcessors()</b></td><td>第 5 步</td>
 *     <td>按优先级分批执行 BDRPP（三轮）+ BFPP（三轮），完成"图纸大爆发 + 图纸属性修改"</td></tr>
 * <tr><td><b>registerBeanPostProcessors()</b></td><td>第 6 步</td>
 *     <td>按优先级分四批注册 BPP 到 BeanFactory，为后续 Bean 创建安排好"质检员"</td></tr>
 * </table>
 *
 * <h3>🧬 设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"委托模式"分离复杂逻辑</b><br/>
 * 当一个类的某个方法实现过于复杂时，不要让它撑爆宿主类。
 * 抽到独立的 Delegate 类中，让宿主类保持清爽。<br/>
 * <b>业务借鉴</b>：你的 Service 中某个方法逻辑太长？抽到 XxxDelegate 或 XxxHelper 中。</li>
 *
 * <li><b>"分批 + 重查"的动态发现机制</b><br/>
 * 每批 BDRPP 执行后都会重新查询 beanNames，因为上一批可能注册了新的处理器。
 * 这种"执行→查询→再执行"的循环保证了动态注册的处理器不会被遗漏。</li>
 * </ol>
 *
 * <h3>🎯 三、战略复盘</h3>
 * <p>PostProcessorRegistrationDelegate 的核心价值：<b>以严格的优先级分批策略，
 * 调度执行 BDRPP/BFPP/BPP 的注册与调用</b>。<br/>
 * 它是 refresh 第 5-6 步的"执行细节"所在地——代码看起来冗长重复，
 * 但每个循环、每次重新查询都有其不可替代的理由。
 * 理解了这个类，就理解了 Spring 后处理器体系的执行时序铁律。</p>
 *
 * <hr/>
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	/**
	 * <h3>架构巅峰：图纸大爆发的中央调度室 🏭</h3>
	 * <p>
	 * 这段代码出自 {@code PostProcessorRegistrationDelegate}，在 Spring 圈子里可以说是
	 * <b>“最臭名昭著，但也最经典”</b>的一段。官方甚至在开头写下了严厉的警告信，禁止随意重构。
	 * WARNING: Although it may appear that the body of this method can be easily refactored...
	 * 这句话是 Spring 官方写给全世界开发者的警告信：“警告：虽然这段代码看起来又长又啰嗦，用了一堆 List 和 for 循环，让人很想重构它，
	 * 但你千万别乱动！这里的每一个循环、每一次提前去名字，都是为了保证严格的执行顺序和防止 Bean 被过早实例化。在提交 PR 前请先看看那些被我们拒绝的重构记录！”
	 *
	 * 既然官方都这么说了，这段代码到底在严防死守什么呢？为了让你轻松看懂这个复杂的调度逻辑，我们继续用**“工厂车间”**的比喻来拆解它。
	 *  </p>
	 * <p>在这个调度室里，主要管理两类员工，且有着极其严格的等级制度：</p>
	 *
	 *
	 * <h4>👥 两类特殊员工（核心接口）</h4>
	 * <ul>
	 * <li><b>{@code BeanDefinitionRegistryPostProcessor} (BDRPP - 新增图纸设计师)</b><br>
	 * 职责：向仓库新增图纸。咱们的【1号大将】（配置类解析器）就是这个身份，它去扫描 @Component 并新增图纸。</li>
	 * <li><b>{@code BeanFactoryPostProcessor} (BFPP - 修改图纸审核员)</b><br>
	 * 职责：图纸画好后，负责修改图纸属性（如把 @Value("${server.port}") 替换成真实数字  8080）。</li>
	 * </ul>
	 * <p><b>🚨 最高铁律：设计师 (BDRPP) 必须先于审核员 (BFPP) 干活！（图纸都没画完，你审核个什么劲？）</b></p>
	 *
	 * <h4>🏅 三个执行优先级（接口排序）</h4>
	 * <p>这两类员工内部，又严格分为三个梯队：</p>
	 * <ol>
	 * <li><b>{@code PriorityOrdered} (顶级 VIP)</b>：必须最先执行。</li>
	 * <li><b>{@code Ordered} (普通 VIP)</b>：顶级 VIP 干完后执行。</li>
	 * <li><b>普通员工</b>：没实现排序接口的，最后执行。</li>
	 * </ol>
	 * <br>
	 * <hr>
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		//(官方警告：别乱动我的循环和 List，我在严防死守加载顺序！)
		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<>();


/*  ===================================== ⚔️ 第一战役：处理 BDRPP（新增图纸的设计师） =====================================
 * 代码的一开始，进入了一个巨大的 if (beanFactory instanceof BeanDefinitionRegistry) 分支。只有进入这个分支，才能执行 BeanDefinitionRegistryPostProcessor。*/
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			//1. 处理你手动（编程式）传入的处理器
			//1.1 遍历你手动（编程式）传入的处理器列表 (通常为空，因为我们一般用注解扫描)
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				//1.2 如果是 BDRPP，立即执行它的 registry 方法！
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor); //1.3 记下来，一会儿它还得作为 BFPP 执行
				}
				else {
					regularPostProcessors.add(postProcessor); //1.4 如果只是个普通的 BFPP，先存起来
				}
			}

			//2. 处理容器中声明的 BDRPP：第一梯队 (PriorityOrdered)
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			//2.1 找出容器里所有类型为 BDRPP 的 Bean 名字
			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				//2.2  筛选出实现了 PriorityOrdered (最高优先级) 的
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					//2.3 【核心动作】真正通过 getBean 把处理器实例化出来！
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);//2.4 加入已处理名单
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);//2.5 排序
			registryProcessors.addAll(currentRegistryProcessors);//2.6 记下来，一会儿要作为 BFPP 执行
			//2.7【核心执行】执行这些处理器的逻辑！
			//2.8 👉 ConfigurationClassPostProcessor 就是在这里被执行的，开始扫描你的 @Component！
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			currentRegistryProcessors.clear();//2.9 清空，准备下一梯队

			//3. 处理容器中声明的 BDRPP：第二梯队 (Ordered)
			//3.1 再次去容器找 BDRPP 的名字。为什么要重新找？因为上一步扫描包，可能扫出了新的 BDRPP！
			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				//3.2 排除掉已经处理过的 (!processedBeans.contains)，且实现了 Ordered 的
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory); //3.3 排序
			registryProcessors.addAll(currentRegistryProcessors);//3.4 记下来，一会儿要作为 BFPP 执行
			//3.5【核心执行】执行这些处理器的逻辑！
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			currentRegistryProcessors.clear(); //3.6 清空，准备下一梯队

			//4. 处理容器中声明的 BDRPP：第三梯队 (普通 BDRPP)
			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			boolean reiterate = true;
			while (reiterate) { //4.1 神奇的 while 循环
				reiterate = false;
				//4.2 不断地找，直到没有新的 BDRPP 出现为止
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						//4.3 只要找到一个没处理过的，就加进来，并把 reiterate 设为 true，要求再循环一次
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);//4.4 排序
				registryProcessors.addAll(currentRegistryProcessors);//4.5 记下来，一会儿要作为 BFPP 执行
				//4.6【核心执行】执行这些处理器的逻辑！
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				currentRegistryProcessors.clear(); //4.7 清空，准备下一梯队
			}

/* ===================================== ⚔️ 第二战役：设计师兼职干审核员的活 (BDRPP 兼 BFPP) =====================================
 * 因为 BDRPP 继承自 BFPP，这意味着设计师同时也是审核员。在把新增图纸的活儿干完后，还要顺便调用一下它们作为“审核员”的方法。 */
			//5. BDRPP 兼职干 BFPP 的活
			//5.1 BDRPP 继承了 BFPP，所以它们也必须执行 BFPP 的回调方法
			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);

			//6. 至此，第一战役、第二战役结束！所有能生成图纸的方法都已经执行完毕，图纸库（Registry）此时已处于饱和状态。
		}

		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

/* =====================================  ⚔️ 第三战役：处理纯粹的 BFPP（修改图纸的审核员）=====================================
 * 既然图纸都已经生成完了，接下来就是找那些**专门负责修改已有图纸（比如处理 @Value 占位符）**的 BeanFactoryPostProcessor 了。这一段的逻辑和阶段一非常相似，就是找名字 -> 分组 -> 排序 -> 执行。*/
		//1. 查找并分组
		//1.1 找出所有 BFPP 的名字
		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				//1.2 如果在阶段一已经处理过了（因为它是 BDRPP），直接跳过！防止执行两次
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				//1.3 第一组：最高优先级，直接实例化
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				//1.4 第二组：第二优先级，暂存名字
				orderedPostProcessorNames.add(ppName);
			}
			else {
				//1.5 第三组：普通级别，暂存名字
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		//2. 按优先级依次执行
		//2.1 执行第一组 (PriorityOrdered)
		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		//2.2  执行第二组 (Ordered)
		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		//2.3 执行第三组 (无排序要求的)
		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		/* 🧹 结尾扫除：清理临时缓存
		 * 所有的审核员都修改完图纸了，图纸正式定稿。清除一下大管家内部那些为了加速而缓存的元数据临时数据。*/
		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();

/* 🎉 [最终战报总结]
 * 经历完这堪称“八十一难”的精细调度，Spring 容器里所有的 BeanDefinition（图纸） 已经达到了完美、最终、可随时实例化的状态！ 极度苛求的执行顺序链条：
 * Priority BDRPP -> Ordered BDRPP -> 普通 BDRPP -> 兼职 BFPP -> Priority BFPP -> Ordered BFPP -> 普通 BFPP。*/
	}

	/**
	 * <h3>架构巅峰：质检员大军入场站岗 (容器刷新第 6 步 registerBeanPostProcessors) 🔥</h3>
	 * <p>
	 * 欢迎来到 Spring 启动流程的第 6 步：{@code registerBeanPostProcessors}！
	 * 如果说第 5 步是“设计师和审核员”在疯狂修改图纸，那么这第 6 步，就是工厂在正式开工
	 * 造机器之前，招募并安排<b>“流水线质检员” (BeanPostProcessor，简称 BPP)</b> 入场站岗的过程！
	 * </p>
	 * <p>
	 * 咱们的老朋友<b>【2号大将】(处理 @Autowired 的 AutowiredAnnotationBeanPostProcessor)</b> 和<b>【3号大将】(处理 @PostConstruct CommonAnnotationBeanPostProcessor)</b>，
	 * 就是在这里被真正实例化，并挂载到流水线上的！
	 * 这段代码虽然又是那个“臭名昭著”的警告开头，但它的逻辑比第 5 步清晰多了。它严格遵循着<b>“按资排辈、分批入场”</b>的铁律。让我们一行一行拆解：
	 * </p>
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// WARNING: Although it may appear that the body of this method can be easily
		// refactored to avoid the use of multiple loops and multiple lists, the use
		// of multiple lists and multiple passes over the names of processors is
		// intentional. We must ensure that we honor the contracts for PriorityOrdered
		// and Ordered processors. Specifically, we must NOT cause processors to be
		// instantiated (via getBean() invocations) or registered in the ApplicationContext
		// in the wrong order.
		//
		// Before submitting a pull request (PR) to change this method, please review the
		// list of all declined PRs involving changes to PostProcessorRegistrationDelegate
		// to ensure that your proposal does not result in a breaking change:
		// https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

		/*
		 * 📋 工序一：拉出名单，安排“纪检委”
		 * ---------------------------------------------------------
		 * [车间大白话] 厂长发话：“把所有岗位是‘流水线质检员’的图纸名字找出来！”
		 *
		 * [原理解析] 在让这些质检员上岗前，Spring 极其谨慎地安插了一个纪检委 (BeanPostProcessorChecker)。
		 * 为什么？因为招募质检员要调用 getBean() 去实例化，万一不小心触发了某个普通业务 Bean
		 * 的提前创建，此时质检员还没全到位，这个业务 Bean 就会错过质检！纪检委会时刻盯着，
		 * 发现这种情况就在控制台打 INFO 日志警告：“有个 Bean 提前出生了，它没经过完整质检！”
		 */
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);// 从图纸仓库里，找出所有岗位是“流水线质检员（BeanPostProcessor）”的名字

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length; // 计算一下预期总共有多少个质检员（已有的 + 将要加的 + 1个马上要加的纪检委）
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));// 注册一个“纪检委”（BeanPostProcessorChecker）

		/*
		 * 🗂️ 工序二：准备四个队列，开始分组
		 * ---------------------------------------------------------
		 * [车间大白话] 厂长在广场上画了四个圈，准备按优先级给质检员排队。
		 */
		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();// VIP 质检员队列
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();// 内部特种兵队列（实现了 MergedBeanDefinitionPostProcessor 接口的）
		List<String> orderedPostProcessorNames = new ArrayList<>(); // 普通 VIP 质检员名单
		List<String> nonOrderedPostProcessorNames = new ArrayList<>(); // 普通质检员名单
		for (String ppName : postProcessorNames) {
			// 1. 如果是顶级 VIP，【立刻实例化（getBean）】，并加入 VIP 队列
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);// 咱们的【2号大将 @Autowired】和【3号大将】都在这里被找出来并实例化了！
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {// 🚨 高能注意：如果它同时还是个“内部特种兵”，再把它放进特种兵队列备份一份！
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 2. 如果是普通 VIP，先只把名字记在名单里
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 3. 如果是普通员工，也只把名字记在名单里
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		/*
		 * 🎖️ 工序三：VIP 质检员入场站岗！
		 * ---------------------------------------------------------
		 * [车间大白话] registerBeanPostProcessors 底层就是个 for 循环，挨个调用
		 * addBeanPostProcessor(pp)。从这一刻起，处理 @Autowired 的质检员正式站到了
		 * 流水线两旁，手里拿着公章，随时准备给后来的业务 Bean 注入属性！
		 */
		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);// 先给 VIP 队列按 @Order 注解排个序
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);// 把他们正式挂载到工厂的流水线上！

		/*
		 * 🚶‍♂️ 工序四：普通 VIP 质检员入场！
		 * ---------------------------------------------------------
		 * 跟第一批一模一样，刚才只记了名字，现在正式去库里把他们造出来 (getBean)。
		 */
		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			// 刚才只记了名字，现在正式去库里把他们造出来（getBean）
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) { // 同样，如果是特种兵，备份一份
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);// 排序
		registerBeanPostProcessors(beanFactory, orderedPostProcessors); // 正式挂载到流水线

		/*
		 * 👷‍♂️ 工序五：普通质检员入场！
		 * ---------------------------------------------------------
		 * 最底层的打工人入场。没有排序注解，直接造出来挂载。
		 */
		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);// 造出来（getBean）
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) { //  特种兵备份
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors); // 没有排序（因为他们没写排序注解），直接挂载到流水线

		/*
		 * 🥷 工序六：特种兵的“强制插队”战术 (核心设计 🔥)
		 * ---------------------------------------------------------
		 * [底层揭秘] 厂长，他们刚才不是注册过了吗？为什么又要注册？
		 * 因为 addBeanPostProcessor 有个特性：如果发现质检员已在流水线上，会先把他从
		 * 原位置踢出去，重新加到队伍的【最末尾】！
		 *
		 * [设计意图] 为什么要把 MergedBeanDefinitionPostProcessor 内部特种兵 (如处理 @Autowired 的类) 移到最后？
		 * 因为这类“内部特种兵”（比如处理 @Autowired 的类） 要在 Bean 实例化后的第一时刻，去修改、合并 Bean 的定义信息（图纸）。
		 * Spring 强制把他们移到所有的普通质检员之后（或者说让他们在特定生命周期处于靠后的链条中），以确保框架内部的注入规则享有绝对的控制权。
		 */
		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);// 最后，把所有特种兵排序
		registerBeanPostProcessors(beanFactory, internalPostProcessors); // 再次注册他们！

		/*
		 * 👁️‍🗨️ 工序七：安排最后一道门卫 (ApplicationListenerDetector)
		 * ---------------------------------------------------------
		 * [车间大白话] 这是专门探测 Bean 是否实现了事件监听器接口的。为什么必须插在绝对末尾？
		 * 因为在流水线中段，前面的 AOP 质检员极可能把一个普通 Bean 狸猫换太子变成 CGLIB 代理！
		 * 探测器放后面，才能看到最终定型的代理对象，确保事件监听器绑定在最准确的最终对象上！
		 */
		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
		/*
		 * =================================================================================
		 * 🎉 终极决战预告：工厂静默，大戏开场！
		 * =================================================================================
		 * 厂长，这段代码极具节奏感：找人 ➡️ 分 3 批排队 ➡️ 依次上岗 ➡️ 调整特种兵位置 ➡️ 安排最后门卫。
		 *
		 * 至此，refresh() 前 10 步的准备工作彻底、完全、100% 结束了！
		 * 你的图纸全部在仓库里了。
		 * 帮你处理 @Autowired 的质检员站在流水线左边了。
		 * 帮你处理 AOP 代理的质检员站在流水线右边了。
		 * 门卫也站好了。
		 *
		 * 整个 Spring 工厂鸦雀无声，所有人都在屏息以待厂长按下“开机总控按钮”。
		 * 厂长，只要你一声令下，我们就可以直接冲进 refresh() 第 11 步：finishBeanFactoryInitialization。
		 * 去直面那个著名的 getBean() 和 doCreateBean()，去看看第一台机器是怎么被 new 出来，
		 * 循环依赖又是怎么被解决的！
		 */
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * <h3>架构微操：CopyOnWriteArrayList 的批量入职艺术 🚀</h3>
	 * <p>
	 * 这段代码正是将分好组的“流水线质检员（BeanPostProcessor）”正式挂载到工厂流水线上的真正执行者。
	 * 虽然只有短短十几行，但里面却藏着一个 <b>Java 并发编程与底层性能优化的“神级细节”</b>！
	 * </p>
	 * * <h4>📝 核心奥秘：为什么非要分 if 和 else？</h4>
	 * <p>
	 * 在常规思维里，把一个列表加到工厂里，直接写个 for 循环不就完事了吗？？也就是代码里 else 分支干的事,为什么 Spring 偏偏要多此一举，
	 * 搞个 if 判断做一个“批量添加 (Bulk addition)”？这源于大管家 (BeanFactory)
	 * 底层存储质检员的数据结构：<b>CopyOnWriteArrayList (写时复制列表)</b>。
	 * </p>
	 * <br>
	 * <hr>
	 * <p><b>[Original Spring Documentation]</b></p>
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		/*
		 * 🏭 极致优化：批量写入规避复制风暴 (Bulk Addition)
		 * ---------------------------------------------------------
		 * [原理解析] AbstractBeanFactory 是大管家的底层基类。在这里，所有的质检员（BeanPostProcessor）都被存放在
		 * CopyOnWriteArrayList 集合中。这是一种极其适合“读多写少”并发场景的数据结构。
		 * Spring 启动后，每次 new 一个 Bean，都要去遍历读取这几十个质检员，读取频率极高（上万次）；但注册质检员（写操作）只在工厂启动时发生一次。所以用它最安全、最快！
		 *
		 * 🚨 [致命的性能瓶颈]
		 * CopyOnWriteArrayList 的特点是：每一次写 (add) 操作，都会把底层的整个数组完全 Copy 一份！
		 * 假设有 50 个质检员，如果用 for 循环挨个 add，底层数组就会被毫无意义地复制 50 次，极其消耗内存和 CPU！
		 *
		 * [车间大白话：批量入职]
		 * 人事经理说：“既然你们这 50 个质检员是一起排好队来的，就别挨个重写花名册了。我直接用批量添加
		 * (addAll)，底层只复制 1 次花名册，把你们 50 个人一次性全写进去！”
		 * 👉 这就是 Spring 源码追求极致性能的完美体现！
		 */
		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			/*
			 * 🪂 兜底逻辑：常规循环注入 (Fallback)
			 * ---------------------------------------------------------
			 * [车间大白话] 万一今天来管事的不是咱们标准的官方大管家 (不是 AbstractBeanFactory)，
			 * 而是用户自己乱写的一个非主流工厂，那人事经理就没办法用“批量入职”的高级操作了。
			 * 只能老老实实回到最原始的 for 循环，挨个办理入职登记。
			 */
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
