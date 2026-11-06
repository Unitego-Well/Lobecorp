package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonModelMaterialPreparationTest {
	private static final String MATERIAL_ID = "surface";
	private static final String VALID = "{type:'example:material',data:{nested:{value:1}}}";

	@Test
	public void preparesSyntaxLazilyOncePerPreparedAndReturnsDeepCopies() throws Exception {
		var prepared = prepared(VALID);
		var blueprint = blueprint(prepared);
		assertNull(parsed(blueprint));

		var first = prepared.materialData(MATERIAL_ID);
		var syntax = parsed(blueprint);
		assertNotNull(syntax);
		assertNotSame(syntax, first);
		first.getCompoundOrEmpty("data").getCompoundOrEmpty("nested").putInt("value", 9);
		first.putString("type", "changed");

		var second = prepared.materialData(MATERIAL_ID);
		assertSame(syntax, parsed(blueprint));
		assertNotSame(first, second);
		assertEquals(PhotonModelAssetUtil.material(VALID), second);
		assertNotSame(syntax.getCompoundOrEmpty("data"), second.getCompoundOrEmpty("data"));
	}

	@Test
	public void isolatesDefinitionsAndDoesNotIncludeLazyStateInValueEquality() throws Exception {
		var first = prepared(VALID);
		var same = new PhotonModelTopologyUtil.Prepared(first.definition(), first.groups());
		var different = prepared("{type:'example:other',data:{nested:{value:2}}}");
		assertEquals(first, same);
		assertEquals(first.hashCode(), same.hashCode());
		assertNotSame(blueprint(first), blueprint(same));

		first.materialData(MATERIAL_ID);
		assertNull(parsed(blueprint(same)));
		assertEquals(first, same);
		assertEquals(PhotonModelAssetUtil.material(VALID), same.materialData(MATERIAL_ID));
		assertNotEquals(first.materialData(MATERIAL_ID), different.materialData(MATERIAL_ID));
	}

	@Test
	public void retriesInvalidSyntaxWithoutPublishingFailureAndNewDefinitionRecovers() throws Exception {
		var invalid = prepared("{type:");
		var blueprint = blueprint(invalid);
		var first = assertThrows(IOException.class, () -> invalid.materialData(MATERIAL_ID));
		assertEquals("模型材质SNBT无效。", first.getMessage());
		assertNotNull(first.getCause());
		assertNull(parsed(blueprint));

		var retry = assertThrows(IOException.class, () -> invalid.materialData(MATERIAL_ID));
		assertNotSame(first, retry);
		assertNotSame(first.getCause(), retry.getCause());
		assertNull(parsed(blueprint));
		assertEquals(PhotonModelAssetUtil.material(VALID), prepared(VALID).materialData(MATERIAL_ID));
	}

	@Test
	public void keepsMissingTypeAndConfigurationValidationLazyAndUncached() throws Exception {
		for (var text : List.of("{data:{}}", "{type:'example:material'}", "{type:'',data:{}}")) {
			var prepared = prepared(text);
			var failure = assertThrows(IOException.class, () -> prepared.materialData(MATERIAL_ID));
			assertEquals("模型材质缺少类型或配置。", failure.getMessage());
			assertNull(failure.getCause());
			assertNull(parsed(blueprint(prepared)));
		}
	}

	private static PhotonModelTopologyUtil.Prepared prepared(String text) throws IOException {
		var material = new PhotonModelDefinition.Material("", text, PhotonModelDefinition.Tint.NONE, false);
		var definition = new PhotonModelDefinition(new JsonObject(), 1, 1, 0,
				List.of(), List.of(), List.of(), Map.of(MATERIAL_ID, material),
				List.of(), Map.of(), List.of(), List.of());
		return PhotonModelTopologyUtil.prepare(definition);
	}

	private static Object blueprint(PhotonModelTopologyUtil.Prepared prepared) throws ReflectiveOperationException {
		var field = PhotonModelTopologyUtil.Prepared.class.getDeclaredField("materials");
		field.setAccessible(true);
		return ((Map<?, ?>) field.get(prepared)).get(MATERIAL_ID);
	}

	private static CompoundTag parsed(Object blueprint) throws ReflectiveOperationException {
		var field = PhotonModelAssetUtil.MaterialBlueprint.class.getDeclaredField("prepared");
		field.setAccessible(true);
		return (CompoundTag) field.get(blueprint);
	}
}
