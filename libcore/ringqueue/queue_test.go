package ringqueue

import (
	"reflect"
	"testing"

	"github.com/sagernet/sing/common"
)

func TestRingQueue_Add_And_All(t *testing.T) {
	q := New[int](3)
	front, popped := q.Add(1)
	if popped {
		t.Errorf("Expected popped=false, got true")
	}
	if !common.IsEmpty(front) {
		t.Errorf("Expected front=0, got %d", front)
	}

	expected := []int{1}
	if got := q.All(); !reflect.DeepEqual(got, expected) {
		t.Errorf("Step 1: Expected %v, got %v", expected, got)
	}

	q.Add(2)
	q.Add(3)

	expected = []int{1, 2, 3}
	if got := q.All(); !reflect.DeepEqual(got, expected) {
		t.Errorf("Step 2: Expected %v, got %v", expected, got)
	}

	front, popped = q.Add(4)
	if !popped {
		t.Errorf("Expected popped=true when ring is full")
	}
	if front != 1 {
		t.Errorf("Expected evicted value to be 1, got %d", front)
	}

	expected = []int{2, 3, 4}
	if got := q.All(); !reflect.DeepEqual(got, expected) {
		t.Errorf("Step 3 (After rotation): Expected %v, got %v", expected, got)
	}

	front, popped = q.Add(5)
	if front != 2 {
		t.Errorf("Expected evicted value to be 2, got %d", front)
	}

	expected = []int{3, 4, 5}
	if got := q.All(); !reflect.DeepEqual(got, expected) {
		t.Errorf("Step 4 (After rotation): Expected %v, got %v", expected, got)
	}
}

func TestRingQueue_Clear(t *testing.T) {
	q := New[string](2)

	q.Add("A")
	q.Add("B")

	if len(q.All()) != 2 {
		t.Fatal("Setup failed, queue should have 2 elements")
	}

	q.Clear()

	if got := q.All(); len(got) != 0 {
		t.Errorf("Expected empty slice after Clear(), got %v", got)
	}

	q.Add("C")
	expected := []string{"C"}
	if got := q.All(); !reflect.DeepEqual(got, expected) {
		t.Errorf("Expected %v after reuse, got %v", expected, got)
	}
}

func TestRingQueue_Empty(t *testing.T) {
	q := New[int](5)

	if got := q.All(); len(got) != 0 {
		t.Errorf("Expected empty slice, got %v", got)
	}

	front, popped := q.Add(100)
	if popped {
		t.Error("Should not pop on empty queue insertion")
	}
	if !common.IsEmpty(front) {
		t.Error("Front should be zero value")
	}
}
