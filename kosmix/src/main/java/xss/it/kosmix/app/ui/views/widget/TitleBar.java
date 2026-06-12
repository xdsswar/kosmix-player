package xss.it.kosmix.app.ui.views.widget;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.SvgImageView;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.util.Duration;
import xss.it.kosmix.app.Context;

import java.util.List;
import java.util.function.Consumer;

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
 * The custom window chrome: brand mark on the left, a YouTube-style
 * pill search box in the center, then the downloads badge, the
 * settings gear and the three caption buttons (minimize, maximize /
 * restore, close). The empty strips flanking the search box act as the
 * draggable caption area; the skeleton registers every region with the
 * Stage hit-region API so the OS keeps native drag, snap and
 * snap-layout behaviors.
 */
public final class TitleBar extends HBox {
    /**
     * Fixed chrome height in logical pixels.
     */
    public static final double HEIGHT = 56;

    /**
     * The application context (window access, services).
     */
    private final Context context;

    /**
     * Left draggable strip (between brand and search).
     */
    private final Region leftDrag;

    /**
     * Right draggable strip (between search and the action cluster).
     */
    private final Region rightDrag;

    /**
     * Brand block (icon + name) — also part of the caption area.
     */
    private final HBox brand;

    /**
     * The central search input.
     */
    private final TextField searchField;

    /**
     * Live suggestions popup shown under the search field.
     */
    private Popup suggestions;

    /**
     * The vertical list of suggestion rows inside the popup.
     */
    private VBox suggestionList;

    /**
     * When {@code true}, suggestions stay hidden even if a pending
     * async response arrives — set on submit (Enter / search button /
     * suggestion pick), cleared the moment the user types again.
     */
    private boolean suppressSuggestions;

    /**
     * History back button, placed right after the brand.
     */
    private final StackPane backBtn;

    /**
     * History forward button, next to the back button.
     */
    private final StackPane forwardBtn;

    /**
     * Chrome-style downloads history button.
     */
    private final DownloadsButton downloadsButton;

    /**
     * Settings (gear) button, placed right before the caption buttons.
     */
    private final StackPane settingsBtn;

    /**
     * Minimize caption button.
     */
    private final StackPane minBtn;

    /**
     * Maximize / restore caption button.
     */
    private final StackPane maxBtn;

    /**
     * Close caption button.
     */
    private final StackPane closeBtn;

    /**
     * Maximize glyph — swapped between maximize and restore icons as
     * the window state changes.
     */
    private final StackPane maxGlyphHolder;

    /**
     * Callback invoked when the user submits a search.
     */
    private Consumer<String> onSearch;

    /**
     * Callback invoked when the settings gear is pressed.
     */
    private Runnable onSettings;

    /**
     * Callback invoked when the brand block is clicked (go home).
     */
    private Runnable onHome;

    /**
     * Builds the chrome widget tree.
     *
     * @param context the application context
     */
    public TitleBar(Context context) {
        super();
        this.context = context;
        getStyleClass().add("title-bar");
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);

        /*
         * Brand: the provided application icon + app name. Clicking it
         * returns to the home grid.
         */
        final ImageView markIcon = new ImageView();
        markIcon.setFitWidth(28);
        markIcon.setFitHeight(28);
        markIcon.setPreserveRatio(true);
        markIcon.setSmooth(true);
        if (!context.icons().isEmpty()) {
            markIcon.setImage(context.icons().get(0));
        } else {
            /*
             * Defensive fallback when the icon resource is missing.
             */
            final var url = TitleBar.class.getResource(
                    "/xss/it/kosmix/assets/images/icon.png");
            if (url != null) {
                markIcon.setImage(new Image(url.toExternalForm(), true));
            }
        }
        final StackPane mark = new StackPane(markIcon);
        mark.getStyleClass().add("brand-mark");
        final Label name = new Label("Kosmix");
        name.getStyleClass().add("brand-name");
        brand = new HBox(8, mark, name);
        brand.getStyleClass().add("brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 12, 0, 16));
        brand.setOnMouseClicked(e -> {
            if (onHome != null) {
                onHome.run();
            }
        });

        /*
         * Browser-style history buttons (wired by the skeleton).
         */
        backBtn = chromeButton("back", 16, "chrome-btn");
        forwardBtn = chromeButton("forward", 16, "chrome-btn");
        backBtn.setDisable(true);
        forwardBtn.setDisable(true);
        Tooltip.install(backBtn, new Tooltip("Back"));
        Tooltip.install(forwardBtn, new Tooltip("Forward"));

        /*
         * Centered pill search: text field with an inline clear button,
         * a magnifier submit button and a live suggestions popup fed by
         * the YouTube suggestion endpoint.
         */
        searchField = new TextField();
        searchField.getStyleClass().add("search-field");
        searchField.setPromptText("Search");
        searchField.setPrefWidth(420);
        searchField.setPrefHeight(38);
        searchField.setOnAction(e -> {
            hideSuggestions();
            submitSearch();
        });

        final StackPane clearBtn = new StackPane(Icons.icon("close", 11, Color.web("#606060")));
        clearBtn.getStyleClass().add("search-clear");
        clearBtn.setVisible(false);
        clearBtn.setOnMouseClicked(e -> {
            searchField.clear();
            hideSuggestions();
            searchField.requestFocus();
        });
        StackPane.setAlignment(clearBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(clearBtn, new Insets(0, 10, 0, 0));

        final StackPane fieldStack = new StackPane(searchField, clearBtn);

        final StackPane searchBtn = new StackPane(Icons.icon("search", 18, Color.web("#0f0f0f")));
        searchBtn.getStyleClass().add("search-btn");
        searchBtn.setPrefSize(64, 38);
        searchBtn.setMinSize(64, 38);
        searchBtn.setMaxSize(64, 38);
        searchBtn.setOnMouseClicked(e -> {
            hideSuggestions();
            submitSearch();
        });

        final HBox searchBox = new HBox(fieldStack, searchBtn);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER);
        searchBox.setMaxHeight(38);

        /*
         * Suggestions: debounce typing, fetch on the executor and show
         * a clickable popup under the field; ESC or focus loss hides it.
         */
        suggestionList = new VBox(2);
        suggestionList.getStyleClass().add("search-suggestions");
        suggestionList.setPadding(new Insets(8));
        /*
         * Popups do not inherit the scene stylesheet — attach it so the
         * suggestion rows are themed.
         */
        suggestionList.getStylesheets().add(context.stylesheet());
        suggestions = new Popup();
        suggestions.setAutoHide(true);
        suggestions.getContent().add(suggestionList);

        final PauseTransition debounce = new PauseTransition(Duration.millis(220));
        debounce.setOnFinished(e -> fetchSuggestions());
        searchField.textProperty().addListener((obs, o, text) -> {
            clearBtn.setVisible(text != null && !text.isEmpty());
            /*
             * Any text change means the user is typing again — re-enable
             * suggestions (a prior submit suppressed them).
             */
            suppressSuggestions = false;
            if (text == null || text.trim().length() < 2) {
                hideSuggestions();
            } else if (searchField.isFocused()) {
                debounce.playFromStart();
            }
        });
        searchField.focusedProperty().addListener((obs, o, focused) -> {
            if (!focused) {
                hideSuggestions();
            }
        });
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hideSuggestions();
            }
        });

        /*
         * Flexible draggable strips on both sides of the search box.
         */
        leftDrag = new Region();
        HBox.setHgrow(leftDrag, Priority.ALWAYS);
        rightDrag = new Region();
        HBox.setHgrow(rightDrag, Priority.ALWAYS);

        /*
         * Action cluster: downloads history (Chrome-style), settings,
         * then caption buttons.
         */
        downloadsButton = new DownloadsButton(context);

        settingsBtn = chromeButton("settings", 18, "chrome-btn");
        settingsBtn.setOnMouseClicked(e -> {
            if (onSettings != null) {
                onSettings.run();
            }
        });
        Tooltip.install(settingsBtn, new Tooltip("Settings"));

        minBtn = captionButton("minimize", "win-btn");
        maxGlyphHolder = new StackPane(Icons.icon("maximize", 16, Color.web("#0f0f0f")));
        maxBtn = new StackPane(maxGlyphHolder);
        maxBtn.getStyleClass().addAll("win-btn");
        maxBtn.setPrefSize(46, HEIGHT);
        maxBtn.setMinSize(46, HEIGHT);
        maxBtn.setMaxSize(46, HEIGHT);
        /*
         * The close glyph is left UNtinted in code so the stylesheet can
         * drive its color: dark normally, white on the red :ht-close
         * hover (a programmatic tint is USER origin and would beat CSS).
         */
        closeBtn = new StackPane(Icons.icon("close", 16, null));
        closeBtn.getStyleClass().addAll("win-btn", "win-btn-close");
        closeBtn.setPrefSize(46, HEIGHT);
        closeBtn.setMinSize(46, HEIGHT);
        closeBtn.setMaxSize(46, HEIGHT);

        /*
         * Caption button actions. The hit-region API routes hover/snap
         * behavior, but the click actions are driven from the FX side.
         */
        minBtn.setOnMouseClicked(e -> context.window().setIconified(true));
        maxBtn.setOnMouseClicked(e ->
                context.window().setMaximized(!context.window().isMaximized()));
        closeBtn.setOnMouseClicked(e -> context.window().close());

        /*
         * The maximize glyph morphs to "restore" whenever the window is
         * maximized — regardless of who triggered it (button, Win+Up,
         * double-click on the caption, Aero snap).
         */
        context.window().maximizedProperty().addListener((obs, was, isMax) ->
                maxGlyphHolder.getChildren().setAll(
                        Icons.icon(isMax ? "restore" : "maximize", 16, Color.web("#0f0f0f"))));

        getChildren().addAll(
                brand, backBtn, forwardBtn, leftDrag, searchBox, rightDrag,
                downloadsButton, settingsBtn, minBtn, maxBtn, closeBtn
        );
    }

    /**
     * Builds a small circular chrome button hosting an SVG glyph.
     *
     * @param iconName the SVG asset name
     * @param size     the glyph size
     * @param styles   style classes for the button surface
     * @return the button surface
     */
    private static StackPane chromeButton(String iconName, double size, String... styles) {
        final SvgImageView glyph = Icons.icon(iconName, size, Color.web("#0f0f0f"));
        final StackPane btn = new StackPane(glyph);
        btn.getStyleClass().addAll(styles);
        btn.setPrefSize(40, 40);
        btn.setMinSize(40, 40);
        btn.setMaxSize(40, 40);
        return btn;
    }

    /**
     * Builds a fixed-size caption button (46 x chrome height) hosting
     * an SVG glyph, suitable for the Stage hit-region API.
     *
     * @param iconName the SVG asset name
     * @param styles   style classes for the button surface
     * @return the button surface
     */
    private static StackPane captionButton(String iconName, String... styles) {
        final StackPane btn = new StackPane(Icons.icon(iconName, 16, Color.web("#0f0f0f")));
        btn.getStyleClass().addAll(styles);
        btn.setPrefSize(46, HEIGHT);
        btn.setMinSize(46, HEIGHT);
        btn.setMaxSize(46, HEIGHT);
        return btn;
    }

    /**
     * Requests suggestions for the current field text and shows them.
     */
    private void fetchSuggestions() {
        final String text = searchField.getText() == null ? "" : searchField.getText().trim();
        if (text.length() < 2) {
            return;
        }
        context.youtube().suggest(text, items -> {
            /*
             * Stale responses (the user kept typing or left the field)
             * are dropped silently.
             */
            if (!searchField.isFocused()
                    || !text.equals(searchField.getText() == null
                    ? "" : searchField.getText().trim())) {
                return;
            }
            showSuggestions(items);
        });
    }

    /**
     * Populates and shows the suggestions popup under the field.
     *
     * @param items the suggestion strings (may be empty)
     */
    private void showSuggestions(List<String> items) {
        /*
         * A submit (Enter / button / pick) just happened — don't pop the
         * list back up from a late async response.
         */
        if (suppressSuggestions || items == null || items.isEmpty()) {
            hideSuggestions();
            return;
        }
        suggestionList.getChildren().clear();
        items.stream().limit(8).forEach(text -> {
            final Label row = new Label(text);
            row.getStyleClass().add("suggestion-item");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setOnMouseClicked(e -> {
                searchField.setText(text);
                hideSuggestions();
                submitSearch();
            });
            suggestionList.getChildren().add(row);
        });
        final Bounds bounds = searchField.localToScreen(searchField.getBoundsInLocal());
        if (bounds != null) {
            suggestionList.setPrefWidth(bounds.getWidth() + 56);
            suggestions.show(searchField, bounds.getMinX() - 6, bounds.getMaxY() + 6);
        }
    }

    /**
     * Hides the suggestions popup when visible.
     */
    private void hideSuggestions() {
        if (suggestions != null && suggestions.isShowing()) {
            suggestions.hide();
        }
    }

    /**
     * Fires the search callback with the trimmed query when non-empty.
     */
    private void submitSearch() {
        /*
         * Suppress and hide the suggestions for this submit so a pending
         * async response can't re-open the dropdown after Enter.
         */
        suppressSuggestions = true;
        hideSuggestions();
        final String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (!query.isEmpty() && onSearch != null) {
            onSearch.accept(query);
        }
    }

    /**
     * Wires the history buttons to the skeleton's navigation: enabled
     * state follows the observable history availability and clicks
     * trigger the back/forward jumps.
     *
     * @param canBack    observable "back available" state
     * @param canForward observable "forward available" state
     * @param back       the back action
     * @param forward    the forward action
     */
    public void wireHistory(BooleanProperty canBack, BooleanProperty canForward,
                            Runnable back, Runnable forward) {
        backBtn.disableProperty().bind(canBack.not());
        forwardBtn.disableProperty().bind(canForward.not());
        backBtn.setOnMouseClicked(e -> back.run());
        forwardBtn.setOnMouseClicked(e -> forward.run());
    }

    /**
     * Sets the search submit callback.
     *
     * @param onSearch consumer of the submitted query
     */
    public void setOnSearch(Consumer<String> onSearch) {
        this.onSearch = onSearch;
    }

    /**
     * Sets the settings gear callback.
     *
     * @param onSettings the action to run
     */
    public void setOnSettings(Runnable onSettings) {
        this.onSettings = onSettings;
    }

    /**
     * Sets the brand-click (home) callback.
     *
     * @param onHome the action to run
     */
    public void setOnHome(Runnable onHome) {
        this.onHome = onHome;
    }

    /**
     * Sets the search field text without firing the callback (used when
     * navigation restores a previous query).
     *
     * @param query the text to show
     */
    public void setQuery(String query) {
        searchField.setText(query == null ? "" : query);
    }

    /**
     * Returns the regions acting as the draggable caption area.
     *
     * @return the caption regions
     */
    public Region[] captionRegions() {
        return new Region[]{brand, leftDrag, rightDrag};
    }

    /**
     * Returns the minimize caption button region.
     *
     * @return the minimize region
     */
    public Region minRegion() {
        return minBtn;
    }

    /**
     * Returns the maximize / restore caption button region.
     *
     * @return the maximize region
     */
    public Region maxRegion() {
        return maxBtn;
    }

    /**
     * Returns the close caption button region.
     *
     * @return the close region
     */
    public Region closeRegion() {
        return closeBtn;
    }

}
