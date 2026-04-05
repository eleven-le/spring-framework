/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.factory;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Aware 家族的"名字感知员"——让 Bean 知道自己在容器中叫什么！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.BeanNameAware}</li>
 * <li><b>中文名</b>：Bean 名称感知接口 —— Bean 获取自身名字的"回调凭证"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 factory 包（factory 包 = 用户可见的工厂契约）</li>
 * <li><b>接口层级</b>：{@code Aware} 的子接口，属于"第一批 Aware"（最先执行）</li>
 * </ul>
 *
 * <h3>💡 执行时机——三兄弟中的老大</h3>
 * <pre>
 * invokeAwareMethods(beanName, bean)
 * ├── 1. BeanNameAware.setBeanName()          ← 👈 你在这里！（最先）
 * ├── 2. BeanClassLoaderAware.setBeanClassLoader()
 * └── 3. BeanFactoryAware.setBeanFactory()
 * </pre>
 *
 * <h3>💡 典型使用场景</h3>
 * <ul>
 * <li>日志标识：Bean 在日志中打印自己的 beanName，方便排查多实例场景</li>
 * <li>条件逻辑：根据 beanName 走不同的初始化分支（如多数据源场景）</li>
 * <li>框架内部：NamedBean 接口的实现依赖此回调</li>
 * </ul>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>BeanNameAware 是最轻量的 Aware——只告诉 Bean 它的名字。
 * 官方不太推荐业务代码依赖 beanName（因为这是"外部配置"，属于脆弱依赖），
 * 但在框架内部和 SPI 扩展中它很常用。</p>
 *
 * <hr/>
 * Interface to be implemented by beans that want to be aware of their
 * bean name in a bean factory. Note that it is not usually recommended
 * that an object depends on its bean name, as this represents a potentially
 * brittle dependence on external configuration, as well as a possibly
 * unnecessary dependence on a Spring API.
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 01.11.2003
 * @see BeanClassLoaderAware
 * @see BeanFactoryAware
 * @see InitializingBean
 */
public interface BeanNameAware extends Aware {

	/**
	 * Set the name of the bean in the bean factory that created this bean.
	 * <p>Invoked after population of normal bean properties but before an
	 * init callback such as {@link InitializingBean#afterPropertiesSet()}
	 * or a custom init-method.
	 * @param name the name of the bean in the factory.
	 * Note that this name is the actual bean name used in the factory, which may
	 * differ from the originally specified name: in particular for inner bean
	 * names, the actual bean name might have been made unique through appending
	 * "#..." suffixes. Use the {@link BeanFactoryUtils#originalBeanName(String)}
	 * method to extract the original bean name (without suffix), if desired.
	 */
	void setBeanName(String name);

}
