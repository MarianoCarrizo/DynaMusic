package com.marianocarrizo.dynamusic.audio;

import com.marianocarrizo.dynamusic.audio.cache.AudioCache;
import com.marianocarrizo.dynamusic.audio.cache.CacheLimit;
import com.marianocarrizo.dynamusic.audio.pcm.FfmpegConverter;
import com.marianocarrizo.dynamusic.audio.pcm.FfmpegProcess;
import com.marianocarrizo.dynamusic.audio.pcm.JavaSoundConverter;
import com.marianocarrizo.dynamusic.audio.pcm.StandardGainMeasurer;
import com.marianocarrizo.dynamusic.service.AppDataFolder;
import com.marianocarrizo.dynamusic.service.FfmpegLocator;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public record AudioServices(AudioCache cache, AudioPrep prep, Prefetcher prefetcher, Executor maintenance) {

    private static final long INDEX_FLUSH_DELAY_SECONDS = 5;
    private static final int USER_PREP_THREADS = 2;
    private static final String CACHE_FOLDER = "cache";
    private static final String AUDIO_FOLDER = "audio";

    public static AudioServices standard() {
        ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(
                runnable -> daemon(runnable, "audio-cache", Thread.MIN_PRIORITY));
        Path directory = AppDataFolder.child(CACHE_FOLDER).resolve(AUDIO_FOLDER);
        AudioCache cache = new AudioCache(directory, CacheLimit.DEFAULT, System::currentTimeMillis,
                AudioCache.filesystemDeleter(),
                flush -> maintenance.schedule(flush, INDEX_FLUSH_DELAY_SECONDS, TimeUnit.SECONDS));
        cache.initialize();
        AudioPrep prep = new AudioPrep(cache, new RouteResolver(FfmpegLocator::isAvailable), new JavaSoundConverter(),
                new FfmpegConverter(), new StandardGainMeasurer(FfmpegLocator::isAvailable),
                FfmpegLocator::isAvailable, Executors.newFixedThreadPool(USER_PREP_THREADS,
                        runnable -> daemon(runnable, "audio-prep", Thread.NORM_PRIORITY)));
        AudioServices services = new AudioServices(cache, prep, new Prefetcher(prep), maintenance);
        Runtime.getRuntime().addShutdownHook(new Thread(services::shutdown, "audio-shutdown"));
        return services;
    }

    public void shutdown() {
        prefetcher.cancel();
        FfmpegProcess.destroyAll();
        cache.flush();
    }

    private static Thread daemon(Runnable runnable, String name, int priority) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.setPriority(priority);
        return thread;
    }
}
