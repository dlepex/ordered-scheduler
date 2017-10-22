package github.jcext.applications;

import github.jcext.Enqueuer;
import github.jcext.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.*;


/**
 * Experimental, {@link Enqueuer} application to exact-date scheduling.
 */
@SuppressWarnings("WeakerAccess")
public final class ExactDateScheduler {  //TODO proper reliable shutdown
	private static final Logger logger = LoggerFactory.getLogger(ExactDateScheduler.class);

	@FunctionalInterface
	public interface Task {
		interface Info {
			LocalDateTime begin();

			LocalDateTime realBegin();

			long order();

			default Duration delay() {
				return Duration.between(begin(), realBegin());
			}
		}

		void run(Info info);

		static Task fromRunnable(Runnable r) {
			return info -> r.run();
		}
	}

	private final Enqueuer<TaskStruct> inboundEnq;
	private final int plannedTasksLimit;
	private final Timer timer;


	public static ExactDateScheduler create(Enqueuer.Conf c, Timer timer, int plannedTasksLimit) {
		return new ExactDateScheduler(c, plannedTasksLimit, timer);
	}

	private ExactDateScheduler(Enqueuer.Conf c, int plannedTasksLimit, Timer timer) {
		this.inboundEnq = Enqueuer.create(c, this::doPoll);
		this.plannedTasksLimit = plannedTasksLimit;
		this.timer = timer;
		//executor.execute(this::pollingLoop);
	}

	public void schedule(LocalDateTime beginAt, Task task) throws RejectedExecutionException {
		Objects.requireNonNull(task);
		Objects.requireNonNull(beginAt);
		if (!inboundEnq.offer(new TaskStruct(beginAt, task))) {
			throw new RejectedExecutionException("github.jcext.sample.ExactDateScheduler is flooded");
		}
	}

	public void schedule(LocalDateTime beginAt, Runnable runnable) throws RejectedExecutionException {
		Objects.requireNonNull(runnable);
		Objects.requireNonNull(beginAt);
		schedule(beginAt, Task.fromRunnable(runnable));
	}

	public <T> CompletableFuture<T> schedule(LocalDateTime beginAt, Callable<T> callable)
			throws RejectedExecutionException {
		Objects.requireNonNull(callable);
		Objects.requireNonNull(beginAt);
		CompletableFuture<T> result = new CompletableFuture<>();
		schedule(beginAt, asRunnable(callable, result, ForkJoinPool.commonPool()));
		return result;
	}

	private static <T> Runnable asRunnable(Callable<T> callable, CompletableFuture<T> promise, Executor exec) {
		return () -> {
			try {
				try {
					T result = callable.call();
					exec.execute(() -> promise.complete(result));
				} catch (Throwable ex) {
					exec.execute(() -> promise.completeExceptionally(ex));
				}
			} catch (Throwable ex) {
				// this should never happen with FJP
				logger.error("Executor rejects us. Can't execute promise completion", ex);
			}
		};
	}

	private static final class TaskStruct implements Comparable<TaskStruct>, Task.Info {
		final LocalDateTime begin;
		final Task t;
		long order;
		LocalDateTime realBegin;

		TaskStruct(LocalDateTime begin, Task t) {
			this.begin = begin;
			this.t = t;
		}

		@Override
		public int compareTo(TaskStruct other) {
			int cmp = begin.compareTo(other.begin);
			if (cmp != 0) {
				return cmp;
			}
			return Long.compare(order, other.order);
		}

		void runSafely() {
			try {
				t.run(this);
			} catch (Throwable ex) {
				logger.warn("uncaught task error", ex);
			}
		}

		@Override
		public LocalDateTime begin() {
			return begin;
		}

		@Override
		public LocalDateTime realBegin() {
			return realBegin;
		}

		@Override
		public long order() {
			return order;
		}
	}

	//<editor-fold desc="Scheduler thread state variables:">
	private long taskCounter;
	private final PriorityQueue<TaskStruct> plannedQueue = new PriorityQueue<>(512);
	private long pollTimeout; // in millis
	//</editor-fold>


	private static final TaskStruct timeoutSpecialValue = new TaskStruct(null, null);

	private CompletionStage<?> doPoll(Queue<TaskStruct> inboundQueue) {
		{
			TaskStruct task = inboundQueue.poll();

			if (task != timeoutSpecialValue) {
				if (plannedQueue.size() < plannedTasksLimit) {
					task.order = taskCounter++;
					plannedQueue.offer(task);
				} else {
					logger.warn("plannedQueue exceeded its limit={}", plannedTasksLimit);
				}
			}
		}

		for (; ; ) {
			TaskStruct task = plannedQueue.peek();
			if (task == null) {
				pollTimeout = 0;
				return null;
			}

			LocalDateTime now = LocalDateTime.now();
			pollTimeout = Duration.between(now, task.begin).toMillis();
			if (pollTimeout > 0) {
				timer.timeout(() -> inboundEnq.offer(timeoutSpecialValue), pollTimeout, TimeUnit.MILLISECONDS);
				return null;
			}

			TaskStruct ignored = plannedQueue.poll();
			assert ignored == task;
			task.realBegin = now;
			task.runSafely();
		}
	}

}
