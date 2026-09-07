package jp.me1han.sam.client;

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
    public InputStream getInputStream(ResourceLocation loc) throws IOException {
        ZipFile zip = new ZipFile(zipFile);
        ZipEntry entry = zip.getEntry("assets/" + loc.getResourceDomain() + "/" + loc.getResourcePath());
        if (entry == null) {
            zip.close();
            throw new FileNotFoundException(loc.getResourcePath());
        }
        return new FilterInputStream(zip.getInputStream(entry)) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { zip.close(); }
            }
        };
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
        Set<String> domains = new java.util.HashSet<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String[] segments = entries.nextElement().getName().split("/", 3);
                if (segments.length == 3 && segments[0].equals("assets")) domains.add(segments[1]);
            }
        } catch (IOException ignored) { }
        return domains;
    }

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
