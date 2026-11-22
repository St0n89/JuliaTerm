package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JuliaTerm: in-app workdir picker.
 *
 * Opens a small directory browser rooted at the phone's Documents folder
 * (fallback: the shared storage root). The chosen absolute path is saved
 * to files/home/.juliaterm/workdir, where the 'julia' and 'notebook'
 * wrappers pick it up at the next start and bind the folder to /workdir
 * in the Debian proot container (and cd there).
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

    private static final String BASE_DIR = "/storage/emulated/0/Documents";
    private static final String FALLBACK_DIR = "/storage/emulated/0";
    private static final String WORKDIR_REL_PATH = "home/.juliaterm/workdir";

    /** Current picker dialog, so navigating dismisses the previous level. */
    private static AlertDialog sDialog;

    private JuliaTermWorkdirDialog() {
    }

    /** Show the picker. Call from the drawer's "Select workdir" button. */
    public static void show(Activity activity) {
        File base = new File(BASE_DIR);
        if (!base.isDirectory() || !base.canRead()) {
            base = new File(FALLBACK_DIR);
        }
        if (!base.isDirectory() || !base.canRead()) {
            if (activity instanceof TermuxActivity) {
                ((TermuxActivity) activity).requestStoragePermission(false);
            }
            toast(activity, "Shared storage is not accessible yet. Grant the"
                + " storage permission (or all-files access) for JuliaTerm in"
                + " Android settings, then open the picker again.");
            return;
        }
        showLevel(activity, base, base);
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

    private static void showLevel(final Activity activity, final File dir, final File base) {
        List<String> names = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
        }
        Collections.sort(names);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(titleFor(dir, base));
        if (!names.isEmpty()) {
            final String[] items = names.toArray(new String[0]);
            builder.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_1, items), (dialog, which) -> {
                File child = new File(dir, items[which]);
                if (child.canRead()) {
                    showLevel(activity, child, base);
                } else {
                    toast(activity, "Cannot open " + child.getName()
                        + " (not readable).");
                }
            });
        } else {
            builder.setMessage("No subfolders here. You can still select this folder.");
        }

        builder.setPositiveButton("Select here", (dialog, which) ->
            saveWorkdir(activity, dir));
        File parent = dir.getParentFile();
        if (parent != null
                && parent.getAbsolutePath().startsWith(base.getAbsolutePath())) {
            builder.setNeutralButton("Up", (dialog, which) ->
                showLevel(activity, parent, base));
        }
        builder.setNegativeButton(android.R.string.cancel, null);

        if (sDialog != null && sDialog.isShowing()) {
            sDialog.dismiss();
        }
        sDialog = builder.show();
    }

    private static String titleFor(File dir, File base) {
        String path = dir.getAbsolutePath();
        if (path.equals(base.getAbsolutePath())) {
            return path.equals(BASE_DIR) ? "Documents" : "Phone storage";
        }
        return path;
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
