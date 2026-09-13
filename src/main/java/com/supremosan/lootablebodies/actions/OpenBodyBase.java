package com.supremosan.lootablebodies.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class OpenBodyBase extends ActionBase {

    public OpenBodyBase(@Nonnull BuilderActionBase builderActionBase) {
        super(builderActionBase);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, executionSupport, sensorInfo, dt, store)
                && executionSupport.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, executionSupport, sensorInfo, dt, store);

        Ref<EntityStore> playerReference = executionSupport.getStateSupport().getInteractionIterationTarget();
        if (playerReference == null || !playerReference.isValid()) return false;
        if (!ref.isValid()) return false;

        PlayerRef playerRefComponent = store.getComponent(playerReference, PlayerRef.getComponentType());
        if (playerRefComponent == null) return false;

        Player playerComponent = store.getComponent(playerReference, Player.getComponentType());
        if (playerComponent == null) return false;

        Store<EntityStore> npcStore = ref.getStore();

        InventoryComponent.Storage storageComp = npcStore.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storageComp == null) return false;

        ItemContainer storage = storageComp.getInventory();

        NPCEntity npcEntity = npcStore.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity == null) return false;

        TransformComponent npcTransform = npcStore.getComponent(ref, TransformComponent.getComponentType());
        if (npcTransform == null) return false;

        BodyWindow bodyWindow = new BodyWindow(storage, npcTransform.getPosition());
        bodyWindow.registerCloseEvent((_) -> {
            if (storage.isEmpty()) {
                npcEntity.remove();
            }
        });

        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, bodyWindow);
        return true;
    }
}
