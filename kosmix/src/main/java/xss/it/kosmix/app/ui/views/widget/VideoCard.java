package xss.it.kosmix.app.ui.views.widget;

import javafx.animation.ScaleTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import xss.it.kosmix.app.Context;
import xss.it.kosmix.helper.utils.Strings;
import xss.it.model.VideoInfo;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.app.ui.views.widget package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * One result card of the home grid, recycled by the lazy list. Follows
 * the nfx-listview demo pattern: the cell-filling root is an
 * {@link AnchorPane} and the actual card body is an inner container
 * anchored with fixed insets — those insets are what create the gutter
 * between grid cells — with a subtle hover scale on the body. Content:
 * 16:9 thumbnail (placeholder while loading) with duration badge and
 * animated storyboard hover preview, then title, channel and the
 * "views • published" line. The node tree is built once;
 * {@link #update(VideoInfo)} only mutates content.
 */
public final class VideoCard extends AnchorPane {
    /**
     * Inner card body, inset from the cell bounds (the visual gutter)
     * and the target of the hover scale animation.
     */
    private final VBox cardBox;

    /**
     * Static thumbnail surface (async, rounded, with placeholder).
     */
    private final AsyncThumb thumb;


    /**
     * Duration badge at the bottom-right of the thumbnail.
     */
    private final Label duration;

    /**
     * Video title (max two lines).
     */
    private final Label title;

    /**
     * Channel name line.
     */
    private final Label channel;

    /**
     * "views • published" metadata line.
     */
    private final Label meta;

    /**
     * The thumbnail stack (thumb + preview + badge) whose height keeps
     * the 16:9 ratio of the current card width.
     */
    private final StackPane thumbStack;

    /**
     * The item currently displayed by this card.
     */
    private VideoItem item;

    /**
     * Builds the card's widget tree.
     *
     * @param context the application context (for the hover preview)
     */
    public VideoCard(Context context) {
        super();
        getStyleClass().add("video-card");

        /*
         * Zero minimum width: the lazy grid sizes each cell to the
         * current column width, but a content-derived minimum (a long
         * title word, the thumbnail) would stop the card shrinking and
         * make cells overlap horizontally while the window is resized
         * smaller. Forcing minWidth 0 lets the cell follow the column
         * exactly at every width.
         */
        setMinWidth(0);

        /*
         * Hard clip to the cell bounds: the lazy grid does not clip its
         * cells, so an overflowing card would paint over the next row.
         */
        final Rectangle cellClip = new Rectangle();
        cellClip.widthProperty().bind(widthProperty());
        cellClip.heightProperty().bind(heightProperty());
        setClip(cellClip);

        thumb = new AsyncThumb();

        duration = new Label();
        duration.getStyleClass().add("duration-badge");
        StackPane.setAlignment(duration, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(duration, new Insets(0, 8, 8, 0));

        thumbStack = new StackPane(thumb, duration);
        thumbStack.getStyleClass().add("thumb-stack");
        /*
         * Keep the surface at 16:9 for whatever width the card body
         * currently has, capped at 200 so the whole card (thumbnail +
         * text block) always fits inside the fixed 300px grid cell —
         * no overlap into the next row even at very wide columns.
         */
        thumbStack.prefHeightProperty().bind(Bindings.min(
                thumbStack.widthProperty().multiply(9.0 / 16.0), 200));
        thumbStack.minHeightProperty().bind(thumbStack.prefHeightProperty());
        thumbStack.maxHeightProperty().bind(thumbStack.prefHeightProperty());

        title = new Label();
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMaxHeight(44);
        /*
         * A long unbreakable word in a title must not force the card
         * (and thus the cell) wider than its column.
         */
        title.setMinWidth(0);

        channel = new Label();
        channel.getStyleClass().add("card-channel");

        meta = new Label();
        meta.getStyleClass().add("card-meta");

        final VBox text = new VBox(2, title, channel, meta);
        text.setPadding(new Insets(2, 4, 0, 4));

        /*
         * The inner body is anchored left/right/top (NOT bottom) inside
         * the cell-filling root, so it stays at its natural content
         * height and is never stretched to the full cell — combined
         * with the hard clip above, content can never bleed into the
         * neighboring cell when the grid re-flows on resize. The fixed
         * insets are the visible gutter between edge-to-edge cells.
         */
        cardBox = new VBox(10, thumbStack, text);
        cardBox.getStyleClass().add("card-box");
        cardBox.setMinWidth(0);
        thumbStack.setMinWidth(0);
        text.setMinWidth(0);
        AnchorPane.setTopAnchor(cardBox, 6d);
        AnchorPane.setRightAnchor(cardBox, 14d);
        AnchorPane.setLeftAnchor(cardBox, 14d);
        getChildren().add(cardBox);

        /*
         * Hover wiring: a subtle grow on the body (demo pattern). The
         * animated storyboard hover-preview was removed — YouTube only
         * serves those frames as WebP, which skia-fx can't decode.
         */
        cardBox.setOnMouseEntered(e -> {
            final ScaleTransition st = new ScaleTransition(Duration.millis(100), cardBox);
            st.setToX(1.01);
            st.setToY(1.01);
            st.play();
        });
        cardBox.setOnMouseExited(e -> {
            final ScaleTransition st = new ScaleTransition(Duration.millis(100), cardBox);
            st.setToX(1);
            st.setToY(1);
            st.play();
        });
    }

    /**
     * Rebinds the card to another item (cell recycling). The thumbnail
     * is served from the item's cached image when present, so a
     * recycled cell never re-downloads a thumbnail it already loaded.
     *
     * @param item the item to display, may be {@code null} for an
     *             empty recycled cell
     */
    public void update(VideoItem item) {
        this.item = item;
        if (item == null) {
            thumb.setItem(null);
            title.setText("");
            channel.setText("");
            meta.setText("");
            duration.setVisible(false);
            return;
        }
        final VideoInfo info = item.info();
        thumb.setItem(item);
        title.setText(info.title());
        channel.setText(info.channelName() == null ? "" : info.channelName());

        final String views = Strings.compactCount(info.viewCount());
        final String published = info.publishedText() == null ? "" : info.publishedText();
        if (!views.isEmpty() && !published.isEmpty()) {
            meta.setText(views + " views • " + published);
        } else if (!views.isEmpty()) {
            meta.setText(views + " views");
        } else {
            meta.setText(published);
        }

        final String dur = Strings.duration(info.durationSeconds());
        duration.setText(dur);
        duration.setVisible(!dur.isEmpty());
    }

    /**
     * Returns the video currently displayed by this card.
     *
     * @return the bound video, or {@code null}
     */
    public VideoInfo video() {
        return item == null ? null : item.info();
    }

    /**
     * Builds a plain-JPEG thumbnail URL from the video id.
     * <p>
     * The listing payloads carry {@code hq720.jpg?sqp=...} URLs whose
     * bytes are actually <b>WebP</b> (the {@code sqp} parameter marks a
     * pre-encoded variant) — undecodable by the skia-fx Image pipeline.
     * The parameterless {@code i.ytimg.com/vi/<id>/<name>.jpg} variants
     * are served as real JPEG.
     *
     * @param id   the video id
     * @param name the variant name ({@code hq720}, {@code hqdefault}, ...)
     * @return the JPEG thumbnail URL
     */
    public static String jpegThumb(String id, String name) {
        return "https://i.ytimg.com/vi/" + id + "/" + name + ".jpg";
    }
}
