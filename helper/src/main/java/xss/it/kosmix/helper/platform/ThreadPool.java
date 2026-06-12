package xss.it.kosmix.helper.platform;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.platform package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * A custom thread pool implementation that extends
 * {@link AbstractExecutorService}.
 * <p>
 * The pool adapts to the runtime: when virtual threads are available it
 * delegates to a thread-per-task executor of daemon virtual threads;
 * otherwise it falls back to a bounded {@link ThreadPoolExecutor} of
 * daemon platform threads. Either way, submitted work never prevents
 * JVM shutdown.
 */
public final class ThreadPool extends AbstractExecutorService {
    /**
     * The underlying {@link ExecutorService} that performs the actual
     * task execution. Either a virtual thread-per-task executor or a
     * platform {@link ThreadPoolExecutor}.
     */
    private final ExecutorService delegate;

    /**
     * Indicates whether this pool runs tasks on virtual threads. When
     * {@code false}, bounded daemon platform threads are used instead.
     */
    private final boolean virtual;

    /**
     * Constructs a {@code ThreadPool} with the specified configuration.
     * Virtual threads are used automatically when the runtime supports
     * them; the sizing parameters only apply to the platform fallback.
     *
     * @param corePoolSize     the minimum number of platform threads kept alive
     * @param maximumPoolSize  the maximum number of platform threads allowed
     * @param keepAliveSeconds idle seconds before excess platform threads die
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveSeconds) {
        this(corePoolSize, maximumPoolSize, keepAliveSeconds, Platform.supportsVirtualThreads());
    }

    /**
     * Constructs a {@code ThreadPool} with an explicit threading mode.
     *
     * @param corePoolSize     the minimum number of platform threads kept alive
     * @param maximumPoolSize  the maximum number of platform threads allowed
     * @param keepAliveSeconds idle seconds before excess platform threads die
     * @param virtual          {@code true} to force virtual threads,
     *                         {@code false} to force platform threads
     */
    public ThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveSeconds, boolean virtual) {
        this.virtual = virtual;

        if (this.virtual) {
            /*
             * Thread-per-task executor over named, daemon virtual threads.
             * Virtual threads are always daemon threads by definition.
             */
            this.delegate = Executors.newThreadPerTaskExecutor(new VirtualThreadFactory());
        } else {
            /*
             * Bounded platform pool with an unbounded work queue and
             * timing-out core threads, so an idle application holds no
             * threads at all.
             */
            final ThreadPoolExecutor platformPool = new ThreadPoolExecutor(
                    corePoolSize,
                    maximumPoolSize,
                    keepAliveSeconds,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new DaemonThreadFactory()
            );
            platformPool.allowCoreThreadTimeOut(true);
            this.delegate = platformPool;
        }
    }

    /**
     * Indicates whether tasks are executed on virtual threads.
     *
     * @return {@code true} when virtual threads are in use
     */
    public boolean isVirtual() {
        return virtual;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    /**
     * Thread factory producing named daemon platform threads
     * ({@code Th-1}, {@code Th-2}, ...).
     */
    private static final class DaemonThreadFactory implements ThreadFactory {
        /**
         * Monotonic counter used to build unique thread names.
         */
        private final AtomicInteger counter = new AtomicInteger(0);

        /**
         * {@inheritDoc}
         */
        @Override
        public Thread newThread(Runnable r) {
            final Thread thread = new Thread(r, "Th-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Thread factory producing named virtual threads
     * ({@code VTh-1}, {@code VTh-2}, ...).
     */
    private static final class VirtualThreadFactory implements ThreadFactory {
        /**
         * Monotonic counter used to build unique thread names.
         */
        private final AtomicInteger counter = new AtomicInteger(0);

        /**
         * {@inheritDoc}
         */
        @Override
        public Thread newThread(Runnable r) {
            return Thread.ofVirtual()
                    .name("VTh-" + counter.incrementAndGet())
                    .unstarted(r);
        }
    }
}
