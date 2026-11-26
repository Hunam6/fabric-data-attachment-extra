package me.hunam.fabricdataattachmentextra.item_stack;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.fabricmc.fabric.impl.attachment.AttachmentTypeImpl;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class StackAttachmentNetworking implements StreamCodec<RegistryFriendlyByteBuf, StackAttachments> {
    private final boolean persistent;

    private StackAttachmentNetworking(boolean persistent) {
        this.persistent = persistent;
    }

    public static StreamCodec<RegistryFriendlyByteBuf, StackAttachments> codec(boolean persistent) {
        return new StackAttachmentNetworking(persistent).apply(ByteBufCodecs.registryFriendlyLengthPrefixed(AttachmentSync.DEFAULT_MAX_DATA_SIZE));
    }

    @SuppressWarnings("ConstantValue")
    @Override
    public StackAttachments decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0) throw new DecoderException("Invalid item stack attachment count: " + count);
        Map<AttachmentType<?>, Object> values = new HashMap<>();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            AttachmentType<?> type = AttachmentRegistryImpl.get(id);
            if (!(type instanceof AttachmentTypeImpl<?> fabricType)
                    || !type.isSynced() || type.isPersistent() != persistent || fabricType.streamCodec() == null) {
                throw new DecoderException("Unknown or invalid item stack attachment type: " + id);
            }
            Object value = fabricType.streamCodec().decode(buf);
            // A third-party codec can violate the non-null contract.
            if (value == null) {
                throw new DecoderException("Null item stack attachment value: " + id);
            }
            if (values.putIfAbsent(type, value) != null) {
                throw new DecoderException("Duplicate item stack attachment type: " + id);
            }
        }
        return values.isEmpty() ? StackAttachments.EMPTY : new StackAttachments(values);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buf, StackAttachments attachments) {
        var syncedEntries = attachments.values().entrySet().stream()
                .filter(entry -> entry.getKey().isSynced())
                .toList();
        buf.writeVarInt(syncedEntries.size());
        for (var entry : syncedEntries) {
            AttachmentType<?> type = entry.getKey();
            if (!(type instanceof AttachmentTypeImpl<?> fabricType)
                    || AttachmentRegistryImpl.get(type.identifier()) != type
                    || type.isPersistent() != persistent || fabricType.streamCodec() == null) {
                throw new EncoderException("Invalid item stack attachment type: " + type.identifier());
            }
            Identifier.STREAM_CODEC.encode(buf, type.identifier());
            @SuppressWarnings("unchecked")
            var codec = (StreamCodec<? super RegistryFriendlyByteBuf, Object>) fabricType.streamCodec();
            codec.encode(buf, entry.getValue());
        }
    }
}
