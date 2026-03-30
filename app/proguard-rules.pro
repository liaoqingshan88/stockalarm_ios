# Keep Apache Commons Logging implementation classes (loaded via reflection)
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**

# Ensure SimpleLog stays intact (we force it via commons-logging.properties)
-keep class org.apache.commons.logging.impl.SimpleLog { *; }

# Apache HttpClient 3.x uses reflection/service lookups internally (cookie specs, SSL, params).
# Keep it to prevent NoClassDefFoundError in minified builds.
-keep class org.apache.commons.httpclient.** { *; }
-dontwarn org.apache.commons.httpclient.**

