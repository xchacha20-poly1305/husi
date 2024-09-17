package moe.matsuri.nb4a.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import io.nekohasekai.sagernet.BuildConfig;
import kotlin.text.StringsKt;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaUtil {

    /**
     * @noinspection deprecation
     */
    public static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setLenient()
            .disableHtmlEscaping()
            .create();
    // The encoded character of each character escape.
    // This array functions as the keys of a sorted map, from encoded characters to decoded characters.
    static final char[] ENCODED_ESCAPES = {'\"', '\'', '\\', 'b', 'f', 'n', 'r', 't'};
    // The decoded character of each character escape.
    // This array functions as the values of a sorted map, from encoded characters to decoded characters.
    static final char[] DECODED_ESCAPES = {'\"', '\'', '\\', '\b', '\f', '\n', '\r', '\t'};
    // A pattern that matches an escape.
    // What follows the escape indicator is captured by group 1=character 2=octal 3=Unicode.
    static final Pattern PATTERN = Pattern.compile("\\\\(?:([btnfr\"'\\\\])|((?:[0-3]?[0-7])?[0-7])|u+(\\p{XDigit}{4}))");

    // Process the return of webView.evaluateJavascript
    public static String unescapeString(CharSequence encodedString) {
        Matcher matcher = PATTERN.matcher(encodedString);
        StringBuffer decodedString = new StringBuffer();
        // Find each escape of the encoded string in succession.
        while (matcher.find()) {
            char ch;
            if (matcher.start(1) >= 0) {
                // Decode a character escape.
                ch = DECODED_ESCAPES[Arrays.binarySearch(ENCODED_ESCAPES, matcher.group(1).charAt(0))];
            } else if (matcher.start(2) >= 0) {
                // Decode an octal escape.
                ch = (char) (Integer.parseInt(matcher.group(2), 8));
            } else /* if (matcher.start(3) >= 0) */ {
                // Decode a Unicode escape.
                ch = (char) (Integer.parseInt(matcher.group(3), 16));
            }
            // Replace the escape with the decoded character.
            matcher.appendReplacement(decodedString, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        // Append the remainder of the encoded string to the decoded string.
        // The remainder is the longest suffix of the encoded string such that the suffix contains no escapes.
        matcher.appendTail(decodedString);
        return new String(decodedString);
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static void tryLockOrRecreateFile(File file) {
        try {
            FileLock tryLock = new RandomAccessFile(file, "rw").getChannel().tryLock();
            if (tryLock != null) {
                tryLock.close();
            } else {
                createFile(file, file.delete());
            }
        } catch (Exception e) {
            e.printStackTrace();
            boolean deleted = false;
            if (file.exists()) {
                deleted = file.delete();
            }
            createFile(file, deleted);
        }
    }

    private static void createFile(File file, boolean deleted) {
        try {
            if (deleted && !file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Old hutool Utils

    @SuppressLint("PrivateApi")
    public static String getProcessName() {
        if (Build.VERSION.SDK_INT >= 28)
            return Application.getProcessName();

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API

        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            String methodName = "currentProcessName";
            Method getProcessName = activityThread.getDeclaredMethod(methodName);
            return (String) getProcessName.invoke(null);
        } catch (Exception e) {
            return BuildConfig.APPLICATION_ID;
        }
    }

    public static boolean isNullOrBlank(String str) {
        return str == null || StringsKt.isBlank(str);
    }

    public static boolean isNotBlank(String str) {
        return !isNullOrBlank(str);
    }

    // gson

    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

}
