/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * <br>
 * <h3>架构设计与源码解析：</h3>
 * <p>
 * <b>[设计模式] Holder 的角色定位</b><br>
 * 在 Java 和 Spring 的源码世界里，以 {@code Holder} 结尾的类（如 {@code ThreadLocalHolder}、
 * {@code ConnectionHolder} 等）通常扮演着“数据搬运工”或“包装盒”的角色。
 * 它本身通常不包含复杂的业务逻辑，唯一的职责就是把几个强相关的数据对象“抱在一起”（Hold），
 * 方便在方法之间作为一个整体来传递。
 * </p>
 *
 * <p>
 * <b>[核心痛点] 为什么不直接传递 BeanDefinition？</b><br>
 * 这是最核心的原因：{@link org.springframework.beans.factory.config.BeanDefinition}
 * 本身“不知道”自己的名字。
 * </p>
 * <ul>
 * <li><b>容器底层的存储结构：</b> 在 Spring 的大管家 {@code DefaultListableBeanFactory} 底层，
 * 所有的图纸是存在一个大 Map 里的（类似 {@code ConcurrentHashMap<String, BeanDefinition>}）。
 * Key 是 Bean 的名字，Value 才是真正的图纸。图纸对象内部并没有一个 name 属性来记录自己叫什么。</li>
 * <li><b>信息传递的完整性：</b> 在向底层 Map 注册完图纸后，方法需要给调用方一个“回执”。
 * 如果只返回 {@code BeanDefinition}，调用方拿到图纸后会一脸懵：“这图纸是注册成功了，但它在容器里叫啥名字啊？”</li>
 * <li><b>组合数据的需求：</b> 因此，Spring 设计了 {@code BeanDefinitionHolder} 这个包装盒。
 * 把解析好的图纸（BeanDefinition）、生成好的名字（beanName）以及可能的别名（aliases）打包塞进这个盒子里统一返回。</li>
 * </ul>
 *
 * <blockquote>
 * <b>💡 [形象比喻]：电视机与快递箱</b><br>
 * {@code BeanDefinition} 就像是一台电视机（包含了尺寸、屏幕类型等核心属性）。<br>
 * {@code beanName} 就像是快递单上的收件人姓名和单号。<br>
 * 在 Spring 工厂的流水线上，单拿一台电视机没法发货（不知道发给谁），单拿一张快递单也没意义（没有实体）。
 * 所以，我们需要一个瓦楞纸包装箱（Holder），把电视机装进去，外面贴上快递单。这个包装箱，
 * 就是 {@code BeanDefinitionHolder}！通过这种封装，Spring 就能在各个内部方法之间安全地传递这套“完整信息”了。
 * </blockquote>
 *
 * <br>
 * <hr>
 * <p><b>[Original Spring Documentation]</b></p>
 * Holder for a BeanDefinition with name and aliases.
 * Can be registered as a placeholder for an inner bean.
 *
 * <p>Can also be used for programmatic registration of inner bean
 * definitions. If you don't care about BeanNameAware and the like,
 * registering RootBeanDefinition or ChildBeanDefinition is good enough.
 *
 * @author Juergen Hoeller
 * @since 1.0.2
 * @see org.springframework.beans.factory.BeanNameAware
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 */
public class BeanDefinitionHolder implements BeanMetadataElement {

	private final BeanDefinition beanDefinition;// 1. 真正的 Bean 图纸（比如类的类型、是否单例、有没有懒加载等属性）

	private final String beanName;// 2. Bean 的全局唯一名称（比如 "configurationClassPostProcessor"）

	@Nullable
	private final String[] aliases;// 3. Bean 的别名数组（如果有的话）


	/**
	 * Create a new BeanDefinitionHolder.
	 * @param beanDefinition the BeanDefinition to wrap
	 * @param beanName the name of the bean, as specified for the bean definition
	 */
	public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName) {
		this(beanDefinition, beanName, null);
	}

	/**
	 * Create a new BeanDefinitionHolder.
	 * @param beanDefinition the BeanDefinition to wrap
	 * @param beanName the name of the bean, as specified for the bean definition
	 * @param aliases alias names for the bean, or {@code null} if none
	 */
	public BeanDefinitionHolder(BeanDefinition beanDefinition, String beanName, @Nullable String[] aliases) {
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");
		Assert.notNull(beanName, "Bean name must not be null");
		this.beanDefinition = beanDefinition;
		this.beanName = beanName;
		this.aliases = aliases;
	}

	/**
	 * Copy constructor: Create a new BeanDefinitionHolder with the
	 * same contents as the given BeanDefinitionHolder instance.
	 * <p>Note: The wrapped BeanDefinition reference is taken as-is;
	 * it is {@code not} deeply copied.
	 * @param beanDefinitionHolder the BeanDefinitionHolder to copy
	 */
	public BeanDefinitionHolder(BeanDefinitionHolder beanDefinitionHolder) {
		Assert.notNull(beanDefinitionHolder, "BeanDefinitionHolder must not be null");
		this.beanDefinition = beanDefinitionHolder.getBeanDefinition();
		this.beanName = beanDefinitionHolder.getBeanName();
		this.aliases = beanDefinitionHolder.getAliases();
	}


	/**
	 * Return the wrapped BeanDefinition.
	 */
	public BeanDefinition getBeanDefinition() {
		return this.beanDefinition;
	}

	/**
	 * Return the primary name of the bean, as specified for the bean definition.
	 */
	public String getBeanName() {
		return this.beanName;
	}

	/**
	 * Return the alias names for the bean, as specified directly for the bean definition.
	 * @return the array of alias names, or {@code null} if none
	 */
	@Nullable
	public String[] getAliases() {
		return this.aliases;
	}

	/**
	 * Expose the bean definition's source object.
	 * @see BeanDefinition#getSource()
	 */
	@Override
	@Nullable
	public Object getSource() {
		return this.beanDefinition.getSource();
	}

	/**
	 * Determine whether the given candidate name matches the bean name
	 * or the aliases stored in this bean definition.
	 */
	public boolean matchesName(@Nullable String candidateName) {
		return (candidateName != null && (candidateName.equals(this.beanName) ||
				candidateName.equals(BeanFactoryUtils.transformedBeanName(this.beanName)) ||
				ObjectUtils.containsElement(this.aliases, candidateName)));
	}


	/**
	 * Return a friendly, short description for the bean, stating name and aliases.
	 * @see #getBeanName()
	 * @see #getAliases()
	 */
	public String getShortDescription() {
		if (this.aliases == null) {
			return "Bean definition with name '" + this.beanName + "'";
		}
		return "Bean definition with name '" + this.beanName + "' and aliases [" + StringUtils.arrayToCommaDelimitedString(this.aliases) + ']';
	}

	/**
	 * Return a long description for the bean, including name and aliases
	 * as well as a description of the contained {@link BeanDefinition}.
	 * @see #getShortDescription()
	 * @see #getBeanDefinition()
	 */
	public String getLongDescription() {
		return getShortDescription() + ": " + this.beanDefinition;
	}

	/**
	 * This implementation returns the long description. Can be overridden
	 * to return the short description or any kind of custom description instead.
	 * @see #getLongDescription()
	 * @see #getShortDescription()
	 */
	@Override
	public String toString() {
		return getLongDescription();
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BeanDefinitionHolder)) {
			return false;
		}
		BeanDefinitionHolder otherHolder = (BeanDefinitionHolder) other;
		return this.beanDefinition.equals(otherHolder.beanDefinition) &&
				this.beanName.equals(otherHolder.beanName) &&
				ObjectUtils.nullSafeEquals(this.aliases, otherHolder.aliases);
	}

	@Override
	public int hashCode() {
		int hashCode = this.beanDefinition.hashCode();
		hashCode = 29 * hashCode + this.beanName.hashCode();
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.aliases);
		return hashCode;
	}

}
