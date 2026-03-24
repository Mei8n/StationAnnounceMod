package jp.me1han.sam.client;

import com.google.common.collect.ImmutableSet;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer; // Iを追加
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourcePackSAM implements IResourcePack {
    private final File zipFile;

    public ResourcePackSAM(File zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public InputStream getInputStream(ResourceLocation loc) throws IOException {
        // ZipFileはメソッド内で開き、最後に確実に閉じるか、
        // 頻繁にアクセスされるならクラス変数に持ってclose処理を書く必要があります。
        // ここではエラー回避のためリソース試行文を使います。
        ZipFile zip = new ZipFile(zipFile);
        ZipEntry entry = zip.getEntry("assets/" + loc.getResourceDomain() + "/" + loc.getResourcePath());
        if (entry == null) {
            zip.close();
            throw new FileNotFoundException(loc.getResourcePath());
        }
        return zip.getInputStream(entry);
    }

    @Override
    public boolean resourceExists(ResourceLocation loc) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            return zip.getEntry("assets/" + loc.getResourceDomain() + "/" + loc.getResourcePath()) != null;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Set<String> getResourceDomains() {
        return ImmutableSet.of("sound_rkkdev", "minecraft", "stationannouncemod");
    }

    // 引数の型を IMetadataSerializer に変更
    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String name) throws IOException {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        return null;
    }

    @Override
    public String getPackName() {
        return "SAM External Pack: " + zipFile.getName();
    }
}
