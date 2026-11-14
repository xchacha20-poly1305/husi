package itersequence

import (
	"iter"
)

func Map[T, R any](seq iter.Seq[T], transform func(T) R) iter.Seq[R] {
	return MapIndexed[T, R](seq, func(_ int, item T) R {
		return transform(item)
	})
}

func MapIndexed[T, R any](seq iter.Seq[T], transform func(int, T) R) iter.Seq[R] {
	return func(yield func(R) bool) {
		var i int
		for item := range seq {
			transformed := transform(i, item)
			if !yield(transformed) {
				return
			}
			i++
		}
	}
}

func Single[T any](item T) iter.Seq[T] {
	return func(yield func(T) bool) {
		yield(item)
	}
}

func FlatMap[T, R any](seq iter.Seq[T], transform func(T) iter.Seq[R]) iter.Seq[R] {
	return FlatMapIndexed(seq, func(_ int, value T) iter.Seq[R] {
		return transform(value)
	})
}

func FlatMapIndexed[T, R any](seq iter.Seq[T], transform func(int, T) iter.Seq[R]) iter.Seq[R] {
	return func(yield func(R) bool) {
		var i int
		for item := range seq {
			transformedIter := transform(i, item)
			for transformed := range transformedIter {
				if !yield(transformed) {
					return
				}
			}
		}
	}
}

func Merge[T any](seqs ...iter.Seq[T]) iter.Seq[T] {
	return func(yield func(T) bool) {
		for _, seq := range seqs {
			for item := range seq {
				if !yield(item) {
					return
				}
			}
		}
	}
}

func DotProduct[R, C, M any](row []R, column []C, multiple func(x, y int, r R, c C) M) iter.Seq[M] {
	return func(yield func(M) bool) {
		for x := range row {
			for y := range column {
				cross := multiple(x, y, row[x], column[y])
				if !yield(cross) {
					return
				}
			}
		}
	}
}
