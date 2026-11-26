package me.hunam.fabricdataattachmentextra;

import me.hunam.fabricdataattachmentextra.item_stack.StackAttachmentComponents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public final class DataAttachmentExtra implements ModInitializer {
    public static final String MOD_ID = "fabric-data-attachment-extra";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DataAttachmentExtra.MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        StackAttachmentComponents.init();
    }
}
