package com.supremosan.lootablebodies.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.lootablebodies.LootableBodies;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BodyComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<BodyComponent> CODEC;

    @Nonnull
    public String playerSkinSerialized;

    @Nonnull
    public String ownerUuidSerialized;

    @Nonnull
    public BodySource bodySource;

    @Nonnull
    private ItemStack[] storageItems;

    @Nonnull
    private ItemStack[] hotbarItems;

    @Nonnull
    private ItemStack[] backpackItems;

    @Nonnull
    private ItemStack[] armorItems;

    public BodyComponent(@Nonnull String playerSkinSerialized, @Nonnull String ownerUuidSerialized, @Nonnull String bodySourceSerialized) {
        this.playerSkinSerialized = playerSkinSerialized;
        this.ownerUuidSerialized = ownerUuidSerialized;
        this.bodySource = parseBodySource(bodySourceSerialized);
        this.storageItems = new ItemStack[0];
        this.hotbarItems = new ItemStack[0];
        this.backpackItems = new ItemStack[0];
        this.armorItems = new ItemStack[0];
    }

    public BodyComponent() {
        this.playerSkinSerialized = "";
        this.ownerUuidSerialized = "";
        this.bodySource = BodySource.DEATH;
        this.storageItems = new ItemStack[0];
        this.hotbarItems = new ItemStack[0];
        this.backpackItems = new ItemStack[0];
        this.armorItems = new ItemStack[0];
    }

    public static ComponentType<EntityStore, BodyComponent> getComponentType() {
        return LootableBodies.bodyComponentType;
    }

    public ItemStack[] getStorageItems() { return storageItems; }
    public ItemStack[] getHotbarItems() { return hotbarItems; }
    public ItemStack[] getBackpackItems() { return backpackItems; }
    public ItemStack[] getArmorItems() { return armorItems; }

    public String toString() {
        return this.playerSkinSerialized;
    }

    private static BodySource parseBodySource(String value) {
        if (value == null || value.isEmpty()) return BodySource.DEATH;
        try {
            return BodySource.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return BodySource.DEATH;
        }
    }

    public PlayerSkin toPlayerSkin() {
        String[] split = this.playerSkinSerialized.split(";");
        PlayerSkin playerSkin = new PlayerSkin();
        if (split.length > 0) playerSkin.bodyCharacteristic = split[0];
        if (split.length > 1) playerSkin.ears = split[1];
        if (split.length > 2) playerSkin.skinFeature = split[2];
        if (split.length > 3) playerSkin.eyes = split[3];
        if (split.length > 4) playerSkin.eyebrows = split[4];
        if (split.length > 5) playerSkin.gloves = split[5];
        if (split.length > 6) playerSkin.overpants = split[6];
        if (split.length > 7) playerSkin.pants = split[7];
        if (split.length > 8) playerSkin.shoes = split[8];
        if (split.length > 9) playerSkin.cape = split[9];
        if (split.length > 10) playerSkin.earAccessory = split[10];
        if (split.length > 11) playerSkin.face = split[11];
        if (split.length > 12) playerSkin.faceAccessory = split[12];
        if (split.length > 13) playerSkin.facialHair = split[13];
        if (split.length > 14) playerSkin.haircut = split[14];
        if (split.length > 15) playerSkin.headAccessory = split[15];
        if (split.length > 16) playerSkin.mouth = split[16];
        if (split.length > 17) playerSkin.overtop = split[17];
        if (split.length > 18) playerSkin.undertop = split[18];
        if (split.length > 19) playerSkin.underwear = split[19];
        return playerSkin;
    }

    @Nullable
    public Component<EntityStore> clone() {
        BodyComponent copy = new BodyComponent(playerSkinSerialized, ownerUuidSerialized, bodySource.name());
        copy.storageItems = this.storageItems;
        copy.hotbarItems = this.hotbarItems;
        copy.backpackItems = this.backpackItems;
        copy.armorItems = this.armorItems;
        return copy;
    }

    static {
        ArrayCodec<ItemStack> itemArrayCodec = new ArrayCodec<>(ItemStack.CODEC, ItemStack[]::new);

        CODEC = BuilderCodec.builder(BodyComponent.class, BodyComponent::new)
                .append(
                        new KeyedCodec<>("PlayerSkinSerialized", Codec.STRING),
                        (e, s) -> e.playerSkinSerialized = s,
                        e -> e.playerSkinSerialized
                )
                .documentation("Serialized version of the PlayerSkin")
                .add()
                .append(
                        new KeyedCodec<>("OwnerUuidSerialized", Codec.STRING),
                        (e, s) -> e.ownerUuidSerialized = s,
                        e -> e.ownerUuidSerialized
                )
                .documentation("Serialized owner UUID for body lookup")
                .add()
                .append(
                        new KeyedCodec<>("BodySourceSerialized", Codec.STRING),
                        (e, s) -> e.bodySource = parseBodySource(s),
                        e -> e.bodySource.name()
                )
                .documentation("Source of the body: DEATH or LOGOUT")
                .add()
                .append(
                        new KeyedCodec<>("StorageItems", itemArrayCodec),
                        (e, v) -> e.storageItems = v != null ? v : new ItemStack[0],
                        e -> e.storageItems
                )
                .documentation("Snapshot of the player storage inventory at body creation")
                .add()
                .append(
                        new KeyedCodec<>("HotbarItems", itemArrayCodec),
                        (e, v) -> e.hotbarItems = v != null ? v : new ItemStack[0],
                        e -> e.hotbarItems
                )
                .documentation("Snapshot of the player hotbar inventory at body creation")
                .add()
                .append(
                        new KeyedCodec<>("BackpackItems", itemArrayCodec),
                        (e, v) -> e.backpackItems = v != null ? v : new ItemStack[0],
                        e -> e.backpackItems
                )
                .documentation("Snapshot of the player backpack inventory at body creation")
                .add()
                .append(
                        new KeyedCodec<>("ArmorItems", itemArrayCodec),
                        (e, v) -> e.armorItems = v != null ? v : new ItemStack[0],
                        e -> e.armorItems
                )
                .documentation("Snapshot of the player armor inventory at body creation")
                .add()
                .build();
    }
}