/**
 * 📖 对应章节：[[L04-03-编程式事务与大事务治理]]
 * 🎯 本包主题：L04-03 编程式事务与大事务治理——用 {@code TransactionTemplate} 精确圈定事务边界，
 *    并以「锁/连接持有时间」为抓手治理大事务。实验用嵌入式 HSQLDB + ConcurrentBench，在真实数据与
 *    真实并发上证明「持有时间塌缩 → 吞吐天花板抬升」「副作用移出事务边界 → 既治大事务又保一致性」。
 * 📦 核心类（阅读顺序 = 类序号）：
 *    - {@link com.leilei.lab.laboratory.l04.l04_03.L0403_01_TransactionTemplateBasicsDemo}
 *      —— 编程式事务三件套：execute 返回值 / TransactionCallbackWithoutResult / setRollbackOnly 不抛异常也回滚。
 *    - {@link com.leilei.lab.laboratory.l04.l04_03.L0403_02_BigTxLockHoldTimeDemo}
 *      —— ⭐ 大事务锁持有时间：慢 RPC 在事务内 vs 事务外，Semaphore 连接池 + ConcurrentBench 量化吞吐天花板。
 *    - {@link com.leilei.lab.laboratory.l04.l04_03.L0403_03_AfterCommitSyncDemo}
 *      —— registerSynchronization + afterCommit：发 MQ / 清缓存移出事务边界，治大事务且杜绝「回滚但消息已发」。
 *    - {@link com.leilei.lab.laboratory.l04.l04_03.L0403_04_ChunkedCommitBatchPriceDemo}
 *      —— 分片提交：超大批量改价从「一个大事务全量回滚」改为「每片独立提交」，锁持有与回滚成本恒定在一片。
 */
package com.leilei.lab.laboratory.l04.l04_03;
