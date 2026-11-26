package me.hunam.fabricdataattachmentextra.item_stack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import static me.hunam.fabricdataattachmentextra.DataAttachmentExtra.id;

@SuppressWarnings("UnstableApiUsage")
public abstract class StackAttachmentComponents {
    public static final DataComponentType<StackAttachments> NON_PERSISTENT = register("non_persistent_attachments",
            DataComponentType.<StackAttachments>builder()
                    .networkSynchronized(StackAttachmentNetworking.codec(false))
                    .build());

    @SuppressWarnings("NullableProblems")
    public static final DataComponentType<StackAttachments> PERSISTENT = register("persistent_attachments",
            DataComponentType.<StackAttachments>builder()
                    .persistent(Codec.<AttachmentType<?>, Object>dispatchedMap(
                            Identifier.CODEC.comapFlatMap(id -> {
                                AttachmentType<?> type = AttachmentRegistryImpl.get(id);
                                if (type == null) return DataResult.error(() -> "Unknown attachment type: " + id);
                                if (!type.isPersistent())
                                    return DataResult.error(() -> "Non-persistent attachment type: " + id);
                                return DataResult.success(type);
                            }, AttachmentType::identifier),
                            AttachmentType::persistenceCodec
                    ).xmap(StackAttachments::new, StackAttachments::values))
                    .networkSynchronized(StackAttachmentNetworking.codec(true))
                    .build());

    private static DataComponentType<StackAttachments> register(String name, DataComponentType<StackAttachments> component) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id(name), component);
    }

    public static void init() {
    }
}
