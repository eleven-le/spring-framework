/**
 * 📖 对应章节：[[L02-07-作用域-scope代理与按需注入]]
 * 🎯 本包主题：L02-07 作用域、scope 代理与按需注入——用「下单上下文 / 营销策略 / 库存扣减任务 / ES 搜索客户端」
 *    四组 C 端 Bean，把四种作用域语义、单例注入短作用域 Bean 必须走 scope 代理、ObjectProvider 按需获取、
 *    @Lookup 方法注入、@Lazy 延迟注入的代理语义，坐实成可运行实验。判定入口在
 *    AbstractBeanFactory#doGetBean 的 singleton/prototype/Scope 三条分支。
 *
 * <p>核心类（阅读顺序 = 类序号）：
 * <ul>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_07.L0207_01_BeanScopeSemanticsDemo}
 *       —— 四种作用域语义：singleton 全局唯一、prototype 每次新建、request/session（用 SimpleThreadScope 线程绑定模拟）
 *          同线程同实例 / 跨线程不同实例。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_07.L0207_02_ScopedProxyInjectionDemo}
 *       —— scope 代理：单例注入 request Bean，无 proxyMode 会被焊死一份 → 高并发跨请求串数据；
 *          proxyMode=TARGET_CLASS 注入 CGLIB 代理，每次方法调用按当前请求重新解析 → 零串号（ConcurrentBench 对比）。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_07.L0207_03_ObjectProviderDemo}
 *       —— ObjectProvider/ObjectFactory 按需获取：getIfAvailable（可选）、getIfUnique（非唯一返回 null）、
 *          orderedStream（按 @Order 遍历）、getObject 取 prototype（每次新实例）。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_07.L0207_04_LookupMethodInjectionDemo}
 *       —— @Lookup 方法注入：单例字段注入 prototype 只定格一份（坑）；@Lookup 由 CGLIB 子类化重写方法，
 *          每次调用 getBean 拿全新 prototype。</li>
 *   <li>{@link com.leilei.lab.laboratory.l02.l02_07.L0207_05_LazyInjectionDemo}
 *       —— @Lazy 延迟注入与代理语义：注入点 @Lazy 注入延迟解析代理；要真正推迟构造，Bean 定义上也要加 @Lazy，
 *          否则 preInstantiateSingletons 照样在刷新期构造（half-lazy 事故对照）。</li>
 * </ul>
 *
 * <p>依赖章节 [[L02-06-Bean生命周期回调全图]]（销毁回调只对 singleton 生效、prototype 容器创建后撒手不管）。
 * 🚫 禁止 hello-world：场景全部取自茶饮 C 端高并发（下单 / 算价 / 库存扣减 / 搜索）。
 */
package com.leilei.lab.laboratory.l02.l02_07;
