package systems.bdev.evetts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that watches the EVE log directory for chat log files matching a specific channel,
 * tracks active log files per Listener character (multi-boxing support), caches Listener Character IDs
 * extracted from filenames to eliminate disk I/O, reads new lines in real-time via byte-offset tailing,
 * and passes them to LogMessageProcessService.
 */
public class LogWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(LogWatcherService.class);

    // EVE Chat log filename format: CHANNELNAME_YYYYMMDD_HHMMSS_LISTENERID.txt
    private static final Pattern FILENAME_TIMESTAMP_PATTERN = Pattern.compile(
            "^.*?_(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})_.*\\.txt$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FILENAME_LISTENER_ID_PATTERN = Pattern.compile(
            "^.*?_\\d{8}_\\d{6}_(\\d+)\\.txt$",
            Pattern.CASE_INSENSITIVE
    );

    // Cache mapping EVE Listener Character ID (e.g. "2112000314") -> Character Name
    private static final Map<String, String> LISTENER_ID_CACHE = new ConcurrentHashMap<>();

    private final String logDirectory;
    private final LogMessageProcessService logMessageProcessService;
    private final long pollIntervalMs;

    public LogWatcherService(String logDirectory, LogMessageProcessService logMessageProcessService, long pollIntervalMs) {
        this.logDirectory = logDirectory;
        this.logMessageProcessService = logMessageProcessService;
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 500L;
    }

    public static Map<String, String> getListenerIdCache() {
        return LISTENER_ID_CACHE;
    }

    public void runWatcher(String channelName, AtomicBoolean running) {
        logger.info("Starting log watcher for channel '{}' in directory: {}", channelName, logDirectory);

        File dir = new File(logDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Log directory '{}' does not exist or is not a directory. Waiting for it to be created...", logDirectory);
        }

        Set<String> processedFilePaths = new HashSet<>();
        Map<String, Long> activeFilePointers = new ConcurrentHashMap<>();
        Map<String, String> activeListenerFilePaths = new HashMap<>(); // listenerName -> activeFilePath

        try {
            while (running.get()) {
                Map<String, List<File>> listenerFilesMap = findMatchingFilesByListener(dir, channelName);
                Set<String> currentIterationActivePaths = new HashSet<>();

                for (Map.Entry<String, List<File>> entry : listenerFilesMap.entrySet()) {
                    String listenerName = entry.getKey();
                    List<File> filesForListener = entry.getValue();

                    List<File> relevantForListener = filterRelevantLogFiles(filesForListener, channelName);
                    if (relevantForListener.isEmpty()) {
                        continue;
                    }

                    File latestActiveForListener = relevantForListener.get(relevantForListener.size() - 1);
                    currentIterationActivePaths.add(latestActiveForListener.getAbsolutePath());

                    // 1. Process any historical files for this listener
                    for (File file : relevantForListener) {
                        if (!running.get()) {
                            break;
                        }
                        if (!file.equals(latestActiveForListener) && !processedFilePaths.contains(file.getAbsolutePath())) {
                            logger.info("[{}] Catching up historical log file for listener '{}': {}", channelName, listenerName, file.getName());
                            readNewLinesFromFile(file, 0L, channelName);
                            processedFilePaths.add(file.getAbsolutePath());
                        }
                    }

                    // 2. Active file tracking & tailing for this listener
                    String previousActivePath = activeListenerFilePaths.get(listenerName);
                    String currentActivePath = latestActiveForListener.getAbsolutePath();

                    if (!Objects.equals(previousActivePath, currentActivePath)) {
                        if (previousActivePath != null) {
                            processedFilePaths.add(previousActivePath);
                            activeFilePointers.remove(previousActivePath);
                        }

                        activeListenerFilePaths.put(listenerName, currentActivePath);
                        logger.info("[{}] Tailing active log file for listener '{}': {}", channelName, listenerName, latestActiveForListener.getName());

                        long pointer = readNewLinesFromFile(latestActiveForListener, 0L, channelName);
                        activeFilePointers.put(currentActivePath, pointer);
                    } else {
                        // Continuously tail new lines appended to active open file
                        long currentPointer = activeFilePointers.getOrDefault(currentActivePath, 0L);
                        long newPointer = readNewLinesFromFile(latestActiveForListener, currentPointer, channelName);
                        activeFilePointers.put(currentActivePath, newPointer);
                    }
                }

                // Clean up stale listeners / active files
                activeListenerFilePaths.entrySet().removeIf(e -> !currentIterationActivePaths.contains(e.getValue()));

                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("[{}] Log watcher encountered an error: {}", channelName, e.getMessage(), e);
        } finally {
            logger.info("[{}] Log watcher stopped.", channelName);
        }
    }

    /**
     * Reads log files matching channelName and groups them by their Listener character name.
     */
    public Map<String, List<File>> findMatchingFilesByListener(File dir, String channelName) {
        List<File> allMatching = findAllMatchingLogFiles(dir, channelName);
        Map<String, List<File>> map = new LinkedHashMap<>();

        for (File file : allMatching) {
            String listener = getOrResolveListenerCharacterName(file);
            if (listener == null || listener.trim().isEmpty()) {
                listener = "UNKNOWN";
            }
            map.computeIfAbsent(listener, k -> new ArrayList<>()).add(file);
        }

        return map;
    }

    /**
     * Resolves the Listener Character Name for a log file using cached Listener IDs from filename,
     * or parses the file header and updates the cache on first access.
     */
    public static String getOrResolveListenerCharacterName(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        String listenerId = parseListenerIdFromFilename(file.getName());
        if (listenerId != null && LISTENER_ID_CACHE.containsKey(listenerId)) {
            return LISTENER_ID_CACHE.get(listenerId);
        }

        String listenerFromHeader = parseListenerFromHeader(file);
        if (listenerFromHeader != null && !listenerFromHeader.trim().isEmpty()) {
            if (listenerId != null) {
                LISTENER_ID_CACHE.put(listenerId, listenerFromHeader);
                logger.debug("Cached EVE Listener ID {} -> '{}'", listenerId, listenerFromHeader);
            }
            return listenerFromHeader;
        }

        return null;
    }

    /**
     * Parses the EVE Listener Character ID from the filename.
     * Example: "4CHIN_20260815_234720_2112000314.txt" -> "2112000314"
     */
    public static String parseListenerIdFromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        Matcher matcher = FILENAME_LISTENER_ID_PATTERN.matcher(filename);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parses the Listener character name from the header of an EVE Online chat log file.
     * Example: "  Listener:   Pilot Name" -> "Pilot Name"
     */
    public static String parseListenerFromHeader(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        try (BufferedReader reader = openBufferedReader(file)) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 25) {
                lineCount++;
                String cleaned = line.replace("\uFEFF", "").trim();
                if (cleaned.toLowerCase().startsWith("listener:")) {
                    String[] parts = cleaned.split(":", 2);
                    if (parts.length == 2) {
                        return parts[1].trim();
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading header from file {}: {}", file.getName(), e.getMessage());
        }

        return null;
    }

    /**
     * Reads new lines appended to a log file starting from startPointer byte position.
     * Returns the updated byte position after reading.
     */
    private long readNewLinesFromFile(File file, long startPointer, String channelName) {
        if (file == null || !file.exists()) {
            return startPointer;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength <= startPointer) {
                return startPointer;
            }

            raf.seek(startPointer);
            int bytesToRead = (int) (fileLength - startPointer);
            byte[] buffer = new byte[bytesToRead];
            raf.readFully(buffer);

            Charset charset = detectCharset(file, buffer, startPointer);
            String text = new String(buffer, charset);

            String[] lines = text.split("\r?\n");
            for (String line : lines) {
                logMessageProcessService.processLogMessage(channelName, line);
            }

            return fileLength;
        } catch (IOException e) {
            logger.error("[{}] Error reading appended lines from {}: {}", channelName, file.getName(), e.getMessage());
            return startPointer;
        }
    }

    private Charset detectCharset(File file, byte[] buffer, long startPointer) {
        if (startPointer == 0 && buffer.length >= 2) {
            if (buffer[0] == (byte) 0xFF && buffer[1] == (byte) 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (buffer.length >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }
        }

        // Check header of original file if reading mid-stream
        try (InputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[2];
            int read = fis.read(header);
            if (read >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
        } catch (IOException ignored) {
        }

        return StandardCharsets.UTF_8;
    }

    /**
     * Filters log files chronologically based on filename timestamp information,
     * skipping obsolete historical files whose session ended before the threshold cutoff timestamp.
     */
    public List<File> filterRelevantLogFiles(List<File> files, String channelName) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        String thresholdTimestamp = logMessageProcessService.getThresholdTimestamp(channelName);
        if (thresholdTimestamp == null || thresholdTimestamp.trim().isEmpty()) {
            return files;
        }

        List<File> relevantFiles = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            // Determine effective end timestamp of this session log file:
            // It ends either when the next session file starts, or at its lastModified time if it's the last file.
            String fileEndTimestamp = null;
            if (i < files.size() - 1) {
                fileEndTimestamp = parseTimestampFromFilename(files.get(i + 1).getName());
            }

            if (fileEndTimestamp == null) {
                fileEndTimestamp = formatFileLastModified(file);
            }

            // Include file if its end timestamp is at or after the threshold cutoff timestamp
            if (fileEndTimestamp == null || fileEndTimestamp.compareTo(thresholdTimestamp) >= 0) {
                relevantFiles.add(file);
            } else {
                logger.trace("[{}] Skipping historical log file {} (ended at {} before threshold {})",
                        channelName, file.getName(), fileEndTimestamp, thresholdTimestamp);
            }
        }

        return relevantFiles;
    }

    /**
     * Parses the session start timestamp from an EVE chat log filename.
     * Example: "Degen Gambling_20260815_191406_2112000314.txt" -> "2026.08.15 19:14:06"
     */
    public static String parseTimestampFromFilename(String filename) {
        if (filename == null) {
            return null;
        }

        Matcher matcher = FILENAME_TIMESTAMP_PATTERN.matcher(filename);
        if (matcher.matches()) {
            String year = matcher.group(1);
            String month = matcher.group(2);
            String day = matcher.group(3);
            String hour = matcher.group(4);
            String minute = matcher.group(5);
            String second = matcher.group(6);
            return String.format("%s.%s.%s %s:%s:%s", year, month, day, hour, minute, second);
        }
        return null;
    }

    private static String formatFileLastModified(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        return sdf.format(new Date(file.lastModified()));
    }

    private List<File> findAllMatchingLogFiles(File dir, String channelName) {
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> {
            String lowerName = name.toLowerCase();
            String lowerChannel = channelName.toLowerCase();
            return (lowerName.startsWith(lowerChannel + "_") || lowerName.startsWith(lowerChannel + " ")) && lowerName.endsWith(".txt");
        });

        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort((f1, f2) -> {
            String ts1 = parseTimestampFromFilename(f1.getName());
            String ts2 = parseTimestampFromFilename(f2.getName());
            if (ts1 != null && ts2 != null) {
                return ts1.compareTo(ts2);
            }
            return Long.compare(f1.lastModified(), f2.lastModified());
        });

        return list;
    }

    /**
     * Opens a BufferedReader with automatic UTF-16LE / UTF-8 BOM detection.
     */
    public static BufferedReader openBufferedReader(File file) throws IOException {
        InputStream rawStream = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(rawStream);
        bis.mark(4);

        byte[] bom = new byte[4];
        int bytesRead = bis.read(bom, 0, 4);
        bis.reset();

        Charset charset = StandardCharsets.UTF_8;
        if (bytesRead >= 2 && (bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE)) {
            charset = StandardCharsets.UTF_16LE;
        } else if (bytesRead >= 3 && (bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)) {
            charset = StandardCharsets.UTF_8;
        }

        return new BufferedReader(new InputStreamReader(bis, charset));
    }
}
