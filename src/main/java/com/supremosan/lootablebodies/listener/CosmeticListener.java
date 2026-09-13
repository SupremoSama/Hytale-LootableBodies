package com.supremosan.lootablebodies.listener;

import com.hypixel.hytale.protocol.Cosmetic;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.supremosan.lootablebodies.cosmetic.CosmeticUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CosmeticListener {

    private CosmeticListener() {
    }

    @Nonnull
    public static Model buildCosmeticModel(@Nonnull Model base,
                                           @Nonnull PlayerSkin skin,
                                           @Nonnull Set<Cosmetic> hiddenCosmetics,
                                           @Nonnull String modelName,
                                           @Nullable Map<String, ModelAttachment> extras) {
        CosmeticRegistry registry = CosmeticsModule.get() != null ? CosmeticsModule.get().getRegistry() : null;
        if (registry == null) {
            return base;
        }

        String bodyGradientId = "";
        String bodyGradientSet = null;
        String bodyTexture = null;

        if (skin.bodyCharacteristic != null) {
            String[] bodyParts = CosmeticUtils.splitId(skin.bodyCharacteristic);
            String assetId = CosmeticUtils.part(bodyParts, 0);
            String gradientId = CosmeticUtils.part(bodyParts, 1);
            bodyGradientId = gradientId != null ? gradientId : "";

            PlayerSkinPart bodyPart = assetId != null ? registry.getBodyCharacteristics().get(assetId) : null;
            if (bodyPart != null) {
                bodyGradientSet = bodyPart.getGradientSet();
                bodyTexture = bodyPart.getGreyscaleTexture();
            }
        }

        List<ModelAttachment> attachments = base.getAttachments() != null
                ? new ArrayList<>(Arrays.asList(base.getAttachments()))
                : new ArrayList<>();

        removeRegisteredSkinAttachments(attachments, registry);
        restoreSkinAttachments(attachments, skin, hiddenCosmetics, registry, bodyGradientId);

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

    private static void removeRegisteredSkinAttachments(List<ModelAttachment> attachments, CosmeticRegistry registry) {
        Set<String> models = new HashSet<>();

        collectModels(models, registry.getBodyCharacteristics());
        collectModels(models, registry.getSkinFeatures());
        collectModels(models, registry.getFaces());
        collectModels(models, registry.getMouths());
        collectModels(models, registry.getEars());
        collectModels(models, registry.getEyebrows());
        collectModels(models, registry.getEyes());
        collectModels(models, registry.getUnderwear());
        collectModels(models, registry.getHaircuts());
        collectModels(models, registry.getFacialHairs());
        collectModels(models, registry.getCapes());
        collectModels(models, registry.getFaceAccessories());
        collectModels(models, registry.getGloves());
        collectModels(models, registry.getHeadAccessories());
        collectModels(models, registry.getOverpants());
        collectModels(models, registry.getOvertops());
        collectModels(models, registry.getPants());
        collectModels(models, registry.getShoes());
        collectModels(models, registry.getUndertops());
        collectModels(models, registry.getEarAccessories());

        attachments.removeIf(attachment -> {
            String model = attachment.getModel();
            return model != null
                    && (models.contains(model) || model.startsWith("Items/Hats/InnerHair_"));
        });
    }

    private static void collectModels(Set<String> models, Map<String, PlayerSkinPart> registry) {
        for (PlayerSkinPart part : registry.values()) {
            if (part.getModel() != null) {
                models.add(part.getModel());
            }

            if (part.getVariants() == null) {
                continue;
            }

            for (PlayerSkinPart.Variant variant : part.getVariants().values()) {
                if (variant.getModel() != null) {
                    models.add(variant.getModel());
                }
            }
        }
    }

    private static void restoreSkinAttachments(List<ModelAttachment> attachments,
                                               PlayerSkin skin,
                                               Set<Cosmetic> hiddenCosmetics,
                                               CosmeticRegistry registry,
                                               String bodyGradientId) {
        if (skin.bodyCharacteristic == null) return;

        addSkinPart(attachments, skin.bodyCharacteristic, registry.getBodyCharacteristics(), bodyGradientId, null);
        addHaircutPart(attachments, skin, registry, hiddenCosmetics, bodyGradientId);
        addSkinPart(attachments, skin.eyebrows, registry.getEyebrows(), bodyGradientId, null);
        addSkinPart(attachments, skin.eyes, registry.getEyes(), bodyGradientId, null);
        addSkinPart(attachments, skin.underwear, registry.getUnderwear(), bodyGradientId, null);
        addSkinPart(attachments, skin.skinFeature, registry.getSkinFeatures(), bodyGradientId, null);

        addSkinPart(attachments, skin.face, registry.getFaces(), bodyGradientId, bodyGradientId);
        addSkinPart(attachments, skin.ears, registry.getEars(), bodyGradientId, bodyGradientId);
        addSkinPart(attachments, skin.mouth, registry.getMouths(), bodyGradientId, bodyGradientId);

        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.FacialHair, skin.facialHair, registry.getFacialHairs(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Cape, skin.cape, registry.getCapes(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.FaceAccessory, skin.faceAccessory, registry.getFaceAccessories(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Gloves, skin.gloves, registry.getGloves(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.HeadAccessory, skin.headAccessory, registry.getHeadAccessories(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Overpants, skin.overpants, registry.getOverpants(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Overtop, skin.overtop, registry.getOvertops(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Pants, skin.pants, registry.getPants(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Shoes, skin.shoes, registry.getShoes(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.Undertop, skin.undertop, registry.getUndertops(), bodyGradientId);
        addHiddenAwarePart(attachments, hiddenCosmetics, Cosmetic.EarAccessory, skin.earAccessory, registry.getEarAccessories(), bodyGradientId);
    }

    private static void addHiddenAwarePart(List<ModelAttachment> attachments,
                                           Set<Cosmetic> hiddenCosmetics,
                                           Cosmetic cosmetic,
                                           String rawId,
                                           Map<String, PlayerSkinPart> registry,
                                           String bodyGradientId) {
        if (!hiddenCosmetics.contains(cosmetic)) {
            addSkinPart(attachments, rawId, registry, bodyGradientId, null);
        }
    }

    private static void addHaircutPart(List<ModelAttachment> attachments,
                                       PlayerSkin skin,
                                       CosmeticRegistry registry,
                                       Set<Cosmetic> hiddenCosmetics,
                                       String bodyGradientId) {
        if (skin.haircut == null) return;

        String[] parts = CosmeticUtils.splitId(skin.haircut);
        String assetId = CosmeticUtils.part(parts, 0);
        if (assetId == null) return;

        PlayerSkinPart part = registry.getHaircuts().get(assetId);
        if (part == null) return;

        String textureId = CosmeticUtils.part(parts, 1);
        String variantId = CosmeticUtils.part(parts, 2);

        if (variantId != null && textureId != null
                && part.getVariants() != null
                && !part.getVariants().containsKey(variantId)
                && part.getVariants().containsKey(textureId)) {
            String resolvedVariant = textureId;
            textureId = variantId;
            variantId = resolvedVariant;
        }

        PlayerSkinPart.HeadAccessoryType headAccessoryType = resolveHeadAccessoryType(skin, registry);
        if (headAccessoryType == PlayerSkinPart.HeadAccessoryType.FullyCovering
                && !hiddenCosmetics.contains(Cosmetic.HeadAccessory)) {
            return;
        }

        if (hiddenCosmetics.contains(Cosmetic.Haircut) && part.getHairType() != null) {
            String hairType = part.getHairType().name();
            String gradientId = textureId != null ? textureId : "Black";
            attachments.add(new ModelAttachment(
                    "Items/Hats/InnerHair_" + hairType + ".blockymodel",
                    "Items/Hats/InnerHair_" + hairType + "_Greyscale.png",
                    "Hair",
                    gradientId,
                    1.0
            ));
            return;
        }

        if (part.doesRequireGenericHaircut() && part.getHairType() != null
                && resolveHeadAccessoryType(skin, registry) == PlayerSkinPart.HeadAccessoryType.HalfCovering) {
            PlayerSkinPart generic = registry.getHaircuts().get("Generic" + part.getHairType());
            if (generic != null) {
                ModelAttachment attachment = CosmeticUtils.resolveAttachment(part, textureId, variantId, bodyGradientId);
                String gradientId = attachment.getGradientId();
                if (gradientId == null || gradientId.isEmpty()) {
                    gradientId = textureId != null ? textureId : bodyGradientId;
                }
                String gradientSet = generic.getGradientSet();
                if (gradientSet == null) {
                    gradientSet = attachment.getGradientSet();
                }
                attachments.add(new ModelAttachment(
                        generic.getModel(),
                        generic.getGreyscaleTexture(),
                        gradientSet,
                        gradientId,
                        1.0
                ));
                return;
            }
        }

        attachments.add(CosmeticUtils.resolveAttachment(part, textureId, variantId, bodyGradientId));
    }

    private static PlayerSkinPart.HeadAccessoryType resolveHeadAccessoryType(PlayerSkin skin, CosmeticRegistry registry) {
        if (skin.headAccessory == null) {
            return PlayerSkinPart.HeadAccessoryType.Simple;
        }

        String assetId = CosmeticUtils.assetId(skin.headAccessory);
        if (assetId == null) {
            return PlayerSkinPart.HeadAccessoryType.Simple;
        }

        PlayerSkinPart headAccessory = registry.getHeadAccessories().get(assetId);
        return headAccessory == null ? PlayerSkinPart.HeadAccessoryType.Simple : headAccessory.getHeadAccessoryType();
    }

    private static void addSkinPart(List<ModelAttachment> attachments,
                                    String skinValue,
                                    Map<String, PlayerSkinPart> registry,
                                    String bodyGradientId,
                                    String forcedGradientId) {
        if (skinValue == null) return;

        String[] parts = CosmeticUtils.splitId(skinValue);
        String assetId = CosmeticUtils.part(parts, 0);
        if (assetId == null) return;

        PlayerSkinPart part = registry.get(assetId);
        if (part == null) return;

        String textureId = CosmeticUtils.part(parts, 1);
        String variantId = CosmeticUtils.part(parts, 2);

        if (textureId == null && forcedGradientId != null) {
            textureId = forcedGradientId;
        }

        if (variantId != null && textureId != null
                && part.getVariants() != null
                && !part.getVariants().containsKey(variantId)
                && part.getVariants().containsKey(textureId)) {
            String resolvedVariant = textureId;
            textureId = variantId;
            variantId = resolvedVariant;
        }

        attachments.add(CosmeticUtils.resolveAttachment(part, textureId, variantId, bodyGradientId));
    }
}
