package com.leilei.lab.laboratory.l01.l01_01;

import java.util.Optional;

import com.leilei.lab.laboratory.common.bench.BenchReport;
import com.leilei.lab.laboratory.common.bench.ConcurrentBench;
import com.leilei.lab.laboratory.common.dao.MockProductDao;
import com.leilei.lab.laboratory.common.domain.Product;
import com.leilei.lab.laboratory.common.mock.MockDataFactory;
import com.leilei.lab.laboratory.common.mock.MockDataSet;

import org.springframework.core.SpringVersion;

/**
 * 📖 知识点：[[L01-01-模块地图与生态定位#🔬 源码导读]]（SpringVersion + 模块地图落地）
 * 🎯 作用：把模块地图与依赖体检串成一次可运行的「产品中心启动自检」：
 *         打印 codebase 版本 → 渲染热路径模块地图 → 跑一次依赖一致性体检 → 顺带压测探针开销。
 * 🔗 业务场景：古茗商品详情查询（MockProductDao 回 MySQL）作为热路径的业务锚点，
 *         证明这张地图不是 PPT 上的框图，而是请求真实穿过的那几层。
 */
public final class L0101_03_ProductQueryStackDemo {

	private L0101_03_ProductQueryStackDemo() {
	}

	public static void main(String[] args) {
		// 1) codebase 版本：源码直连返回 null，jar 部署返回 5.3.39
		System.out.println("SpringVersion.getVersion() = " + SpringVersion.getVersion());

		// 2) 业务锚点：一次商品详情查询，落在热路径最底层的 JDBC/MySQL（MockProductDao 注入 MySQL 级延迟）
		MockDataSet dataSet = MockDataFactory.seed(30, 5);
		MockProductDao productDao = new MockProductDao(dataSet);
		Optional<Product> detail = productDao.findById(100001L);
		System.out.println("商品详情查询命中: " + detail.map(Product::getName).orElse("<miss>"));

		// 3) 渲染商品查询热路径的模块地图
		L0101_01_ModuleMapProbe probe = new L0101_01_ModuleMapProbe();
		System.out.println(probe.renderTable());

		// 4) 启动期依赖一致性体检
		L0101_02_StartupDependencyConsistencyGuard guard =
				new L0101_02_StartupDependencyConsistencyGuard(probe);
		System.out.println(guard.assertConsistentOrThrow());

		// 5) 探针纯反射、零 IO，热路径自检放在 refresh 早期也不该成为启动瓶颈——压测佐证
		BenchReport report = ConcurrentBench.run("module-map-probe", 16, 2000, probe::probeProductQueryStack);
		System.out.println(report.prettyPrint());
	}

}
