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

package org.springframework.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>Aware 家族的"全能感知员"——让 Bean 拿到整个 ApplicationContext！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ApplicationContextAware}</li>
 * <li><b>中文名</b>：应用上下文感知接口 —— Bean 获取容器全能引用的"回调凭证"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 context 包（context 包 = 上下文层的用户契约）</li>
 * <li><b>接口层级</b>：{@code Aware} 的子接口，属于"第二批 Aware"（由 BPP 驱动）</li>
 * </ul>
 *
 * <h3>💡 第二批 Aware 的执行顺序（ApplicationContextAwareProcessor 中）</h3>
 * <pre>
 * ApplicationContextAwareProcessor.postProcessBeforeInitialization()
 * ├── 1. EnvironmentAware.setEnvironment()
 * ├── 2. EmbeddedValueResolverAware.setEmbeddedValueResolver()
 * ├── 3. ResourceLoaderAware.setResourceLoader()
 * ├── 4. ApplicationEventPublisherAware.setApplicationEventPublisher()
 * ├── 5. MessageSourceAware.setMessageSource()
 * └── 6. ApplicationContextAware.setApplicationContext()  ← 👈 你在这里！（最后）
 * </pre>
 *
 * <h3>💡 ApplicationContextAware vs 更精确的 Aware</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>需求</th><th>推荐 Aware</th><th>而非 ApplicationContextAware</th></tr>
 * <tr><td>读取资源文件</td><td>ResourceLoaderAware</td><td>因为 AC 继承了 ResourceLoader</td></tr>
 * <tr><td>发布事件</td><td>ApplicationEventPublisherAware</td><td>因为 AC 继承了 EventPublisher</td></tr>
 * <tr><td>国际化消息</td><td>MessageSourceAware</td><td>因为 AC 继承了 MessageSource</td></tr>
 * <tr><td>获取环境变量</td><td>EnvironmentAware</td><td>因为 AC 继承了 EnvironmentCapable</td></tr>
 * <tr><td>需要全部能力</td><td colspan="2">那才用 ApplicationContextAware（ISP 原则：按需取用）</td></tr>
 * </table>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>ApplicationContextAware 是 Aware 家族中最"强大"的——因为 ApplicationContext 继承了
 * BeanFactory + ResourceLoader + EventPublisher + MessageSource 等所有能力。
 * 但正因为太强大，官方建议遵循 ISP（接口隔离原则）：如果只需要发事件，就用
 * ApplicationEventPublisherAware，而不是直接拿整个 ApplicationContext。</p>
 *
 * <hr/>
 * Interface to be implemented by any object that wishes to be notified
 * of the {@link ApplicationContext} that it runs in.
 *
 * <p>Implementing this interface makes sense for example when an object
 * requires access to a set of collaborating beans. Note that configuration
 * via bean references is preferable to implementing this interface just
 * for bean lookup purposes.
 *
 * <p>This interface can also be implemented if an object needs access to file
 * resources, i.e. wants to call {@code getResource}, wants to publish
 * an application event, or requires access to the MessageSource. However,
 * it is preferable to implement the more specific {@link ResourceLoaderAware},
 * {@link ApplicationEventPublisherAware} or {@link MessageSourceAware} interface
 * in such a specific scenario.
 *
 * <p>Note that file resource dependencies can also be exposed as bean properties
 * of type {@link org.springframework.core.io.Resource}, populated via Strings
 * with automatic type conversion by the bean factory. This removes the need
 * for implementing any callback interface just for the purpose of accessing
 * a specific file resource.
 *
 * <p>{@link org.springframework.context.support.ApplicationObjectSupport} is a
 * convenience base class for application objects, implementing this interface.
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link org.springframework.beans.factory.BeanFactory BeanFactory javadocs}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see ResourceLoaderAware
 * @see ApplicationEventPublisherAware
 * @see MessageSourceAware
 * @see org.springframework.context.support.ApplicationObjectSupport
 * @see org.springframework.beans.factory.BeanFactoryAware
 */
public interface ApplicationContextAware extends Aware {

	/**
	 * Set the ApplicationContext that this object runs in.
	 * Normally this call will be used to initialize the object.
	 * <p>Invoked after population of normal bean properties but before an init callback such
	 * as {@link org.springframework.beans.factory.InitializingBean#afterPropertiesSet()}
	 * or a custom init-method. Invoked after {@link ResourceLoaderAware#setResourceLoader},
	 * {@link ApplicationEventPublisherAware#setApplicationEventPublisher} and
	 * {@link MessageSourceAware}, if applicable.
	 * @param applicationContext the ApplicationContext object to be used by this object
	 * @throws ApplicationContextException in case of context initialization errors
	 * @throws BeansException if thrown by application context methods
	 * @see org.springframework.beans.factory.BeanInitializationException
	 */
	void setApplicationContext(ApplicationContext applicationContext) throws BeansException;

}
