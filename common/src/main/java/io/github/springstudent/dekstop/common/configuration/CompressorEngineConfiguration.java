package io.github.springstudent.dekstop.common.configuration;


import io.github.springstudent.dekstop.common.bean.CompressionMethod;
import io.github.springstudent.dekstop.common.bean.Constants;

public class CompressorEngineConfiguration extends Configuration {
    private static final String PREF_VERSION = "compression.version";

    private static final String PREF_METHOD = "compression.method";

    private static final String PREF_USE_CACHE = "compression.useCache";

    private static final String PREF_CACHE_MAX_SIZE = "compression.cacheMaxSize";

    private static final String PREF_CACHE_PURGE_SIZE = "compression.cachePurgeSize";

    private final CompressionMethod method;

    private final boolean useCache;

    private final int maxSize;

    private final int purgeSize;

    /**
     * Default : takes its values from the current preferences.
     */
    public CompressorEngineConfiguration() {
        final Preferences prefs = Preferences.getPreferences();
        this.method = prefs.getEnumPreference(PREF_METHOD, CompressionMethod.ZSTD, CompressionMethod.values());
        this.useCache = prefs.getBooleanPreference(PREF_USE_CACHE, true);
        this.maxSize = prefs.getIntPreference(PREF_CACHE_MAX_SIZE, Constants.DEFAULT_MAX_SIZE);
        this.purgeSize = prefs.getIntPreference(PREF_CACHE_PURGE_SIZE, Constants.DEFAULT_PURGE_SIZE);
    }

    public CompressorEngineConfiguration(CompressionMethod method, boolean useCache, int maxSize, int purgeSize) {
        this.method = method;
        this.useCache = useCache;
        this.maxSize = maxSize;
        this.purgeSize = purgeSize;
    }

    public CompressionMethod getMethod() {
        return method;
    }

    public boolean useCache() {
        return useCache;
    }

    public int getCacheMaxSize() {
        return maxSize;
    }

    public int getCachePurgeSize() {
        return purgeSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CompressorEngineConfiguration that = (CompressorEngineConfiguration) o;
        return maxSize == that.getCacheMaxSize() && purgeSize == that.getCachePurgeSize() && useCache == that.useCache() && method == that.getMethod();
    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + (useCache ? 1 : 0);
        result = 31 * result + maxSize;
        result = 31 * result + purgeSize;
        return result;
    }

    /**
     * @param clear allows for clearing properties from previous version
     */
    @Override
    protected void persist(boolean clear) {
        final Preferences.Props props = new Preferences.Props();
        props.set(PREF_VERSION, String.valueOf(1));
        props.set(PREF_METHOD, String.valueOf(method.ordinal()));
        props.set(PREF_USE_CACHE, String.valueOf(useCache));
        props.set(PREF_CACHE_MAX_SIZE, String.valueOf(maxSize));
        props.set(PREF_CACHE_PURGE_SIZE, String.valueOf(purgeSize));
        Preferences.getPreferences().update(props); // atomic (!)
    }

    @Override
    public String toString() {
        return "[method:" + method + "][useCache:" + useCache + "][max:" + maxSize + "][purge:" + purgeSize + "]";
    }
}
