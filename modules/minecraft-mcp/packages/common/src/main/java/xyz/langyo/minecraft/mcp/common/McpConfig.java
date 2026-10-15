package xyz.langyo.minecraft.mcp.common;

public final class McpConfig {
    private McpConfig() {}

    private static final String MOD_ID = "mcpmod";
    public static final int PORT_START = 9876;
    public static final int PORT_END = 9000;

    public static int getServerPort() {
        return getConfiguredPort();
    }

    public static int getConfiguredPort() {
        String p = System.getProperty("mcp.port");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        p = System.getenv("MC_MCP_PORT");
        if (p != null && !p.isEmpty()) {
            try { return Integer.parseInt(p); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public static String getModVersion() {
        String v = getModVersionFromModList("net.neoforged.fml.ModList");
        if (v != null) return v;
        v = getModVersionFromModList("net.minecraftforge.fml.ModList");
        if (v != null) return v;
        v = getFabricModVersion();
        if (v != null) return v;
        v = System.getProperty("mcp.mod.version");
        if (v != null && !v.isEmpty()) return v;
        Package modPackage = McpConfig.class.getPackage();
        if (modPackage != null) {
            v = modPackage.getImplementationVersion();
            if (v != null && !v.isEmpty()) return v;
        }
        return "unknown";
    }

    private static String getModVersionFromModList(String className) {
        try {
            Class<?> modListClass = Class.forName(className, false, McpConfig.class.getClassLoader());
            Object modList = modListClass.getMethod("get").invoke(null);
            Object optional = modListClass.getMethod("getModContainerById", String.class).invoke(modList, MOD_ID);
            Object container = ((java.util.Optional<?>) optional).orElse(null);
            if (container == null) return null;
            Object modInfo = container.getClass().getMethod("getModInfo").invoke(container);
            Object version = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
            return version == null ? null : version.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getFabricModVersion() {
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false, McpConfig.class.getClassLoader());
            Object fabricLoader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            Object optional = fabricLoaderClass.getMethod("getModContainer", String.class).invoke(fabricLoader, MOD_ID);
            Object container = ((java.util.Optional<?>) optional).orElse(null);
            if (container == null) return null;
            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            Object friendlyVersion = version.getClass().getMethod("getFriendlyString").invoke(version);
            return friendlyVersion == null ? null : friendlyVersion.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getModLoader() {
        String l = System.getProperty("mcp.mod.loader");
        if (l != null && !l.isEmpty()) return l;
        if (classExists("net.neoforged.fml.ModList")) return "neoforge";
        if (classExists("net.minecraftforge.fml.ModList")) return "forge";
        if (classExists("net.fabricmc.loader.api.FabricLoader")) return "fabric";
        return "unknown";
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, McpConfig.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static String getForgeVersion() {
        String f = System.getProperty("mcp.mod.forge.version");
        if (f != null && !f.isEmpty()) return f;
        return null;
    }
}
