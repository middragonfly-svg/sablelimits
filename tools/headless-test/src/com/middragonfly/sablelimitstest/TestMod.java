package com.middragonfly.sablelimitstest;

import com.middragonfly.sablelimits.config.ConfigManager;
import com.middragonfly.sablelimits.limits.LimitManager;
import com.middragonfly.sablelimits.limits.StructureCounts;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod("sablelimitstest")
public final class TestMod {

    public TestMod(final IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(TestMod::commands);
    }

    private static void commands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("slstest")
                .requires(source -> source.hasPermission(2))
                .executes(TestMod::run));
    }

    private static int run(final CommandContext<CommandSourceStack> context) {
        final ServerLevel level = context.getSource().getLevel();
        final StringBuilder out = new StringBuilder();
        try {
            runTests(level, out);
        } catch (final Throwable t) {
            out.append("EXCEPTION: ").append(t);
            t.printStackTrace();
        }
        context.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static void runTests(final ServerLevel level, final StringBuilder out) throws Exception {
        final BlockPos base = new BlockPos(200, 10, 200);
        clear(level, base, 6, 4, 6);

        // ---- placement on an existing Sable structure -------------------
        final List<BlockPos> blocks = new ArrayList<>();
        for (int dx = 0; dx < 3; dx++) {
            final BlockPos pos = base.east(dx);
            blocks.add(pos);
            level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
        }

        final ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                BoundingBox3i.from(blocks));
        out.append("assembled=").append(sub != null).append("; ");

        final BlockPos anchor = sub.getPlot().getCenterBlock();
        writeConfig(3, 100);
        ConfigManager.reload();

        StructureCounts counts = LimitManager.get().getOrScan(sub);
        out.append("seed total=").append(counts.totalBlocks)
                .append(" hopper=").append(counts.counts.getInt(Blocks.HOPPER)).append("; ");

        final InteractionResult denied = place(level, anchor.above());
        out.append("place over block limit=").append(denied).append("; ");

        level.destroyBlock(anchor.east(), false);
        counts = LimitManager.get().getOrScan(sub);
        out.append("after break total=").append(counts.totalBlocks).append("; ");

        final InteractionResult allowed = place(level, anchor.above());
        counts = LimitManager.get().getOrScan(sub);
        out.append("place after break=").append(allowed).append(" total=").append(counts.totalBlocks).append("; ");

        // ---- size limit --------------------------------------------------
        writeConfig(64, 3);
        ConfigManager.reload();
        final InteractionResult sizeDenied = place(level, anchor.east(2).above());
        out.append("place over size limit=").append(sizeDenied).append("; ");

        // ---- Sable assembly cancellation ---------------------------------
        writeConfig(1, 100, "minecraft:slime_block");
        ConfigManager.reload();
        final BlockPos base2 = new BlockPos(220, 10, 200);
        clear(level, base2, 4, 3, 4);
        level.setBlockAndUpdate(base2, Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base2.east(), Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base2.east(2), Blocks.SLIME_BLOCK.defaultBlockState());
        out.append("beforeAssembly=").append(level.getBlockState(base2).getBlock())
                .append("/").append(level.getBlockState(base2.east()).getBlock())
                .append("/").append(level.getBlockState(base2.east(2)).getBlock()).append("; ");
        boolean cancelled = false;
        try {
            SimAssemblyHelper.assembleFromSingleBlock(level, base2, base2.east(2), false, true);
        } catch (final AssemblyException e) {
            cancelled = true;
            out.append("assembly message=").append(e.component.getString()).append("; ");
        }
        out.append("assembly cancelled=").append(cancelled)
                .append(" blocksStillThere=").append(!level.getBlockState(base2).isAir()
                        && !level.getBlockState(base2.east()).isAir()
                        && !level.getBlockState(base2.east(2)).isAir()).append("; ");

        // ---- Sable assembly size cancellation -----------------------------
        writeJson("{\"enabled\":true,\"defaultMaxBlocks\":1,\"limits\":{},\"messages\":{}}");
        ConfigManager.reload();
        final BlockPos base5 = new BlockPos(280, 10, 200);
        clear(level, base5, 4, 3, 4);
        level.setBlockAndUpdate(base5, Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base5.east(), Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base5.east(2), Blocks.SLIME_BLOCK.defaultBlockState());
        boolean sizeCancelled = false;
        try {
            SimAssemblyHelper.assembleFromSingleBlock(level, base5, base5.east(2), false, true);
        } catch (final AssemblyException e) {
            sizeCancelled = true;
            out.append("size message=").append(e.component.getString()).append("; ");
        }
        out.append("assemblySize cancelled=").append(sizeCancelled).append("; ");

        // ---- Create contraption scope ------------------------------------
        final BlockPos base3 = new BlockPos(240, 10, 200);
        clear(level, base3, 4, 3, 4);
        level.setBlockAndUpdate(base3, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base3.east(), Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base3.east(2), Blocks.SLIME_BLOCK.defaultBlockState());

        writeJson("{\"enabled\":true,\"limits\":{\"C minecraft:slime_block\":1},\"messages\":{}}");
        ConfigManager.reload();
        boolean createCancelled = false;
        int createBlocks = -1;
        try {
            final TestContraption testContraption = new TestContraption();
            testContraption.searchMovedStructure(level, base3, Direction.UP);
            createBlocks = testContraption.getBlocks().size();
        } catch (final AssemblyException e) {
            createCancelled = true;
        }
        out.append("create cancelled=").append(createCancelled)
                .append(" blocks=").append(createBlocks)
                .append(" still=").append(!level.getBlockState(base3).isAir()
                        && !level.getBlockState(base3.east()).isAir()
                        && !level.getBlockState(base3.east(2)).isAir()).append("; ");

        // A Sable-only rule must not cancel a Create contraption.
        writeJson("{\"enabled\":true,\"limits\":{\"minecraft:slime_block\":1},\"messages\":{}}");
        ConfigManager.reload();
        boolean createAllowed = true;
        try {
            new TestContraption().searchMovedStructure(level, base3, Direction.UP);
        } catch (final AssemblyException e) {
            createAllowed = false;
        }
        out.append("create unaffectedBySableRule=").append(createAllowed).append("; ");

        // A Create-only rule must not block placement on a Sable structure.
        writeJson("{\"enabled\":true,\"limits\":{\"C minecraft:hopper\":1},\"messages\":{}}");
        ConfigManager.reload();
        final InteractionResult createRuleOnSable = place(level, anchor.above());
        out.append("sable unaffectedByCreateRule=").append(createRuleOnSable).append("; ");

        // Group combined limit (hopper + slime in one group, 1 total).
        final BlockPos base4 = new BlockPos(260, 10, 200);
        clear(level, base4, 4, 3, 4);
        level.setBlockAndUpdate(base4, Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(base4.east(), Blocks.SLIME_BLOCK.defaultBlockState());
        final List<BlockPos> mixed = List.of(base4, base4.east());
        final ServerSubLevel groupSub = SubLevelAssemblyHelper.assembleBlocks(level, base4, mixed,
                BoundingBox3i.from(mixed));
        writeJson("{\"enabled\":true,\"defaultMaxBlocks\":100,\"groups\":{\"mixed\":"
                + "[\"minecraft:hopper\",\"minecraft:slime_block\"]},\"limits\":{\"group:mixed\":1},\"messages\":{}}");
        ConfigManager.reload();
        final InteractionResult groupDenied = place(level, groupSub.getPlot().getCenterBlock().above());
        out.append("group denied=").append(groupDenied).append("; ");

        // ---- Forbidden blocks: placement on a Sable structure ------------
        final BlockPos forbiddenSupport = anchor.west();
        final BlockPos forbiddenPlacePos = forbiddenSupport.above();
        level.setBlockAndUpdate(forbiddenSupport, Blocks.STONE.defaultBlockState());

        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"minecraft:hopper\"],\"messages\":{}}");
        ConfigManager.reload();
        out.append("forbiddenPlace=").append(place(level, forbiddenPlacePos)).append("; ");

        // A Create-only forbidden entry must not block Sable placement.
        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"C minecraft:hopper\"],\"messages\":{}}");
        ConfigManager.reload();
        out.append("sableUnaffectedByCreateForbidden=").append(place(level, forbiddenPlacePos)).append("; ");

        // ---- Forbidden blocks: Sable assembly ----------------------------
        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"minecraft:slime_block\"],\"messages\":{}}");
        ConfigManager.reload();
        final BlockPos base6 = new BlockPos(300, 10, 200);
        clear(level, base6, 4, 3, 4);
        level.setBlockAndUpdate(base6, Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base6.east(), Blocks.SLIME_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(base6.east(2), Blocks.SLIME_BLOCK.defaultBlockState());
        boolean forbiddenAssembly = false;
        try {
            SimAssemblyHelper.assembleFromSingleBlock(level, base6, base6.east(2), false, true);
        } catch (final AssemblyException e) {
            forbiddenAssembly = true;
            out.append("forbiddenAssemblyMessage=").append(e.component.getString()).append("; ");
        }
        out.append("forbiddenAssembly=").append(forbiddenAssembly).append("; ");

        // ---- Forbidden blocks: Create assembly ---------------------------
        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"C minecraft:slime_block\"],\"messages\":{}}");
        ConfigManager.reload();
        boolean createForbidden = false;
        try {
            new TestContraption().searchMovedStructure(level, base3, Direction.UP);
        } catch (final AssemblyException e) {
            createForbidden = true;
        }
        out.append("createForbidden=").append(createForbidden).append("; ");

        // A Sable-only forbidden entry must not cancel a Create contraption.
        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"minecraft:slime_block\"],\"messages\":{}}");
        ConfigManager.reload();
        boolean createForbiddenScoped = true;
        try {
            new TestContraption().searchMovedStructure(level, base3, Direction.UP);
        } catch (final AssemblyException e) {
            createForbiddenScoped = false;
        }
        out.append("createUnaffectedBySableForbidden=").append(createForbiddenScoped).append("; ");

        // Bedrock specifically: a common "do not put this on a ship" block.
        final BlockPos bedrockSupport = anchor.east(4);
        final BlockPos bedrockPos = bedrockSupport.above();
        level.setBlockAndUpdate(bedrockSupport, Blocks.STONE.defaultBlockState());
        writeJson("{\"enabled\":true,\"forbiddenBlocks\":[\"* minecraft:bedrock\"],\"messages\":{}}");
        ConfigManager.reload();
        out.append("bedrockPlace=").append(place(level, bedrockPos, Items.BEDROCK)).append("; ");

        out.append("cache entries=").append(LimitManager.get() == null ? -1
                : LimitManager.get().cachedStructures()).append("; done");
    }

    private static void clear(final ServerLevel level, final BlockPos base,
                              final int sx, final int sy, final int sz) {
        for (int x = -1; x <= sx; x++) {
            for (int y = -1; y <= sy; y++) {
                for (int z = -1; z <= sz; z++) {
                    level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static InteractionResult place(final ServerLevel level, final BlockPos pos) {
        return place(level, pos, Items.HOPPER);
    }

    private static InteractionResult place(final ServerLevel level, final BlockPos pos,
                                           final net.minecraft.world.item.Item item) {
        final TestContext context = new TestContext(level, pos, item);
        return ((BlockItem) item).place(context);
    }

    private static void writeConfig(final int hopperLimit, final int maxBlocks) throws Exception {
        writeConfig(hopperLimit, maxBlocks, "minecraft:hopper");
    }

    private static void writeConfig(final int limit, final int maxBlocks, final String block) throws Exception {
        final String json = "{\"enabled\":true,\"defaultMaxBlocks\":" + maxBlocks
                + ",\"limits\":{\"" + block + "\":" + limit + "},\"messages\":{}}";
        Files.writeString(ConfigManager.configPath(), json, StandardCharsets.UTF_8);
    }

    private static void writeJson(final String json) throws Exception {
        Files.writeString(ConfigManager.configPath(), json, StandardCharsets.UTF_8);
    }

    private static final class TestContraption extends com.simibubi.create.content.contraptions.Contraption {
        @Override
        public boolean assemble(final net.minecraft.world.level.Level world, final BlockPos pos) {
            return true;
        }

        @Override
        public boolean canBeStabilized(final Direction facing, final BlockPos localPos) {
            return false;
        }

        @Override
        public com.simibubi.create.api.contraption.ContraptionType getType() {
            return null;
        }

        @Override
        protected boolean addToInitialFrontier(final net.minecraft.world.level.Level world, final BlockPos pos,
                                               final Direction forcedDirection,
                                               final java.util.Queue<BlockPos> frontier) {
            for (final Direction direction : Direction.values()) {
                frontier.add(pos.relative(direction));
            }
            return true;
        }
    }

    private static final class TestContext extends BlockPlaceContext {
        TestContext(final ServerLevel level, final BlockPos pos, final net.minecraft.world.item.Item item) {
            super(level, new FakePlayer(level, new GameProfile(UUID.randomUUID(), "slstest")),
                    InteractionHand.MAIN_HAND, new ItemStack(item),
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        }
    }
}
