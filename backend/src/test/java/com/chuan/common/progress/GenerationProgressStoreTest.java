package com.chuan.common.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GenerationProgressStoreTest {

    @Test
    void tracksStagesAndClearsOnDone() {
        GenerationProgressStore store = new GenerationProgressStore();
        store.start(1L, GenerationProgressStore.TYPE_RESUME, 9L);

        GenerationProgressStore.Progress progress = store.get(1L, GenerationProgressStore.TYPE_RESUME, 9L);
        assertEquals(GenerationProgressStore.PREPARING, progress.getStage());

        store.stage(1L, GenerationProgressStore.TYPE_RESUME, 9L, GenerationProgressStore.GENERATING);
        assertEquals(GenerationProgressStore.GENERATING,
                store.get(1L, GenerationProgressStore.TYPE_RESUME, 9L).getStage());

        store.stage(1L, GenerationProgressStore.TYPE_RESUME, 9L, GenerationProgressStore.PARSING);
        assertEquals(GenerationProgressStore.PARSING,
                store.get(1L, GenerationProgressStore.TYPE_RESUME, 9L).getStage());

        store.done(1L, GenerationProgressStore.TYPE_RESUME, 9L);
        assertNull(store.get(1L, GenerationProgressStore.TYPE_RESUME, 9L));
    }
}