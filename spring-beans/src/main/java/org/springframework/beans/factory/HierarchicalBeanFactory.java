/*
 * Copyright 2002-2012 the original author or authors.
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

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>BeanFactory 的"层级扩展"——赋予工厂父子关系的能力！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.HierarchicalBeanFactory}</li>
 * <li><b>中文名</b>：层级化 Bean 工厂 —— 超级工厂的"父子关系协议"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块</li>
 * <li><b>接口层级</b>：{@code BeanFactory} 的直系子接口，只增加了 <b>2 个方法</b>！</li>
 * </ul>
 *
 * <h3>💡 为什么要拆出这个子接口？——"隔离" 和 "共享" 的矛盾需要"层级"来调和！</h3>
 * <p>BeanFactory 是一个"扁平"的容器——所有 Bean 都在同一层，互相平等、互相可见。<br/>
 * 但现实中存在一个核心矛盾：<b>有些 Bean 需要全局共享，有些 Bean 需要局部隔离</b>。</p>
 * <ul>
 * <li><b>数据源、事务管理器、Service 层</b>：全应用共享，放在"根容器"</li>
 * <li><b>Controller、ViewResolver、HandlerMapping</b>：每个 DispatcherServlet 独有，放在"子容器"</li>
 * <li>两个 DispatcherServlet 的 Controller 互不可见——但都能共享同一个 DataSource</li>
 * </ul>
 * <p>如果没有层级，你只有两个选择：要么所有 Bean 混在一起（无隔离），要么完全分开（无共享）。<br/>
 * HierarchicalBeanFactory 用<b>"父子层级 + 双亲委派"</b>优雅地解决了这个矛盾——<br/>
 * 子容器有自己的私有 Bean（隔离），同时能委托父容器获取公共 Bean（共享）。</p>
 *
 * <h3>🧬 这里蕴含的设计精髓——你的业务能偷师什么？</h3>
 * <ol>
 * <li><b>"共享 vs 隔离"的层级化解决方案</b><br/>
 * 这个模式在业务中极其常见：多租户系统中，公共配置全局共享，租户配置局部隔离。<br/>
 * <b>业务借鉴</b>：设计多租户/多门店/多区域系统时，用"层级配置"替代"全量复制"——
 * 公共配置放父级，租户差异化配置放子级，子级优先、父级兜底。
 * 比如定价策略：全国默认价（父级）→ 区域调整价（子级）→ 门店特殊价（孙级），查找时就近覆盖。</li>
 *
 * <li><b>"containsLocalBean" 的边界意识——精确区分"自有"和"继承"</b><br/>
 * 这是一个容易被忽视但极其重要的方法——它让你能精确判断一个能力到底是"本层自有的"还是"从上层继承来的"。<br/>
 * <b>业务借鉴</b>：配置管理系统中，一定要区分"本级覆盖"和"上级继承"。用户在管理界面上应该能看到：
 * "这个配置值是本门店自己设置的（可以修改/删除），还是从区域继承来的（只能在区域级修改）？"</li>
 * </ol>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 * BeanFactory
 * ├── HierarchicalBeanFactory  ← 👈 你在这里！赋予"父子层级"能力
 * │     └── ConfigurableBeanFactory（可配置+可设置父工厂）
 * │           └── ConfigurableListableBeanFactory（终极合体）
 * ├── ListableBeanFactory
 * └── AutowireCapableBeanFactory
 * </pre>
 *
 * <h3>🏭 核心设计思想：双亲委派 + 就近优先</h3>
 * <p>这个接口只有 2 个方法，但蕴含了两个深刻的设计思想：</p>
 * <ol>
 * <li><b>双亲委派查找</b>：{@code BeanFactory.getBean()} 在 {@code HierarchicalBeanFactory} 体系下，
 * 如果子工厂找不到 Bean，会自动向父工厂递归查找。这让公共 Bean（如数据源、事务管理器）只需在父容器注册一份，
 * 所有子容器共享！</li>
 * <li><b>就近覆盖/隔离</b>：{@code containsLocalBean()} 提供了"只看本厂，不问父级"的精确查询。
 * 子工厂中同名 Bean 会覆盖父工厂的同名 Bean——就近优先！</li>
 * </ol>
 *
 * <h3>🗂️ 二、战区划分·全局作战地图</h3>
 * <p>极简！只有 <b>2 个方法</b>，形成一个完美的"上下文切换"对：</p>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>方法</th><th>使命</th><th>视角</th></tr>
 * <tr><td>{@code getParentBeanFactory()}</td><td>获取父工厂引用</td><td>向上看——我的上级是谁？</td></tr>
 * <tr><td>{@code containsLocalBean(name)}</td><td>只查本厂花名册</td><td>向内看——这人是不是我自己的？</td></tr>
 * </table>
 *
 * <h3>🧠 三、架构师透视·现实应用场景</h3>
 * <p>Spring MVC 就是 HierarchicalBeanFactory 最经典的实战场：</p>
 * <ul>
 * <li><b>Root WebApplicationContext（父容器）</b>：放 Service、DataSource、事务管理器等公共 Bean</li>
 * <li><b>DispatcherServlet WebApplicationContext（子容器）</b>：放 Controller、ViewResolver 等 Web 专属 Bean</li>
 * <li>Controller 要注入 Service？子容器找不到 → 委托父容器 → 找到！</li>
 * <li>Service 要注入 Controller？父容器找不到 → 没有更上级了 → 报错！<b>（父看不到子，只能子看父！）</b></li>
 * </ul>
 *
 * <h3>🎯 四、战略复盘</h3>
 * <p>HierarchicalBeanFactory 用 2 个方法开辟了工厂的"纵向扩展"维度。<br/>
 * 与 {@code ListableBeanFactory}（横向枚举）形成正交：一个让工厂有了"深度"，一个让工厂有了"广度"。<br/>
 * 最终在 {@code DefaultListableBeanFactory} 中，纵向层级 + 横向枚举合二为一，成就了 Spring 容器的完整能力。</p>
 *
 * <hr/>
 * Sub-interface implemented by bean factories that can be part
 * of a hierarchy.
 *
 * <p>The corresponding {@code setParentBeanFactory} method for bean
 * factories that allow setting the parent in a configurable
 * fashion can be found in the ConfigurableBeanFactory interface.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 07.07.2003
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setParentBeanFactory
 */
public interface HierarchicalBeanFactory extends BeanFactory {

	/**
	 * <h3>🔗 方法 1：BeanFactory getParentBeanFactory()</h3>
	 * <p><b>🏭【查上级——我的母公司/总厂是谁？】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 返回本工厂的父工厂引用。如果没有父工厂（已经是顶层根工厂），返回 null。<br/>
	 * 注意：这里只是"读"操作——获取引用。"写"操作（设置父工厂）在 {@code ConfigurableBeanFactory.setParentBeanFactory()} 中，
	 * 体现了<b>读写分离</b>的接口设计——普通使用者只能查父级，配置者才能设父级。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问分厂经理："你们的总公司是哪家？"<br/>
	 * "是那个 rootApplicationContext！" → 返回父工厂引用<br/>
	 * "我们就是总公司，没有上级了！" → 返回 null<br/>
	 * 注意：你只能问谁是上级，不能在这里改上级——改上级是"行政管理部"（ConfigurableBeanFactory）的权限！</blockquote>
	 * <hr/>
	 * Return the parent bean factory, or {@code null} if there is none.
	 */
	@Nullable
	BeanFactory getParentBeanFactory();

	/**
	 * <h3>🔍 方法 2：boolean containsLocalBean(String name)</h3>
	 * <p><b>🏭【只查本厂花名册——不打电话问总公司！】</b></p>
	 * <p><b>【硬核释义】</b><br/>
	 * 检查<b>本工厂</b>是否包含指定名称的 Bean，<b>不向父工厂递归查找</b>！<br/>
	 * 与 {@code BeanFactory.containsBean(name)} 的关键区别：
	 * <ul>
	 * <li>{@code containsBean(name)} → 本厂没有？问父厂！父厂没有？问爷厂！一路委托到根！</li>
	 * <li>{@code containsLocalBean(name)} → 只翻本厂花名册，翻完就结束，绝不打电话问上级！</li>
	 * </ul>
	 * 这个方法的核心价值：<b>精确判断一个 Bean 到底"属于"哪一层容器</b>——是子容器自己定义的，
	 * 还是从父容器继承来的。</p>
	 * <blockquote><b>【车间大白话】 🏭</b><br/>
	 * 你问分厂："你们厂的花名册上有 userController 吗？"<br/>
	 * 分厂翻了翻自己的花名册："有！是我们自己招的！" → true<br/>
	 * "没有！但总公司可能有。不过你问的是我这里有没有，那答案就是没有！" → false<br/>
	 * <b>不会像 containsBean 那样自动帮你打电话问总公司！</b></blockquote>
	 * <p><b>【使用场景】</b><br/>
	 * // 判断某个 Bean 是子容器自己注册的，还是继承自父容器的<br/>
	 * {@code if (hierarchicalBf.containsLocalBean("dataSource")) { }}<br/>
	 * // 是本容器自己定义的 dataSource（可能是覆盖了父容器的）<br/>
	 * {@code else if (hierarchicalBf.containsBean("dataSource")) { }}<br/>
	 * // 是从父容器继承来的 dataSource</p>
	 * <hr/>
	 * Return whether the local bean factory contains a bean of the given name,
	 * ignoring beans defined in ancestor contexts.
	 * <p>This is an alternative to {@code containsBean}, ignoring a bean
	 * of the given name from an ancestor bean factory.
	 * @param name the name of the bean to query
	 * @return whether a bean with the given name is defined in the local factory
	 * @see BeanFactory#containsBean
	 */
	boolean containsLocalBean(String name);

}
