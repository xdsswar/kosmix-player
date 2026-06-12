package xss.it.kosmix.app.ui.provider;

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.windows.Window;

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
 * Convenience base class for providers: an {@link AnchorPane} that
 * holds its hosting {@link Skeleton} and delegates context access to
 * it. Concrete views build their widget tree in the constructor.
 */
public abstract class AbstractProvider extends AnchorPane implements Provider {
    /**
     * The skeleton hosting this provider.
     */
    private final Skeleton<?> skeleton;

    /**
     * Creates the provider bound to its hosting skeleton.
     *
     * @param skeleton the skeleton this provider belongs to
     */
    protected AbstractProvider(Skeleton<?> skeleton) {
        super();
        this.skeleton = skeleton;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Context context() {
        return skeleton.context();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public final <S extends Window> Skeleton<S> skeleton() {
        return (Skeleton<S>) skeleton;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Node asNode() {
        return this;
    }

    /**
     * Anchors a child node to all four edges of this provider.
     *
     * @param node   the child node to anchor
     * @param top    top inset
     * @param right  right inset
     * @param bottom bottom inset
     * @param left   left inset
     */
    protected static void anchor(Node node, double top, double right, double bottom, double left) {
        AnchorPane.setTopAnchor(node, top);
        AnchorPane.setRightAnchor(node, right);
        AnchorPane.setBottomAnchor(node, bottom);
        AnchorPane.setLeftAnchor(node, left);
    }
}
