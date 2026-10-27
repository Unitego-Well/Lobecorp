package org.unitego.lobecorp.util.photon.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 只读索引的回归：用真实资源验证对象分类、子特效链接与失败隔离，不创建FX运行态。
public class PhotonEffectInventoryUtilTest {
	@Test
	public void classifiesLaserObjectsWithoutBuildingRuntime() throws Exception {
		var root = projectRoot().resolve("src/main/resources/assets/lobecorp/fx");
		var file = root.resolve("the_queen_of_hatred/laser.fx");
		var entry = PhotonEffectInventoryUtil.inspect(file, root);

		assertTrue(entry.readable(), "激光定义必须可读。" + entry.failure());
		assertEquals("the_queen_of_hatred/laser", entry.id());
		assertEquals(13, entry.objects(), "激光定义当前有13个对象。");
		assertEquals(4, entry.models(), "激光定义当前有4个模型发射器。");
		assertEquals(6, entry.emitters(), "激光定义当前有6个粒子发射器。");
		assertEquals(1, entry.spawners(), "激光定义当前有1个子发射器。");
		assertEquals(1, entry.controllers(), "激光定义当前有1个命名控制器。");
		assertTrue(entry.decodedBytes() > entry.sourceBytes(), "解压体积必须大于压缩体积。");
		assertTrue(entry.cost() > 0, "开销权重必须为正。");
	}

	@Test
	public void scanFindsLaserDefinition() throws Exception {
		var root = projectRoot().resolve("src/main/resources/assets/lobecorp/fx");
		var entries = PhotonEffectInventoryUtil.scan(root);

		assertFalse(entries.isEmpty(), "资源目录必须包含特效定义。");
		assertTrue(entries.stream().anyMatch(entry -> entry.id().equals("the_queen_of_hatred/laser")),
				"扫描结果必须包含激光定义。");
		assertTrue(entries.stream().allMatch(entry -> entry.file() != null
						&& entry.file().toString().endsWith(".fx")), "扫描结果只应包含 .fx 文件。");
		assertTrue(entries.stream().allMatch(entry -> !entry.id().isEmpty()), "每条记录都必须有ID。");
	}

	@Test
	public void inspectReportsFailureInsteadOfThrowing() throws Exception {
		var root = projectRoot().resolve("src/main/resources/assets/lobecorp/fx");
		var broken = Files.createTempFile("photon-inventory", ".fx");

		try {
			Files.writeString(broken, "not a gzip nbt payload");
			var entry = PhotonEffectInventoryUtil.inspect(broken, root);

			assertNotNull(entry.failure(), "不可读文件必须记录失败原因。");
			assertFalse(entry.readable());
			assertEquals(0, entry.objects());
		} finally {
			Files.deleteIfExists(broken);
		}
	}

	@Test
	public void scanOfMissingDirectoryReturnsEmpty() throws Exception {
		var missing = projectRoot().resolve("src/main/resources/assets/lobecorp/does-not-exist");
		assertEquals(0, PhotonEffectInventoryUtil.scan(missing).size());
	}

	/// 从工作目录向上找资源锚点；IDEA 运行测试时的工作目录可能是模块或仓库根。
	private static Path projectRoot() {
		var anchor = Path.of("src/main/resources/assets/lobecorp/fx");
		var current = Path.of("").toAbsolutePath();

		while (current != null) {
			if (Files.isDirectory(current.resolve(anchor))) {
				return current;
			}

			current = current.getParent();
		}

		throw new IllegalStateException("测试必须由当前项目的IDEA运行，当前目录=" + Path.of("").toAbsolutePath());
	}
}
