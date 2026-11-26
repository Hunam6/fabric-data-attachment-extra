package me.hunam.fabricdataattachmentextra;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import me.hunam.fabricdataattachmentextra.item_stack.StackAttachmentComponents;
import me.hunam.fabricdataattachmentextra.item_stack.StackAttachmentNetworking;
import me.hunam.fabricdataattachmentextra.item_stack.StackAttachments;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemStackAttachmentTest {
    private static int fabricCodecDecodeCalls;
    private static int fabricCodecEncodeCalls;
    private static AttachmentType<Integer> persistent;
    private static AttachmentType<Integer> transientType;
    private static AttachmentType<Integer> synced;
    private static AttachmentType<Integer> syncedTransient;
    private static AttachmentType<Integer> fabricSyncedOnly;
    private static AttachmentType<Integer> reusedFabricCodec;

    @BeforeAll
    @NullMarked
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        StackAttachmentComponents.init();
        BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace("stone")).orElseThrow().bindComponents(DataComponentMap.EMPTY);
        BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace("dirt")).orElseThrow().bindComponents(DataComponentMap.EMPTY);
        persistent = AttachmentRegistry.createPersistent(id("persistent"), Codec.INT);
        transientType = AttachmentRegistry.createDefaulted(id("transient"), () -> 9);
        synced = AttachmentRegistry.create(id("synced"), builder ->
                builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
        syncedTransient = AttachmentRegistry.create(id("synced_transient"), builder ->
                builder.syncWith(ByteBufCodecs.VAR_INT, (_, _) -> false));
        fabricSyncedOnly = AttachmentRegistry.create(id("fabric_synced"), builder ->
                builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
        StreamCodec<RegistryFriendlyByteBuf, Integer> fabricCodec = new StreamCodec<>() {
            @Override
            public Integer decode(RegistryFriendlyByteBuf buf) {
                fabricCodecDecodeCalls++;
                return ByteBufCodecs.VAR_INT.decode(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Integer value) {
                fabricCodecEncodeCalls++;
                ByteBufCodecs.VAR_INT.encode(buf, value);
            }
        };
        reusedFabricCodec = AttachmentRegistry.create(id("reused_fabric_codec"), builder ->
                builder.persistent(Codec.INT).syncWith(fabricCodec, AttachmentSyncPredicate.targetOnly()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("attachment_extra_test", path);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ItemStack networkRoundTrip(ItemStack stack) {
        var buf = buffer();
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            return ItemStack.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    void commonInitializerIsRegistered() {
        assertTrue(FabricLoader.getInstance().getEntrypointContainers("main", ModInitializer.class).stream()
                .filter(entrypoint -> entrypoint.getProvider().getMetadata().getId().equals(DataAttachmentExtra.MOD_ID))
                .anyMatch(entrypoint -> entrypoint.getEntrypoint() instanceof DataAttachmentExtra));
    }

    @Test
    void attachmentOperationsAndEvents() {
        ItemStack stack = new ItemStack(Items.STONE);
        int[] events = {0};
        stack.onAttachedSet(persistent).register((_, _) -> events[0]++);
        assertNull(stack.setAttached(persistent, 1));
        assertEquals(1, stack.getAttached(persistent));
        assertEquals(1, stack.setAttached(persistent, 2));
        assertEquals(2, stack.removeAttached(persistent));
        assertFalse(stack.hasAttached(persistent));
        assertEquals(3, events[0]);
        assertEquals(9, stack.getAttachedOrCreate(transientType));
        assertThrows(IllegalStateException.class, () -> ItemStack.EMPTY.setAttached(persistent, 1));
        assertThrows(IllegalStateException.class, () -> ItemStack.EMPTY.getAttachedOrCreate(transientType));
    }

    @Test
    void copiesSplitsAndMergeComparison() {
        ItemStack stack = new ItemStack(Items.STONE, 4);
        stack.setAttached(persistent, 1);
        stack.setAttached(transientType, 2);
        ItemStack copy = stack.copy();
        int[] events = {0};
        stack.onAttachedSet(persistent).register((_, _) -> events[0]++);
        ItemStack split = stack.split(1);
        ItemStack transmuted = stack.transmuteCopy(Items.DIRT);
        assertEquals(1, copy.getAttached(persistent));
        assertEquals(2, split.getAttached(transientType));
        assertEquals(1, transmuted.getAttached(persistent));
        assertTrue(ItemStack.isSameItemSameComponents(stack, copy));
        copy.setAttached(persistent, 5);
        assertEquals(0, events[0]);
        assertEquals(1, stack.getAttached(persistent));
        assertFalse(ItemStack.isSameItemSameComponents(stack, copy));
    }

    @Test
    void persistenceAndNetworkFiltering() {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.setAttached(persistent, 42);
        stack.setAttached(transientType, 7);
        stack.setAttached(synced, 13);
        stack.setAttached(syncedTransient, 16);
        stack.setAttached(fabricSyncedOnly, 88);
        stack.setAttached(reusedFabricCodec, 21);
        var saved = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(42, loaded.getAttached(persistent));
        assertNull(loaded.getAttached(transientType));
        assertEquals(13, loaded.getAttached(synced));
        assertEquals(21, loaded.getAttached(reusedFabricCodec));
        assertNull(loaded.getAttached(syncedTransient));

        int decodeCallsBefore = fabricCodecDecodeCalls;
        int encodeCallsBefore = fabricCodecEncodeCalls;
        ItemStack received = networkRoundTrip(stack);
        assertNull(received.getAttached(persistent));
        assertNull(received.getAttached(transientType));
        assertEquals(13, received.getAttached(synced));
        assertEquals(16, received.getAttached(syncedTransient));
        assertEquals(88, received.getAttached(fabricSyncedOnly));
        assertEquals(21, received.getAttached(reusedFabricCodec));
        assertTrue(fabricCodecDecodeCalls > decodeCallsBefore);
        assertTrue(fabricCodecEncodeCalls > encodeCallsBefore);
    }

    @Test
    void invalidSavedAttachmentsFail() {
        var codec = StackAttachmentComponents.PERSISTENT.codecOrThrow();
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"missing:type\":1}")).isError());
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"attachment_extra_test:transient\":1}")).isError());
    }

    @Test
    @SuppressWarnings("UnstableApiUsage")
    void invalidNetworkLengthsFail() {
        var buf = buffer();
        try {
            buf.writeVarInt(5);
            buf.writeVarInt(-1);
            assertThrows(DecoderException.class, () -> StackAttachmentNetworking.codec(true).decode(buf));
            buf.clear();
            buf.writeVarInt(AttachmentSync.DEFAULT_MAX_DATA_SIZE + 1);
            assertThrows(DecoderException.class, () -> StackAttachmentNetworking.codec(true).decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void networkSupportsMoreThan1024Attachments() {
        Map<AttachmentType<?>, Object> values = new HashMap<>();
        for (int i = 0; i < 1025; i++) {
            AttachmentType<Integer> type = AttachmentRegistry.create(id("many_" + i), builder ->
                    builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
            values.put(type, i);
        }
        var buf = buffer();
        try {
            var codec = StackAttachmentNetworking.codec(false);
            codec.encode(buf, new StackAttachments(values));
            assertEquals(values, codec.decode(buf).values());
        } finally {
            buf.release();
        }
    }

    @Test
    void networkRejectsUnknownUnsyncedAndWrongComponentTypes() {
        var payload = buffer();
        var buf = buffer();
        try {
            for (Identifier id : new Identifier[]{id("unknown"), transientType.identifier(), syncedTransient.identifier()}) {
                payload.clear();
                payload.writeVarInt(1);
                Identifier.STREAM_CODEC.encode(payload, id);
                buf.clear();
                buf.writeVarInt(payload.readableBytes());
                buf.writeBytes(payload);
                assertThrows(DecoderException.class, () -> StackAttachmentNetworking.codec(true).decode(buf), id.toString());
            }
        } finally {
            payload.release();
            buf.release();
        }
    }

    @Test
    void serverOnlyAttachmentsDoNotLeaveEmptyClientComponents() {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.setAttached(persistent, 42);
        stack.setAttached(transientType, 7);
        ItemStack received = networkRoundTrip(stack);
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE), received));

        ItemStack patched = new ItemStack(Items.STONE);
        var emptyPatch = DataComponentPatch.builder()
                .set(StackAttachmentComponents.PERSISTENT, StackAttachments.EMPTY)
                .set(StackAttachmentComponents.NON_PERSISTENT, StackAttachments.EMPTY)
                .build();
        patched.applyComponents(emptyPatch);
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE), patched));
        patched.applyComponentsAndValidate(emptyPatch);
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE), patched));
        patched.applyComponents(DataComponentMap.builder()
                .set(StackAttachmentComponents.PERSISTENT, StackAttachments.EMPTY)
                .set(StackAttachmentComponents.NON_PERSISTENT, StackAttachments.EMPTY)
                .build());
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE), patched));
    }

    @Test
    void transientOnlyStackLeavesNoSavedComponent() {
        ItemStack stack = new ItemStack(Items.STONE);
        stack.setAttached(transientType, 7);
        var saved = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE), loaded));
    }
}
