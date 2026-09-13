package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.supremosan.lootablebodies.LootableBodies;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.components.BodySource;
import com.supremosan.lootablebodies.listener.CosmeticListener;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BodyManager {
    private static final String BODY_DEATH_ROLE = "Body_Death_Entity_Role";
    private static final String BODY_LOGOUT_ROLE = "Body_Logout_Entity_Role";
    private static final float DEFAULT_SCALE = 1.0F;

    public static void spawnBody(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            ItemStack[] storageItems,
            ItemStack[] hotbarItems,
            ItemStack[] backpackItems,
            ItemStack[] armorItems,
            ItemStack[] utilityItems,
            ItemStack[] toolItems,
            ItemStack[] abilityItems,
            ItemStack[] runeBagItems,
            BodySource source
    ) {
        if (ref == null || !ref.isValid()) {
            return;
        }

        World world = store.getExternalData().getWorld();
        if (!LootableBodies.isBodySpawnAllowed(world)) {
            return;
        }

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            return;
        }

        PlayerSkinComponent playerSkinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (playerSkinComponent == null) {
            return;
        }

        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        String ownerUuid = playerRefComponent != null ? playerRefComponent.getUuid().toString() : "";

        // One active body per owner at a time: clear any stale body before spawning a new one.
        if (!ownerUuid.isEmpty()) {
            Ref<EntityStore> existingBody = findBodyRefByPlayer(playerRefComponent.getUuid(), null, store);
            if (existingBody != null && existingBody.isValid()) {
                forceRemoveBody(existingBody, existingBody.getStore());
            }
        }

        String skinSerialized = serializePlayerSkin(playerSkinComponent);

        String roleName = source == BodySource.DEATH ? BODY_DEATH_ROLE : BODY_LOGOUT_ROLE;
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            return;
        }

        ModelAsset playerModelAsset = ModelAsset.getAssetMap().getAsset("Player");
        if (playerModelAsset == null) {
            return;
        }

        float scale = DEFAULT_SCALE;
        try {
            float assetScale = playerModelAsset.getMinScale();
            if (assetScale > 0f) {
                scale = assetScale;
            }
        } catch (Exception ignored) {
        }

        Model newModel;
        try {
            Model scaled = Model.createScaledModel(playerModelAsset, scale);
            Map<String, ModelAsset.AnimationSet> animMap = CosmeticListener.createSleepingAnimationMap(scaled.getAnimationSetMap());
            newModel = new Model(
                    scaled.getModelAssetId(),
                    scaled.getScale(),
                    scaled.getRandomAttachmentIds(),
                    scaled.getAttachments(),
                    scaled.getBoundingBox(),
                    scaled.getModel(),
                    scaled.getTexture(),
                    scaled.getGradientSet(),
                    scaled.getGradientId(),
                    scaled.getEyeHeight(),
                    scaled.getCrouchOffset(),
                    scaled.getSittingOffset(),
                    scaled.getSleepingOffset(),
                    animMap,
                    scaled.getCamera(),
                    scaled.getLight(),
                    scaled.getParticles(),
                    scaled.getTrails(),
                    scaled.getPhysicsValues(),
                    scaled.getDetailBoxes(),
                    scaled.getPhobia(),
                    scaled.getPhobiaModelAssetId()
            );
        } catch (Exception ignored) {
            return;
        }

        Vector3d position = transformComponent.getPosition();
        String playerName = playerRefComponent != null ? playerRefComponent.getUsername() : null;

        PlayerSkinComponent skinComp = new PlayerSkinComponent(playerSkinComponent.getPlayerSkin().clone());
        skinComp.setNetworkOutdated();

        List<ItemStack> allItems = new ArrayList<>();
        addNonEmpty(storageItems, allItems);
        addNonEmpty(hotbarItems, allItems);
        addNonEmpty(backpackItems, allItems);
        addNonEmpty(armorItems, allItems);
        addNonEmpty(utilityItems, allItems);
        addNonEmpty(toolItems, allItems);
        addNonEmpty(abilityItems, allItems);
        addNonEmpty(runeBagItems, allItems);

        List<ItemStack> merged = mergeStacks(allItems);
        short capacity = (short) Math.max(36, ((merged.size() + 8) / 9) * 9);
        SimpleItemContainer storageContainer = new SimpleItemContainer(capacity);
        for (short i = 0; i < merged.size(); ++i) {
            storageContainer.setItemStackForSlot(i, merged.get(i));
        }

        BodyComponent bodyComponent = new BodyComponent(skinSerialized, ownerUuid, source.name());
        bodyComponent.setStorageItems(storageItems != null ? storageItems : new ItemStack[0]);
        bodyComponent.setHotbarItems(hotbarItems != null ? hotbarItems : new ItemStack[0]);
        bodyComponent.setBackpackItems(backpackItems != null ? backpackItems : new ItemStack[0]);
        bodyComponent.setArmorItems(armorItems != null ? armorItems : new ItemStack[0]);
        bodyComponent.setUtilityItems(utilityItems != null ? utilityItems : new ItemStack[0]);
        bodyComponent.setToolItems(toolItems != null ? toolItems : new ItemStack[0]);
        bodyComponent.setAbilityItems(abilityItems != null ? abilityItems : new ItemStack[0]);
        bodyComponent.setRuneBagItems(runeBagItems != null ? runeBagItems : new ItemStack[0]);

        Pair<Ref<EntityStore>, NPCEntity> pair = NPCPlugin.get().spawnEntity(
                store,
                roleIndex,
                position,
                transformComponent.getRotation(),
                newModel,
                (npcEntity, holder, s) -> {
                    holder.putComponent(PlayerSkinComponent.getComponentType(), skinComp);
                    holder.putComponent(DisplayNameComponent.getComponentType(), new DisplayNameComponent(
                            Message.translation("server.npcRoles.Body_Entity.name").param("player", playerName != null ? playerName : "")
                    ));
                    holder.putComponent(InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage(storageContainer));
                    holder.putComponent(LootableBodies.bodyComponentType, bodyComponent);

                    MovementStatesComponent movementStates = new MovementStatesComponent();
                    movementStates.getMovementStates().sleeping = true;
                    movementStates.getMovementStates().idle = true;
                    movementStates.getSentMovementStates().sleeping = true;
                    movementStates.getSentMovementStates().idle = true;
                    holder.putComponent(MovementStatesComponent.getComponentType(), movementStates);
                },
                null
        );

        if (pair == null) {
            return;
        }

        Ref<EntityStore> newEntityRef = pair.first();
        Store<EntityStore> newEntityStore = newEntityRef.getStore();

        // Ensure components are also set on store if not captured by preAddToWorld
        newEntityStore.putComponent(newEntityRef, PlayerSkinComponent.getComponentType(), skinComp);
        newEntityStore.putComponent(newEntityRef, DisplayNameComponent.getComponentType(), new DisplayNameComponent(
                Message.translation("server.npcRoles.Body_Entity.name").param("player", playerName != null ? playerName : "")
        ));
        newEntityStore.putComponent(newEntityRef, InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage(storageContainer));
        newEntityStore.putComponent(newEntityRef, LootableBodies.bodyComponentType, bodyComponent);

        MovementStatesComponent movementStates = newEntityStore.getComponent(newEntityRef, MovementStatesComponent.getComponentType());
        if (movementStates == null) {
            movementStates = new MovementStatesComponent();
            newEntityStore.putComponent(newEntityRef, MovementStatesComponent.getComponentType(), movementStates);
        }
        movementStates.getMovementStates().sleeping = true;
        movementStates.getMovementStates().idle = true;
        movementStates.getSentMovementStates().sleeping = true;
        movementStates.getSentMovementStates().idle = true;

        try {
            AnimationUtils.playAnimation(newEntityRef, AnimationSlot.Status, "Sleep", newEntityStore);
        } catch (Exception ignored) {
        }
    }

    private static String serializePlayerSkin(PlayerSkinComponent skinComponent) {
        if (skinComponent == null) return "";
        var skin = skinComponent.getPlayerSkin();
        return String.join(";",
                nullToEmpty(skin.bodyCharacteristic),
                nullToEmpty(skin.ears),
                nullToEmpty(skin.skinFeature),
                nullToEmpty(skin.eyes),
                nullToEmpty(skin.eyebrows),
                nullToEmpty(skin.gloves),
                nullToEmpty(skin.overpants),
                nullToEmpty(skin.pants),
                nullToEmpty(skin.shoes),
                nullToEmpty(skin.cape),
                nullToEmpty(skin.earAccessory),
                nullToEmpty(skin.face),
                nullToEmpty(skin.faceAccessory),
                nullToEmpty(skin.facialHair),
                nullToEmpty(skin.haircut),
                nullToEmpty(skin.headAccessory),
                nullToEmpty(skin.mouth),
                nullToEmpty(skin.overtop),
                nullToEmpty(skin.undertop),
                nullToEmpty(skin.underwear)
        );
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public static void syncBodyToPlayer(UUID uuid, Store<EntityStore> playerStore, Ref<EntityStore> playerRef) {
        if (uuid == null || playerStore == null || playerRef == null || !playerRef.isValid()) {
            return;
        }

        World world = playerStore.getExternalData().getWorld();
        if (!LootableBodies.isBodySpawnAllowed(world)) {
            return;
        }

        Ref<EntityStore> bodyRef = findBodyRefByPlayer(uuid, BodySource.LOGOUT, playerStore);
        if (bodyRef == null || !bodyRef.isValid()) {
            return;
        }

        Store<EntityStore> bodyStore = bodyRef.getStore();

        BodyComponent bodyComponent = bodyStore.getComponent(bodyRef, LootableBodies.bodyComponentType);
        if (bodyComponent == null) {
            forceRemoveBody(bodyRef, bodyStore);
            return;
        }

        InventoryComponent.Storage bodyStorageComp = bodyStore.getComponent(bodyRef, InventoryComponent.Storage.getComponentType());
        ItemContainer bodyLive = bodyStorageComp != null ? bodyStorageComp.getInventory() : null;
        if (bodyLive == null) {
            forceRemoveBody(bodyRef, bodyStore);
            return;
        }

        // Clear player sections so reloaded state doesn't duplicate items
        clearSection(playerStore, playerRef, InventoryComponent.Storage.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.Hotbar.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.Backpack.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.Armor.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.Utility.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.Tool.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.AbilitySlots.getComponentType());
        clearSection(playerStore, playerRef, InventoryComponent.RuneBag.getComponentType());

        // Pass 1: restore matching items to their original equipment and inventory slots
        restoreSlotMatch(bodyComponent.getArmorItems(), bodyLive, playerStore, playerRef, InventoryComponent.Armor.getComponentType());
        restoreSlotMatch(bodyComponent.getHotbarItems(), bodyLive, playerStore, playerRef, InventoryComponent.Hotbar.getComponentType());
        restoreSlotMatch(bodyComponent.getBackpackItems(), bodyLive, playerStore, playerRef, InventoryComponent.Backpack.getComponentType());
        restoreSlotMatch(bodyComponent.getStorageItems(), bodyLive, playerStore, playerRef, InventoryComponent.Storage.getComponentType());
        restoreSlotMatch(bodyComponent.getUtilityItems(), bodyLive, playerStore, playerRef, InventoryComponent.Utility.getComponentType());
        restoreSlotMatch(bodyComponent.getToolItems(), bodyLive, playerStore, playerRef, InventoryComponent.Tool.getComponentType());
        restoreSlotMatch(bodyComponent.getAbilityItems(), bodyLive, playerStore, playerRef, InventoryComponent.AbilitySlots.getComponentType());
        restoreSlotMatch(bodyComponent.getRuneBagItems(), bodyLive, playerStore, playerRef, InventoryComponent.RuneBag.getComponentType());

        // Pass 2: any items remaining in bodyLive (unmatched, extra items added while offline, etc.)
        List<ItemStack> leftoverItems = new ArrayList<>();
        for (short slot = 0; slot < bodyLive.getCapacity(); ++slot) {
            ItemStack live = bodyLive.getItemStack(slot);
            if (!ItemStack.isEmpty(live)) {
                leftoverItems.add(live);
                bodyLive.removeItemStackFromSlot(slot);
            }
        }

        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack leftover : leftoverItems) {
            ItemStack remaining = tryAddRemainingItem(playerStore, playerRef, leftover);
            if (!ItemStack.isEmpty(remaining)) {
                overflow.add(remaining);
            }
        }

        if (!overflow.isEmpty()) {
            TransformComponent transform = playerStore.getComponent(playerRef, TransformComponent.getComponentType());
            Vector3d dropPos = transform != null ? transform.getPosition() : new Vector3d();
            try {
                Holder<EntityStore>[] dropHolders = ItemComponent.generateItemDrops(playerStore, overflow, dropPos, Rotation3f.IDENTITY);
                if (dropHolders != null && dropHolders.length > 0) {
                    playerStore.addEntities(dropHolders, AddReason.SPAWN);
                }
            } catch (Exception ignored) {
            }
        }

        // Rust behavior: unconditionally despawn the sleeper on owner login
        forceRemoveBody(bodyRef, bodyStore);
    }

    public static void clearSection(@Nonnull Store<EntityStore> playerStore,
                                    @Nonnull Ref<EntityStore> playerRef,
                                    @Nonnull ComponentType<EntityStore, ? extends InventoryComponent> componentType) {
        @SuppressWarnings("unchecked")
        InventoryComponent component = playerStore.getComponent(playerRef, (ComponentType<EntityStore, InventoryComponent>) componentType);
        if (component == null) return;

        ItemContainer container = component.getInventory();
        for (short slot = 0; slot < container.getCapacity(); ++slot) {
            if (!ItemStack.isEmpty(container.getItemStack(slot))) {
                container.removeItemStackFromSlot(slot);
            }
        }
    }

    private static void restoreSlotMatch(@Nullable ItemStack[] snapshot,
                                         @Nonnull ItemContainer bodyLive,
                                         @Nonnull Store<EntityStore> playerStore,
                                         @Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull ComponentType<EntityStore, ? extends InventoryComponent> componentType) {
        if (snapshot == null || snapshot.length == 0) return;

        @SuppressWarnings("unchecked")
        InventoryComponent component = playerStore.getComponent(playerRef, (ComponentType<EntityStore, InventoryComponent>) componentType);
        if (component == null) return;

        ItemContainer playerTarget = component.getInventory();
        for (short slot = 0; slot < snapshot.length && slot < playerTarget.getCapacity(); ++slot) {
            ItemStack original = snapshot[slot];
            if (ItemStack.isEmpty(original)) continue;

            ItemStack taken = drainMatchingFromLive(original, bodyLive);
            if (!ItemStack.isEmpty(taken)) {
                playerTarget.setItemStackForSlot(slot, taken);
            }
        }
    }

    private static ItemStack drainMatchingFromLive(ItemStack original, ItemContainer bodyLive) {
        // 1. Exact stackable match
        for (short slot = 0; slot < bodyLive.getCapacity(); ++slot) {
            ItemStack live = bodyLive.getItemStack(slot);
            if (ItemStack.isEmpty(live) || !live.isStackableWith(original)) continue;

            int take = Math.min(live.getQuantity(), original.getQuantity());
            int leftover = live.getQuantity() - take;

            if (leftover > 0) {
                bodyLive.setItemStackForSlot(slot, live.withQuantity(leftover));
            } else {
                bodyLive.removeItemStackFromSlot(slot);
            }

            return live.withQuantity(take);
        }

        // 2. Same itemId match
        for (short slot = 0; slot < bodyLive.getCapacity(); ++slot) {
            ItemStack live = bodyLive.getItemStack(slot);
            if (ItemStack.isEmpty(live) || !live.getItemId().equals(original.getItemId())) continue;

            int take = Math.min(live.getQuantity(), original.getQuantity());
            int leftover = live.getQuantity() - take;

            if (leftover > 0) {
                bodyLive.setItemStackForSlot(slot, live.withQuantity(leftover));
            } else {
                bodyLive.removeItemStackFromSlot(slot);
            }

            return live.withQuantity(take);
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack tryAddRemainingItem(@Nonnull Store<EntityStore> playerStore,
                                                 @Nonnull Ref<EntityStore> playerRef,
                                                 @Nonnull ItemStack item) {
        if (ItemStack.isEmpty(item)) return ItemStack.EMPTY;

        InventoryComponent.Storage storage = playerStore.getComponent(playerRef, InventoryComponent.Storage.getComponentType());
        if (storage != null) {
            item = tryAddToContainer(storage.getInventory(), item);
            if (ItemStack.isEmpty(item)) return ItemStack.EMPTY;
        }

        InventoryComponent.Hotbar hotbar = playerStore.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar != null) {
            item = tryAddToContainer(hotbar.getInventory(), item);
            if (ItemStack.isEmpty(item)) return ItemStack.EMPTY;
        }

        InventoryComponent.Backpack backpack = playerStore.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());
        if (backpack != null) {
            item = tryAddToContainer(backpack.getInventory(), item);
            if (ItemStack.isEmpty(item)) return ItemStack.EMPTY;
        }

        return item;
    }

    private static ItemStack tryAddToContainer(@Nullable ItemContainer container, @Nonnull ItemStack item) {
        if (container == null || ItemStack.isEmpty(item)) {
            return item;
        }
        ItemStackTransaction transaction = container.addItemStack(item);
        ItemStack remainder = transaction.getRemainder();
        return remainder != null ? remainder : ItemStack.EMPTY;
    }

    public static void forceRemoveBody(Ref<EntityStore> bodyRef, Store<EntityStore> bodyStore) {
        if (bodyRef == null || bodyStore == null || !bodyRef.isValid()) {
            return;
        }

        try {
            @Nullable
            ComponentType<EntityStore, NPCEntity> npcComponentType = NPCEntity.getComponentType();
            if (npcComponentType != null) {
                NPCEntity npcEntity = bodyStore.getComponent(bodyRef, npcComponentType);
                if (npcEntity != null) {
                    npcEntity.remove();
                }
            }

            if (bodyRef.isValid()) {
                bodyStore.removeEntity(bodyRef, RemoveReason.REMOVE);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasBody(UUID uuid, Store<EntityStore> store) {
        return findBodyRefByPlayer(uuid, null, store) != null;
    }

    private static Ref<EntityStore> findBodyRefByPlayer(UUID uuid, BodySource requiredSource, Store<EntityStore> store) {
        if (uuid == null || store == null) {
            return null;
        }

        final Ref<EntityStore>[] found = new Ref[1];
        store.forEachChunk(LootableBodies.bodyComponentType, (archetypeChunk, commandBuffer) -> {
            if (found[0] != null) {
                return;
            }

            for (int index = 0; index < archetypeChunk.size(); ++index) {
                BodyComponent bodyComponent = archetypeChunk.getComponent(index, LootableBodies.bodyComponentType);
                if (bodyComponent == null) {
                    continue;
                }

                if (bodyComponent.ownerUuidSerialized.isEmpty()) {
                    continue;
                }

                if (!uuid.toString().equals(bodyComponent.ownerUuidSerialized)) {
                    continue;
                }

                if (requiredSource != null && bodyComponent.bodySource != requiredSource) {
                    continue;
                }

                found[0] = archetypeChunk.getReferenceTo(index);
                return;
            }
        });

        return found[0];
    }

    private static void addNonEmpty(ItemStack[] items, List<ItemStack> target) {
        if (items == null) return;
        for (ItemStack s : items) {
            if (!ItemStack.isEmpty(s)) target.add(s);
        }
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack incoming : stacks) {
            if (incoming == null || ItemStack.isEmpty(incoming)) {
                continue;
            }

            boolean mergedIntoExisting = false;

            for (int i = 0; i < merged.size(); i++) {
                ItemStack existing = merged.get(i);

                if (!existing.isStackableWith(incoming)) {
                    continue;
                }

                int maxStackSize = existing.getItem().getMaxStack();
                int combined = existing.getQuantity() + incoming.getQuantity();

                if (combined <= maxStackSize) {
                    merged.set(i, existing.withQuantity(combined));
                    mergedIntoExisting = true;
                    break;
                }

                if (existing.getQuantity() < maxStackSize) {
                    int amountToFill = maxStackSize - existing.getQuantity();
                    merged.set(i, existing.withQuantity(maxStackSize));
                    incoming = incoming.withQuantity(incoming.getQuantity() - amountToFill);
                }
            }

            if (!mergedIntoExisting) {
                merged.add(incoming);
            }
        }

        return merged;
    }
}
