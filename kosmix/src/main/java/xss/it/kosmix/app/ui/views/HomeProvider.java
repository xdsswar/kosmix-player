package xss.it.kosmix.app.ui.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.app.K;
import xss.it.kosmix.app.ui.Skeleton;
import xss.it.kosmix.app.ui.provider.AbstractProvider;
import xss.it.kosmix.app.ui.views.pages.PlayerPage;
import xss.it.kosmix.app.ui.views.widget.KosmixDualLoader;
import xss.it.kosmix.app.ui.views.widget.VideoCard;
import xss.it.kosmix.app.ui.views.widget.VideoItem;
import xss.it.nfx.listview.control.NfxCell;
import xss.it.nfx.listview.control.NfxListView;
import xss.it.model.VideoInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.views package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * The home view: a lazy, virtualized grid of video cards backed by
 * {@link NfxListView}. Search results stream in page by page —
 * scrolling near the bottom fetches the next continuation token
 * (infinite scroll) and clicking a card opens the player. The instance
 * survives navigation so results and scroll position are preserved.
 */
public final class HomeProvider extends AbstractProvider {
    /**
     * Scroll fraction past which the next page is requested.
     */
    private static final double LOAD_MORE_AT = 0.82;

    /**
     * Maximum cards per row.
     */
    private static final int MAX_COLS = 6;

    /**
     * Minimum column width before the grid drops a column (px).
     */
    private static final double MIN_COL_WIDTH = 330;

    /**
     * Grid left inset (px); kept in sync with {@code setLeftGap}.
     */
    private static final double LEFT_GAP = 4;

    /**
     * Grid right inset (px); kept in sync with {@code setRightGap}.
     */
    private static final double RIGHT_GAP = 16;

    /**
     * Fixed grid row height. Sized comfortably above the tallest card
     * (16:9 thumbnail capped at 200 + the text block ≈ 290) so every
     * row keeps a clear gap below the card and content never bleeds
     * into the next row.
     */
    private static final double CELL_HEIGHT = 348;

    /**
     * The virtualized results grid.
     */
    private final NfxListView<VideoItem> listView;

    /**
     * Backing items of the grid (each wraps a video and caches its
     * decoded thumbnail across cell recycling).
     */
    private final ObservableList<VideoItem> items = FXCollections.observableArrayList();

    /**
     * Ids already present in the grid — continuation pages occasionally
     * repeat entries and duplicates break recycling assumptions.
     */
    private final Set<String> seenIds = new HashSet<>();

    /**
     * Empty / error message shown instead of the grid.
     */
    private final Label message;

    /**
     * Centered placeholder loader shown over the grid while a fresh
     * search is loading.
     */
    private final KosmixDualLoader placeholder;

    /**
     * The query currently shown in the grid.
     */
    private String query;

    /**
     * Continuation token of the next page; {@code null} when exhausted.
     */
    private String nextToken;

    /**
     * Guards against concurrent page loads while one is in flight.
     */
    private boolean loading;

    /**
     * Whether the scrollbar listener has been attached (the scrollbar
     * only exists after the control's skin is created).
     */
    private boolean scrollHooked;

    /**
     * Builds the home grid and starts the initial search.
     *
     * @param skeleton the hosting skeleton
     */
    public HomeProvider(Skeleton<?> skeleton) {
        super(skeleton);
        getStyleClass().add("home");

        listView = new NfxListView<>();
        listView.getStyleClass().add("video-grid");
        /*
         * Structural grid metrics are set in code (USER origin) so no
         * stylesheet — including the control's bundled defaults — can
         * override them: responsive columns, generous initial row
         * height (refined live by recomputeCellHeight()).
         */
        /*
         * Fixed cell height — exactly the nfx-listview demo's approach:
         * a constant row height with the card content anchored + clipped
         * inside the cell, so resizing only re-flows the columns and the
         * rows never overlap and never blink. The height fits a card at
         * the common column width (16:9 thumbnail capped + text block);
         * the card's own clip absorbs the small variance at extreme
         * widths. (A width-driven height recompute was tried and removed
         * — calling setCellHeight per pixel rebuilds every cell and makes
         * the whole grid blink during a drag.)
         */
        listView.setCellHeight(CELL_HEIGHT);
        listView.setMinCellWidthBreakPoint(MIN_COL_WIDTH);
        listView.setMaxCellsPerRow(MAX_COLS);
        listView.setLeftGap(LEFT_GAP);
        listView.setRightGap(RIGHT_GAP);
        listView.setItems(items);
        listView.setCellFactory(lv -> new NfxCell<>(lv) {
            /**
             * The recycled card bound to whatever item this cell
             * currently represents.
             */
            private final VideoCard card = new VideoCard(context());

            /*
             * Build the cell content once; clicking anywhere on the
             * card opens the player for the bound video.
             */
            {
                /*
                 * Zero minimum width on the cell too, so neither the
                 * cell nor its card can resist shrinking with the
                 * column when the window is resized (which would make
                 * adjacent cells overlap).
                 */
                setMinWidth(0);
                setGraphics(card);
                card.setOnMouseClicked(e -> {
                    final VideoInfo video = card.video();
                    if (video != null) {
                        open(video);
                    }
                });
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void update(VideoItem item) {
                super.update(item);
                card.update(item);
            }
        });

        message = new Label();
        message.getStyleClass().add("home-message");
        message.setVisible(false);

        /*
         * Placeholder loader centered over the (empty) grid while a
         * search is in flight — nfx-listview has no built-in placeholder
         * API, so we overlay our own dual-circle loader.
         */
        placeholder = new KosmixDualLoader();
        placeholder.setRadius(11);
        placeholder.setVisible(false);

        final StackPane stack = new StackPane(listView, placeholder, message);
        anchor(stack, 16, 24, 0, 24);
        getChildren().add(stack);

        /*
         * Initial content: the configured home query.
         */
        search(context().settings().get(K.HOME_QUERY, K.DEFAULT_HOME_QUERY));
    }

    /**
     * Runs a fresh search, replacing the grid content.
     *
     * @param query the query to search for
     */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        this.query = query.trim();
        this.nextToken = null;
        this.loading = true;
        message.setVisible(false);
        /*
         * Clear immediately so the placeholder loader shows over an
         * empty grid (not over stale results from the previous query).
         */
        items.clear();
        seenIds.clear();
        showPlaceholder(true);
        skeleton().titleBar().setQuery(this.query);

        context().youtube().search(this.query, null, page -> {
            loading = false;
            showPlaceholder(false);
            append(page.items());
            nextToken = page.nextPageToken();
            listView.scrollTo(0);
            hookScrollBar();
            if (items.isEmpty()) {
                showMessage("No results for \"" + this.query + "\"");
            }
        }, err -> {
            loading = false;
            showPlaceholder(false);
            showMessage("Search failed — " + friendly(err));
        });
    }

    /**
     * Loads the next continuation page when one is available.
     */
    private void loadMore() {
        if (loading || nextToken == null || query == null) {
            return;
        }
        loading = true;
        context().youtube().search(query, nextToken, page -> {
            loading = false;
            append(page.items());
            nextToken = page.nextPageToken();
        }, err -> {
            /*
             * A failed continuation is silent; scrolling again retries.
             */
            loading = false;
        });
    }

    /**
     * Appends new, de-duplicated, normal (non-live, non-premiere)
     * videos to the grid.
     *
     * @param fresh the page items to add
     */
    private void append(List<VideoInfo> fresh) {
        for (VideoInfo info : fresh) {
            if (info.isNormalVideo() && seenIds.add(info.id())) {
                items.add(new VideoItem(info));
            }
        }
    }

    /**
     * Opens the player page for the given video.
     *
     * @param video the video to play
     */
    private void open(VideoInfo video) {
        skeleton().setProvider(new PlayerPage(skeleton(), video));
    }

    /**
     * Attaches the infinite-scroll listener to the grid's vertical
     * scrollbar once the skin exists.
     */
    private void hookScrollBar() {
        if (scrollHooked) {
            return;
        }
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                bar.valueProperty().addListener((obs, o, value) -> {
                    final double range = bar.getMax() - bar.getMin();
                    if (range > 0 && value.doubleValue() >= bar.getMin() + range * LOAD_MORE_AT) {
                        loadMore();
                    }
                });
                scrollHooked = true;
                break;
            }
        }
    }

    /**
     * Shows or hides the centered placeholder loader.
     *
     * @param show {@code true} to start and show the loader
     */
    private void showPlaceholder(boolean show) {
        if (show) {
            placeholder.start();
            placeholder.setVisible(true);
        } else {
            placeholder.stop();
            placeholder.setVisible(false);
        }
    }

    /**
     * Shows the centered message label (empty/error states).
     *
     * @param text the message text
     */
    private void showMessage(String text) {
        message.setText(text);
        message.setVisible(true);
    }

    /**
     * Maps an exception to a short, human-friendly line.
     *
     * @param t the failure
     * @return a display-safe message
     */
    private static String friendly(Throwable t) {
        final String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }
}
