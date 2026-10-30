package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.client.photon.performance.PhotonRenderPrewarm.WorkQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 只执行生产队列的纯任务调度核心，不初始化 Photon、GL 或游戏。
public class PhotonRenderPrewarmQueueTest {
    @Test
    public void budgetLimitsStartsAndDoesNotInterruptOverrun() {
        var clock = new AtomicLong();
        var failures = new ArrayList<RuntimeException>();
        var queue = new WorkQueue<String>(clock::get, failures::add);
        var calls = new ArrayList<String>();
        queue.addLast("prepare", () -> true, () -> {
            calls.add("prepare");
            clock.addAndGet(11);
        });
        queue.addLast("material", () -> true, () -> calls.add("material"));

        assertEquals(0, queue.drain(0));
        assertEquals(1, queue.drain(10));
        assertEquals(List.of("prepare"), calls);
        assertEquals(1, queue.remaining("material"));
        assertEquals(1, queue.drain(10));
        assertEquals(List.of("prepare", "material"), calls);
        assertTrue(failures.isEmpty());
    }

    @Test
    public void exactBudgetDefersNextTask() {
        var clock = new AtomicLong();
        var queue = new WorkQueue<String>(clock::get, _ -> {
            throw new AssertionError("不应失败");
        });
        queue.addLast("material", () -> true, () -> clock.addAndGet(10));
        queue.addLast("variant", () -> true, clock::incrementAndGet);

        assertEquals(1, queue.drain(10));
        assertEquals(10, clock.get());
        assertEquals(1, queue.remaining("variant"));
    }

    @Test
    public void clearDiscardsPendingAndRejectsOldContinuationButAllowsNewWork() {
        var queue = new WorkQueue<String>(() -> 0, _ -> {
            throw new AssertionError("不应失败");
        });
        var calls = new ArrayList<String>();
        queue.addLast("prepare", () -> true, () -> {
            queue.addLast("material", () -> true, () -> calls.add("before-clear"));
            queue.clear();
            queue.addFirst("material", () -> true, () -> calls.add("stale-first"));
            queue.addLast("variant", () -> true, () -> calls.add("stale-last"));
        });
        queue.addLast("child", () -> true, () -> calls.add("old-child"));

        assertEquals(1, queue.drain(10));
        assertTrue(queue.isEmpty());
        assertTrue(calls.isEmpty());

        queue.addLast("prepare", () -> true, () -> calls.add("new"));
        assertEquals(1, queue.drain(10));
        assertEquals(List.of("new"), calls);
    }

    @Test
    public void destructionSkipsEveryRemainingTaskAndItsContinuations() {
        var alive = new AtomicBoolean(true);
        var failures = new ArrayList<RuntimeException>();
        var queue = new WorkQueue<String>(() -> 0, failures::add);
        var calls = new ArrayList<String>();
        queue.addLast("material", alive::get, () -> {
            calls.add("first");
            alive.set(false);
        });
        queue.addLast("material", alive::get, () -> calls.add("second"));
        queue.addLast("variant", alive::get, () -> calls.add("variant"));
        queue.addLast("child", alive::get, () -> {
            calls.add("child");
            queue.addLast("prepare", alive::get, () -> calls.add("continuation"));
        });
        queue.addLast("unrelated", () -> true, () -> calls.add("unrelated"));

        assertEquals(5, queue.drain(10));
        assertEquals(List.of("first", "unrelated"), calls);
        assertTrue(queue.isEmpty());
        assertTrue(failures.isEmpty());
    }

    @Test
    public void materialAndVariantFailuresDoNotBlockRemainingTasks() {
        var failures = new ArrayList<RuntimeException>();
        var queue = new WorkQueue<String>(() -> 0, failures::add);
        var calls = new ArrayList<String>();
        queue.addLast("material", () -> true, () -> {
            throw new IllegalStateException("material");
        });
        queue.addLast("material", () -> true, () -> calls.add("other-material"));
        queue.addLast("variant", () -> true, () -> {
            throw new IllegalStateException("variant");
        });
        queue.addLast("variant", () -> true, () -> calls.add("other-variant"));

        assertEquals(4, queue.drain(10));
        assertEquals(List.of("other-material", "other-variant"), calls);
        assertEquals(List.of("material", "variant"), failures.stream().map(Throwable::getMessage).toList());
    }

    @Test
    public void queuedPrepareKeepsGroupMaterialAndBaseThenVariantInsertionOrder() {
        var calls = new ArrayList<String>();
        var queue = new WorkQueue<String>(() -> 0, _ -> {
            throw new AssertionError("不应失败");
        });
        queue.addFirst("prepare", () -> true, () -> {
            calls.add("prepare");
            for (var group : List.of("g2", "g0")) {
                for (var material : List.of("m1", "m0")) {
                    queue.addLast("material", () -> true, () -> calls.add(group + "/" + material));
                }
            }
            for (var material : List.of("m1", "m0")) {
                for (var variant : List.of("tile", "model")) {
                    queue.addLast("variant", () -> true, () -> calls.add(material + "/" + variant));
                }
            }
        });

        assertTrue(calls.isEmpty());
        assertEquals(1, queue.remaining("prepare"));
        assertEquals(9, queue.drain(10));
        assertEquals(List.of("prepare", "g2/m1", "g2/m0", "g0/m1", "g0/m0",
                "m1/tile", "m1/model", "m0/tile", "m0/model"), calls);
        assertTrue(queue.isEmpty());
    }

    @Test
    public void failingTaskAfterClearCannotRestoreDiscardedWork() {
        var failures = new ArrayList<RuntimeException>();
        var calls = new ArrayList<String>();
        var queue = new WorkQueue<String>(() -> 0, failures::add);
        queue.addLast("prepare", () -> true, () -> {
            queue.clear();
            queue.addLast("material", () -> true, () -> calls.add("stale"));
            throw new IllegalStateException("prepare");
        });
        queue.addLast("variant", () -> true, () -> calls.add("discarded"));

        assertEquals(1, queue.drain(10));
        assertEquals(1, failures.size());
        assertTrue(queue.isEmpty());
        assertTrue(calls.isEmpty());
    }
}
