package com.supremosan.lootablebodies;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.supremosan.lootablebodies.actions.OpenBodyAction;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.components.BodySource;
import com.supremosan.lootablebodies.events.CreateBodyOnDeathEvent;
import com.supremosan.lootablebodies.system.BodyManager;
import com.supremosan.lootablebodies.system.BodySkinReapplySystem;

import java.lang.reflect.Field;
import java.util.UUID;

public class LootableBodies extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static ComponentType<EntityStore, BodyComponent> bodyComponentType;

    public LootableBodies(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        NPCPlugin.get().registerCoreComponentType("OpenBody", OpenBodyAction::new);

        bodyComponentType = this.getEntityStoreRegistry().registerComponent(
                BodyComponent.class, "BodyComponent", BodyComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new CreateBodyOnDeathEvent());
        this.getEntityStoreRegistry().registerSystem(new BodySkinReapplySystem());

        Field modelReferenceScaleField;
        try {
            modelReferenceScaleField = Model.ModelReference.class.getDeclaredField("scale");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        modelReferenceScaleField.setAccessible(true);
        try {
            modelReferenceScaleField.set(Model.ModelReference.DEFAULT_PLAYER_MODEL, 1.0F);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) return;

            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
                InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
                InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

                if (transform == null) return;

                ItemStack[] storageItems = snapshotContainer(storageComp != null ? storageComp.getInventory() : null);
                ItemStack[] hotbarItems = snapshotContainer(hotbarComp != null ? hotbarComp.getInventory() : null);
                ItemStack[] backpackItems = snapshotContainer(backpackComp != null ? backpackComp.getInventory() : null);
                ItemStack[] armorItems = snapshotContainer(armorComp != null ? armorComp.getInventory() : null);

                boolean hasAny = hasItems(storageItems) || hasItems(hotbarItems) || hasItems(backpackItems) || hasItems(armorItems);
                if (!hasAny) return;

                LOGGER.atInfo().log("[LootableBodies] Saving body for %s", uuid);

                BodyManager.spawnBody(store, ref, uuid, storageItems, hotbarItems, backpackItems, armorItems, BodySource.LOGOUT);
            });
        });

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = player.getReference();
            if (ref == null) return;

            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            UUID uuid = playerRef.getUuid();
            if (!BodyManager.hasBody(uuid, store)) return;

            World world = player.getWorld();
            if (world == null) return;

            if (!world.isAlive()) return;
            world.execute(() -> {
                LOGGER.atInfo().log("[LootableBodies] PlayerReadyEvent: syncing body to player %s", uuid);
                BodyManager.syncBodyToPlayer(uuid, store, ref);
            });
        });

        LOGGER.atInfo().log("[LootableBodies] Setup complete");
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("[LootableBodies] Ready");
    }

    private static ItemStack[] snapshotContainer(ItemContainer container) {
        if (container == null) return new ItemStack[0];
        ItemStack[] snapshot = new ItemStack[container.getCapacity()];
        for (short i = 0; i < container.getCapacity(); ++i) {
            snapshot[i] = container.getItemStack(i);
        }
        return snapshot;
    }

    private static boolean hasItems(ItemStack[] slots) {
        if (slots == null) return false;
        for (ItemStack s : slots) {
            if (!ItemStack.isEmpty(s)) return true;
        }
        return false;
    }
}