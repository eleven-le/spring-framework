/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>"通用图纸"——XML/注解解析后的原始 BD，可指定 parent 继承公共属性！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.beans.factory.support.GenericBeanDefinition}</li>
 * <li><b>中文名</b>：通用 Bean 定义 —— 解析阶段产出的"原始图纸"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-beans} 模块的 support 包（support 包 = 骨架实现区）</li>
 * <li><b>类层级</b>：{@code AbstractBeanDefinition} 的子类，Spring 2.5 引入，替代了旧的 ChildBeanDefinition</li>
 * </ul>
 *
 * <h3>💡 GenericBD vs RootBD——"原始图纸" vs "终态图纸"</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 * <tr><th>对比</th><th>GenericBeanDefinition</th><th>RootBeanDefinition</th></tr>
 * <tr><td>角色</td><td>解析阶段的原始产物</td><td>合并后的终态图纸</td></tr>
 * <tr><td>parentName</td><td>可以有（支持 BD 继承）</td><td>没有（已合并完毕）</td></tr>
 * <tr><td>运行时缓存</td><td>没有</td><td>有（已解析构造器、BPP 短路标记等）</td></tr>
 * <tr><td>使用者</td><td>BFPP 可修改/重配 parent</td><td>createBean 直接使用</td></tr>
 * </table>
 *
 * <h3>🧬 图纸家族中的位置</h3>
 * <pre>
 * AbstractBeanDefinition
 * ├── GenericBeanDefinition        ← 👈 你在这里！（通用原始图纸，可有 parentName）
 * │     ├── AnnotatedGenericBeanDefinition  （Reader 注册时产出，携带注解元信息）
 * │     └── ScannedGenericBeanDefinition    （Scanner 扫描时产出，携带 ASM 元信息）
 * └── RootBeanDefinition           （终态图纸，合并后产出）
 * </pre>
 *
 * <h3>🎯 战略复盘</h3>
 * <p>GenericBeanDefinition 是 XML/注解解析阶段的标准产物——它支持 parentName 继承，
 * 让 BFPP 有机会在合并前修改。Spring 2.5 后推荐用 GenericBD 替代旧的 ChildBD + RootBD 组合。</p>
 *
 * <hr/>
 * GenericBeanDefinition is a one-stop shop for standard bean definition purposes.
 * Like any bean definition, it allows for specifying a class plus optionally
 * constructor argument values and property values. Additionally, deriving from a
 * parent bean definition can be flexibly configured through the "parentName" property.
 *
 * <p>In general, use this {@code GenericBeanDefinition} class for the purpose of
 * registering user-visible bean definitions (which a post-processor might operate on,
 * potentially even reconfiguring the parent name). Use {@code RootBeanDefinition} /
 * {@code ChildBeanDefinition} where parent/child relationships happen to be pre-determined.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #setParentName
 * @see RootBeanDefinition
 * @see ChildBeanDefinition
 */
@SuppressWarnings("serial")
public class GenericBeanDefinition extends AbstractBeanDefinition {

	@Nullable
	private String parentName;


	/**
	 * Create a new GenericBeanDefinition, to be configured through its bean
	 * properties and configuration methods.
	 * @see #setBeanClass
	 * @see #setScope
	 * @see #setConstructorArgumentValues
	 * @see #setPropertyValues
	 */
	public GenericBeanDefinition() {
		super();
	}

	/**
	 * Create a new GenericBeanDefinition as deep copy of the given
	 * bean definition.
	 * @param original the original bean definition to copy from
	 */
	public GenericBeanDefinition(BeanDefinition original) {
		super(original);
	}


	@Override
	public void setParentName(@Nullable String parentName) {
		this.parentName = parentName;
	}

	@Override
	@Nullable
	public String getParentName() {
		return this.parentName;
	}


	@Override
	public AbstractBeanDefinition cloneBeanDefinition() {
		return new GenericBeanDefinition(this);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GenericBeanDefinition)) {
			return false;
		}
		GenericBeanDefinition that = (GenericBeanDefinition) other;
		return (ObjectUtils.nullSafeEquals(this.parentName, that.parentName) && super.equals(other));
	}

	@Override
	public String toString() {
		if (this.parentName != null) {
			return "Generic bean with parent '" + this.parentName + "': " + super.toString();
		}
		return "Generic bean: " + super.toString();
	}

}
