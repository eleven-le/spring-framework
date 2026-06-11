package com.leilei.lab.laboratory.common.mock;

/**
 * 📖 知识点：[[01-军火库规范]]（common 基建）
 * 🎯 作用：模拟资源访问异常（故障注入 / 中断），RuntimeException 以便事务回滚实验直接使用
 * 🔗 业务场景：演示 DAO 抛异常时 @Transactional 的回滚边界、重试与降级
 */
public class MockAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MockAccessException(String message) {
        super(message);
    }

    public MockAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
