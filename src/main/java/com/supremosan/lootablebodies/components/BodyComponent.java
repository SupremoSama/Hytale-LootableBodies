package com.supremosan.lootablebodies.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.lootablebodies.LootableBodies;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class BodyComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BodyComponent> CODEC;

    @Nonnull
    String playerSkinSerialized;

    @Nonnull
    public String ownerUuidSerialized;

    @Nonnull
    public BodySource bodySource;

    public BodyComponent(PlayerSkinComponent toCopyFrom, UUID ownerUuid, BodySource bodySource) {
        this.playerSkinSerialized = this.stringifyPlayerSkin(toCopyFrom.getPlayerSkin());
        this.ownerUuidSerialized = ownerUuid != null ? ownerUuid.toString() : "";
        this.bodySource = bodySource != null ? bodySource : BodySource.DEATH;
    }

    public BodyComponent(String playerSkinSerialized, String ownerUuidSerialized, String bodySourceSerialized) {
        this.playerSkinSerialized = playerSkinSerialized != null ? playerSkinSerialized : "";
        this.ownerUuidSerialized = ownerUuidSerialized != null ? ownerUuidSerialized : "";
        this.bodySource = parseBodySource(bodySourceSerialized);
    }

    public BodyComponent() {
        this.playerSkinSerialized = "";
        this.ownerUuidSerialized = "";
        this.bodySource = BodySource.DEATH;
    }

    public static ComponentType<EntityStore, BodyComponent> getComponentType() {
        return LootableBodies.bodyComponentType;
    }

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

    private String stringifyPlayerSkin(PlayerSkin skin) {
        return skin.bodyCharacteristic +
                ";" +
                skin.ears +
                ";" +
                skin.skinFeature +
                ";" +
                skin.eyes +
                ";" +
                skin.eyebrows +
                ";" +
                skin.gloves +
                ";" +
                skin.overpants +
                ";" +
                skin.pants +
                ";" +
                skin.shoes +
                ";" +
                skin.cape +
                ";" +
                skin.earAccessory +
                ";" +
                skin.face +
                ";" +
                skin.faceAccessory +
                ";" +
                skin.facialHair +
                ";" +
                skin.haircut +
                ";" +
                skin.headAccessory +
                ";" +
                skin.mouth +
                ";" +
                skin.overtop +
                ";" +
                skin.undertop +
                ";" +
                skin.underwear;
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
        return new BodyComponent(this.playerSkinSerialized, this.ownerUuidSerialized, this.bodySource.name());
    }

    static {
        CODEC = BuilderCodec.builder(BodyComponent.class, BodyComponent::new)
                .appendInherited(
                        new KeyedCodec<>("PlayerSkinSerialized", Codec.STRING),
                        (e, s) -> e.playerSkinSerialized = s,
                        e -> e.playerSkinSerialized,
                        (e, p) -> e.playerSkinSerialized = p.playerSkinSerialized
                )
                .documentation("Serialized version of the PlayerSkin")
                .add()
                .appendInherited(
                        new KeyedCodec<>("OwnerUuidSerialized", Codec.STRING),
                        (e, s) -> e.ownerUuidSerialized = s,
                        e -> e.ownerUuidSerialized,
                        (e, p) -> e.ownerUuidSerialized = p.ownerUuidSerialized
                )
                .documentation("Serialized owner UUID for body lookup")
                .add()
                .appendInherited(
                        new KeyedCodec<>("BodySourceSerialized", Codec.STRING),
                        (e, s) -> e.bodySource = parseBodySource(s),
                        e -> e.bodySource.name(),
                        (e, p) -> e.bodySource = p.bodySource
                )
                .documentation("Source of the body: DEATH or LOGOUT")
                .add()
                .build();
    }
}