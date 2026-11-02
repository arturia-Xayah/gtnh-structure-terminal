package dev.aestoragevault.ui;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** The network thread only enqueues bounded requests; all world work runs on the server tick. */
public final class ReturnNetwork {

    public static SimpleNetworkWrapper channel;
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger();

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("vaultreturns");
        channel.registerMessage(Handler.class, Request.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new ReturnNetwork());
    }

    public static void clear() {
        QUEUE.clear();
        SIZE.set(0);
    }

    public static final class Request implements IMessage {

        public int window;
        public String key, amount;

        public Request() {}

        public Request(int window, String key, String amount) {
            this.window = window;
            this.key = key;
            this.amount = amount;
        }

        @Override
        public void toBytes(ByteBuf out) {
            out.writeInt(window);
            ByteBufUtils.writeUTF8String(out, key);
            ByteBufUtils.writeUTF8String(out, amount);
        }

        @Override
        public void fromBytes(ByteBuf in) {
            if (in.readableBytes() > 12000) throw new IllegalArgumentException("Return request too large");
            window = in.readInt();
            key = ByteBufUtils.readUTF8String(in);
            amount = ByteBufUtils.readUTF8String(in);
            if (key.length() > 300 || amount.length() > 10000 || in.isReadable())
                throw new IllegalArgumentException("Invalid return request");
        }
    }

    public static final class Handler implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (SIZE.incrementAndGet() > 128) {
                SIZE.decrementAndGet();
                return null;
            }
            QUEUE.add(() -> {
                if (player.openContainer instanceof ContributionContainer
                    && player.openContainer.windowId == message.window)
                    ((ContributionContainer) player.openContainer).returnCells(player, message.key, message.amount);
            });
            return null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (int i = 0; i < 4; i++) {
            Runnable task = QUEUE.poll();
            if (task == null) break;
            SIZE.decrementAndGet();
            task.run();
        }
    }
}
