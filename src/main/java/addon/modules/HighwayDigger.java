package addon.modules;

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.misc.MBlockPos;
import meteordevelopment.meteorclient.utils.player.CustomPlayerInput;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.input.Input;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import addon.Bar2Dee2;

@SuppressWarnings("ConstantConditions")
public class HighwayDigger extends Module {
    public enum Rotation {
        None(false, false),
        Mine(true, false),
        Place(false, true),
        Both(true, true);

        public final boolean mine, place;

        Rotation(boolean mine, boolean place) {
            this.mine = mine;
            this.place = place;
        }
    }

    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgDigging = settings.createGroup("Digging");
    public final SettingGroup sgPaving = settings.createGroup("Paving");
    public final SettingGroup sgInventory = settings.createGroup("Inventory");
    public final SettingGroup sgRenderDigging = settings.createGroup("Render Digging");
    public final SettingGroup sgRenderPaving = settings.createGroup("Render Paving");

    public float cameraYaw, cameraPitch;

    // General

    public final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
            .name("width")
            .description("Width of the highway.")
            .defaultValue(4)
            .range(1, 5)
            .sliderRange(1, 5)
            .build());

    public final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
            .name("height")
            .description("Height of the highway.")
            .defaultValue(3)
            .range(2, 5)
            .sliderRange(2, 5)
            .build());

    public final Setting<Rotation> rotation = sgGeneral.add(new EnumSetting.Builder<Rotation>()
            .name("rotation")
            .description("Mode of rotation.")
            .defaultValue(Rotation.Both)
            .build());

    // Digging

    public final Setting<Boolean> dontBreakTools = sgDigging.add(new BoolSetting.Builder()
            .name("dont-break-tools")
            .description("Don't break tools.")
            .defaultValue(false)
            .build());

    public final Setting<Integer> savePickaxes = sgDigging.add(new IntSetting.Builder()
            .name("save-pickaxes")
            .description("How many pickaxes to ensure are saved.")
            .defaultValue(0)
            .range(0, 36)
            .sliderRange(0, 36)
            .visible(() -> !dontBreakTools.get())
            .build());

    public final Setting<Integer> breakDelay = sgDigging.add(new IntSetting.Builder()
            .name("break-delay")
            .description("The delay between breaking blocks.")
            .defaultValue(0)
            .min(0)
            .build());

    public final Setting<Integer> blocksPerTick = sgDigging.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description(
                    "The maximum amount of blocks that can be mined in a tick. Only applies to blocks instantly breakable.")
            .defaultValue(1)
            .range(1, 100)
            .sliderRange(1, 25)
            .build());

    // Paving

    public final Setting<Integer> placeDelay = sgPaving.add(new IntSetting.Builder()
            .name("place-delay")
            .description("The delay between placing blocks.")
            .defaultValue(0)
            .min(0)
            .build());

    public final Setting<Integer> placementsPerTick = sgPaving.add(new IntSetting.Builder()
            .name("placements-per-tick")
            .description("The maximum amount of blocks that can be placed in a tick.")
            .defaultValue(1)
            .min(1)
            .build());

    // Inventory

    public final Setting<List<Item>> trashItems = sgInventory.add(new ItemListSetting.Builder()
            .name("trash-items")
            .description("Items that are considered trash and can be thrown out.")
            .defaultValue(
                    Items.NETHERRACK, Items.QUARTZ, Items.GOLD_NUGGET, Items.GOLDEN_SWORD, Items.GLOWSTONE_DUST,
                    Items.GLOWSTONE, Items.BLACKSTONE, Items.BASALT, Items.GHAST_TEAR, Items.SOUL_SAND, Items.SOUL_SOIL,
                    Items.ROTTEN_FLESH)
            .build());

    // Render Digging

    public final Setting<Boolean> renderMine = sgRenderDigging.add(new BoolSetting.Builder()
            .name("render-blocks-to-mine")
            .description("Render blocks to be mined.")
            .defaultValue(true)
            .build());

    public final Setting<ShapeMode> renderMineShape = sgRenderDigging.add(new EnumSetting.Builder<ShapeMode>()
            .name("blocks-to-mine-shape-mode")
            .description("How the blocks to be mined are rendered.")
            .defaultValue(ShapeMode.Both)
            .build());

    public final Setting<SettingColor> renderMineSideColor = sgRenderDigging.add(new ColorSetting.Builder()
            .name("blocks-to-mine-side-color")
            .description("Color of blocks to be mined.")
            .defaultValue(new SettingColor(225, 25, 25, 25))
            .build());

    public final Setting<SettingColor> renderMineLineColor = sgRenderDigging.add(new ColorSetting.Builder()
            .name("blocks-to-mine-line-color")
            .description("Color of blocks to be mined.")
            .defaultValue(new SettingColor(225, 25, 25))
            .build());

    // Render Paving

    public final Setting<Boolean> renderPlace = sgRenderPaving.add(new BoolSetting.Builder()
            .name("render-blocks-to-place")
            .description("Render blocks to be placed.")
            .defaultValue(true)
            .build());

    public final Setting<ShapeMode> renderPlaceShape = sgRenderPaving.add(new EnumSetting.Builder<ShapeMode>()
            .name("blocks-to-place-shape-mode")
            .description("How the blocks to be placed are rendered.")
            .defaultValue(ShapeMode.Both)
            .build());

    public final Setting<SettingColor> renderPlaceSideColor = sgRenderPaving.add(new ColorSetting.Builder()
            .name("blocks-to-place-side-color")
            .description("Color of blocks to be placed.")
            .defaultValue(new SettingColor(25, 25, 225, 25))
            .build());

    public final Setting<SettingColor> renderPlaceLineColor = sgRenderPaving.add(new ColorSetting.Builder()
            .name("blocks-to-place-line-color")
            .description("Color of blocks to be placed.")
            .defaultValue(new SettingColor(25, 25, 225))
            .build());

    private HorizontalDirection dir, leftDir, rightDir;

    private Input prevInput;
    private CustomPlayerInput input;

    private State state, lastState;
    private IBlockPosProvider blockPosProvider;

    public Vec3d start;
    public int blocksBroken, blocksPlaced;
    private final MBlockPos lastBreakingPos = new MBlockPos();
    private int placeTimer, breakTimer, count;

    private final MBlockPos posRender2 = new MBlockPos();
    private final MBlockPos posRender3 = new MBlockPos();

    public HighwayDigger() {
        super(Bar2Dee2.CURRY, "Highway Dig", "Automatically dig highways.");
    }

    /*
     * todo
     * - separate digging and paving more effectively
     * - better inventory management
     * - getting echests and picks from shulker boxes - refactor echest blockade to
     * be more general purpose?
     * - access to your ec
     * - separate walking forwards from the current state to speed up actions
     * - fix issues related to y level changes
     */

    @Override
    public void onActivate() {
        dir = HorizontalDirection.get(mc.player.getYaw());
        leftDir = dir.rotateLeftSkipOne();
        rightDir = leftDir.opposite();

        prevInput = mc.player.input;
        mc.player.input = input = new CustomPlayerInput();

        state = State.Forward;
        setState(State.Center);
        blockPosProvider = dir.diagonal ? new DiagonalBlockPosProvider() : new StraightBlockPosProvider();

        start = mc.player.getPos();
        blocksBroken = blocksPlaced = 0;
        lastBreakingPos.set(0, 0, 0);

        placeTimer = 0;
        breakTimer = 0;
        count = 0;

        if (blocksPerTick.get() > 1 && rotation.get().mine)
            warning("With rotations enabled, you can break at most 1 block per tick.");
        if (placementsPerTick.get() > 1 && rotation.get().place)
            warning("With rotations enabled, you can place at most 1 block per tick.");

        if (Modules.get().get(InstantRebreak.class).isActive())
            warning("It's recommended to disable the Instant Rebreak module and instead use the 'instantly-rebreak-echests' setting to avoid errors.");

        cameraPitch = mc.player.getPitch();
        cameraYaw = mc.player.getYaw();
    }

    @Override
    public void onDeactivate() {
        mc.player.input = prevInput;

        mc.player.setYaw(dir.yaw);

        if (!Modules.get().isActive(HighwayFly.class)) {
            mc.player.setPitch(cameraPitch);
            mc.player.setYaw(cameraYaw);
        }

        /*
         * if (displayInfo) {
         * info("Distance: (highlight)%.0f", PlayerUtils.distanceTo(start));
         * info("Blocks broken: (highlight)%d", blocksBroken);
         * info("Blocks placed: (highlight)%d", blocksPlaced);
         * }
         */
    }

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        toggle();
    }

    private void errorEarly(String message, Object... args) {
        super.error(message, args);

        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (width.get() < 3 && dir.diagonal) {
            errorEarly("Diagonal highways with width less than 3 are not supported.");
            return;
        }

        if (Modules.get().get(AutoEat.class).eating)
            return;
        if (Modules.get().get(AutoGap.class).isEating())
            return;
        if (Modules.get().get(KillAura.class).attacking)
            return;

        count = 0;

        state.tick(this);

        if (breakTimer > 0)
            breakTimer--;
        if (placeTimer > 0)
            placeTimer--;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderMine.get()) {
            render(event, blockPosProvider.getFront(), mBlockPos -> canMine(mBlockPos, true), true);
        }

        if (renderPlace.get()) {
            render(event, blockPosProvider.getLiquids(), mBlockPos -> canPlace(mBlockPos, true), false);
            render(event, blockPosProvider.getFloor(), mBlockPos -> canPlace(mBlockPos, false), false);
        }
    }

    private void render(Render3DEvent event, MBPIterator it, Predicate<MBlockPos> predicate, boolean mine) {
        Color sideColor = mine ? renderMineSideColor.get() : renderPlaceSideColor.get();
        Color lineColor = mine ? renderMineLineColor.get() : renderPlaceLineColor.get();
        ShapeMode shapeMode = mine ? renderMineShape.get() : renderPlaceShape.get();

        for (MBlockPos pos : it) {
            posRender2.set(pos);

            if (predicate.test(posRender2)) {
                int excludeDir = 0;

                for (Direction side : Direction.values()) {
                    posRender3.set(posRender2).add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());

                    it.save();
                    for (MBlockPos p : it) {
                        if (p.equals(posRender3) && predicate.test(p))
                            excludeDir |= Dir.get(side);
                    }
                    it.restore();
                }

                event.renderer.box(posRender2.getBlockPos(), sideColor, lineColor, shapeMode, excludeDir);
            }
        }
    }

    private void setState(State state) {
        lastState = this.state;
        this.state = state;

        input.stop();
        state.start(this);
    }

    private int getWidthLeft() {
        return switch (width.get()) {
            default -> 0;
            case 2, 3 -> 1;
            case 4, 5 -> 2;
        };
    }

    private int getWidthRight() {
        return switch (width.get()) {
            default -> 0;
            case 3, 4 -> 1;
            case 5 -> 2;
        };
    }

    private boolean canMine(MBlockPos pos, boolean ignoreBlocksToPlace) {
        BlockState state = pos.getState();
        return BlockUtils.canBreak(pos.getBlockPos(), state);
    }

    private boolean canPlace(MBlockPos pos, boolean liquids) {
        return liquids ? !pos.getState().getFluidState().isEmpty() : BlockUtils.canPlace(pos.getBlockPos());
    }

    public MutableText getStatsText() {
        MutableText text = Text.literal(String.format("%sDistance: %s%.0f\n", Formatting.GRAY, Formatting.WHITE,
                mc.player == null ? 0.0f : PlayerUtils.distanceTo(start)));
        text.append(String.format("%sBlocks broken: %s%d\n", Formatting.GRAY, Formatting.WHITE, blocksBroken));
        text.append(String.format("%sBlocks placed: %s%d", Formatting.GRAY, Formatting.WHITE, blocksPlaced));

        return text;
    }

    private enum State {
        Center {
            @Override
            protected void tick(HighwayDigger b) {
                // There is probably a much better way to do this
                double x = Math.abs(b.mc.player.getX() - (int) b.mc.player.getX()) - 0.5;
                double z = Math.abs(b.mc.player.getZ() - (int) b.mc.player.getZ()) - 0.5;

                boolean isX = Math.abs(x) <= 0.1;
                boolean isZ = Math.abs(z) <= 0.1;

                if (isX && isZ) {
                    b.input.stop();
                    b.mc.player.setVelocity(0, 0, 0);
                    b.mc.player.setPosition((int) b.mc.player.getX() + (b.mc.player.getX() < 0 ? -0.5 : 0.5),
                            b.mc.player.getY(), (int) b.mc.player.getZ() + (b.mc.player.getZ() < 0 ? -0.5 : 0.5));
                    b.setState(b.lastState);
                } else {
                    b.mc.player.setYaw(0);

                    if (!isZ) {
                        b.input.pressingForward = z < 0;
                        b.input.pressingBack = z > 0;

                        if (b.mc.player.getZ() < 0) {
                            boolean forward = b.input.pressingForward;
                            b.input.pressingForward = b.input.pressingBack;
                            b.input.pressingBack = forward;
                        }
                    }

                    if (!isX) {
                        b.input.pressingRight = x > 0;
                        b.input.pressingLeft = x < 0;

                        if (b.mc.player.getX() < 0) {
                            boolean right = b.input.pressingRight;
                            b.input.pressingRight = b.input.pressingLeft;
                            b.input.pressingLeft = right;
                        }
                    }

                    b.input.sneaking = true;
                }
            }
        },

        Forward {
            @Override
            protected void start(HighwayDigger b) {
                b.mc.player.setYaw(b.dir.yaw);

                checkTasks(b);
            }

            @Override
            protected void tick(HighwayDigger b) {
                checkTasks(b);

                if (b.state == Forward)
                    b.input.pressingForward = true; // Move
            }

            private void checkTasks(HighwayDigger b) {
                if (needsToPlace(b, b.blockPosProvider.getLiquids(), true))
                    b.setState(FillLiquids); // Fill Liquids
                else if (needsToMine(b, b.blockPosProvider.getFront(), true))
                    b.setState(MineFront); // Mine Front
                // else if (needsToPlace(b, b.blockPosProvider.getFloor(), false))
                // b.setState(Forward); // Place Floor
            }

            private boolean needsToMine(HighwayDigger b, MBPIterator it, boolean ignoreBlocksToPlace) {
                for (MBlockPos pos : it) {
                    if (b.canMine(pos, ignoreBlocksToPlace))
                        return true;
                }

                return false;
            }

            private boolean needsToPlace(HighwayDigger b, MBPIterator it, boolean liquids) {
                for (MBlockPos pos : it) {
                    if (b.canPlace(pos, liquids))
                        return true;
                }

                return false;
            }
        },

        FillLiquids {
            @Override
            protected void tick(HighwayDigger b) {
                int slot = findBlocksToPlacePrioritizeTrash(b);
                if (slot == -1)
                    return;

                place(b, new MBPIteratorFilter(b.blockPosProvider.getLiquids(),
                        pos -> !pos.getState().getFluidState().isEmpty()), slot, Forward);
            }
        },

        MineFront {
            @Override
            protected void tick(HighwayDigger b) {
                mine(b, b.blockPosProvider.getFront(), true, Forward, this);
            }
        },

        ThrowOutTrash {
            private int skipSlot;
            private boolean timerEnabled, firstTick;
            private int timer;

            @Override
            protected void start(HighwayDigger b) {
                int biggestCount = 0;

                for (int i = 0; i < b.mc.player.getInventory().main.size(); i++) {
                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (itemStack.getItem() instanceof BlockItem && b.trashItems.get().contains(itemStack.getItem())
                            && itemStack.getCount() > biggestCount) {
                        biggestCount = itemStack.getCount();
                        skipSlot = i;

                        if (biggestCount >= 64)
                            break;
                    }
                }

                if (biggestCount == 0)
                    skipSlot = -1;
                timerEnabled = false;
                firstTick = true;
            }

            @Override
            protected void tick(HighwayDigger b) {
                if (timerEnabled) {
                    if (timer > 0)
                        timer--;
                    else
                        b.setState(b.lastState);

                    return;
                }

                b.mc.player.setYaw(b.dir.opposite().yaw);
                b.mc.player.setPitch(-25);

                if (firstTick) {
                    firstTick = false;
                    return;
                }

                if (!b.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    InvUtils.dropHand();
                    return;
                }

                for (int i = 0; i < b.mc.player.getInventory().main.size(); i++) {
                    if (i == skipSlot)
                        continue;

                    ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                    if (b.trashItems.get().contains(itemStack.getItem())) {
                        InvUtils.drop().slot(i);
                        return;
                    }
                }

                timerEnabled = true;
                timer = 10;
            }
        };

        protected void start(HighwayDigger b) {
        }

        protected abstract void tick(HighwayDigger b);

        protected void mine(HighwayDigger b, MBPIterator it, boolean ignoreBlocksToPlace, State nextState,
                State lastState) {
            boolean breaking = false;
            boolean finishedBreaking = false; // if you can multi break this lets you mine blocks between tasks in a
                                              // single tick

            for (MBlockPos pos : it) {
                if (b.count >= b.blocksPerTick.get())
                    return;
                if (b.breakTimer > 0)
                    return;

                BlockState state = pos.getState();
                if (state.isAir())
                    continue;

                int slot = findAndMoveBestToolToHotbar(b, state, false);
                if (slot == -1)
                    return;

                InvUtils.swap(slot, false);

                BlockPos mcPos = pos.getBlockPos();
                if (BlockUtils.canBreak(mcPos)) {
                    if (b.rotation.get().mine)
                        Rotations.rotate(Rotations.getYaw(mcPos), Rotations.getPitch(mcPos),
                                () -> BlockUtils.breakBlock(mcPos, true));
                    else
                        BlockUtils.breakBlock(mcPos, true);
                    breaking = true;

                    b.breakTimer = b.breakDelay.get();

                    if (!b.lastBreakingPos.equals(pos)) {
                        b.lastBreakingPos.set(pos);
                        b.blocksBroken++;
                    }

                    b.count++;

                    // can only multi break if we aren't rotating and the block can be instamined
                    if (b.blocksPerTick.get() == 1 || !BlockUtils.canInstaBreak(mcPos) || b.rotation.get().mine)
                        break;
                }

                if (!it.hasNext() && BlockUtils.canInstaBreak(mcPos))
                    finishedBreaking = true;
            }

            if (finishedBreaking || !breaking) {
                b.setState(nextState);
                b.lastState = lastState;
            }
        }

        protected void place(HighwayDigger b, MBPIterator it, int slot, State nextState) {
            boolean placed = false;
            boolean finishedPlacing = false;

            for (MBlockPos pos : it) {
                if (b.count >= b.placementsPerTick.get())
                    return;
                if (b.placeTimer > 0)
                    return;

                if (BlockUtils.place(pos.getBlockPos(), Hand.MAIN_HAND, slot, b.rotation.get().place, 0, true, true,
                        true)) {
                    placed = true;
                    b.blocksPlaced++;
                    b.placeTimer = b.placeDelay.get();

                    b.count++;
                    if (b.placementsPerTick.get() == 1)
                        break;
                }

                if (!it.hasNext())
                    finishedPlacing = true;
            }

            if (finishedPlacing || !placed)
                b.setState(nextState);
        }

        private int findSlot(HighwayDigger b, Predicate<ItemStack> predicate, boolean hotbar) {
            for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : b.mc.player.getInventory().main.size()); i++) {
                if (predicate.test(b.mc.player.getInventory().getStack(i)))
                    return i;
            }

            return -1;
        }

        private int findHotbarSlot(HighwayDigger b, boolean replaceTools) {
            int thrashSlot = -1;
            int slotsWithBlocks = 0;
            int slotWithLeastBlocks = -1;
            int slotWithLeastBlocksCount = Integer.MAX_VALUE;

            // Loop hotbar
            for (int i = 0; i < 9; i++) {
                ItemStack itemStack = b.mc.player.getInventory().getStack(i);

                // Return if the slot is empty
                if (itemStack.isEmpty())
                    return i;

                // Return if the slot contains a tool and replacing tools is enabled
                if (replaceTools && AutoTool.isTool(itemStack))
                    return i;

                // Store the slot if it contains thrash
                if (b.trashItems.get().contains(itemStack.getItem()))
                    thrashSlot = i;

                // Update tracked stats about slots that contain building blocks
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    slotsWithBlocks++;

                    if (itemStack.getCount() < slotWithLeastBlocksCount) {
                        slotWithLeastBlocksCount = itemStack.getCount();
                        slotWithLeastBlocks = i;
                    }
                }
            }

            // Return thrash slot if found
            if (thrashSlot != -1)
                return thrashSlot;

            // If there are more than 1 slots with building blocks return the slot with the
            // lowest amount of blocks
            if (slotsWithBlocks > 1)
                return slotWithLeastBlocks;

            // No space found in hotbar
            b.error("No empty space in hotbar.");
            return -1;
        }

        private boolean hasItem(HighwayDigger b, Item item) {
            for (int i = 0; i < b.mc.player.getInventory().main.size(); i++) {
                if (b.mc.player.getInventory().getStack(i).getItem() == item)
                    return true;
            }

            return false;
        }

        protected int countItem(HighwayDigger b, Predicate<ItemStack> predicate) {
            int count = 0;
            for (int i = 0; i < b.mc.player.getInventory().main.size(); i++) {
                ItemStack stack = b.mc.player.getInventory().getStack(i);
                if (predicate.test(stack))
                    count += stack.getCount();
            }

            return count;
        }

        protected int findAndMoveToHotbar(HighwayDigger b, Predicate<ItemStack> predicate, boolean required) {
            // Check hotbar
            int slot = findSlot(b, predicate, true);
            if (slot != -1)
                return slot;

            // Find hotbar slot to move to
            int hotbarSlot = findHotbarSlot(b, false);
            if (hotbarSlot == -1)
                return -1;

            // Check inventory
            slot = findSlot(b, predicate, false);

            // Stop if no items were found and are required
            if (slot == -1) {
                if (required) {
                    b.error("Out of items.");
                }

                return -1;
            }

            // Move items from inventory to hotbar
            InvUtils.move().from(slot).toHotbar(hotbarSlot);
            InvUtils.dropHand();

            return hotbarSlot;
        }

        protected int findAndMoveBestToolToHotbar(HighwayDigger b, BlockState blockState, boolean noSilkTouch) {
            // Check for creative
            if (b.mc.player.isCreative())
                return b.mc.player.getInventory().selectedSlot;

            // Find best tool
            double bestScore = -1;
            int bestSlot = -1;

            for (int i = 0; i < b.mc.player.getInventory().main.size(); i++) {
                double score = AutoTool.getScore(b.mc.player.getInventory().getStack(i), blockState, false, false,
                        AutoTool.EnchantPreference.None, itemStack -> {
                            if (noSilkTouch && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH))
                                return false;
                            return !b.dontBreakTools.get() || itemStack.getMaxDamage() - itemStack.getDamage() > 1;
                        });

                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }

            if (bestSlot == -1)
                return b.mc.player.getInventory().selectedSlot;

            if (b.mc.player.getInventory().getStack(bestSlot).getItem() instanceof PickaxeItem) {
                int count = countItem(b, stack -> stack.getItem() instanceof PickaxeItem);

                if (count <= b.savePickaxes.get()) {
                    b.error("Found less than the selected amount of pickaxes required: " + count + "/"
                            + (b.savePickaxes.get() + 1));
                    return -1;
                }
            }

            // Check if the tool is already in hotbar
            if (bestSlot < 9)
                return bestSlot;

            // Find hotbar slot to move to
            int hotbarSlot = findHotbarSlot(b, true);
            if (hotbarSlot == -1)
                return -1;

            // Move tool from inventory to hotbar
            InvUtils.move().from(bestSlot).toHotbar(hotbarSlot);
            InvUtils.dropHand();

            return hotbarSlot;
        }

        protected int findBlocksToPlace(HighwayDigger b) {
            int slot = findAndMoveToHotbar(b, itemStack -> itemStack.getItem() instanceof BlockItem blockItem, false);

            return slot;
        }

        protected int findBlocksToPlacePrioritizeTrash(HighwayDigger b) {
            int slot = findAndMoveToHotbar(b, itemStack -> {
                if (!(itemStack.getItem() instanceof BlockItem))
                    return false;
                return b.trashItems.get().contains(itemStack.getItem());
            }, false);

            return slot != -1 ? slot : findBlocksToPlace(b);
        }
    }

    private interface MBPIterator extends Iterator<MBlockPos>, Iterable<MBlockPos> {
        void save();

        void restore();

        @NotNull
        @Override
        default Iterator<MBlockPos> iterator() {
            return this;
        }
    }

    private static class MBPIteratorFilter implements MBPIterator {
        private final MBPIterator it;
        private final Predicate<MBlockPos> predicate;

        private MBlockPos pos;
        private boolean isOld = true;

        private boolean pisOld = true;

        public MBPIteratorFilter(MBPIterator it, Predicate<MBlockPos> predicate) {
            this.it = it;
            this.predicate = predicate;
        }

        @Override
        public void save() {
            it.save();
            pisOld = isOld;
            isOld = true;
        }

        @Override
        public void restore() {
            it.restore();
            isOld = pisOld;
        }

        @Override
        public boolean hasNext() {
            if (isOld) {
                isOld = false;
                pos = null;

                while (it.hasNext()) {
                    pos = it.next();

                    if (predicate.test(pos))
                        return true;
                    else
                        pos = null;
                }
            }

            return pos != null && predicate.test(pos);
        }

        @Override
        public MBlockPos next() {
            isOld = true;
            return pos;
        }
    }

    private interface IBlockPosProvider {
        MBPIterator getFront();

        MBPIterator getFloor();

        MBPIterator getRailings(boolean mine);

        MBPIterator getLiquids();

        MBPIterator getEChestBlockade(boolean mine);
    }

    private class StraightBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.set(mc.player).offset(dir).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int w, y;
                private int pw, py;

                @Override
                public boolean hasNext() {
                    return w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= width.get()) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.set(mc.player).offset(dir).offset(leftDir, getWidthLeft()).add(0, -1, 0);

            return new MBPIterator() {
                private int w;
                private int pw;

                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public MBlockPos next() {
                    return pos2.set(pos).offset(rightDir, w++);
                }

                @Override
                public void save() {
                    pw = w;
                    w = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                }
            };
        }

        @Override
        public MBPIterator getRailings(boolean _mine) {
            return new MBPIterator() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public MBlockPos next() {
                    return null;
                }

                @Override
                public void save() {
                }

                @Override
                public void restore() {
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            pos.set(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + 1);

            return new MBPIterator() {
                private int w, y;
                private int pw, py;

                private int getWidth() {
                    return width.get() + 0;
                }

                @Override
                public boolean hasNext() {
                    return w < getWidth() + 2 && y < height.get() + 1;
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y, 0);

                    w++;
                    if (w >= getWidth() + 2) {
                        w = 0;
                        y++;
                    }

                    return pos2;
                }

                @Override
                public void save() {
                    pw = w;
                    py = y;
                    w = y = 0;
                }

                @Override
                public void restore() {
                    w = pw;
                    y = py;
                }
            };
        }

        @Override
        public MBPIterator getEChestBlockade(boolean mine) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0, y;
                private int pi, py;

                private MBlockPos get(int i) {
                    pos.set(mc.player).offset(dir.opposite());

                    return switch (i) {
                        case -1 -> pos;
                        default -> pos.offset(dir.opposite());
                        case 1 -> pos.offset(leftDir);
                        case 2 -> pos.offset(rightDir);
                        case 3 -> pos.offset(dir, 2);
                    };
                }

                @Override
                public boolean hasNext() {
                    return i < 4 && y < 2;
                }

                @Override
                public MBlockPos next() {
                    if (width.get() == 1 && i > 0 && y == 0)
                        y++;

                    MBlockPos pos = get(i).add(0, y, 0);

                    y++;
                    if (y > 1) {
                        y = 0;
                        i++;
                    }

                    return pos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = y = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }
            };
        }
    }

    private class DiagonalBlockPosProvider implements IBlockPosProvider {
        private final MBlockPos pos = new MBlockPos();
        private final MBlockPos pos2 = new MBlockPos();

        @Override
        public MBPIterator getFront() {
            pos.set(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w, y;
                private int pi, pw, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get() && y < height.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.get()) {
                        y = 0;
                        w++;

                        if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                            w = 0;
                            i++;

                            pos.set(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                        }
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0)
                        pos.set(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else
                        pos.set(mc.player).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getFloor() {
            pos.set(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);

            return new MBPIterator() {
                private int i, w;
                private int pi, pw;

                @Override
                public boolean hasNext() {
                    return i < 2 && w < width.get();
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).offset(rightDir, w++);

                    if (w >= (i == 0 ? width.get() - 1 : width.get())) {
                        w = 0;
                        i++;

                        pos.set(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0)
                        pos.set(mc.player).add(0, -1, 0).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft() - 1);
                    else
                        pos.set(mc.player).add(0, -1, 0).offset(dir).offset(leftDir, getWidthLeft());
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    i = w = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getRailings(boolean mine) {
            boolean mineAll = false;
            pos.set(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i, y;
                private int pi, py;

                @Override
                public boolean hasNext() {
                    return i < 2 && y < (mineAll ? height.get() : 1);
                }

                @Override
                public MBlockPos next() {
                    pos2.set(pos).add(0, y++, 0);

                    if (y >= (mineAll ? height.get() : 1)) {
                        y = 0;
                        i++;

                        pos.set(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0)
                        pos.set(mc.player).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else
                        pos.set(mc.player).offset(dir.rotateRight()).offset(rightDir, getWidthRight());
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getLiquids() {
            boolean m = false;
            pos.set(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());

            return new MBPIterator() {
                private int i, w, y;
                private int pi, pw, py;

                private int getWidth() {
                    return width.get() + (i == 0 ? 1 : 0) + (m && i == 1 ? 2 : 0);
                }

                @Override
                public boolean hasNext() {
                    if (m && i == 1 && y == height.get() && w == getWidth() - 1)
                        return false;
                    return i < 2 && w < getWidth() && y < height.get() + 1;
                }

                private void updateW() {
                    w++;

                    if (w >= getWidth()) {
                        w = 0;
                        i++;

                        pos.set(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                    }
                }

                @Override
                public MBlockPos next() {
                    if (i == (m ? 1 : 0) && y == height.get() && (w == 0 || w == getWidth() - 1)) {
                        y = 0;
                        updateW();
                    }

                    pos2.set(pos).offset(rightDir, w).add(0, y++, 0);

                    if (y >= height.get() + 1) {
                        y = 0;
                        updateW();
                    }

                    return pos2;
                }

                private void initPos() {
                    if (i == 0)
                        pos.set(mc.player).offset(dir).offset(dir.rotateLeft()).offset(leftDir, getWidthLeft());
                    else
                        pos.set(mc.player).offset(dir, 2).offset(leftDir, getWidthLeft() + (m ? 1 : 0));
                }

                @Override
                public void save() {
                    pi = i;
                    pw = w;
                    py = y;
                    i = w = y = 0;

                    initPos();
                }

                @Override
                public void restore() {
                    i = pi;
                    w = pw;
                    y = py;

                    initPos();
                }
            };
        }

        @Override
        public MBPIterator getEChestBlockade(boolean mine) {
            return new MBPIterator() {
                private int i = mine ? -1 : 0, y;
                private int pi, py;

                private MBlockPos get(int i) {
                    HorizontalDirection dir2 = dir.rotateLeft().rotateLeftSkipOne();

                    pos.set(mc.player).offset(dir2);

                    return switch (i) {
                        case -1 -> pos;
                        default -> pos.offset(dir2);
                        case 1 -> pos.offset(dir2.rotateLeftSkipOne());
                        case 2 -> pos.offset(dir2.rotateLeftSkipOne().opposite());
                        case 3 -> pos.offset(dir2.opposite(), 2);
                    };
                }

                @Override
                public boolean hasNext() {
                    return i < 4 && y < 2;
                }

                @Override
                public MBlockPos next() {
                    MBlockPos pos = get(i).add(0, y, 0);

                    y++;
                    if (y > 1) {
                        y = 0;
                        i++;
                    }

                    return pos;
                }

                @Override
                public void save() {
                    pi = i;
                    py = y;
                    i = y = 0;
                }

                @Override
                public void restore() {
                    i = pi;
                    y = py;
                }
            };
        }
    }
}