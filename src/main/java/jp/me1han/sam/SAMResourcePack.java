package jp.me1han.sam;

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

public class SAMResourcePack implements IResourcePack {
    private final File zipFile;

    public SAMResourcePack(File zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        // 1. 引数の location が null でないかチェック
        if (location == null || location.getResourcePath() == null || location.getResourcePath().isEmpty()) {
            throw new FileNotFoundException(location == null ? "null" : location.toString());
        }

        // 2. ZipFile を開いてリソースを取得
        ZipFile zip = new ZipFile(zipFile);
        try {
            // "assets/ドメイン/パス" の形式でエントリを探す
            String path = "assets/" + location.getResourceDomain() + "/" + location.getResourcePath();
            ZipEntry entry = zip.getEntry(path);

            if (entry == null) {
                zip.close();
                throw new FileNotFoundException("Resource not found in zip: " + path);
            }

            return zip.getInputStream(entry);
        } catch (IOException e) {
            zip.close();
            throw e;
        }
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
