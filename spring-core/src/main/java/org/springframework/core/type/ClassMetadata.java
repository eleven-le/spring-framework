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

package org.springframework.core.type;

import org.springframework.lang.Nullable;

/**
 * <h1>🗺️ 一、架构坐标·全局定位</h1>
 * <h2>类的"体检报告单"——不用加载 Class 就能知道它的身份信息！</h2>
 * <ul>
 * <li><b>全限定名</b>：{@code org.springframework.core.type.ClassMetadata}</li>
 * <li><b>中文名</b>：类元数据接口 —— 类的"身份证读卡器"</li>
 * <li><b>所属车间 🏭</b>：{@code spring-core} 模块的 {@code core.type} 包
 *     <br/>（注意！{@code type} 包 = <b>类型元数据抽象层</b>！Spring 在这个包里定义了"不加载类就能读取类信息"的全部契约）</li>
 * <li><b>接口层级</b>：顶层接口，<b>10 个方法</b>（7 个抽象 + 3 个 default）——定义了类的"结构骨架"读取能力</li>
 * </ul>
 *
 * <h3>📦 包的设计寓意：{@code org.springframework.core.type}</h3>
 * <p>这个包是 Spring 的<b>"类型侦察兵"</b>——它的核心使命是：<br/>
 * <b>在不触发 ClassLoader.loadClass() 的前提下，获取一个类的全部结构信息和注解信息。</b></p>
 * <p>为什么"不加载"如此重要？因为 Spring 启动时需要扫描成千上万个 .class 文件：</p>
 * <ul>
 * <li>如果每扫描一个就 Class.forName()，会触发类的静态初始化块、加载所有依赖类——<b>内存炸了</b></li>
 * <li>如果某个类依赖的 jar 不存在（条件装配场景），Class.forName() 直接 ClassNotFoundException——<b>启动崩了</b></li>
 * <li>type 包的解法：用 ASM 直接读 .class 字节码，像"X光"一样透视类的结构，<b>绝不触发类加载</b></li>
 * </ul>
 * <p>type 包的两层结构：</p>
 * <ul>
 * <li>{@code core.type} —— <b>接口契约层</b>：ClassMetadata / AnnotatedTypeMetadata / AnnotationMetadata / MethodMetadata
 *     <br/>以及 Standard* 反射实现（已加载的类用这条路）</li>
 * <li>{@code core.type.classreading} —— <b>ASM 实现层</b>：MetadataReader / SimpleAnnotationMetadata / *ReadingVisitor
 *     <br/>（未加载的 .class 文件用这条路，<b>包扫描的主力军</b>）</li>
 * </ul>
 *
 * <h3>💡 为什么需要 ClassMetadata？——"不加载就能判断，是 Spring 包扫描的命脉"</h3>
 * <p>想象你是一个海关检查员，每天要检查 10000 个集装箱（.class 文件）。<br/>
 * 你不可能把每个箱子都打开（加载类），那样太慢了。<br/>
 * ClassMetadata 就是贴在箱子外面的<b>"报关单"</b>——不用开箱就能知道：</p>
 * <ul>
 * <li>这个箱子里装的是什么？（className）</li>
 * <li>是接口还是具体类？（isInterface / isConcrete）——接口不能实例化，直接跳过</li>
 * <li>是抽象的还是可以直接 new 的？（isAbstract）——抽象类也不能实例化</li>
 * <li>是顶层类还是内部类？（isIndependent）——非静态内部类依赖外围实例，需要特殊处理</li>
 * <li>它的父类和实现的接口是什么？（getSuperClassName / getInterfaceNames）——用于类型匹配</li>
 * </ul>
 * <p>这些信息让 Spring 在扫描阶段就能<b>快速过滤掉不合格的候选者</b>，只对真正需要的类进行深度处理。</p>
 *
 * <h3>🧬 继承体系定位</h3>
 * <pre>
 *                    ClassMetadata                     AnnotatedTypeMetadata
 *                   (类的结构信息)                       (注解信息读取)
 *                    ↗          ↘                              ↓
 *   StandardClassMetadata    AnnotationMetadata ←──────────────┘
 *      (反射实现)           (类结构 + 注解 = 完整画像)
 *                            ↗              ↘
 *         StandardAnnotationMetadata    SimpleAnnotationMetadata
 *            (反射路径：已加载的类)       (ASM路径：未加载的.class)
 * </pre>
 *
 * <h3>⚡ 核心矛盾：信息丰富度 vs 加载成本</h3>
 * <p>ClassMetadata 只暴露"结构信息"，不涉及注解——这是<b>刻意的最小化设计</b>。<br/>
 * 有些场景只需要判断"是不是接口/是不是抽象类"就够了，不需要读注解（读注解更贵）。<br/>
 * 把结构信息和注解信息拆成两个接口（ClassMetadata vs AnnotatedTypeMetadata），<br/>
 * 让调用者可以<b>"按需取用"</b>——需要结构就用 ClassMetadata，需要注解就升级到 AnnotationMetadata。</p>
 *<hr></>
 *
 *
 * Interface that defines abstract metadata of a specific class,
 * in a form that does not require that class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 */
public interface ClassMetadata {

	// =====================================================================================
	// 一、基础身份信息——"你是谁？"
	// =====================================================================================

	/**
	 * 【身份证号】返回类的全限定名。
	 * <p>这是类的唯一标识——不管是反射路径还是 ASM 路径，这个值都是 "com.example.MyService" 这种格式。
	 * <p><b>使用场景</b>：Spring 用它做 Bean 名称的默认值（首字母小写）、BD 的 beanClassName、类型匹配等。
	 * <hr>
	 *
	 * Return the name of the underlying class.
	 */
	String getClassName();

	/**
	 * 【是否是接口？】
	 * <p>接口不能被实例化，Spring 扫描时遇到接口会直接跳过（除非是 @FunctionalInterface 等特殊用途）。
	 * <p><b>关键调用点</b>：ClassPathScanningCandidateComponentProvider 用它过滤候选 BD。
	 * <hr>
	 *
	 * Return whether the underlying class represents an interface.
	 */
	boolean isInterface();

	/**
	 * 【是否是注解类型？】比如 @Component、@Service 这些本身就是注解。
	 * <p>注解本质上也是 interface，但 isAnnotation() 能更精确地识别。
	 * <hr>
	 *
	 * Return whether the underlying class represents an annotation.
	 * @since 4.1
	 */
	boolean isAnnotation();

	/**
	 * 【是否是抽象类？】
	 * <p>抽象类不能直接实例化。Spring 扫描时，抽象类默认被排除——
	 * 除非它标注了 @Lookup（此时 Spring 会用 CGLIB 生成子类来实例化）。
	 * <hr>
	 *
	 * Return whether the underlying class is marked as abstract.
	 */
	boolean isAbstract();

	/**
	 * 【是否是具体类？】= 既不是接口，也不是抽象类。
	 * <p>这是 Spring 注册 BD 的<b>基本门槛</b>——只有具体类才能被实例化。
	 * <p><b>default 方法</b>：直接组合 isInterface() + isAbstract() 的结果，子类一般不需要覆写。
	 * <hr>
	 *
	 * Return whether the underlying class represents a concrete class,
	 * i.e. neither an interface nor an abstract class.
	 */
	default boolean isConcrete() {
		return !(isInterface() || isAbstract());
	}

	/**
	 * 【是否是 final 类？】
	 * <p>final 类不能被继承——这意味着 <b>CGLIB 无法为它生成代理</b>！
	 * <p>当 @Transactional 或 @Async 标注在 final 类上时，CGLIB 代理会失败。
	 * 这也是为什么 Spring 推荐用接口代理（JDK 动态代理）而不是类代理的原因之一。
	 * <hr>
	 *
	 * Return whether the underlying class is marked as 'final'.
	 */
	boolean isFinal();

	// =====================================================================================
	// 二、嵌套关系信息——"你的家庭结构是什么？"
	// =====================================================================================

	/**
	 * 【是否是"独立"的类？】= 顶层类 or 静态内部类。
	 * <p>"独立"的意思是：可以不依赖外围类实例就能 new 出来。
	 * <ul>
	 * <li>顶层类（如 MyService.java 里的 MyService）—— ✅ 独立</li>
	 * <li>静态内部类（static class Inner）—— ✅ 独立</li>
	 * <li>非静态内部类（class Inner）—— ❌ 不独立，依赖外围实例</li>
	 * <li>方法内局部类 —— ❌ 不独立</li>
	 * </ul>
	 * <p><b>为什么重要？</b>Spring 只能管理"独立"的类——非静态内部类需要外围实例才能构造，
	 * 但 Spring 的 createBean 流程不知道怎么获取那个外围实例。
	 * <hr>
	 *
	 * Determine whether the underlying class is independent, i.e. whether
	 * it is a top-level class or a nested class (static inner class) that
	 * can be constructed independently of an enclosing class.
	 */
	boolean isIndependent();

	/**
	 * 【是否有外围类？】即：是否是内部类/嵌套类/局部类？
	 * <p>如果返回 false，说明这是一个顶层类。
	 * <p><b>default 方法</b>：直接看 getEnclosingClassName() 是否为 null。
	 * <hr>
	 *
	 * Return whether the underlying class is declared within an enclosing
	 * class (i.e. the underlying class is an inner/nested class or a
	 * local class within a method).
	 * <p>If this method returns {@code false}, then the underlying
	 * class is a top-level class.
	 */
	default boolean hasEnclosingClass() {
		return (getEnclosingClassName() != null);
	}

	/**
	 * 【外围类的全限定名】
	 * <p>如果当前类是 Outer.Inner，返回 "com.example.Outer"。
	 * <p>如果是顶层类，返回 null。
	 * <hr>
	 *
	 * Return the name of the enclosing class of the underlying class,
	 * or {@code null} if the underlying class is a top-level class.
	 */
	@Nullable
	String getEnclosingClassName();

	// =====================================================================================
	// 三、继承/实现关系——"你的血统是什么？"
	// =====================================================================================

	/**
	 * 【是否有父类？】
	 * <p>除了 Object 之外所有类都有父类。如果 getSuperClassName() == null，说明当前就是 Object 或者是接口。
	 * <p><b>default 方法</b>：直接看 getSuperClassName() 是否为 null。
	 * <hr>
	 *
	 * Return whether the underlying class has a superclass.
	 */
	default boolean hasSuperClass() {
		return (getSuperClassName() != null);
	}

	/**
	 * 【父类的全限定名】
	 * <p>注意：只返回直接父类，不会递归返回整个继承链。
	 * <p>如果是接口或 Object，返回 null。
	 * <hr>
	 *
	 * Return the name of the superclass of the underlying class,
	 * or {@code null} if there is no superclass defined.
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * 【实现的所有接口名称】
	 * <p>只返回直接实现的接口（declared），不包含通过父类/父接口间接继承的。
	 * <p><b>使用场景</b>：Spring 用它判断类是否实现了某个特定接口（如 BeanFactoryPostProcessor），
	 * 从而决定是否需要特殊处理。
	 * <hr>
	 *
	 * Return the names of all interfaces that the underlying class
	 * implements, or an empty array if there are none.
	 */
	String[] getInterfaceNames();

	/**
	 * 【声明的成员类名称】
	 * <p>返回当前类内部声明的所有类（inner class / nested class），包括 public/protected/default/private。
	 * <p>不包含继承来的内部类。如果没有内部类，返回空数组。
	 * <p><b>使用场景</b>：Spring 处理 @Configuration 类时，会检查其内部类是否也是 @Configuration，
	 * 实现"配置类嵌套"的递归扫描。
	 * <hr>
	 *
	 * Return the names of all classes declared as members of the class represented by
	 * this ClassMetadata object. This includes public, protected, default (package)
	 * access, and private classes and interfaces declared by the class, but excludes
	 * inherited classes and interfaces. An empty array is returned if no member classes
	 * or interfaces exist.
	 * @since 3.1
	 */
	String[] getMemberClassNames();

}
