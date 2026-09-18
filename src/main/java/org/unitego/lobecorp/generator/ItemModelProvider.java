package org.unitego.lobecorp.generator;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.item.SpawnEggItems;

import java.util.function.BiConsumer;

public class ItemModelProvider extends ModelProvider {
    public ItemModelProvider(PackOutput output) {
        super(output, Lobecorp.NAMESPACE);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ItemModelOutput itemModelOutput = itemModels.itemModelOutput;
        BiConsumer<Identifier, ModelInstance> modelOutput = itemModels.modelOutput;
        generateFlatItem(itemModelOutput, SpawnEggItems.SWEEPER.get(), modelOutput, "", "", "sweeper/", "", ModelTemplates.FLAT_ITEM);
        generateFlatItem(itemModelOutput, SpawnEggItems.THE_QUEEN_OF_HATRED.get(), modelOutput, "", "", "the_queen_of_hatred/", "", ModelTemplates.FLAT_ITEM);
    }

    private void generateFlatItem(ItemModelOutput itemModelOutput, Item item, BiConsumer<Identifier, ModelInstance> modelOutput,
                                  String prefix, String suffix, String texturePrefix, String textureSuffix, ModelTemplate template) {
        itemModelOutput.accept(item, ItemModelUtils.plainModel(createFlatItemModel(modelOutput, item, prefix, suffix, texturePrefix, textureSuffix, template)));
    }

    // 手持3d
    private void a3d(BiConsumer<Identifier, ModelInstance> modelOutput, Item item, String prefix, String suffix, String texturePrefix, String textureSuffix,
                     ModelTemplate template, ItemModelOutput itemModelOutput) {
        ItemModel.Unbaked model = ItemModelUtils.plainModel(createFlatItemModel(modelOutput, item, prefix, suffix, texturePrefix, textureSuffix, template));
        ItemModel.Unbaked model3d = ItemModelUtils.plainModel(getModelLocation(item, prefix, suffix));
        itemModelOutput.accept(item, ItemModelUtils.select(new DisplayContext(), model3d, ItemModelUtils.when(ItemDisplayContext.GUI, model)));
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

    public static Identifier getModelLocation(Item item, String prefix, String suffix) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key.withPath(path -> "item/" + prefix + path + suffix);
    }

    public static Material getItemTexture(Item item, String prefix, String suffix) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return new Material(id.withPath(path -> "item/" + prefix + path + suffix));
    }
}
