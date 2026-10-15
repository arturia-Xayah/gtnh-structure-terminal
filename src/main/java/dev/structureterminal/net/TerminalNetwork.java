package dev.structureterminal.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.structureterminal.StructureTerminal;
import dev.structureterminal.server.BuildService;
import io.netty.buffer.ByteBuf;

public final class TerminalNetwork {

    public static SimpleNetworkWrapper channel;

    private TerminalNetwork() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(StructureTerminal.ID);
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
    }

    public static class Request implements IMessage {

        public NBTTagCompound tag;

        public Request() {}

        public Request(NBTTagCompound tag) {
            this.tag = tag;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeTag(buf, tag);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() > 32768) throw new IllegalArgumentException("Terminal request exceeds 32 KiB");
            tag = ByteBufUtils.readTag(buf);
        }
    }

    public static class Reply extends Request {

        public Reply() {}

        public Reply(NBTTagCompound tag) {
            super(tag);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (message.tag != null) BuildService.INSTANCE.enqueue(player, message.tag);
            return null;
        }
    }

    public static final class ReplyHandler implements IMessageHandler<Reply, IMessage> {

        @Override
        public IMessage onMessage(Reply message, MessageContext ctx) {
            if (message.tag != null) StructureTerminal.proxy.receive(message.tag);
            return null;
        }
    }
}
