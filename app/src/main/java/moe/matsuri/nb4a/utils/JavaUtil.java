package moe.matsuri.nb4a.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import io.nekohasekai.sagernet.BuildConfig;
import kotlin.text.StringsKt;

import java.lang.reflect.Method;

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
