package com.supremosan.lootablebodies.listener;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.supremosan.lootablebodies.cosmetic.CosmeticUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CosmeticListener {

    private static final double DEFAULT_SCALE = 1.0D;

    private CosmeticListener() {
    }

    @Nonnull
    public static Model buildCosmeticModel(@Nonnull Model base,
                                           @Nonnull PlayerSkin skin,
                                           @Nonnull Set<Cosmetic> hiddenCosmetics,
                                           @Nonnull String modelName,
                                           @Nullable Map<String, ModelAttachment> extras) {
        String bodyGradientId = "";
        String bodyGradientSet = null;
        String bodyTexture = null;

        if (skin.bodyCharacteristic != null) {
            String[] bodyParts = skin.bodyCharacteristic.split("\\.");
            if (bodyParts.length > 1) bodyGradientId = bodyParts[1];
            CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
            PlayerSkinPart bodyPart = registry.getBodyCharacteristics().get(bodyParts[0]);
            if (bodyPart != null) {
                bodyGradientSet = bodyPart.getGradientSet();
                bodyTexture = bodyPart.getGreyscaleTexture();
            }
        }

        List<ModelAttachment> attachments = new ArrayList<>();
        restoreSkinAttachments(attachments, skin, hiddenCosmetics, bodyGradientId);
        if (extras != null) attachments.addAll(extras.values());

        return new Model(
                modelName,
                base.getScale(),
                base.getRandomAttachmentIds(),
                attachments.toArray(new ModelAttachment[0]),
                base.getBoundingBox(),
                base.getModel(),
                bodyTexture != null ? bodyTexture : base.getTexture(),
                bodyGradientSet != null ? bodyGradientSet : base.getGradientSet(),
                !bodyGradientId.isEmpty() ? bodyGradientId : base.getGradientId(),
                base.getEyeHeight(),
                base.getCrouchOffset(),
                base.getSittingOffset(),
                base.getSleepingOffset(),
                base.getAnimationSetMap(),
                base.getCamera(),
                base.getLight(),
                base.getParticles(),
                base.getTrails(),
                base.getPhysicsValues(),
                base.getDetailBoxes(),
                base.getPhobia(),
                base.getPhobiaModelAssetId()
        );
    }

    private static void restoreSkinAttachments(@Nonnull List<ModelAttachment> attachments,
                                               @Nonnull PlayerSkin skin,
                                               @Nonnull Set<Cosmetic> hiddenCosmetics,
                                               @Nonnull String bodyGradientId) {
        if (skin.bodyCharacteristic == null) return;

        CosmeticRegistry registry = CosmeticsModule.get().getRegistry();

        addSkinPart(attachments, skin.bodyCharacteristic, registry.getBodyCharacteristics(), bodyGradientId, null);
        addHaircutPart(attachments, skin, registry, bodyGradientId);
        addSkinPart(attachments, skin.eyebrows, registry.getEyebrows(), bodyGradientId, null);
        addSkinPart(attachments, skin.eyes, registry.getEyes(), bodyGradientId, null);
        addSkinPart(attachments, skin.underwear, registry.getUnderwear(), bodyGradientId, null);
        addSkinPart(attachments, skin.skinFeature, registry.getSkinFeatures(), bodyGradientId, null);

        addSkinPart(attachments, skin.face, registry.getFaces(), bodyGradientId, bodyGradientId);
        addSkinPart(attachments, skin.ears, registry.getEars(), bodyGradientId, bodyGradientId);
        addSkinPart(attachments, skin.mouth, registry.getMouths(), bodyGradientId, bodyGradientId);

        if (!hiddenCosmetics.contains(Cosmetic.FacialHair))
            addSkinPart(attachments, skin.facialHair, registry.getFacialHairs(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Cape))
            addSkinPart(attachments, skin.cape, registry.getCapes(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.FaceAccessory))
            addSkinPart(attachments, skin.faceAccessory, registry.getFaceAccessories(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Gloves))
            addSkinPart(attachments, skin.gloves, registry.getGloves(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.HeadAccessory))
            addSkinPart(attachments, skin.headAccessory, registry.getHeadAccessories(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Overpants))
            addSkinPart(attachments, skin.overpants, registry.getOverpants(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Overtop))
            addSkinPart(attachments, skin.overtop, registry.getOvertops(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Pants))
            addSkinPart(attachments, skin.pants, registry.getPants(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Shoes))
            addSkinPart(attachments, skin.shoes, registry.getShoes(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.Undertop))
            addSkinPart(attachments, skin.undertop, registry.getUndertops(), bodyGradientId, null);
        if (!hiddenCosmetics.contains(Cosmetic.EarAccessory))
            addSkinPart(attachments, skin.earAccessory, registry.getEarAccessories(), bodyGradientId, null);
    }

    private static void addHaircutPart(@Nonnull List<ModelAttachment> attachments,
                                       @Nonnull PlayerSkin skin,
                                       @Nonnull CosmeticRegistry registry,
                                       @Nonnull String bodyGradientId) {
        if (skin.haircut == null) return;
        String[] parts = skin.haircut.split("\\.");
        PlayerSkinPart part = registry.getHaircuts().get(parts[0]);
        if (part == null) return;

        ModelAttachment attachment = CosmeticUtils.resolveAttachment(part, parts, bodyGradientId);

        if (part.doesRequireGenericHaircut() && part.getHairType() != null && isHalfCoveringHelmetEquipped(skin, registry)) {
            PlayerSkinPart generic = registry.getHaircuts().get("Generic" + part.getHairType());
            if (generic != null) {
                attachment = new ModelAttachment(
                        generic.getModel(),
                        generic.getGreyscaleTexture(),
                        attachment.getGradientSet(),
                        attachment.getGradientId(),
                        DEFAULT_SCALE
                );
            }
        }

        attachments.add(attachment);
    }

    private static boolean isHalfCoveringHelmetEquipped(@Nonnull PlayerSkin skin,
                                                        @Nonnull CosmeticRegistry registry) {
        if (skin.headAccessory == null) return false;
        String[] parts = skin.headAccessory.split("\\.");
        PlayerSkinPart headAcc = registry.getHeadAccessories().get(parts[0]);
        return headAcc != null
                && headAcc.getHeadAccessoryType() == PlayerSkinPart.HeadAccessoryType.HalfCovering;
    }

    private static void addSkinPart(@Nonnull List<ModelAttachment> attachments,
                                    @Nullable String skinValue,
                                    @Nonnull Map<String, PlayerSkinPart> registry,
                                    @Nonnull String bodyGradientId,
                                    @Nullable String forcedGradientId) {
        if (skinValue == null) return;
        String[] parts = skinValue.split("\\.");
        PlayerSkinPart part = registry.get(parts[0]);
        if (part == null) return;

        if (forcedGradientId != null && parts.length == 1) {
            parts = new String[]{parts[0], forcedGradientId};
        }

        attachments.add(CosmeticUtils.resolveAttachment(part, parts, bodyGradientId));
    }
}