package me.hunam.fabricdataattachmentextra.mixin;

import me.hunam.fabricdataattachmentextra.item_stack.StackAttachmentComponents;
import me.hunam.fabricdataattachmentextra.item_stack.StackAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget.OnAttachedSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

@Mixin(ItemStack.class)
@SuppressWarnings("unused")
abstract class ItemStackMixin {
    @Unique
    private @Nullable Map<AttachmentType<?>, Event<OnAttachedSet<?>>> listeners;

    @Unique
    private static DataComponentType<StackAttachments> getItemComponent(AttachmentType<?> type) {
        return type.isPersistent() ? StackAttachmentComponents.PERSISTENT : StackAttachmentComponents.NON_PERSISTENT;
    }

    @Inject(method = {
            "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponentsAndValidate(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V"
    }, at = @At("TAIL"))
    private void removeEmptyComponents(CallbackInfo ci) {
        if (StackAttachments.EMPTY.equals(self().get(StackAttachmentComponents.PERSISTENT))) {
            self().remove(StackAttachmentComponents.PERSISTENT);
        }
        if (StackAttachments.EMPTY.equals(self().get(StackAttachmentComponents.NON_PERSISTENT))) {
            self().remove(StackAttachmentComponents.NON_PERSISTENT);
        }
    }

    @SuppressWarnings("MissingUnique")
    public <T> @Nullable T getAttached(AttachmentType<T> type) {
        return self().getOrDefault(getItemComponent(type), StackAttachments.EMPTY).get(type);
    }

    @SuppressWarnings("MissingUnique")
    public <T> @Nullable T setAttached(AttachmentType<T> type, @Nullable T value) {
        if (self().isEmpty()) throw new IllegalStateException("Cannot attach data to an empty item stack");

        DataComponentType<StackAttachments> component = getItemComponent(type);
        StackAttachments attachments = self().getOrDefault(component, StackAttachments.EMPTY);
        T oldValue = attachments.get(type);
        StackAttachments updated = attachments.with(type, value);
        if (updated.values().isEmpty()) {
            self().remove(component);
        } else {
            self().set(component, updated);
        }
        notifyListeners(type, oldValue, value);
        return oldValue;
    }

    @SuppressWarnings("MissingUnique")
    public boolean hasAttached(AttachmentType<?> type) {
        return getAttached(type) != null;
    }

    @SuppressWarnings({"unchecked", "MissingUnique"})
    public <T> Event<OnAttachedSet<T>> onAttachedSet(AttachmentType<T> type) {
        if (self().isEmpty()) throw new IllegalStateException("Cannot attach listeners to an empty item stack");
        if (listeners == null) {
            listeners = new IdentityHashMap<>();
        }
        return (Event<OnAttachedSet<T>>) (Event<?>) listeners.computeIfAbsent(type, _ ->
                (Event<OnAttachedSet<?>>) (Event<?>) EventFactory.createArrayBacked(OnAttachedSet.class,
                        (Function<OnAttachedSet<T>[], OnAttachedSet<T>>) listeners -> (oldValue, newValue) -> {
                            for (OnAttachedSet<T> listener : listeners) {
                                listener.onAttachedSet(oldValue, newValue);
                            }
                        }));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <T> void notifyListeners(AttachmentType<T> type, @Nullable T oldValue, @Nullable T newValue) {
        if (listeners == null) return;

        Event<OnAttachedSet<T>> event = (Event<OnAttachedSet<T>>) (Event<?>) listeners.get(type);
        if (event != null) event.invoker().onAttachedSet(oldValue, newValue);
    }

    @Unique
    private ItemStack self() {
        return (ItemStack) (Object) this;
    }
}
