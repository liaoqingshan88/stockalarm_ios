package alarm.stock;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashCatcherApp extends Application {
    public static final String CRASH_FILE_NAME = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    writeCrashToFile(getApplicationContext(), t, e);
                } catch (Throwable ignore) {
                }

                if (previous != null) {
                    previous.uncaughtException(t, e);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    private static void writeCrashToFile(Context context, Thread t, Throwable e) throws Exception {
        File dir = context.getFilesDir();
        File crashFile = new File(dir, CRASH_FILE_NAME);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("time=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        pw.println("thread=" + (t != null ? t.getName() : "null"));
        pw.println("process=" + context.getPackageName() + " pid=" + Process.myPid());
        pw.println("sdk=" + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")");
        pw.println("device=" + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println();
        if (e != null) {
            e.printStackTrace(pw);
        } else {
            pw.println("Throwable is null");
        }
        pw.flush();

        FileOutputStream fos = new FileOutputStream(crashFile, false);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        try {
            osw.write(sw.toString());
            osw.flush();
        } finally {
            try { osw.close(); } catch (Exception ignore) {}
            try { fos.close(); } catch (Exception ignore) {}
        }
    }
}

