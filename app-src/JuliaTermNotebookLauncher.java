package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * JuliaTerm: waits for the Pluto notebook server (the 'notebook' command)
 * to accept connections on localhost:8889, then opens the page in the
 * system browser. The polling runs on a background thread; the browser
 * open happens on the UI thread. If the server is already up, the
 * browser opens immediately (the button doubles as "open notebooks").
 *
 * The session itself is started by the drawer button hook in
 * build-juliaterm.sh (a fresh terminal session via addNewSession plus a
 * "notebook\n" write to the pty); this class only handles the waiting
 * and the browser handoff, so it needs nothing but an Activity.
 */
public final class JuliaTermNotebookLauncher {

    private static final String SERVER_URL = "http://localhost:8889";
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8889;
    private static final long TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 2000L;

    /** True while one launcher is already waiting for the server. */
    private static volatile boolean sWaiting = false;

    private JuliaTermNotebookLauncher() {
    }

    public static void launch(final Activity activity) {
        if (sWaiting) {
            toast(activity, "Already waiting for Pluto. The browser opens"
                + " as soon as the server is up.");
            return;
        }
        sWaiting = true;
        toast(activity, "Waiting for Pluto on " + SERVER_URL + ". The browser"
            + " opens when the server is up; first-time setup installs Pluto"
            + " and can take a few minutes.");
        new Thread() {
            @Override
            public void run() {
                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                boolean up = false;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 1500);
                        socket.close();
                        up = true;
                        break;
                    } catch (Exception ignore) {
                        try {
                            Thread.sleep(POLL_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                final boolean serverUp = up;
                activity.runOnUiThread(() -> {
                    sWaiting = false;
                    if (serverUp) {
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(SERVER_URL)));
                        } catch (Exception e) {
                            toast(activity, "Pluto is up on " + SERVER_URL
                                + " - open it in your browser.");
                        }
                    } else {
                        toast(activity, "Pluto did not come up within"
                            + " 10 minutes. Check the terminal session for"
                            + " errors.");
                    }
                });
            }
        }.start();
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
