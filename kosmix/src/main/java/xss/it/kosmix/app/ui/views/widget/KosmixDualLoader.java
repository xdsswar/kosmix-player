package xss.it.kosmix.app.ui.views.widget;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * Dual-circle pulse loader — a faithful port of NfxDualLoader to the
 * skia-fx toolkit. The outer circle shrinks and fades toward the center
 * while the inner circle grows and fades in from the center, combined
 * in an indefinite {@link ParallelTransition}. Radius, both paints and
 * the cycle duration are CSS-styleable:
 * {@code -nfx-radius}, {@code -nfx-inner-color}, {@code -nfx-outer-color},
 * {@code -nfx-cycle-duration}.
 */
public final class KosmixDualLoader extends StackPane {
    /**
     * Style class
     */
    private static final String STYLE_CLASS = "nfx-dual-loader";

    /**
     * Circle representing the outer part of the loader animation.
     */
    private final Circle outerCircle;

    /**
     * Circle representing the inner part of the loader animation.
     */
    private final Circle innerCircle;

    /**
     * Parallel transition combining animations for both circles to
     * create the loader effect.
     */
    private ParallelTransition parallelTransition;

    /**
     * State of animations
     */
    private boolean playing = false;

    /**
     * Constructs an instance of the {@code KosmixDualLoader}.
     * <p>
     * This constructor initializes the loader by building both circles
     * and the looping transitions, then honors the auto-start flag.
     */
    public KosmixDualLoader() {
        super();
        setCache(false);
        setVisible(false);
        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setPrefHeight(Region.USE_COMPUTED_SIZE);
        getStyleClass().add(STYLE_CLASS);
        setMaxWidth(3 * getRadius());
        outerCircle = new Circle(getRadius());
        innerCircle = new Circle(getRadius());
        /*
         * Set the fills up front — the original control only assigns
         * them inside handleChange() (on a property change), so before
         * any change the circles would paint with the default black
         * fill at the default radius and read as a tiny dark dot.
         */
        outerCircle.setFill(getOuterPaint());
        innerCircle.setFill(getInnerPaint());
        this.getChildren().addAll(innerCircle, outerCircle);
        setupTransitions();

        radiusProperty().addListener(o -> handleChange());
        outerPaintProperty().addListener(o -> handleChange());
        innerPaintProperty().addListener(o -> handleChange());
        cycleDurationProperty().addListener(o -> handleChange());

        /*
         * AutoStart
         */
        autoStartProperty().addListener((obs, o, yes) -> {
            if (yes) {
                start();
                setVisible(true);
            } else {
                stop();
                setVisible(false);
            }
        });
        if (isAutoStart()) {
            setVisible(true);
            start();
        }
    }

    /**
     * Updates the loader's appearance by applying the latest color and
     * radius properties to the circles.
     * <p>
     * This method temporarily stops the animation, sets the fill color
     * and radius for both the outer and inner circles based on the
     * current properties, and reconfigures the animation transitions.
     * If the loader was previously playing, the animation is restarted
     * to reflect the changes.
     */
    private void handleChange() {
        final boolean wasPlaying = playing;
        stop();
        outerCircle.setFill(getOuterPaint());
        outerCircle.setRadius(getRadius());
        innerCircle.setFill(getInnerPaint());
        innerCircle.setRadius(getRadius());
        setMaxWidth(3 * getRadius());
        setupTransitions();
        if (wasPlaying) {
            start();
        }
    }

    /**
     * Radius property for controlling the size of the circles in the
     * loader animation.
     */
    private DoubleProperty radius;

    /**
     * Gets the current radius value of the circles.
     *
     * @return the current radius of the circles
     */
    public double getRadius() {
        return radiusProperty().get();
    }

    /**
     * Provides access to the radius property, allowing it to be bound
     * or observed.
     *
     * @return the {@link DoubleProperty} representing the radius
     */
    public DoubleProperty radiusProperty() {
        if (radius == null) {
            radius = new StyleableDoubleProperty(5) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public Object getBean() {
                    return KosmixDualLoader.this;
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String getName() {
                    return "radius";
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return Styleables.RADIUS_STYLE;
                }
            };
        }
        return radius;
    }

    /**
     * Sets the radius of the circles in the loader animation.
     *
     * @param radius the new radius value for the circles
     */
    public void setRadius(double radius) {
        radiusProperty().set(radius);
    }

    /**
     * Property for controlling the color of the inner circle in the
     * loader animation.
     */
    private ObjectProperty<Paint> innerPaint;

    /**
     * Gets the current color of the inner circle.
     *
     * @return the current {@link Paint} of the inner circle
     */
    public Paint getInnerPaint() {
        return innerPaintProperty().get();
    }

    /**
     * Provides access to the inner color property, allowing it to be
     * bound or observed.
     *
     * @return the {@link ObjectProperty} representing the inner color
     */
    public ObjectProperty<Paint> innerPaintProperty() {
        if (innerPaint == null) {
            innerPaint = new StyleableObjectProperty<>(Color.web("#ff6b6b")) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public Object getBean() {
                    return KosmixDualLoader.this;
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String getName() {
                    return "innerPaint";
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return Styleables.INNER_STYLE;
                }
            };
        }
        return innerPaint;
    }

    /**
     * Sets the color of the inner circle in the loader animation.
     *
     * @param innerPaint the new {@link Paint} for the inner circle
     */
    public void setInnerPaint(Paint innerPaint) {
        innerPaintProperty().set(innerPaint);
    }

    /**
     * Property for controlling the color of the outer circle in the
     * loader animation.
     */
    private ObjectProperty<Paint> outerPaint;

    /**
     * Gets the current color of the outer circle.
     *
     * @return the current {@link Paint} of the outer circle
     */
    public Paint getOuterPaint() {
        return outerPaintProperty().get();
    }

    /**
     * Provides access to the outer color property, allowing it to be
     * bound or observed.
     *
     * @return the {@link ObjectProperty} representing the outer color
     */
    public ObjectProperty<Paint> outerPaintProperty() {
        if (outerPaint == null) {
            outerPaint = new StyleableObjectProperty<>(Color.web("#cc0000")) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public Object getBean() {
                    return KosmixDualLoader.this;
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String getName() {
                    return "outerPaint";
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return Styleables.OUTER_STYLE;
                }
            };
        }
        return outerPaint;
    }

    /**
     * Sets the color of the outer circle in the loader animation.
     *
     * @param outerPaint the new {@link Paint} for the outer circle
     */
    public void setOuterPaint(Paint outerPaint) {
        outerPaintProperty().set(outerPaint);
    }

    /**
     * Property for controlling the duration of one cycle of the loader
     * animation.
     */
    private DoubleProperty cycleDuration;

    /**
     * Gets the current duration of one animation cycle.
     *
     * @return the cycle duration, in seconds
     */
    public double getCycleDuration() {
        return cycleDurationProperty().get();
    }

    /**
     * Provides access to the cycle duration property, allowing it to be
     * bound or observed.
     *
     * @return the {@link DoubleProperty} representing the cycle duration
     */
    public DoubleProperty cycleDurationProperty() {
        if (cycleDuration == null) {
            cycleDuration = new StyleableDoubleProperty(0.6) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public Object getBean() {
                    return KosmixDualLoader.this;
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String getName() {
                    return "cycleDuration";
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return Styleables.CYCLE_STYLE;
                }
            };
        }
        return cycleDuration;
    }

    /**
     * Sets the duration of one cycle of the loader animation.
     *
     * @param cycleDuration the new duration, in seconds
     */
    public void setCycleDuration(double cycleDuration) {
        cycleDurationProperty().set(cycleDuration);
    }

    /**
     * Property indicating whether the loader animation should start
     * automatically.
     */
    private BooleanProperty autoStart;

    /**
     * Checks if the loader animation is set to start automatically.
     *
     * @return {@code true} if the animation should auto-start
     */
    public boolean isAutoStart() {
        return autoStartProperty().get();
    }

    /**
     * Provides access to the auto-start property, allowing it to be
     * bound or observed.
     *
     * @return the {@link BooleanProperty} representing auto-start
     */
    public BooleanProperty autoStartProperty() {
        if (autoStart == null) {
            autoStart = new SimpleBooleanProperty(this, "autoStart", false);
        }
        return autoStart;
    }

    /**
     * Sets whether the loader animation should start automatically.
     *
     * @param autoStart {@code true} to enable auto-start
     */
    public void setAutoStart(boolean autoStart) {
        autoStartProperty().set(autoStart);
    }

    /**
     * Configures the transitions for the loader animation.
     * <p>
     * This method sets up scale and fade transitions for both the outer
     * and inner circles. The outer circle shrinks and fades out towards
     * the center, while the inner circle grows and fades in from the
     * center to the outer edge. The transitions are combined into a
     * parallel transition that loops indefinitely, creating a
     * continuous animation effect.
     */
    private void setupTransitions() {
        /*
         * Outer circle: moving from out to center (shrinking).
         */
        final ScaleTransition scaleOutToCenter =
                new ScaleTransition(Duration.seconds(getCycleDuration()), outerCircle);
        scaleOutToCenter.setFromX(3.0);
        scaleOutToCenter.setFromY(3.0);
        scaleOutToCenter.setToX(0.1);
        scaleOutToCenter.setToY(0.1);
        scaleOutToCenter.setInterpolator(Interpolator.EASE_IN);

        final FadeTransition fadeOutToCenter =
                new FadeTransition(Duration.seconds(getCycleDuration()), outerCircle);
        fadeOutToCenter.setFromValue(1.0);
        fadeOutToCenter.setToValue(0.0);
        fadeOutToCenter.setInterpolator(Interpolator.EASE_IN);

        /*
         * Inner circle: moving from center to out (growing).
         */
        final ScaleTransition scaleCenterToOut =
                new ScaleTransition(Duration.seconds(getCycleDuration()), innerCircle);
        scaleCenterToOut.setFromX(0.1);
        scaleCenterToOut.setFromY(0.1);
        scaleCenterToOut.setToX(3.0);
        scaleCenterToOut.setToY(3.0);
        scaleCenterToOut.setInterpolator(Interpolator.EASE_IN);

        final FadeTransition fadeCenterToOut =
                new FadeTransition(Duration.seconds(getCycleDuration()), innerCircle);
        fadeCenterToOut.setFromValue(0.0);
        fadeCenterToOut.setToValue(1.0);
        fadeCenterToOut.setInterpolator(Interpolator.EASE_IN);

        parallelTransition = new ParallelTransition(
                scaleOutToCenter, fadeOutToCenter,
                scaleCenterToOut, fadeCenterToOut
        );
        parallelTransition.setCycleCount(ParallelTransition.INDEFINITE);
    }

    /**
     * Starts the loader animation.
     */
    public void start() {
        if (parallelTransition != null
                && parallelTransition.getStatus() != Animation.Status.RUNNING) {
            parallelTransition.play();
        }
        playing = true;
    }

    /**
     * Stops the loader animation.
     */
    public void stop() {
        if (parallelTransition != null) {
            parallelTransition.stop();
        }
        playing = false;
    }

    /**
     * Styleables
     */
    @SuppressWarnings("all")
    private static final class Styleables {
        /**
         * CSS metadata for styling the radius of the loader circles via
         * "-nfx-radius" (default 5).
         */
        private static final CssMetaData<KosmixDualLoader, Number> RADIUS_STYLE = new CssMetaData<>(
                "-nfx-radius", SizeConverter.getInstance(), 5
        ) {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isSettable(KosmixDualLoader s) {
                return s.radiusProperty() == null || !s.radiusProperty().isBound();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public StyleableProperty<Number> getStyleableProperty(KosmixDualLoader s) {
                return (StyleableProperty<Number>) s.radiusProperty();
            }
        };

        /**
         * CSS metadata for styling the inner circle color via
         * "-nfx-inner-color".
         */
        private static final CssMetaData<KosmixDualLoader, Paint> INNER_STYLE = new CssMetaData<>(
                "-nfx-inner-color", PaintConverter.getInstance(), Color.web("#ff6b6b")
        ) {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isSettable(KosmixDualLoader s) {
                return s.innerPaintProperty() == null || !s.innerPaintProperty().isBound();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public StyleableProperty<Paint> getStyleableProperty(KosmixDualLoader s) {
                return (StyleableProperty<Paint>) s.innerPaintProperty();
            }
        };

        /**
         * CSS metadata for styling the outer circle color via
         * "-nfx-outer-color".
         */
        private static final CssMetaData<KosmixDualLoader, Paint> OUTER_STYLE = new CssMetaData<>(
                "-nfx-outer-color", PaintConverter.getInstance(), Color.web("#cc0000")
        ) {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isSettable(KosmixDualLoader s) {
                return s.outerPaintProperty() == null || !s.outerPaintProperty().isBound();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public StyleableProperty<Paint> getStyleableProperty(KosmixDualLoader s) {
                return (StyleableProperty<Paint>) s.outerPaintProperty();
            }
        };

        /**
         * CSS metadata for styling the animation cycle duration via
         * "-nfx-cycle-duration" (seconds, default 0.6).
         */
        private static final CssMetaData<KosmixDualLoader, Number> CYCLE_STYLE = new CssMetaData<>(
                "-nfx-cycle-duration", SizeConverter.getInstance(), 0.6
        ) {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isSettable(KosmixDualLoader s) {
                return s.cycleDurationProperty() == null || !s.cycleDurationProperty().isBound();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public StyleableProperty<Number> getStyleableProperty(KosmixDualLoader s) {
                return (StyleableProperty<Number>) s.cycleDurationProperty();
            }
        };

        /**
         * List of CSS metadata for the {@code KosmixDualLoader} class,
         * exposing its custom properties to the CSS engine.
         */
        public static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(RADIUS_STYLE);
            styleables.add(INNER_STYLE);
            styleables.add(OUTER_STYLE);
            styleables.add(CYCLE_STYLE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return Styleables.STYLEABLES;
    }
}
