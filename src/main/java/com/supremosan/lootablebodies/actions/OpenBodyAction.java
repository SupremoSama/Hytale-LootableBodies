package com.supremosan.lootablebodies.actions;

import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nullable;

public class OpenBodyAction extends BuilderActionBase {

    @Nullable
    @Override
    public String getShortDescription() {
        return "Opens UI";
    }

    @Nullable
    @Override
    public String getLongDescription() {
        return "Opens the UI related to the body";
    }

    @Nullable
    @Override
    public Action build(BuilderSupport builderSupport) {
        return new OpenBodyBase(this);
    }

    @Nullable
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}