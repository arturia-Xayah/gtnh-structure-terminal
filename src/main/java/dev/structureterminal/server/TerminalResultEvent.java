package dev.structureterminal.server;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.Event;

/** Server-side observation of an outcome; listeners cannot authorize or alter a construction request. */
public final class TerminalResultEvent extends Event {

    public final EntityPlayerMP player;
    public final String key, value;

    public TerminalResultEvent(EntityPlayerMP player, String key, String value) {
        this.player = player;
        this.key = key;
        this.value = value;
    }
}
