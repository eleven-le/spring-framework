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
	 * <br>
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


		/*
		 * =================================================================================
		 * ⚔️ 第一战役：处理 BDRPP（新增图纸的设计师）
		 * =================================================================================
		 * 代码的一开始，进入了一个巨大的 if (beanFactory instanceof BeanDefinitionRegistry) 分支。只有进入这个分支，才能执行 BDRPP。
		 */
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
					regularPostProcessors.add(postProcessor);//1.4 如果只是个普通的 BFPP，先存起来
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

			/*
			 * =================================================================================
			 * ⚔️ 第二战役：设计师兼职干审核员的活 (BDRPP 兼 BFPP)
			 * =================================================================================
			 * 因为 BDRPP 继承自 BFPP，这意味着设计师同时也是审核员。
			 * 在把新增图纸的活儿干完后，还要顺便调用一下它们作为“审核员”的方法。
			 */
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

		/*
		 * =================================================================================
		 * ⚔️ 第三战役：处理纯粹的 BFPP（修改图纸的审核员）
		 * =================================================================================
		 * 既然图纸都已经生成完了，接下来就是找那些**专门负责修改已有图纸（比如处理 @Value 占位符）**的 BeanFactoryPostProcessor 了。
		 * 这一段的逻辑和阶段一非常相似，就是找名字 -> 分组 -> 排序 -> 执行。
		 */
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

		/*
		 * 🧹 结尾扫除：清理临时缓存
		 * ---------------------------------------------------------
		 * 所有的审核员都修改完图纸了，图纸正式定稿。
		 * 清除一下大管家内部那些为了加速而缓存的元数据临时数据。
		 */
		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();

		/*
		 * 🎉 [最终战报总结]
		 * ---------------------------------------------------------
		 * 经历完这堪称“八十一难”的精细调度，Spring 容器里所有的 BeanDefinition（图纸）
		 * 已经达到了完美、最终、可随时实例化的状态！
		 * 极度苛求的执行顺序链条：
		 * Priority BDRPP -> Ordered BDRPP -> 普通 BDRPP -> 兼职 BFPP -> Priority BFPP -> Ordered BFPP -> 普通 BFPP。
		 */
	}

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

		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
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
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
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
