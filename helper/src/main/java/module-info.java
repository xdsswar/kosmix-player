/**
 * Kosmix helper module.
 * <p>
 * Dependency-free infrastructure shared by the application: platform
 * utilities, the daemon thread pool and the persistent settings stack.
 * Only {@code java.base} is required.
 */
module kosmix.helper {
    requires java.net.http;


    exports xss.it.kosmix.helper.platform;
    exports xss.it.kosmix.helper.settings;
    exports xss.it.kosmix.helper.ec;
    exports xss.it.kosmix.helper.utils;
}
