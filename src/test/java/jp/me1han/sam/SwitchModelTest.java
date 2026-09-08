package jp.me1han.sam;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.script.*;
import jp.me1han.sam.api.DepartureProgram;
import jp.me1han.sam.network.PacketDepartureSwitchItemConfig;
import jp.me1han.sam.render.*;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

public final class SwitchModelTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static Reader resource(String file) {
        return new InputStreamReader(SwitchModelTest.class.getResourceAsStream("/assets/stationannouncemod/switches/" + file), StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        verifyYaw();
        verifyBundledModels();
        verifySoundResources();
        verifyItemModelSelection();
        String config = "{\"name\":\"sample\",\"model\":{\"modelFile\":\"sample.mqo\",\"offset\":[0,-132.60004,-32.25]},"
            + "\"pressedState\":{\"translations\":[{\"parts\":[\"On\"],\"offset\":[0,0,-1.1]}]}}";
        SwitchModelDefinition sample = SwitchModelDefinition.parse(new StringReader(config), "stationannouncemod:switches/test.json");
        check(Math.abs(sample.offset("On", true)[2] * sample.scale + 0.011) < 1e-9, "Legacy renderer movement matches 1.1cm");
        check(Math.abs(sample.modelOffset[1] + 132.60004) < 1e-9, "Whole-model origin correction");
        try { SwitchModelDefinition.resolveResource("stationannouncemod:switches/x.json", "../escape.mqo"); throw new AssertionError("Traversal accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        // Read user-provided references directly; never copy or change them.
        Path references = Paths.get("switch_sample");
        if (Files.isDirectory(references)) {
            try (java.util.stream.Stream<Path> files = Files.walk(references)) {
                for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".mqo"))::iterator) {
                    byte[] before = Files.readAllBytes(path);
                    MqoMesh mesh;
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { mesh = MqoMesh.read(reader); }
                    check(mesh.parts.size() == 2, "Reference part count: " + path);
                    if (mesh.parts.containsKey("On")) sample.validateParts(mesh.parts.keySet());
                    check(Arrays.equals(before, Files.readAllBytes(path)), "Reference unchanged");
                }
            }
        }
        AnnouncePackLoader.soundTicks.put("test:m", 20);
        AnnouncePackLoader.soundTicks.put("test:d", 5);
        verifyBlockClick();
        verifySwitches();
        verifyModeMatrix();
        verifyModeJson();
        verifyTachikawaCompletion();
        verifyLegacyDuration();
        verifyExternalPack();
        System.out.println("Switch models and interactions: " + checks + " checks passed");
    }

    private static void verifyBundledModels() throws Exception {
        SwitchModelRegistry.reset();
        SwitchModelDefinition alternate = SwitchModelRegistry.get("melodysw_alternate_sample");
        SwitchModelDefinition momentary = SwitchModelRegistry.get("melodysw_momentary_sample");
        check(alternate != null && alternate.switchMode == SwitchModelDefinition.SwitchMode.ALTERNATE,
            "Bundled alternate switch mode");
        check(momentary != null && momentary.switchMode == SwitchModelDefinition.SwitchMode.MOMENTARY,
            "Bundled momentary switch mode");
        check(SwitchModelRegistry.list().size() == 2, "Only the organized switch definitions are registered");
        try (Reader reader = resource("melodysw_alternate_sample.mqo")) {
            MqoMesh mesh = MqoMesh.read(reader);
            alternate.validateParts(mesh.parts.keySet());
            check(mesh.materials.size() == 1 && mesh.parts.containsKey("Main") && mesh.parts.containsKey("On"),
                "Bundled alternate MQO");
        }
        try (Reader reader = resource("melodysw_momentary_sample.mqo")) {
            MqoMesh mesh = MqoMesh.read(reader);
            momentary.validateParts(mesh.parts.keySet());
            check(mesh.materials.size() == 1 && mesh.parts.containsKey("obj1") && mesh.parts.containsKey("obj3"),
                "Edited bundled momentary MQO");
        }
        check(alternate.soundOn.equals("stationannouncemod:melodysw_alternate_off")
            && alternate.soundOff.equals("stationannouncemod:melodysw_alternate_off"), "RTM activation sound is used both ways");
        check(alternate.offset("On", true)[2] == -1.1, "RTM Point movement is preserved");
        check(alternate.buttonTexture.isEmpty() && momentary.buttonTexture.isEmpty(), "Organized models use text-only lists");
    }

    private static void verifySoundResources() throws Exception {
        try (Reader reader = new InputStreamReader(SwitchModelTest.class.getResourceAsStream(
                "/assets/stationannouncemod/sounds.json"), StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject sounds = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> event : sounds.entrySet()) {
                for (com.google.gson.JsonElement element : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = element.getAsString();
                    net.minecraft.util.ResourceLocation location = new net.minecraft.util.ResourceLocation(name);
                    String domain = name.contains(":") ? location.getResourceDomain() : "stationannouncemod";
                    String path = "/assets/" + domain + "/sounds/" + location.getResourcePath() + ".ogg";
                    try (InputStream stream = SwitchModelTest.class.getResourceAsStream(path)) {
                        check(stream != null && stream.read() >= 0, "Sound resource resolves: " + event.getKey());
                    }
                }
            }
        }
    }

    private static void verifyItemModelSelection() {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(new net.minecraft.item.Item());
        NBTTagCompound root = new NBTTagCompound();
        root.setString("unrelated", "preserved");
        NBTTagCompound block = new NBTTagCompound();
        block.setString("linkKey", "platform-1");
        root.setTag("BlockEntityTag", block);
        stack.setTagCompound(root);
        check(jp.me1han.sam.item.ItemDepartureSwitch.selectedModel(stack).equals(SwitchModelRegistry.DEFAULT_MODEL),
            "Unconfigured item uses the default switch model");
        check(jp.me1han.sam.item.ItemDepartureSwitch.selectModel(stack, "melodysw_alternate_sample"),
            "Picker applies an installed model");
        check(jp.me1han.sam.item.ItemDepartureSwitch.selectedModel(stack).equals("melodysw_alternate_sample"),
            "Picker selection round trip");
        check(stack.getTagCompound().getString("unrelated").equals("preserved")
            && stack.getTagCompound().getCompoundTag("BlockEntityTag").getString("linkKey").equals("platform-1"),
            "Picker preserves unrelated and portable item metadata");
        check(!jp.me1han.sam.item.ItemDepartureSwitch.selectModel(stack, "missing-model")
            && jp.me1han.sam.item.ItemDepartureSwitch.selectedModel(stack).equals("melodysw_alternate_sample"),
            "Picker rejects an unavailable model without changing the item");

        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            new PacketDepartureSwitchItemConfig(4, "melodysw_momentary_sample").toBytes(buffer);
            PacketDepartureSwitchItemConfig decoded = new PacketDepartureSwitchItemConfig();
            decoded.fromBytes(buffer);
            check(decoded.slot == 4 && decoded.modelName.equals("melodysw_momentary_sample"), "Item picker packet round trip");
        } finally {
            buffer.release();
        }
    }

    private static void verifySwitches() throws Exception {
        SwitchModelRegistry.reset();
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("sam", new SAMScriptAPI());
        engine.eval("function samMain(tile) { return sam.build('test:m', ['test:d'], sam.toggle().tachikawa(true)); }");
        AnnouncePackLoader.scriptEngines.put("test.js", engine);
        FixtureWorld world = new FixtureWorld();
        Parent parent = new Parent(); parent.linkKey = "test";
        Melody melody = new Melody(); melody.linkKey = "test"; melody.scriptName = "test.js";
        Button a = new Button(); a.linkKey = "test"; a.modelName = "melodysw_alternate_sample";
        Button b = new Button(); b.linkKey = "test"; b.modelName = "melodysw_alternate_sample";
        world.add(parent, 0); world.add(melody, 1); world.add(a, 2); world.add(b, 3);
        check(melody.click(a), "A click succeeds");
        check(a.isLatched() && !b.isActivated() && melody.isOn() && parent.starts == 1, "Independent A ON");
        check(melody.click(b), "B click succeeds");
        check(b.isLatched() && parent.starts == 1, "Second ON does not restart");
        melody.click(a);
        check(!a.isActivated() && b.isLatched() && melody.isOn() && melody.releases == 0, "A OFF while B stays ON");
        melody.click(b);
        check(!b.isActivated() && !melody.isOn() && melody.isPlaying() && melody.releases == 1, "Last OFF begins Tachikawa finish");
        check(world.sounds.equals(Arrays.asList("stationannouncemod:melodysw_alternate_off",
            "stationannouncemod:melodysw_alternate_off", "stationannouncemod:melodysw_alternate_off",
            "stationannouncemod:melodysw_alternate_off")), "One model click sound per operated switch");
        melody.click(a); melody.click(b);
        int releases = melody.releases;
        a.onChunkUnload();
        check(b.isLatched() && melody.isOn() && melody.releases == releases, "Unloading A leaves B active");
        b.applyConfig("other", b.modelName, 1);
        check(!melody.isOn() && melody.releases == releases + 1, "Relinking last switch releases old device");
        b.applyConfig("test", b.modelName, 1);
        a.validate(); // Reload before operating the previously unloaded switch again.
        melody.click(a); melody.click(b); melody.cancelPlayback();
        check(!a.isActivated() && !b.isActivated() && !melody.isPlaying(), "Emergency stop resets whole group");

        a.modelName = "melodysw_alternate_sample"; a.setRotationYaw(137); a.setOffset(0.25F, -0.5F, 1.25F);
        a.operate(true, false);
        check(world.soundX == 2.5D && world.soundY == 0.5D && world.soundZ == 0.5D,
            "Click sound remains at the placed block when the model is offset");
        NBTTagCompound portable = a.copySettings();
        check(!portable.hasKey("x") && !portable.hasKey("Activated"), "Portable metadata excludes position and live state");
        Button copy = new Button(); world.add(copy, 8); copy.readSettings(portable);
        check(copy.xCoord == 8 && copy.linkKey.equals("test") && copy.modelName.equals("melodysw_alternate_sample") && copy.getRotationYaw() == 137
            && copy.getOffsetX() == 0.25F && copy.getOffsetY() == -0.5F && copy.getOffsetZ() == 1.25F
            && !copy.isActivated(), "Copy preserves settings and offsets at a new position");
        NBTTagCompound saved = (NBTTagCompound) portable.copy();
        saved.setInteger("x", 12); saved.setInteger("y", 4); saved.setInteger("z", 7); saved.setBoolean("Activated", true);
        Button restored = new Button(); restored.readFromNBT(saved);
        check(restored.xCoord == 12 && restored.yCoord == 4 && restored.zCoord == 7 && restored.modelName.equals("melodysw_alternate_sample")
            && restored.getRotationYaw() == 137 && restored.getOffsetX() == 0.25F && restored.getOffsetY() == -0.5F
            && restored.getOffsetZ() == 1.25F && !restored.isActivated(), "Save reload retains settings and offsets without restoring stale ON state");
        jp.me1han.sam.block.BlockDepartureSwitch block = new jp.me1han.sam.block.BlockDepartureSwitch();
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(new net.minecraft.item.Item());
        NBTTagCompound itemTag = new NBTTagCompound(); itemTag.setTag("BlockEntityTag", portable); stack.setTagCompound(itemTag);
        Button placed = new Button(); world.add(placed, 9);
        block.onBlockPlacedBy(world, 9, 0, 0, new net.minecraft.entity.passive.EntityPig(world), stack);
        check(placed.xCoord == 9 && placed.modelName.equals("melodysw_alternate_sample") && placed.linkKey.equals("test") && placed.getRotationYaw() == 180
            && placed.getOffsetX() == 0.25F && placed.getOffsetY() == -0.5F && placed.getOffsetZ() == 1.25F
            && !portable.hasKey("x"), "Block placement restores metadata without mutating copied item data");
        placed.setOffset(10000, -10000, 5000);
        block.setBlockBoundsBasedOnState(world, 9, 0, 0);
        net.minecraft.util.AxisAlignedBB interaction = block.getCollisionBoundingBoxFromPool(world, 9, 0, 0);
        check(interaction.minX == 9 && interaction.minY == 0 && interaction.minZ == 0
            && interaction.maxX == 10 && interaction.maxY == 1 && interaction.maxZ == 1,
            "Interaction remains one block at the placement position regardless of offset");
        placed.setOffset(0.25F, -0.5F, 1.25F);
        net.minecraft.entity.passive.EntityPig placer = new net.minecraft.entity.passive.EntityPig(world);
        placer.rotationYaw = -32.6F;
        block.onBlockPlacedBy(world, 9, 0, 0, placer, stack);
        check(placed.getRotationYaw() == 210, "Placement hook snaps after loading copied settings");
        placer.setSneaking(true);
        block.onBlockPlacedBy(world, 9, 0, 0, placer, stack);
        check(placed.getRotationYaw() == 213, "Placement hook uses entity sneaking state");
        world.isRemote = true;
        placer.rotationYaw = 90;
        block.onBlockPlacedBy(world, 9, 0, 0, placer, stack);
        check(placed.getRotationYaw() == 213, "Client placement cannot decide yaw");
        world.isRemote = false;
        a.resetState(); copy.resetState();
        a.modelName = "melodysw_momentary_sample";
        engine.eval("function samMain(tile) { return sam.build('test:m', ['test:d'], sam.push()); }");
        world.sounds.clear();
        melody.click(a);
        check(a.isActivated() && !b.isActivated() && !a.isLatched(), "Momentary only presses source");
        world.time += 2; a.updateEntity();
        check(!a.isActivated() && world.sounds.isEmpty(), "Momentary model and automatic release are silent");
        int starts = parent.starts; melody.click(b);
        check(parent.starts == starts && b.isActivated(), "Busy momentary does not overlap playback");
        melody.cancelPlayback();
    }

    private static void verifyModeJson() throws Exception {
        String base = "{\"name\":\"mode_test\",\"model\":{\"modelFile\":\"push.mqo\"}";
        check(SwitchModelDefinition.parse(new StringReader(base + "}"), "stationannouncemod:switches/test.json")
            .switchMode == SwitchModelDefinition.SwitchMode.MOMENTARY, "Missing mode defaults to momentary");
        try {
            SwitchModelDefinition.parse(new StringReader(base + ",\"switchMode\":\"bad\"}"), "stationannouncemod:switches/test.json");
            throw new AssertionError("Invalid mode accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        for (String mode : new String[]{"alternate", "momentary"}) {
            try (Reader reader = Files.newBufferedReader(Paths.get("switch_" + mode + "_sample.json"), StandardCharsets.UTF_8)) {
                SwitchModelDefinition model = SwitchModelDefinition.parse(reader, "stationannouncemod:switches/sample.json");
                check(model.switchMode.name().equalsIgnoreCase(mode), "Sample mode " + mode);
                try (Reader mesh = resource(mode.equals("alternate") ? "melodysw_alternate_sample.mqo" : "melodysw_momentary_sample.mqo")) {
                    model.validateParts(MqoMesh.read(mesh).parts.keySet());
                }
                check(model.modelFile.equals("stationannouncemod:switches/" + (mode.equals("alternate")
                    ? "melodysw_alternate_sample" : "melodysw_momentary_sample") + ".mqo"), "Sample resource exists");
                check(model.tags.contains(mode.equals("alternate") ? "toggle" : "push"), "Sample tags describe current JS mode");
                if (mode.equals("alternate")) check(model.soundOn.equals("stationannouncemod:melodysw_alternate_off")
                    && model.soundOff.equals("stationannouncemod:melodysw_alternate_off"), "Sample uses a registered click sound");
            }
        }
    }

    private static void advance(FixtureWorld world, Melody melody, Button button, int ticks) {
        for (int i = 0; i < ticks; i++) { world.time++; button.updateEntity(); melody.updateEntity(); }
    }

    private static void verifyModeMatrix() throws Exception {
        for (boolean alternateJs : new boolean[]{false, true}) for (boolean alternateSwitch : new boolean[]{false, true}) {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            engine.put("sam", new SAMScriptAPI());
            engine.eval("function samMain(tile) { return sam.build('test:m', ['test:d'], sam."
                + (alternateJs ? "toggle" : "push") + "().interval(0.5)); }");
            AnnouncePackLoader.scriptEngines.put("matrix.js", engine);
            FixtureWorld world = new FixtureWorld();
            Parent parent = new Parent(); parent.linkKey = "matrix";
            Melody melody = new Melody(); melody.linkKey = parent.linkKey; melody.scriptName = "matrix.js";
            Button button = new Button(); button.linkKey = parent.linkKey;
            button.modelName = alternateSwitch ? "melodysw_alternate_sample" : "melodysw_momentary_sample";
            world.add(parent, 0); world.add(melody, 1); world.add(button, 2);
            check(melody.click(button) && parent.starts == 1 && button.isActivated(), "Matrix first press");
            advance(world, melody, button, 3);
            check(button.isActivated() == alternateSwitch && melody.isPlaying(), "Model determines display return");
            Button client = new Button();
            client.onDataPacket(null, (net.minecraft.network.play.server.S35PacketUpdateTileEntity) button.getDescriptionPacket());
            check(client.isActivated() == alternateSwitch, "Display sync is independent of playback mode");
            if (alternateJs) {
                check(button.isControlOn() && melody.isOn(), "Pulse return preserves logical ON");
                advance(world, melody, button, 40);
                check(melody.isOn() && parent.finishes == 0, "ON loops past chorus end");
                Button other = new Button(); other.linkKey = parent.linkKey;
                other.modelName = alternateSwitch ? "melodysw_momentary_sample" : "melodysw_alternate_sample"; world.add(other, 3);
                melody.click(other); melody.click(button);
                check(!button.isControlOn() && other.isControlOn() && melody.isOn() && parent.starts == 1, "Mixed models retain independent ON");
                world.time += 2; other.updateEntity();
                melody.click(other);
                check(!melody.isOn() && melody.releases == 1, "Last logical OFF releases playback");
                advance(world, melody, button, 15);
                check(parent.finishes == 1 && !melody.isPlaying(), "OFF completes closing sequence");
            } else {
                // Exercise melody, interval and door-close while rejecting every extra start.
                for (int target : new int[]{5, 22, 32}) {
                    advance(world, melody, button, target - (int) world.time);
                    int sounds = world.sounds.size();
                    melody.click(button);
                    if (alternateSwitch) melody.click(button); // OFF and ON while busy.
                    check(parent.starts == 1 && (alternateSwitch ? world.sounds.size() > sounds : world.sounds.size() == sounds),
                        "Busy click follows model feedback without replay");
                }
                advance(world, melody, button, 100);
                check(parent.finishes == 1 && parent.starts == 1 && !melody.isPlaying(), "Single sequence never loops or queues");
                check(button.isActivated() == alternateSwitch, "Completion preserves physical latch");
                if (alternateSwitch) {
                    melody.click(button);
                    check(parent.starts == 1 && !button.isActivated(), "OFF after completion does nothing to playback");
                }
                melody.click(button);
                check(parent.starts == 2, "New press after completion starts again");
            }
            melody.cancelPlayback();
            check(!button.isActivated() && !button.isControlOn(), "Emergency stop clears both states");
        }
    }

    private static void verifyYaw() throws Exception {
        SwitchModelRegistry.reset();
        java.lang.reflect.Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true);
        mapping.invoke(null, Button.class, "sam_test_switch_yaw");
        double[][] normal = {{3,180},{7,180},{8,195},{22,195},{23,210},{82,255},{83,270},{178,0},{359,180},{352.5,180}};
        for (double[] pair : normal) check(SwitchYaw.placement((float) -pair[0], false) == pair[1], "15 degree placement " + pair[0]);
        double[][] sneak = {{32.2,212},{32.4,212},{32.6,213},{137.2,317},{137.8,318},{359.6,180}};
        for (double[] pair : sneak) check(SwitchYaw.placement((float) -pair[0], true) == pair[1], "1 degree placement " + pair[0]);
        int[][] inputs = {{360,0},{361,1},{-1,359},{450,90},{-450,270},{Integer.MAX_VALUE,127},{Integer.MIN_VALUE,232}};
        Button tile = new Button();
        for (int[] pair : inputs) {
            check(SwitchYaw.parse(Integer.toString(pair[0])) == pair[1], "Integer normalization " + pair[0]);
            tile.applyConfig("", "melodysw_momentary_sample", pair[0]);
            check(tile.getRotationYaw() == pair[1], "Server config normalization " + pair[0]);
        }
        for (String invalid : new String[]{"", "abc", "12.5", "-", "2147483648"}) {
            try { SwitchYaw.parse(invalid); throw new AssertionError("Accepted " + invalid); }
            catch (NumberFormatException expected) { checks++; }
        }
        tile.setRotationYaw(0); tile.setOffset(1, 2, 3);
        net.minecraft.util.AxisAlignedBB shifted = tile.getRenderBoundingBox();
        check(Math.abs(shifted.minX - 1.425) < 1e-6 && Math.abs(shifted.minY - 2.0) < 1e-6
            && Math.abs(shifted.minZ - 3.45) < 1e-6, "Render bounds follow world-axis offsets");
        try { tile.setOffset(Float.NaN, 0, 0); throw new AssertionError("NaN offset accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        tile.setOffset(10000, -10000, Float.MAX_VALUE);
        check(tile.getOffsetX() == 10000 && tile.getOffsetY() == -10000 && tile.getOffsetZ() == Float.MAX_VALUE,
            "RTM-compatible finite offsets are not range-limited");
        for (int playerYaw = -720; playerYaw <= 720; playerYaw += 45) {
            double angle = Math.toRadians(SwitchYaw.placement(playerYaw, false));
            double playerAngle = Math.toRadians(playerYaw);
            check(Math.abs(Math.sin(angle) - Math.sin(playerAngle)) < 1e-6
                && Math.abs(Math.cos(angle) + Math.cos(playerAngle)) < 1e-6, "RTM model -Z front matches player " + playerYaw);
        }
        tile.setRotationYaw(137.25F); tile.setOffset(0.25F, -0.5F, 1.25F);
        NBTTagCompound saved = new NBTTagCompound(); tile.writeToNBT(saved);
        Button restored = new Button(); restored.readFromNBT(saved);
        check(restored.getRotationYaw() == 137.25F && restored.getOffsetX() == 0.25F
            && restored.getOffsetY() == -0.5F && restored.getOffsetZ() == 1.25F
            && !saved.hasKey("facing"), "Float yaw and offsets survive NBT round trip");
        Button client = new Button();
        client.onDataPacket(null, (net.minecraft.network.play.server.S35PacketUpdateTileEntity) tile.getDescriptionPacket());
        check(client.getRotationYaw() == 137.25F && client.getOffsetX() == 0.25F
            && client.getOffsetY() == -0.5F && client.getOffsetZ() == 1.25F,
            "Description packet retains yaw and offsets");
        double[] bounds = SwitchYaw.rotateBounds(new double[]{0.3,0,0.3,0.7,0.2,0.7}, 45);
        check(Math.abs(bounds[0] - (0.5 - Math.sqrt(0.08))) < 1e-6
            && Math.abs(bounds[5] - (0.5 + Math.sqrt(0.08))) < 1e-6, "Diagonal bounds enclose the rotated model");
    }

    private static void verifyTachikawaCompletion() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("sam", new SAMScriptAPI());
        engine.eval("function samMain(tile) { return sam.build('test:m', ['test:d'], sam.toggle().interval(0).tachikawa(true)); }");
        AnnouncePackLoader.scriptEngines.put("completion.js", engine);
        FixtureWorld world = new FixtureWorld();
        Parent parent = new Parent(); parent.linkKey = "completion";
        Melody melody = new Melody(); melody.linkKey = parent.linkKey; melody.scriptName = "completion.js";
        Button button = new Button(); button.linkKey = parent.linkKey;
        world.add(parent, 0); world.add(melody, 1); world.add(button, 2);
        melody.click(button);
        for (world.time = 1; world.time < 5; world.time++) melody.updateEntity();
        melody.click(button); melody.updateEntity();
        for (world.time = 6; world.time <= 10; world.time++) melody.updateEntity();
        check(parent.finishes == 0 && melody.isPlaying(), "Server waits for chorus after short door-close");
        for (world.time = 11; world.time <= 20; world.time++) melody.updateEntity();
        check(parent.finishes == 1 && !melody.isPlaying(), "Server completes on original chorus boundary, without OFF tick delay");
        melody.updateEntity();
        check(parent.finishes == 1, "Server completion fires once");
        melody.click(button); melody.click(button); melody.click(button);
        check(parent.starts == 3 && melody.isOn(), "Re-ON replaces the overlapping sequence");
        melody.cancelPlayback();
        for (world.time = 22; world.time < 60; world.time++) melody.updateEntity();
        check(parent.finishes == 1, "Canceled server sequence never notifies completion");
    }

    private static void verifyLegacyDuration() {
        FixtureWorld world = new FixtureWorld();
        Parent parent = new Parent(); parent.linkKey = "legacy";
        Melody melody = new Melody(); melody.linkKey = parent.linkKey; melody.soundId = "test:m";
        world.add(parent, 0); world.add(melody, 1);
        check(melody.click(null), "Legacy soundId uses registered JSON duration");
        for (world.time = 1; world.time <= 20; world.time++) melody.updateEntity();
        check(parent.finishes == 1 && !melody.isPlaying(), "Legacy playback completes at JSON duration");
        melody.soundId = "test:unregistered";
        check(!melody.click(null) && !melody.isPlaying() && parent.starts == 1,
            "Legacy sound without JSON duration cannot start or fall back to one second");
        check(melody.lastError.contains("test:unregistered") && melody.lastError.contains("sam_length.json"),
            "Missing legacy duration identifies the sound and JSON file");
    }

    private static void verifyExternalPack() throws Exception {
        Path path = Files.createTempFile(Paths.get("build"), "switch-pack-test-", ".zip");
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("assets/stationannouncemod/switches/external.json"));
                zip.write("{\"name\":\"external_test\",\"model\":{\"modelFile\":\"external.mqo\"},\"sounds\":{\"on\":\"custom_switch:on\"}}".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("assets/stationannouncemod/switches/external.mqo"));
                try (InputStream source = SwitchModelTest.class.getResourceAsStream("/assets/stationannouncemod/switches/melodysw_momentary_sample.mqo")) {
                    byte[] buffer = new byte[4096]; int length;
                    while ((length = source.read(buffer)) >= 0) zip.write(buffer, 0, length);
                }
                zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("assets/custom_switch/sounds.json"));
                zip.write("{}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path.toFile())) { SwitchModelRegistry.loadPack(zip); }
            SwitchModelDefinition model = SwitchModelRegistry.get("external_test");
            check(model != null && model.modelFile.equals("stationannouncemod:switches/external.mqo"), "External model registry and relative path");
            jp.me1han.sam.client.SAMResourcePack pack = new jp.me1han.sam.client.SAMResourcePack(path.toFile());
            check(pack.getResourceDomains().contains("custom_switch"), "External sound namespace discovered");
            try (Reader reader = new InputStreamReader(pack.getInputStream(new net.minecraft.util.ResourceLocation(model.modelFile)), StandardCharsets.UTF_8)) {
            check(MqoMesh.read(reader).parts.containsKey("obj1"), "External ZIP MQO resource loads through the production resource pack");
            }
            try (InputStream stream = pack.getInputStream(new net.minecraft.util.ResourceLocation("custom_switch:sounds.json"))) {
                check(stream.read() == '{', "External resources readable");
            }
        } finally { Files.deleteIfExists(path); }
        check(!Files.exists(path), "ZIP streams release Windows file handles");
    }

    private static void verifyBlockClick() throws Exception {
        FixtureWorld world = new FixtureWorld();
        Parent parent = new Parent(); parent.linkKey = "click-test";
        Melody melody = new Melody(); melody.linkKey = parent.linkKey; melody.soundId = "test:m";
        Button button = new Button();
        world.add(parent, 0); world.add(melody, 1); world.add(button, 2);
        // Only the overridden interaction methods are needed; avoid EntityPlayer's
        // crafting inventory constructor, which requires a running Forge registry.
        java.lang.reflect.Constructor<?> constructor = sun.reflect.ReflectionFactory.getReflectionFactory()
            .newConstructorForSerialization(ClickPlayer.class, Object.class.getDeclaredConstructor());
        ClickPlayer player = (ClickPlayer) constructor.newInstance();
        jp.me1han.sam.block.BlockDepartureSwitch block = new jp.me1han.sam.block.BlockDepartureSwitch();
        block.onBlockActivated(world, 2, 0, 0, player, 1, 0.5F, 0.5F, 0.5F);
        check(player.opened == 0 && player.messages == 0 && parent.starts == 0 && world.sounds.isEmpty(),
            "Unlinked ordinary right-click has no effects");
        button.applyConfig("click-test", "melodysw_momentary_sample", 0);
        block.onBlockActivated(world, 2, 0, 0, player, 1, 0.5F, 0.5F, 0.5F);
        check(parent.starts == 1 && button.isActivated() && player.opened == 0 && player.messages == 0,
            "Configured block right-click reaches linked melody device");
        player.setSneaking(true);
        block.onBlockActivated(world, 2, 0, 0, player, 1, 0.5F, 0.5F, 0.5F);
        check(player.opened == 1 && parent.starts == 1, "Sneak right-click opens settings without replay");
        melody.cancelPlayback();
    }

    private static class ClickPlayer extends net.minecraft.entity.player.EntityPlayer {
        int opened, messages;
        boolean sneaking;
        ClickPlayer(World world) { super(world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "click-test")); }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) { messages++; }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public void setSneaking(boolean value) { sneaking = value; }
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return true; }
        @Override public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() { return new net.minecraft.util.ChunkCoordinates(2, 0, 0); }
        @Override public void openGui(Object mod, int id, World world, int x, int y, int z) { opened++; }
    }

    private static class Button extends TileEntityDepartureSwitch { @Override public void markDirty() {} }
    private static class Parent extends TileEntityAnnouncer {
        int starts;
        int finishes;
        @Override public void startDeparture(DepartureProgram program) { starts++; }
        @Override public void notifyDepartureMelodyFinished() { finishes++; }
        @Override public void markDirty() {}
    }
    private static class Melody extends TileEntityDepartureMelody {
        int releases;
        @Override protected void sendControl(boolean cancel) { if (!cancel) releases++; }
        @Override public void markDirty() {}
    }
    private static class FixtureWorld extends World {
        long time;
        final List<String> sounds = new ArrayList<>();
        double soundX, soundY, soundZ;
        FixtureWorld() { super(new SaveHandlerMP(), "switch-test", new WorldProviderSurface(), new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT), new Profiler()); }
        void add(TileEntity tile, int x) { tile.setWorldObj(this); tile.xCoord = x; loadedTileEntityList.add(tile); tile.validate(); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public boolean isBlockIndirectlyGettingPowered(int x, int y, int z) { return false; }
        @Override public long getTotalWorldTime() { return time; }
        @Override public TileEntity getTileEntity(int x, int y, int z) {
            for (Object value : loadedTileEntityList) { TileEntity tile = (TileEntity) value; if (tile.xCoord == x && tile.yCoord == y && tile.zCoord == z) return tile; }
            return null;
        }
        @Override public void markBlockForUpdate(int x, int y, int z) {}
        @Override public void playSoundEffect(double x, double y, double z, String sound, float volume, float pitch) {
            sounds.add(sound); soundX = x; soundY = y; soundZ = z;
        }
    }
}
