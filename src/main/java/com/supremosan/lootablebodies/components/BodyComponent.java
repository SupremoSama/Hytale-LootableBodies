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

public class BodyComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BodyComponent> CODEC;

    @Nonnull
    private String playerSkinSerialized;

    public BodyComponent(PlayerSkinComponent toCopyFrom) {
        this.playerSkinSerialized = this.stringifyPlayerSkin(toCopyFrom.getPlayerSkin());
    }

    public BodyComponent(String playerSkinSerialized) {
        this.playerSkinSerialized = playerSkinSerialized;
    }

    public BodyComponent() {
    }

    public static ComponentType<EntityStore, BodyComponent> getComponentType() {
        return LootableBodies.bodyComponentType;
    }

    public String toString() {
        return this.playerSkinSerialized;
    }

    private String stringifyPlayerSkin(PlayerSkin skin) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(skin.bodyCharacteristic);
        stringBuilder.append(";");
        stringBuilder.append(skin.ears);
        stringBuilder.append(";");
        stringBuilder.append(skin.skinFeature);
        stringBuilder.append(";");
        stringBuilder.append(skin.eyes);
        stringBuilder.append(";");
        stringBuilder.append(skin.eyebrows);
        stringBuilder.append(";");
        stringBuilder.append(skin.gloves);
        stringBuilder.append(";");
        stringBuilder.append(skin.overpants);
        stringBuilder.append(";");
        stringBuilder.append(skin.pants);
        stringBuilder.append(";");
        stringBuilder.append(skin.shoes);
        stringBuilder.append(";");
        stringBuilder.append(skin.cape);
        stringBuilder.append(";");
        stringBuilder.append(skin.earAccessory);
        stringBuilder.append(";");
        stringBuilder.append(skin.face);
        stringBuilder.append(";");
        stringBuilder.append(skin.faceAccessory);
        stringBuilder.append(";");
        stringBuilder.append(skin.facialHair);
        stringBuilder.append(";");
        stringBuilder.append(skin.haircut);
        stringBuilder.append(";");
        stringBuilder.append(skin.headAccessory);
        stringBuilder.append(";");
        stringBuilder.append(skin.mouth);
        stringBuilder.append(";");
        stringBuilder.append(skin.overtop);
        stringBuilder.append(";");
        stringBuilder.append(skin.undertop);
        stringBuilder.append(";");
        stringBuilder.append(skin.underwear);
        return stringBuilder.toString();
    }

    public PlayerSkin toPlayerSkin() {
        String[] split = this.playerSkinSerialized.split(";");
        PlayerSkin playerSkin = new PlayerSkin();
        playerSkin.bodyCharacteristic = split[0];
        playerSkin.ears = split[1];
        playerSkin.skinFeature = split[2];
        playerSkin.eyes = split[3];
        playerSkin.eyebrows = split[4];
        playerSkin.gloves = split[5];
        playerSkin.overpants = split[6];
        playerSkin.pants = split[7];
        playerSkin.shoes = split[8];
        playerSkin.cape = split[9];
        playerSkin.earAccessory = split[10];
        playerSkin.face = split[11];
        playerSkin.faceAccessory = split[12];
        playerSkin.facialHair = split[13];
        playerSkin.haircut = split[14];
        playerSkin.headAccessory = split[15];
        playerSkin.mouth = split[16];
        playerSkin.overtop = split[17];
        playerSkin.undertop = split[18];
        playerSkin.underwear = split[19];
        return playerSkin;
    }

    @Nullable
    public Component<EntityStore> clone() {
        return new BodyComponent(this.playerSkinSerialized);
    }

    static {
        CODEC = BuilderCodec.builder(BodyComponent.class, BodyComponent::new).appendInherited(new KeyedCodec("PlayerSkinSerialized", Codec.STRING), (e, s) -> e.playerSkinSerialized = s, (e) -> e.playerSkinSerialized, (e, p) -> e.playerSkinSerialized = p.playerSkinSerialized).documentation("Serialized version of the PlayerSkin").add().build();
    }
}
