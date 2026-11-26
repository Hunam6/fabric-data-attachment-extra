# Fabric Data Attachment Extra

A *Fabric Data Attachments API* extension that adds support for attaching attachments to item stacks.

## Usage

Register attachment types as usual:

```java
public static final AttachmentType<Integer> CHARGE = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("modid", "charge"),
        builder -> builder.persistent(Codec.INT).initializer(() -> 0)
);

int charge = stack.getAttachedOrCreate(CHARGE);
stack.setAttached(CHARGE, charge + 1);
```

## Persistence

The attachment storage is backed by item data components. Attachments are copied with stacks and affect stack merging.

## Synchronization

Use Fabric's `syncWith` to synchronize an attachment. **Its predicate is ignored for item stacks** so every client receiving the stack can see its attachments.

## License

[MIT License](LICENSE)
