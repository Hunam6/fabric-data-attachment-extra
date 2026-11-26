package me.hunam.fabricdataattachmentextra.item_stack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public record StackAttachments(Map<AttachmentType<?>, Object> values) {
    public static final StackAttachments EMPTY = new StackAttachments(Map.of());

    public StackAttachments {
        values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(AttachmentType<T> type) {
        return (T) values.get(type);
    }

    public <T> StackAttachments with(AttachmentType<T> type, @Nullable T value) {
        Map<AttachmentType<?>, Object> result = new HashMap<>(values);
        if (value == null) {
            result.remove(type);
        } else {
            result.put(type, value);
        }
        return result.isEmpty() ? EMPTY : new StackAttachments(result);
    }
}
