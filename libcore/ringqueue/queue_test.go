package ringqueue

import (
	"testing"

	"github.com/sagernet/sing/common"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRingQueue_Add_And_All(t *testing.T) {
	q := New[int](3)
	front, popped := q.Add(1)
	assert.False(t, popped)
	assert.True(t, common.IsEmpty(front))

	expected := []int{1}
	assert.Equal(t, expected, q.All())

	q.Add(2)
	q.Add(3)

	expected = []int{1, 2, 3}
	assert.Equal(t, expected, q.All())

	front, popped = q.Add(4)
	assert.True(t, popped)
	assert.Equal(t, 1, front)

	expected = []int{2, 3, 4}
	assert.Equal(t, expected, q.All())

	front, popped = q.Add(5)
	assert.True(t, popped)
	assert.Equal(t, 2, front)

	expected = []int{3, 4, 5}
	assert.Equal(t, expected, q.All())
}

func TestRingQueue_Clear(t *testing.T) {
	q := New[string](2)

	q.Add("A")
	q.Add("B")

	require.Len(t, q.All(), 2)

	q.Clear()

	assert.Empty(t, q.All())

	q.Add("C")
	expected := []string{"C"}
	assert.Equal(t, expected, q.All())
}

func TestRingQueue_Empty(t *testing.T) {
	q := New[int](5)

	assert.Empty(t, q.All())

	front, popped := q.Add(100)
	assert.False(t, popped)
	assert.True(t, common.IsEmpty(front))
}
