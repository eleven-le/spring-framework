package org.springframework.lab.acactx;

/**
 * 手动 register 的普通类 (无 @Component)
 * 用于 Scene 7 验证注册顺序无关性
 */
public class ManualBean {

	@Override
	public String toString() {
		return "ManualBean@" + Integer.toHexString(hashCode());
	}
}
