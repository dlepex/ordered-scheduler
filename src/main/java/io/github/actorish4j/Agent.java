package io.github.actorish4j;


import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.actorish4j.internal.ActorishUtil.with;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Agents provide access to shared state in async fashion.
 * Use Agents when you need lock-like behaviour for your async computations.
 * <p>
 * This implementation is inspired by <a href="https://hexdocs.pm/elixir/Agent.html">Elixir Agent module</a>. <p>
 * Clojure Agents are different (more limited), aside from the fact that they can participate in STM
 * <p>
 * Be careful, all methods in this class may throw RejectedExecutionException, if queue overflows!
 *
 * @param <S> state type. It's recommended for S to be immutable
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class Agent<S> extends EnqueuerBasedEntity {

	private final TaskEnqueuer enq;
	/**
	 * Shared mutable state.
	 * Non-volatile since all accesses to this field are done inside TaskEnqueuer tasks.
	 */
	private S state;

	public Agent(S initialState) {
		this(initialState, new TaskEnqueuer.Conf());
	}

	public Agent(S initialState, Consumer<TaskEnqueuer.Conf> configInit) {
		this(initialState, with(new TaskEnqueuer.Conf(), configInit));
	}

	public Agent(S initialState, TaskEnqueuer.Conf config) {
		this(initialState, new TaskEnqueuer(config));
	}

	private Agent(S state, TaskEnqueuer enq) {
		this.enq = enq;
		this.state = state;
	}

	public CompletionStage<S> get() {
		return get(st -> st);
	}

	public <A> CompletionStage<A> get(Function<? super S, ? extends A> mapper) {
		return getAsync(st -> completedFuture(mapper.apply(st)));
	}

	public <A> CompletionStage<A> getAsync(Function<? super S, ? extends CompletionStage<A>> asyncMapper) {
		return enq.mustOfferCall(() -> asyncMapper.apply(state));
	}

	public void updateAsync(Function<? super S, ? extends CompletionStage<? extends S>> asyncModifierFn) {
		enq.mustOffer(() -> asyncModifierFn.apply(this.state).thenAccept(newState -> this.state = newState));
	}

	public void update(Function<? super S, ? extends S> modifierFn) {
		enq.mustOffer(() -> {
			this.state = modifierFn.apply(this.state);
			return null;
		});
	}

	public <A> CompletionStage<A> getAndUpdateAsync(
			Function<? super S, ? extends CompletionStage<StateValuePair<S, A>>> asyncModifierFn) {

		return enq.mustOfferCall(() -> asyncModifierFn.apply(this.state).thenApply(tuple -> {
			this.state = tuple.state;
			return tuple.value;
		}));
	}

	public <A> CompletionStage<A> getAndUpdate(Function<? super S, StateValuePair<S, A>> modifierFn) {
		return getAndUpdateAsync(st -> completedFuture(modifierFn.apply(st)));
	}


	@Override
	protected Enqueuer<?> underlyingEnq() {
		return enq;
	}

	public final static class StateValuePair<S, V> {
		public final S state;
		public final V value;

		public StateValuePair(S state, V value) {
			this.state = state;
			this.value = value;
		}
	}
}

