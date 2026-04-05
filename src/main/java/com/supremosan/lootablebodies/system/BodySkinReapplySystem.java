package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.listener.CosmeticListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;

public class BodySkinReapplySystem extends HolderSystem<EntityStore> {

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason addReason, @Nonnull Store<EntityStore> store) {
        BodyComponent graveComp = holder.getComponent(BodyComponent.getComponentType());
        if (graveComp == null) return;

        PlayerSkin skin = graveComp.toPlayerSkin();

        PlayerSkinComponent skinComp = new PlayerSkinComponent(skin);
        holder.putComponent(PlayerSkinComponent.getComponentType(), skinComp);
        skinComp.setNetworkOutdated();

        ModelComponent modelComp = holder.getComponent(ModelComponent.getComponentType());
        if (modelComp == null) return;

        Model baseModel = modelComp.getModel();
        if (baseModel == null) return;

        try {
            float scale = baseModel.getScale();
            if (scale <= 0f) return;
        } catch (Exception ignored) {
            return;
        }

        try {
            Model cosmeticModel = CosmeticListener.buildCosmeticModel(
                    baseModel,
                    skin,
                    EnumSet.noneOf(Cosmetic.class),
                    "Body_CustomModel",
                    null
            );
            holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(cosmeticModel));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason removeReason, @Nonnull Store<EntityStore> store) {
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return BodyComponent.getComponentType();
    }
}