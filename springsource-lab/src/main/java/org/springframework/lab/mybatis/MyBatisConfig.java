package org.springframework.lab.mybatis;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Spring 集成 — 三巨头配置展示:
 *
 * 1. SqlSessionFactoryBean  — FactoryBean, 产物是 SqlSessionFactory (MyBatis 核心)
 * 2. MapperScannerConfigurer — BeanDefinitionRegistryPostProcessor, 扫描 Mapper 接口
 *                               并为每个接口注册 MapperFactoryBean 的 BeanDefinition
 * 3. SqlSessionTemplate      — 线程安全的 SqlSession 代理, 每次调用自动管理会话生命周期
 *
 * 可选方案对比:
 *   - @MapperScan 注解: 内部通过 @Import(MapperScannerRegistrar) 完成同样的事
 *     MapperScannerRegistrar implements ImportBeanDefinitionRegistrar
 *     效果等价于手动注册 MapperScannerConfigurer
 *   - 这里故意用显式 @Bean 注册 MapperScannerConfigurer, 方便在 BeanDefinition 层面断点调试
 */
@Configuration
@ComponentScan("org.springframework.lab.mybatis")
@EnableTransactionManagement
public class MyBatisConfig {

	/**
	 * H2 内嵌数据源 + 初始化脚本
	 */
	@Bean
	public DataSource dataSource() {
		return new EmbeddedDatabaseBuilder()
				.setType(EmbeddedDatabaseType.H2)
				.setName("mybatis-lab")
				.addScript("lab-mybatis-schema.sql")
				.build();
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

	// ===================== MyBatis 三巨头 =====================

	/**
	 * 巨头 1: SqlSessionFactoryBean (FactoryBean<SqlSessionFactory>)
	 *
	 * 将 MyBatis 原生 Configuration/Environment/MapperRegistry 打包成 SqlSessionFactory。
	 * afterPropertiesSet() 时 buildSqlSessionFactory() — 这是 MyBatis 初始化的核心入口。
	 *
	 * 断点: SqlSessionFactoryBean#buildSqlSessionFactory
	 */
	@Bean
	public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
		SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
		factoryBean.setDataSource(dataSource);
		// 可配: setMapperLocations / setTypeAliasesPackage / setConfigLocation 等
		return factoryBean;
	}

	/**
	 * 巨头 2: MapperScannerConfigurer (BeanDefinitionRegistryPostProcessor)
	 *
	 * 核心流程:
	 *   postProcessBeanDefinitionRegistry()
	 *   → ClassPathMapperScanner#scan()
	 *   → 扫描 basePackage 下所有接口
	 *   → 为每个接口注册 BeanDefinition(beanClass = MapperFactoryBean)
	 *   → 将 sqlSessionFactory/sqlSessionTemplate 注入到 MapperFactoryBean
	 *
	 * 断点: MapperScannerConfigurer#postProcessBeanDefinitionRegistry
	 *        ClassPathMapperScanner#processBeanDefinitions
	 *
	 * 注意: 必须用 static @Bean 方法! 因为 BDRPP 需要在所有普通 Bean 之前创建,
	 *       如果是实例方法, 就会过早触发 MyBatisConfig 的创建, 导致 @Configuration 增强失效。
	 */
	@Bean
	public static MapperScannerConfigurer mapperScannerConfigurer() {
		MapperScannerConfigurer configurer = new MapperScannerConfigurer();
		configurer.setBasePackage("org.springframework.lab.mybatis");
		// 只扫描该包下的接口, 结合 annotationClass 或 markerInterface 可进一步过滤
		// configurer.setAnnotationClass(org.apache.ibatis.annotations.Mapper.class);
		return configurer;
	}

	/**
	 * 巨头 3: SqlSessionTemplate (线程安全 SqlSession)
	 *
	 * 内部持有一个 SqlSession 的 JDK 代理 (sqlSessionProxy),
	 * 每次方法调用时经过 SqlSessionInterceptor:
	 *   1. SqlSessionUtils.getSqlSession() — 检查 TransactionSynchronizationManager
	 *      - 有事务 → 复用绑定的 SqlSession (事务内一级缓存生效)
	 *      - 无事务 → openSession() 创建新的
	 *   2. 执行目标方法 (selectOne/insert/update...)
	 *   3. SqlSessionUtils.closeSqlSession()
	 *      - 有事务 → 不关闭, 注册 SqlSessionSynchronization 等事务结束时关闭
	 *      - 无事务 → 立即 commit + close
	 *
	 * 断点: SqlSessionTemplate.SqlSessionInterceptor#invoke
	 *        SqlSessionUtils#getSqlSession
	 */
	@Bean
	public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		return new SqlSessionTemplate(sqlSessionFactory);
	}
}
