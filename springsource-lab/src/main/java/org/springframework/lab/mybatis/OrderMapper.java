package org.springframework.lab.mybatis;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 订单 Mapper — 纯接口, 没有实现类。
 *
 * 运行时由 MyBatis 生成 JDK 动态代理 (MapperProxy);
 * 而在 Spring 容器中, 由 MapperFactoryBean 负责把这个代理注册为一个 Bean。
 *
 * 关键路径:
 *   MapperScannerConfigurer 扫描到此接口
 *   → 注册 MapperFactoryBean&lt;OrderMapper&gt; 的 BeanDefinition
 *   → getBean() 时 MapperFactoryBean.getObject()
 *   → SqlSessionTemplate.getMapper(OrderMapper.class)
 *   → Configuration.getMapper() → MapperProxyFactory.newInstance()
 *   → 返回 JDK Proxy
 */
public interface OrderMapper {

	@Select("SELECT id, order_no AS orderNo, user_id AS userId, amount, status FROM t_order WHERE id = #{id}")
	Order findById(@Param("id") Long id);

	@Select("SELECT id, order_no AS orderNo, user_id AS userId, amount, status FROM t_order WHERE user_id = #{userId}")
	List<Order> findByUserId(@Param("userId") Long userId);

	@Insert("INSERT INTO t_order (order_no, user_id, amount, status) VALUES (#{orderNo}, #{userId}, #{amount}, #{status})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Order order);

	@Update("UPDATE t_order SET status = #{status} WHERE id = #{id}")
	int updateStatus(@Param("id") Long id, @Param("status") String status);
}
