package tpa;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import static tpa.tools.*;
import tpa.gui.TpaGui;

public class TpaCommand {

    /** 请求数据类 */
    public static class TpaRequest {
        public final String initiatorUUID; // 发起者 UUID
        public final String targetUUID;    // 目标 UUID
        public final boolean here;         // tpahere 模式
        public final long timestamp;       // 创建时间戳（毫秒）

        public TpaRequest(String initiatorUUID, String targetUUID, boolean here) {
            this.initiatorUUID = initiatorUUID;
            this.targetUUID    = targetUUID;
            this.here          = here;
            this.timestamp     = System.currentTimeMillis();
        }
    }

    /** 目标UUID -> 该玩家收到的请求列表 */
    private static final Map<String, CopyOnWriteArrayList<TpaRequest>> requestMap = new ConcurrentHashMap<>();

    private static final long EXPIRE_MS = 120_000L; // 120秒过期

    // 启动自动清理线程（服务器启动时调用一次）
    static {
        Thread cleaner = new Thread(() -> {
            while (true) {
                try { Thread.sleep(30_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                long now = System.currentTimeMillis();
                requestMap.forEach((uuid, list) -> list.removeIf(r -> now - r.timestamp > EXPIRE_MS));
                requestMap.entrySet().removeIf(e -> e.getValue().isEmpty());
            }
        }, "tpa-request-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    // --------- 兼容旧代码的辅助列表（tpaSuggestionProvider 使用）---------
    public static List<TpaRequest> getAllRequests() {
        List<TpaRequest> all = new ArrayList<>();
        requestMap.values().forEach(all::addAll);
        return all;
    }

    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {

        commandDispatcher.register(Commands.literal("tpagui")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    List<ServerPlayer> targets = tpa.SERVER.getPlayerList().getPlayers()
                            .stream()
                            .filter(p -> !p.getStringUUID().equals(player.getStringUUID()))
                            .toList();
                    new TpaGui(player, new ArrayList<>(targets)).open();
                    return 0;
                }));

        commandDispatcher.register(Commands.literal("tpa")
                .requires(source -> source.getPlayer() != null)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            final ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            try { sendRequest(player, target, false); }
                            catch (Exception e) { Constants.LOGGER.error("Error in /tpa => ", e); return 1; }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("tpahere")
                .requires(source -> source.getPlayer() != null)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            final ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            try { sendRequest(player, target, true); }
                            catch (Exception e) { Constants.LOGGER.error("Error in /tpahere => ", e); return 1; }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("tpaaccept")
                .executes(context -> {
                    // 无参数：自动接受最新的请求
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    try { acceptLatestRequest(player); }
                    catch (Exception e) { Constants.LOGGER.error("Error in /tpaaccept => ", e); return 1; }
                    return 0;
                })
                .then(Commands.argument("player", EntityArgument.player()).suggests(new tpaSuggestionProvider())
                        .executes(context -> {
                            final ServerPlayer initiator = EntityArgument.getPlayer(context, "player");
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            try { acceptRequest(player, initiator); }
                            catch (Exception e) { Constants.LOGGER.error("Error in /tpaaccept => ", e); return 1; }
                            return 0;
                        })));

        commandDispatcher.register(Commands.literal("tpadeny")
                .executes(context -> {
                    // 无参数：自动拒绝最新的请求
                    final ServerPlayer player = context.getSource().getPlayerOrException();
                    try { denyLatestRequest(player); }
                    catch (Exception e) { Constants.LOGGER.error("Error in /tpadeny => ", e); return 1; }
                    return 0;
                })
                .then(Commands.argument("player", EntityArgument.player()).suggests(new tpaSuggestionProvider())
                        .executes(context -> {
                            final ServerPlayer initiator = EntityArgument.getPlayer(context, "player");
                            final ServerPlayer player = context.getSource().getPlayerOrException();
                            try { denyRequest(player, initiator); }
                            catch (Exception e) { Constants.LOGGER.error("Error in /tpadeny => ", e); return 1; }
                            return 0;
                        })));
    }

    // ----------------------------------------------------------------
    // 1. 发送请求
    // ----------------------------------------------------------------
    private static void sendRequest(ServerPlayer initiator, ServerPlayer target, boolean here) {
        if (initiator == target) {
            initiator.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.self", initiator).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        String targetUUID    = target.getStringUUID();
        String initiatorUUID = initiator.getStringUUID();

        // 检查是否已有相同方向的请求
        CopyOnWriteArrayList<TpaRequest> list = requestMap.computeIfAbsent(targetUUID, k -> new CopyOnWriteArrayList<>());
        boolean alreadySent = list.stream().anyMatch(r -> r.initiatorUUID.equals(initiatorUUID));
        if (alreadySent) {
            initiator.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.alreadySent", initiator,
                            Component.literal(target.getName().getString()).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.AQUA), true);
            return;
        }

        TpaRequest request = new TpaRequest(initiatorUUID, targetUUID, here);
        list.add(request);

        String initiatorName = initiator.getName().getString();
        String targetName    = target.getName().getString();

        // 通知发起者
        if (here) {
            initiator.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.sent_here", initiator,
                            Component.literal(targetName).withStyle(ChatFormatting.BOLD)), true);
        } else {
            initiator.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.sent", initiator,
                            Component.literal(targetName).withStyle(ChatFormatting.BOLD)), true);
        }

        // 通知目标（带接受/拒绝按钮，直接绑定 /tpaaccept 和 /tpadeny 命令）
        Component receivedMsg = here
                ? getTranslatedText("commands.teleport_commands.tpa.received_here", target,
                        Component.literal(initiatorName).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                : getTranslatedText("commands.teleport_commands.tpa.received", target,
                        Component.literal(initiatorName).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        Component acceptButton = Component.literal("[✔]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withHoverEvent(new HoverEvent.ShowText(
                                getTranslatedText("commands.teleport_commands.tpa.accept", target)))
                        .withClickEvent(new ClickEvent.RunCommand(
                                "/tpaaccept " + initiatorName)));

        Component denyButton = Component.literal("[❌]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withHoverEvent(new HoverEvent.ShowText(
                                getTranslatedText("commands.teleport_commands.tpa.deny", target)))
                        .withClickEvent(new ClickEvent.RunCommand(
                                "/tpadeny " + initiatorName)));

        target.sendSystemMessage(
                ((MutableComponent) receivedMsg).withStyle(ChatFormatting.AQUA)
                        .append(" ")
                        .append(acceptButton)
                        .append(" ")
                        .append(denyButton)
        );

        // 30秒提醒过期（120秒才真正移除）
        new Timer().schedule(new TimerTask() {
            @Override public void run() {
                CopyOnWriteArrayList<TpaRequest> l = requestMap.get(targetUUID);
                if (l != null && l.contains(request)) {
                    tpa.SERVER.execute(() -> {
                        initiator.displayClientMessage(
                                getTranslatedText("commands.teleport_commands.tpa.expired", initiator)
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                        target.displayClientMessage(
                                getTranslatedText("commands.teleport_commands.tpa.expired", target)
                                        .withStyle(ChatFormatting.WHITE), true);
                    });
                }
            }
        }, 30_000);
    }

    // ----------------------------------------------------------------
    // 2. 接受请求
    // ----------------------------------------------------------------
    /** 无参数版：自动接受最新收到的请求 */
    private static void acceptLatestRequest(ServerPlayer target) {
        CopyOnWriteArrayList<TpaRequest> list = requestMap.get(target.getStringUUID());
        if (list == null || list.isEmpty()) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }
        // 取最新（最后一个）请求
        TpaRequest latest = list.get(list.size() - 1);
        ServerPlayer initiator = tpa.SERVER.getPlayerList().getPlayer(java.util.UUID.fromString(latest.initiatorUUID));
        if (initiator == null) {
            list.remove(latest);
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }
        acceptRequest(target, initiator);
    }

    private static void acceptRequest(ServerPlayer target, ServerPlayer initiator) {
        if (target == initiator) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.self", target).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        CopyOnWriteArrayList<TpaRequest> list = requestMap.get(target.getStringUUID());
        if (list == null) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }

        Optional<TpaRequest> opt = list.stream()
                .filter(r -> r.initiatorUUID.equals(initiator.getStringUUID()))
                .findFirst();

        if (opt.isEmpty()) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }

        TpaRequest request = opt.get();
        list.remove(request);

        // here=true: 把 target 传送到 initiator 身边; here=false: 把 initiator 传送到 target 身边
        ServerPlayer traveller   = request.here ? target    : initiator;
        ServerPlayer destination = request.here ? initiator : target;

        target.displayClientMessage(
                getTranslatedText("commands.teleport_commands.tpa.accepted", target).withStyle(ChatFormatting.WHITE), true);
        initiator.displayClientMessage(
                getTranslatedText("commands.teleport_commands.tpa.accepted", initiator).withStyle(ChatFormatting.GREEN), true);
        // 接受音效
        tools.playAcceptSound(target);
        tools.playAcceptSound(initiator);

        TeleportDelayManager.startDelay(traveller, destination, () -> {
            net.minecraft.server.level.ServerLevel destLevel =
                    (net.minecraft.server.level.ServerLevel) destination.level();
            Optional<BlockPos> safePos = getSafeBlockPos(destination.blockPosition(), destLevel);
            Vec3 teleportPos = safePos
                    .map(p -> new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))
                    .orElse(destination.position());
            Teleporter(traveller, destLevel, teleportPos);
        });
    }

    // ----------------------------------------------------------------
    // 3. 拒绝请求
    // ----------------------------------------------------------------
    /** 无参数版：自动拒绝最新收到的请求 */
    private static void denyLatestRequest(ServerPlayer target) {
        CopyOnWriteArrayList<TpaRequest> list = requestMap.get(target.getStringUUID());
        if (list == null || list.isEmpty()) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }
        TpaRequest latest = list.get(list.size() - 1);
        ServerPlayer initiator = tpa.SERVER.getPlayerList().getPlayer(java.util.UUID.fromString(latest.initiatorUUID));
        if (initiator == null) {
            list.remove(latest);
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }
        denyRequest(target, initiator);
    }

    private static void denyRequest(ServerPlayer target, ServerPlayer initiator) {
        if (target == initiator) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.self", target).withStyle(ChatFormatting.AQUA), true);
            return;
        }

        CopyOnWriteArrayList<TpaRequest> list = requestMap.get(target.getStringUUID());
        if (list == null) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }

        Optional<TpaRequest> opt = list.stream()
                .filter(r -> r.initiatorUUID.equals(initiator.getStringUUID()))
                .findFirst();

        if (opt.isEmpty()) {
            target.displayClientMessage(
                    getTranslatedText("commands.teleport_commands.tpa.notFound", target).withStyle(ChatFormatting.RED), true);
            return;
        }

        list.remove(opt.get());
        initiator.displayClientMessage(
                getTranslatedText("commands.teleport_commands.tpa.denied", initiator).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        target.displayClientMessage(
                getTranslatedText("commands.teleport_commands.tpa.denied", target).withStyle(ChatFormatting.WHITE), true);
        // 拒绝音效
        tools.playDenySound(target);
        tools.playDenySound(initiator);
    }
}
