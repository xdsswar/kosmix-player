package xss.it.kosmix.app.ui.provider;

import xss.it.kosmix.app.ui.signal.Signal;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.provider package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * A named provider that can receive {@link Signal} messages from other
 * parts of the application without coupling to the concrete page type.
 */
public interface Page extends Provider {
    /**
     * Returns the logical name of this page.
     *
     * @return the page name
     */
    String name();

    /**
     * Sends or applies the given {@link Signal} to this page.
     *
     * @param signal the signal to deliver
     */
    void signal(Signal signal);

    /**
     * Returns the current {@link Signal} associated with this page.
     *
     * @return the last delivered signal
     */
    Signal signal();
}
