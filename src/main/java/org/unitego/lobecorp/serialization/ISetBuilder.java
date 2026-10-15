package org.unitego.lobecorp.serialization;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.Lifecycle;

import java.util.function.UnaryOperator;

public interface ISetBuilder<T> {
	DynamicOps<T> ops();

	DataResult<T> build(T prefix);

	ISetBuilder<T> add(final T value);

	ISetBuilder<T> add(final DataResult<T> value);

	ISetBuilder<T> withErrorsFrom(final DataResult<?> result);

	ISetBuilder<T> mapError(UnaryOperator<String> onError);

	default DataResult<T> build(final DataResult<T> prefix) {
		return prefix.flatMap(this::build);
	}

	default <E> ISetBuilder<T> add(final E value, final Encoder<E> encoder) {
		return add(encoder.encodeStart(ops(), value));
	}

	default <E> ISetBuilder<T> addAll(final Iterable<E> values, final Encoder<E> encoder) {
		values.forEach(v -> encoder.encode(v, ops(), ops().empty()));
		return this;
	}

	final class Builder<T> implements ISetBuilder<T> {
		private final DynamicOps<T> ops;
		private DataResult<ImmutableSet.Builder<T>> builder = DataResult.success(ImmutableSet.builder(), Lifecycle.stable());

		public Builder(final DynamicOps<T> ops) {
			this.ops = ops;
		}

		@Override
		public DynamicOps<T> ops() {
			return ops;
		}

		@Override
		public ISetBuilder<T> add(final T value) {
			builder = builder.map(b -> b.add(value));
			return this;
		}

		@Override
		public ISetBuilder<T> add(final DataResult<T> value) {
			builder = builder.apply2stable(ImmutableSet.Builder::add, value);
			return this;
		}

		@Override
		public ISetBuilder<T> withErrorsFrom(final DataResult<?> result) {
			builder = builder.flatMap(r -> result.map(_ -> r));
			return this;
		}

		@Override
		public ISetBuilder<T> mapError(final UnaryOperator<String> onError) {
			builder = builder.mapError(onError);
			return this;
		}

		@Override
		public DataResult<T> build(final T prefix) {
			final DataResult<T> result = builder.flatMap(b -> ops.mergeToList(prefix, b.build().asList()));
			builder = DataResult.success(ImmutableSet.builder(), Lifecycle.stable());
			return result;
		}
	}
}
