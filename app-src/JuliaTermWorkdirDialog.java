package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.widget.ArrayAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * JuliaTerm: in-app workdir picker.
 *
 * Opens a volume chooser (internal storage, SD card; via /storage listing
 * with an android StorageManager fallback), then a directory browser in
 * the chosen volume. Any folder can be picked as the container workdir;
 * its absolute path is saved to files/home/.juliaterm/workdir, where the
 * 'julia' and 'notebook' wrappers pick it up at the next start and bind
 * the folder to /workdir in the Debian proot container (and cd there).
 *
 * v0.3.2 lesson: do NOT gate on File("/storage") being listable - on
 * several devices (seen on /e/OS, Fairphone 6) the storage root itself
 * is not writable/listable for apps even with all-files access, while
 * the volumes below it are fully accessible. Probe volumes directly.
 *
 * This file is copied into the tree BEFORE the generator's name
 * replacement runs (see build-juliaterm.sh), so the package line below
 * and this file's directory are migrated together with everything else.
 * The bare identifier TermuxActivity is intentionally stable: the name
 * replacement only rewrites the dotted/underscore/slash package path
 * forms and quoted or XML-text occurrences of the termux name, not
 * plain Java identifiers, so referencing it is safe.
 */
public final class JuliaTermWorkdirDialog {

    private static final String INTERNAL_DIR = "/storage/emulated/0";
    private static final String STORAGE_ROOT = "/storage";

    /** Where the choice is stored, relative to the files dir (= $PREFIX). */
    private static final String WORKDIR_REL_PATH = "home/.juliaterm/workdir";

    /** Current picker dialog, so navigating dismisses the previous level. */
    private static AlertDialog sDialog;

    /** Volume list for the chooser level (refreshed on every show()). */
    private static List<File> sVolumes = new ArrayList<>();

    private JuliaTermWorkdirDialog() {
    }

    /** Show the picker. Call from the drawer's "Select workdir" button. */
    public static void show(Activity activity) {
        sVolumes = listVolumes(activity);
        if (sVolumes.isEmpty()) {
            if (activity instanceof TermuxActivity) {
                ((TermuxActivity) activity).requestStoragePermission(false);
            }
            toast(activity, "Shared storage is not accessible yet. Grant the"
                + " storage permission (or all-files access) for JuliaTerm in"
                + " Android settings, then open the picker again.");
            return;
        }
        showVolumes(activity);
    }

    /** Long-press on the drawer button: forget the saved workdir. */
    public static void clear(Activity activity) {
        File file = new File(activity.getFilesDir(), WORKDIR_REL_PATH);
        if (file.exists() && file.delete()) {
            toast(activity, "Workdir cleared; the next 'julia' starts in /root.");
        } else {
            toast(activity, "No workdir saved yet.");
        }
    }

    /**
     * Accessible volumes: the internal one, then everything listed under
     * /storage (if the root is listable), then anything StorageManager
     * reports (API 30+) that is not already in the list.
     */
    private static List<File> listVolumes(Activity activity) {
        List<File> volumes = new ArrayList<>();
        File internal = new File(INTERNAL_DIR);
        if (internal.isDirectory() && internal.canRead()) {
            volumes.add(internal);
        }
        File[] children = new File(STORAGE_ROOT).listFiles();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                if (name.equals("emulated") || name.equals("self")) {
                    continue;
                }
                if (child.isDirectory() && !containsPath(volumes, child)) {
                    volumes.add(child);
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                StorageManager sm =
                    (StorageManager) activity.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume volume : sm.getStorageVolumes()) {
                        File dir = volume.getDirectory();
                        if (dir == null
                                || !dir.getAbsolutePath().startsWith(STORAGE_ROOT + "/")
                                || !dir.isDirectory()
                                || !dir.canRead()) {
                            continue;
                        }
                        if (!containsPath(volumes, dir)) {
                            volumes.add(dir);
                        }
                    }
                }
            } catch (Exception ignore) {
                // StorageManager is only a fallback; ignore any failure.
            }
        }
        return volumes;
    }

    private static boolean containsPath(List<File> volumes, File dir) {
        for (File volume : volumes) {
            if (volume.getAbsolutePath().equals(dir.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    private static String labelFor(File volume) {
        if (volume.getAbsolutePath().equals(INTERNAL_DIR)) {
            return "Internal storage";
        }
        return "SD card (" + volume.getName() + ")";
    }

    /** First level: the volume chooser. */
    private static void showVolumes(final Activity activity) {
        final List<File> volumes = sVolumes;
        List<String> labels = new ArrayList<>();
        for (File volume : volumes) {
            labels.add(labelFor(volume));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Storage");
        builder.setAdapter(new ArrayAdapter<>(activity,
            android.R.layout.simple_list_item_1, labels), (dialog, which) ->
                showLevel(activity, volumes.get(which), volumes.get(which)));

        builder.setNegativeButton(android.R.string.cancel, null);
        if (sDialog != null && sDialog.isShowing()) {
            sDialog.dismiss();
        }
        sDialog = builder.show();
    }

    /** Browser level inside one volume. Back out of the volume returns
     *  to the chooser. */
    private static void showLevel(final Activity activity, final File dir, final File volume) {
        final List<File> targets = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    targets.add(entry);
                    labels.add(entry.getName());
                }
            }
        }
        // Keep the label/target pairs sorted by label.
        for (int i = 0; i < labels.size(); i++) {
            for (int j = i + 1; j < labels.size(); j++) {
                if (labels.get(j).compareTo(labels.get(i)) < 0) {
                    String ls = labels.set(i, labels.get(j));
                    labels.set(j, ls);
                    File fs = targets.set(i, targets.get(j));
                    targets.set(j, fs);
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(dir.getAbsolutePath());
        if (targets.isEmpty()) {
            builder.setMessage("No subfolders here. You can still select this folder.");
        } else {
            builder.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_1, labels), (dialog, which) -> {
                File child = targets.get(which);
                if (child.canRead()) {
                    showLevel(activity, child, volume);
                } else {
                    toast(activity, "Cannot open " + child.getName()
                        + " (not readable).");
                }
            });
        }

        builder.setPositiveButton("Select here", (dialog, which) ->
            saveWorkdir(activity, dir));
        if (dir.getAbsolutePath().equals(volume.getAbsolutePath())) {
            builder.setNeutralButton("Up", (dialog, which) ->
                showVolumes(activity));
        } else {
            File parent = dir.getParentFile();
            final File up = parent != null
                && parent.getAbsolutePath().startsWith(volume.getAbsolutePath())
                ? parent : null;
            if (up != null) {
                builder.setNeutralButton("Up", (dialog, which) ->
                    showLevel(activity, up, volume));
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null);

        if (sDialog != null && sDialog.isShowing()) {
            sDialog.dismiss();
        }
        sDialog = builder.show();
    }

    private static void saveWorkdir(Activity activity, File dir) {
        try {
            File file = new File(activity.getFilesDir(), WORKDIR_REL_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                toast(activity, "Could not create " + parent.getAbsolutePath());
                return;
            }
            FileOutputStream output = new FileOutputStream(file);
            try {
                output.write(dir.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }
            toast(activity, "Workdir: " + dir.getAbsolutePath()
                + ". The next 'julia' or 'notebook' starts there (/workdir).");
        } catch (Exception e) {
            toast(activity, "Could not save the workdir: " + e.getMessage());
        }
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
