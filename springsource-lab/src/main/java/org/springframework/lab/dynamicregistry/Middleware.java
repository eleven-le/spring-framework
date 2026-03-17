package org.springframework.lab.dynamicregistry;

/**
 * 中间件标记接口, 用于 Scene 3 ImportSelector 按条件选择实现.
 */
public interface Middleware {

	String name();
}
