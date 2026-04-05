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

package org.springframework.context;

import org.springframework.beans.factory.Aware;
import org.springframework.core.metrics.ApplicationStartup;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>启动度量的 Aware 回调——让 Bean 感知到当前容器用的是哪个 ApplicationStartup！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.context.ApplicationStartupAware}</li>
 * <li><b>中文名</b>：应用启动度量感知接口 —— "我要领码表"的申请表</li>
 * <li><b>所属车间 🏭</b>：{@code spring-context} 模块的 {@code context} 包
 * （注意！{@code context} 包 = 应用上下文的顶层契约层！
 * 所有 XxxAware 接口都在这里：ApplicationContextAware、EnvironmentAware、
 * ApplicationEventPublisherAware……ApplicationStartupAware 是同族。
 * 一句话：<b>context 包定义了"Bean 能从容器感知到什么"的所有契约！</b>）</li>
 * <li><b>接口层级</b>：{@code Aware} → {@code ApplicationStartupAware}</li>
 * </ul>
 *
 * <h3>💡 什么时候需要实现这个接口？</h3>
 * <p>当你的 Bean 自己也想在某些阶段打码表时（比如自定义的初始化逻辑耗时追踪），
 * 实现此接口即可拿到容器的 {@link ApplicationStartup} 实例。</p>
 *
 * <h3>📍 注入时机（在 Aware 家族中的排序）：</h3>
 * <pre>
 * ApplicationContextAwareProcessor.invokeAwareInterfaces() 中：
 *   1. EnvironmentAware
 *   2. EmbeddedValueResolverAware
 *   3. ResourceLoaderAware
 *   4. ApplicationEventPublisherAware
 *   5. MessageSourceAware
 *   6. ApplicationStartupAware       ← 这里！在 ApplicationContextAware 之前
 *   7. ApplicationContextAware
 * </pre>
 *
 * <h3>🎯 使用示例：</h3>
 * <pre>
 * {@code @Component}
 * public class HeavyInitBean implements ApplicationStartupAware, InitializingBean {
 *     private ApplicationStartup startup;
 *
 *     {@code @Override}
 *     public void setApplicationStartup(ApplicationStartup startup) {
 *         this.startup = startup;
 *     }
 *
 *     {@code @Override}
 *     public void afterPropertiesSet() {
 *         StartupStep step = startup.start("myapp.heavy-init");
 *         // ... 耗时初始化逻辑
 *         step.tag("itemCount", String.valueOf(items.size()));
 *         step.end();
 *     }
 * }
 * </pre>
 *
 * <p>Interface to be implemented by any object that wishes to be notified
 * of the {@link ApplicationStartup} that it runs with.
 *
 * @author Brian Clozel
 * @since 5.3
 * @see ApplicationContextAware
 */
public interface ApplicationStartupAware extends Aware {

	/**
	 * 容器回调注入 ApplicationStartup。
	 * <p>调用时机：属性注入完成之后、InitializingBean.afterPropertiesSet() 之前、
	 * ApplicationContextAware.setApplicationContext() 之前。
	 *
	 * Set the ApplicationStartup that this object runs with.
	 * <p>Invoked after population of normal bean properties but before an init
	 * callback like InitializingBean's afterPropertiesSet or a custom init-method.
	 * Invoked before ApplicationContextAware's setApplicationContext.
	 * @param applicationStartup application startup to be used by this object
	 */
	void setApplicationStartup(ApplicationStartup applicationStartup);

}
