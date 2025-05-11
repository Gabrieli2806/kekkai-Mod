package com.g2806.kekkai;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public class KekkaiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(Kekkai.SHIMENAWA, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(Kekkai.OFUDA, RenderLayer.getCutout());
    }
}
