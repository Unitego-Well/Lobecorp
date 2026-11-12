package org.unitego.lobecorp.generator;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.world.item.SpawnEggItems;
import org.unitego.lobecorp.registry.world.item.LcItems;

import java.util.function.BiConsumer;

public class ItemModelProvider extends ModelProvider {
	public ItemModelProvider(PackOutput output) {
		super(output, Lobecorp.NAMESPACE);
	}

	public static Identifier getModelLocation(Item item, String prefix, String suffix) {
		Identifier key = BuiltInRegistries.ITEM.getKey(item);
		return key.withPath(path -> "item/" + prefix + path + suffix);
	}

	public static Material getItemTexture(Item item, String prefix, String suffix) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return new Material(id.withPath(path -> "item/" + prefix + path + suffix));
	}

	@Override
	protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
		ItemModelOutput itemModelOutput = itemModels.itemModelOutput;
		BiConsumer<Identifier, ModelInstance> modelOutput = itemModels.modelOutput;
		var canGui = ModelTemplates.FLAT_ITEM.create(getModelLocation(LcItems.CANNED_ENKEPHALIN.get(), "", "_gui"),
				TextureMapping.layer0(getItemTexture(LcItems.CANNED_ENKEPHALIN.get(), "", "_gui")), modelOutput);
		itemModelOutput.accept(LcItems.CANNED_ENKEPHALIN.get(), ItemModelUtils.select(new DisplayContext(),
				ItemModelUtils.plainModel(Lobecorp.id("item/canned_enkephalin_3d")),
				ItemModelUtils.when(ItemDisplayContext.GUI, ItemModelUtils.plainModel(canGui))));
		var anchorModel = ModelTemplates.FLAT_ITEM.create(getModelLocation(LcItems.WARP_ANCHOR.get(), "", ""),
				TextureMapping.layer0(getItemTexture(LcItems.WARP_ANCHOR.get(), "", "")), modelOutput);
		itemModelOutput.accept(LcItems.WARP_ANCHOR.get(), ItemModelUtils.plainModel(anchorModel));
		itemModelOutput.accept(LcItems.WARP_ANCHOR_BACKSTREETS_MIDNIGHT.get(), ItemModelUtils.plainModel(anchorModel));
		generateFlatItem(itemModelOutput, SpawnEggItems.SWEEPER.get(), modelOutput, "", "", "sweeper/", "", ModelTemplates.FLAT_ITEM);
		generateFlatItem(itemModelOutput, SpawnEggItems.THE_QUEEN_OF_HATRED.get(), modelOutput, "", "", "the_queen_of_hatred/", "", ModelTemplates.FLAT_ITEM);
	}

	private void generateFlatItem(ItemModelOutput itemModelOutput, Item item, BiConsumer<Identifier, ModelInstance> modelOutput,
	                              String prefix, String suffix, String texturePrefix, String textureSuffix, ModelTemplate template) {
		itemModelOutput.accept(item, ItemModelUtils.plainModel(createFlatItemModel(modelOutput, item, prefix, suffix, texturePrefix, textureSuffix, template)));
	}

	public Identifier createFlatItemModel(BiConsumer<Identifier, ModelInstance> modelOutput, Item item,
	                                      String prefix, String suffix, ModelTemplate template) {
		return template.create(getModelLocation(item, prefix, suffix),
				TextureMapping.layer0(getItemTexture(item, prefix, suffix)), modelOutput);
	}

	public Identifier createFlatItemModel(BiConsumer<Identifier, ModelInstance> modelOutput, Item item,
	                                      String prefix, String suffix, String texturePrefix, String textureSuffix,
	                                      ModelTemplate template) {
		return template.create(getModelLocation(item, prefix, suffix),
				TextureMapping.layer0(getItemTexture(item, texturePrefix, textureSuffix)), modelOutput);
	}
}
