package jp.me1han.sam;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jp.me1han.sam.block.BlockSpeaker;
import jp.me1han.sam.client.SwitchMeshRenderer;
import jp.me1han.sam.network.*;
import jp.me1han.sam.render.TileEntitySpeaker;
import jp.me1han.sam.speakermodel.*;
import jp.me1han.sam.switchmodel.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

/** Headless checks for Speaker pack metadata, portable configuration and placement. */
public final class SpeakerModelTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final String VALID = "{\"name\":\"platform\",\"displayName\":\"Platform Speaker\","
        + "\"tags\":\"speaker platform station\",\"model\":{\"modelFile\":\"platform.mqo\","
        + "\"scale\":0.02,\"offset\":[1,2,3],\"textures\":[[\"mat1\",\"platform.png\"]]},"
        + "\"bounds\":[0.2,-0.1,0.3,0.8,1.2,0.7]}";

    public static void main(String[] args) throws Exception {
        Method mapping = TileEntity.class.getDeclaredMethod("addMapping", Class.class, String.class);
        mapping.setAccessible(true); mapping.invoke(null, TestSpeaker.class, "speaker-model-test");
        definition(); texturePolicy(); registry(); itemSelection(); tileAndPacket(); placement(); cache();
        SwitchModelRegistry.reset();
        check(SwitchModelRegistry.list().size() == 2, "Speaker registry work does not alter switch models");
        System.out.println("Speaker models: " + checks + " checks passed");
    }
    private static void definition() {
        SpeakerModelDefinition model = SpeakerModelDefinition.parse(new StringReader(VALID),
            "stationannouncemod:speakers/platform.json");
        check(model.name.equals("platform") && model.displayName.equals("Platform Speaker")
            && model.tags.contains("station"), "Identity metadata");
        check(model.modelFile.equals("stationannouncemod:speakers/platform.mqo"), "Relative MQO path");
        check(model.textures.get("mat1").equals("stationannouncemod:speakers/platform.png"), "Relative texture path");
        check(model.scale == .02 && Arrays.equals(model.modelOffset, new double[]{1,2,3}), "Scale and model offset");
        check(!model.smoothing && !model.doCulling, "RTM shading flags retain false defaults");
        check(Arrays.equals(model.bounds, new double[]{.2,-.1,.3,.8,1.2,.7}), "Bounds");
        check(model.visible("anything", false) && model.visible("anything", true), "Speaker model has one static state");
        for (String json : new String[]{
            VALID.replace("\"platform\"", "\"bad/name\""),
            VALID.replace("platform.mqo", "../platform.mqo"),
            VALID.replace("\"scale\":0.02", "\"scale\":0"),
            VALID.replace("[0.2,-0.1,0.3,0.8,1.2,0.7]", "[1,0,0,0,1,1]"),
            VALID.replace("[0.2,-0.1,0.3,0.8,1.2,0.7]", "[-17,0,0,1,1,1]"),
            VALID.replace("[1,2,3]", "[1,2]")
        }) {
            try { SpeakerModelDefinition.parse(new StringReader(json), "stationannouncemod:speakers/x.json"); throw new AssertionError("Invalid JSON accepted"); }
            catch (RuntimeException expected) { checks++; }
        }
    }
    private static void texturePolicy() {
        SpeakerModelDefinition none = SpeakerModelDefinition.parse(new StringReader(
            "{\"name\":\"none\",\"model\":{\"modelFile\":\"speaker.mqo\"}}"),
            "stationannouncemod:speakers/test.json");
        check(none.textures.isEmpty() && ModelTextureResolver.select(none.textures, "body") == null,
            "Speaker without JSON textures remains valid and uses missing texture");

        String base = "{\"name\":\"speaker_texture\",\"model\":{\"modelFile\":\"speaker.mqo\",\"textures\":[";
        SpeakerModelDefinition exact = SpeakerModelDefinition.parse(new StringReader(base
            + "[\"body\",\"missing_body.png\"],[\"default\",\"default.png\"]]} }"),
            "stationannouncemod:speakers/test.json");
        check(ModelTextureResolver.select(exact.textures, "body").endsWith("/missing_body.png"),
            "Speaker exact JSON resource is selected without an existence check");
        check(ModelTextureResolver.select(exact.textures, "grill").endsWith("/default.png"),
            "Speaker absent exact texture uses JSON default");

        SpeakerModelDefinition invalidExact = SpeakerModelDefinition.parse(new StringReader(base
            + "[\"body\",\"C:\\\\Users\\\\bad.png\"],[\"default\",\"default.png\"]]} }"),
            "stationannouncemod:speakers/test.json");
        check(invalidExact.textures.containsKey("body") && invalidExact.textures.get("body").isEmpty(),
            "Invalid Speaker exact path is nonfatal metadata");
        check(ModelTextureResolver.select(invalidExact.textures, "body") == null,
            "Invalid Speaker exact path uses missing texture without default fallback");
        check(ModelTextureResolver.select(invalidExact.textures, "grill").endsWith("/default.png"),
            "Other Speaker materials retain valid default fallback");

        SpeakerModelDefinition invalidDefault = SpeakerModelDefinition.parse(new StringReader(base
            + "[\"default\",\"../bad.png\"]]} }"), "stationannouncemod:speakers/test.json");
        check(ModelTextureResolver.select(invalidDefault.textures, "body") == null,
            "Invalid Speaker default uses missing texture");
        SpeakerModelDefinition unused = SpeakerModelDefinition.parse(new StringReader(base
            + "[\"old_part\",\"deleted.png\"]]} }"), "stationannouncemod:speakers/test.json");
        check(unused.modelFile.endsWith("speaker.mqo") && unused.textures.containsKey("old_part"),
            "Unused missing Speaker texture does not invalidate geometry metadata");
    }
    private static void registry() throws Exception {
        SpeakerModelRegistry.reset();
        SpeakerModelDefinition sample = SpeakerModelRegistry.get(SpeakerModelRegistry.SAMPLE_MODEL);
        check(sample != null && SpeakerModelRegistry.list().size() == 1,
            "Bundled lighting Speaker sample is registered without becoming the default selection");
        check(sample.displayName.equals("\u30b9\u30d4\u30fc\u30ab\u30fc1") && sample.tags.isEmpty(),
            "Bundled Speaker sample exposes searchable metadata");
        check(sample.scale == .01 && Arrays.equals(sample.modelOffset, new double[]{0,0,0}),
            "Bundled Speaker sample uses Metasequoia centimeters with no origin correction");
        check(sample.smoothing && sample.doCulling,
            "Bundled RTM Speaker sample enables RTM smoothing and back-face culling");
        check(Arrays.equals(sample.bounds, new double[]{.14,.25,.12,.81,1.01,.88}),
            "Bundled Speaker sample bounds cover its MQO geometry");
        try (InputStream model = SpeakerModelTest.class.getResourceAsStream("/assets/stationannouncemod/speakers/sam_speaker1.mqo");
             InputStream texture = SpeakerModelTest.class.getResourceAsStream("/assets/stationannouncemod/speakers/sam_speaker1.png")) {
            check(model != null && texture != null, "Bundled Speaker MQO and PNG resources exist");
            MqoMesh mesh = MqoMesh.read(new InputStreamReader(model, StandardCharsets.UTF_8));
            sample.validateParts(mesh.parts.keySet());
            check(mesh.parts.size() == 2 && mesh.materials.size() == 1,
                "Bundled RTM lighting MQO is readable without modifying its structure");
            check(mesh.smoothingAngles.get("obj1") == 59.5 && mesh.smoothingAngles.get("obj2") == 59.5,
                "Bundled Speaker retains each MQO Object facet angle");
            boolean positive = false, negative = false;
            for (MqoMesh.Triangle triangle : mesh.parts.get("obj2")) for (double[] vertex : triangle.vertices) {
                if (Math.abs(vertex[0] - 30) < 1e-6) positive = true;
                if (Math.abs(vertex[0] + 30) < 1e-6) negative = true;
            }
            check(positive && negative, "Bundled MQO mirror is expanded across its X axis");
        }
        check(sample.textures.get("mat1").equals("stationannouncemod:speakers/sam_speaker1.png"),
            "Bundled sample uses the SAM JSON texture mapping");
        Path zipPath = zip(new String[][]{{"assets/stationannouncemod/speakers/platform.json", VALID}});
        try (ZipFile zip = new ZipFile(zipPath.toFile())) { SpeakerModelRegistry.loadPack(zip); }
        check(SpeakerModelRegistry.get("platform") != null && SpeakerModelRegistry.list().size() == 2, "Pack model loads beside bundled sample");
        Files.delete(zipPath);
        Path duplicate = zip(new String[][]{
            {"assets/stationannouncemod/speakers/a.json", VALID},
            {"assets/stationannouncemod/speakers/b.json", VALID.replace("platform.mqo", "other.mqo")}
        });
        SpeakerModelRegistry.reset();
        try (ZipFile zip = new ZipFile(duplicate.toFile())) { SpeakerModelRegistry.loadPack(zip); }
        check(SpeakerModelRegistry.list().size() == 2 && SpeakerModelRegistry.get("platform").modelFile.endsWith("platform.mqo"),
            "Duplicate name is rejected without replacing the first definition");
        Files.delete(duplicate);
    }
    private static void itemSelection() {
        ItemStack stack = new ItemStack(new net.minecraft.item.Item());
        NBTTagCompound root = new NBTTagCompound(); root.setString("unrelated", "preserved");
        NBTTagCompound block = new NBTTagCompound(); block.setString("linkKey", "platform-1");
        root.setTag("BlockEntityTag", block); stack.setTagCompound(root);
        check(jp.me1han.sam.item.ItemSpeaker.selectedModel(stack).isEmpty(),
            "Unconfigured Speaker item keeps No Model as its default");
        check(jp.me1han.sam.item.ItemSpeaker.selectModel(stack, SpeakerModelRegistry.SAMPLE_MODEL)
            && jp.me1han.sam.item.ItemSpeaker.selectedModel(stack).equals(SpeakerModelRegistry.SAMPLE_MODEL),
            "Speaker item picker stores an installed model");
        check(root.getString("unrelated").equals("preserved")
            && block.getString("linkKey").equals("platform-1"),
            "Speaker item picker preserves other portable settings");
        check(!jp.me1han.sam.item.ItemSpeaker.selectModel(stack, "missing-model")
            && jp.me1han.sam.item.ItemSpeaker.selectedModel(stack).equals(SpeakerModelRegistry.SAMPLE_MODEL),
            "Speaker item picker rejects an unavailable model");
        check(jp.me1han.sam.item.ItemSpeaker.selectModel(stack, "")
            && jp.me1han.sam.item.ItemSpeaker.selectedModel(stack).isEmpty(),
            "Speaker item picker restores the explicit No Model selection");

        ByteBuf buffer = Unpooled.buffer();
        try {
            new PacketSpeakerItemConfig(4, SpeakerModelRegistry.SAMPLE_MODEL).toBytes(buffer);
            PacketSpeakerItemConfig decoded = new PacketSpeakerItemConfig(); decoded.fromBytes(buffer);
            check(decoded.slot == 4 && decoded.modelName.equals(SpeakerModelRegistry.SAMPLE_MODEL),
                "Speaker item picker packet round trip");
            check(new PacketSpeakerItemConfig(8, "").isValidPayload(),
                "Speaker item picker packet permits No Model");
            check(!new PacketSpeakerItemConfig(9, SpeakerModelRegistry.SAMPLE_MODEL).isValidPayload(),
                "Speaker item picker rejects an invalid hotbar slot");
        } finally { buffer.release(); }
    }
    private static Path zip(String[][] entries) throws Exception {
        Path path = Files.createTempFile(Paths.get("build"), "speaker-model-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String[] entry : entries) {
                out.putNextEntry(new ZipEntry(entry[0])); out.write(entry[1].getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
        return path;
    }
    private static void tileAndPacket() {
        NBTTagCompound old = new NBTTagCompound(); old.setString("linkKey", "A"); old.setInteger("range", 24); old.setFloat("volume", .5F);
        TestSpeaker legacy = new TestSpeaker(); legacy.readFromNBT(old);
        check(legacy.modelName.isEmpty() && legacy.getRotationYaw() == 0
            && legacy.getOffsetX() == 0 && legacy.getOffsetY() == 0 && legacy.getOffsetZ() == 0, "Old NBT defaults to No Model");
        check(legacy.linkKey.equals("A") && legacy.range == 24 && legacy.volume == .5F, "Old audio settings remain intact");
        legacy.applyConfig("A", 24, .5F, "missing", 45, 1, -2, 3);
        NBTTagCompound saved = new NBTTagCompound(); legacy.writeToNBT(saved);
        TestSpeaker restored = new TestSpeaker(); restored.readFromNBT(saved);
        check(restored.modelName.equals("missing") && restored.getRotationYaw() == 45, "Model and yaw NBT round trip");
        check(restored.getOffsetX() == 1 && restored.getOffsetY() == -2 && restored.getOffsetZ() == 3, "Offsets NBT round trip");
        check(restored.getRenderBoundingBox().minX == restored.xCoord, "Missing model uses block bounds without fallback");
        restored.modelName = "";
        check(restored.getModelDefinition() == null, "No Model performs no model resolution");
        NBTTagCompound unsafe = (NBTTagCompound)saved.copy();
        unsafe.setFloat("offsetX", Float.NaN); unsafe.setFloat("offsetY", Float.POSITIVE_INFINITY); unsafe.setFloat("offsetZ", 17);
        restored.readFromNBT(unsafe);
        check(restored.getOffsetX() == 0 && restored.getOffsetY() == 0 && restored.getOffsetZ() == 0, "Unsafe NBT offsets sanitize");
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 16.000002F, -16.000002F})
            check(!TileEntitySpeaker.validOffset(value), "Unsafe offset rejected");

        PacketSpeakerConfig packet = new PacketSpeakerConfig(1,2,3,"A",32,.75F,"platform",361,1,2,3);
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf); PacketSpeakerConfig decoded = new PacketSpeakerConfig(); decoded.fromBytes(buf);
        check(decoded.modelName.equals("platform") && decoded.rotationYaw == 361
            && decoded.offsetX == 1 && decoded.offsetY == 2 && decoded.offsetZ == 3 && buf.readableBytes() == 0,
            "Speaker model packet round trip");
        PacketSpeakerConfig noModel = new PacketSpeakerConfig(1,2,3,"A",16,1,"",0,0,0,0);
        check(noModel.isValidPayload(), "Empty model name is valid");
        check(!new PacketSpeakerConfig(1,2,3,"A",16,1,"x",0,Float.NaN,0,0).isValidPayload(), "Packet rejects NaN offset");
        check(!new PacketSpeakerConfig(1,2,3,"A",16,1,
            String.join("", Collections.nCopies(PacketLimits.MODEL + 1, "m")),0,0,0,0).isValidPayload(), "Packet rejects long model name");
        buf.release();

        SpeakerModelRegistry.reset();
        try {
            Field models = SpeakerModelRegistry.class.getDeclaredField("MODELS"); models.setAccessible(true);
            ((Map<String, SpeakerModelDefinition>)models.get(null)).put("platform",
                SpeakerModelDefinition.parse(new StringReader(VALID), "stationannouncemod:speakers/platform.json"));
        } catch (Exception e) { throw new AssertionError(e); }
        FixtureWorld world = new FixtureWorld(); TestSpeaker tile = new TestSpeaker(); world.add(tile);
        SpeakerRegistry.Entry entry = SpeakerRegistry.at(world, SpeakerRegistry.position(0,0,0));
        int dirty = tile.dirty, updates = world.updates;
        check(tile.applyConfig("",16,1,"platform",90,1,0,0), "Visual config applies");
        check(tile.getModelDefinition() == SpeakerModelRegistry.get("platform"),
            "Valid model resolves from the Speaker-only registry");
        check(SpeakerRegistry.at(world, SpeakerRegistry.position(0,0,0)) == entry, "Visual-only config does not rebuild routing");
        check(!tile.applyConfig("",16,1,"platform",90,1,0,0)
            && tile.dirty == dirty + 1 && world.updates == updates + 1, "Identical full config emits no update");
        tile.applyConfig("B",32,.5F,"platform",90,1,0,0);
        check(SpeakerRegistry.at(world, SpeakerRegistry.position(0,0,0)) != entry, "Audio config rebuilds routing");
        AxisAlignedBB bounds = tile.getRenderBoundingBox();
        check(bounds.minX > .7 && bounds.maxX < 1.8 && bounds.minZ > .1 && bounds.maxZ < .9, "Render bounds include yaw and tile offset");
        TestSpeaker client = new TestSpeaker();
        client.onDataPacket(null, (S35PacketUpdateTileEntity)tile.getDescriptionPacket());
        check(client.modelName.equals("platform") && client.getRotationYaw() == 90
            && client.getOffsetX() == 1, "Description packet synchronizes visual configuration");
        jp.me1han.sam.client.ClientSpeakerRegistry.clear();
        SpeakerRegistry.clear(world);
    }
    private static void placement() throws Exception {
        FixtureWorld world = new FixtureWorld(); TestSpeaker tile = new TestSpeaker(); world.add(tile);
        NBTTagCompound settings = new NBTTagCompound(); settings.setString("modelName", "platform");
        settings.setString("linkKey", "copied"); settings.setInteger("range", 30); settings.setFloat("volume", .4F);
        settings.setFloat("RotationYaw", 12); settings.setFloat("offsetX", 1); settings.setFloat("offsetY", 2); settings.setFloat("offsetZ", 3);
        ItemStack stack = new ItemStack(net.minecraft.init.Items.stick); NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("BlockEntityTag", settings); stack.setTagCompound(tag);
        Constructor<?> ctor = sun.reflect.ReflectionFactory.getReflectionFactory()
            .newConstructorForSerialization(TestPlayer.class, Object.class.getDeclaredConstructor());
        TestPlayer player = (TestPlayer)ctor.newInstance(); player.rotationYaw = -32.2F;
        new BlockSpeaker().onBlockPlacedBy(world,0,0,0,player,stack);
        check(tile.modelName.equals("platform") && tile.getOffsetX() == 1 && tile.getOffsetY() == 2 && tile.getOffsetZ() == 3,
            "Copied model and offsets survive placement");
        check(tile.linkKey.equals("copied") && tile.range == 30 && tile.volume == .4F, "Copied audio settings survive placement");
        check(tile.getRotationYaw() == SwitchYaw.placement(player.rotationYaw, false), "Normal placement overrides copied yaw");
        player.sneaking = true; player.rotationYaw = -32.6F;
        new BlockSpeaker().onBlockPlacedBy(world,0,0,0,player,new ItemStack(net.minecraft.init.Items.stick));
        check(tile.getRotationYaw() == SwitchYaw.placement(player.rotationYaw, true), "Sneak placement uses one-degree orientation");
        SpeakerRegistry.clear(world);
    }
    private static void cache() throws Exception {
        Field meshes = SwitchMeshRenderer.class.getDeclaredField("meshes"); meshes.setAccessible(true);
        Field failed = SwitchMeshRenderer.class.getDeclaredField("failed"); failed.setAccessible(true);
        Field failedTextures = SwitchMeshRenderer.class.getDeclaredField("failedTextures"); failedTextures.setAccessible(true);
        Field textureLocations = SwitchMeshRenderer.class.getDeclaredField("textureLocations"); textureLocations.setAccessible(true);
        ((Map)meshes.get(SwitchMeshRenderer.INSTANCE)).put("speaker:test", new MqoMesh());
        ((Map)textureLocations.get(SwitchMeshRenderer.INSTANCE)).put("stationannouncemod:speakers/test.png",
            new net.minecraft.util.ResourceLocation("stationannouncemod:speakers/test.png"));
        SpeakerModelDefinition broken = SpeakerModelDefinition.parse(new StringReader(
            VALID.replace("\"platform\"", "\"broken\"")), "stationannouncemod:speakers/broken.json");
        ((Set)failed.get(SwitchMeshRenderer.INSTANCE)).add(broken.getClass().getName() + ":broken");
        check(SwitchMeshRenderer.INSTANCE.mesh(broken) == null, "Failed Speaker resource is not retried each frame");
        ((Set)failedTextures.get(SwitchMeshRenderer.INSTANCE)).add("stationannouncemod:speakers/missing.png");
        check(((Map)meshes.get(SwitchMeshRenderer.INSTANCE)).size() == 1
            && ((Set)failed.get(SwitchMeshRenderer.INSTANCE)).size() == 1,
            "Texture failure neither discards mesh cache nor enters model failed cache");
        try { MqoMesh.read(new StringReader("not an MQO")); throw new AssertionError("Broken MQO accepted"); }
        catch (RuntimeException expected) { checks++; }
        catch (IOException expected) { checks++; }
        SwitchMeshRenderer.INSTANCE.onResourceManagerReload(null);
        check(((Map)meshes.get(SwitchMeshRenderer.INSTANCE)).isEmpty()
            && ((Set)failed.get(SwitchMeshRenderer.INSTANCE)).isEmpty()
            && ((Set)failedTextures.get(SwitchMeshRenderer.INSTANCE)).isEmpty()
            && ((Map)textureLocations.get(SwitchMeshRenderer.INSTANCE)).isEmpty(),
            "Resource reload clears mesh, model-failed and texture resolution caches");
    }
    private static class TestSpeaker extends TileEntitySpeaker { int dirty; @Override public void markDirty() { dirty++; } }
    private static class TestPlayer extends EntityPlayer {
        boolean sneaking;
        TestPlayer() { super(null, new GameProfile(UUID.randomUUID(), "speaker")); }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) {}
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return true; }
        @Override public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() { return new net.minecraft.util.ChunkCoordinates(); }
    }
    private static class FixtureWorld extends World {
        TileEntity tile; int updates;
        FixtureWorld() { super(new SaveHandlerMP(), "speaker-model", new WorldProviderSurface(),
            new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT), new Profiler()); }
        void add(TileEntity tile) { this.tile=tile; tile.setWorldObj(this); tile.xCoord=tile.yCoord=tile.zCoord=0; tile.validate(); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public TileEntity getTileEntity(int x,int y,int z) { return tile; }
        @Override public boolean blockExists(int x,int y,int z) { return true; }
        @Override public void markBlockForUpdate(int x,int y,int z) { updates++; }
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile) {}
    }
}
