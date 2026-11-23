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
 * Opens a directory browser starting at the storage root, so every volume
 * (internal storage and SD card) can be browsed and any folder can be
 * picked as the container workdir. The chosen absolute path is saved to
 * files/home/.juliaterm/workdir, where the 'julia' and 'notebook' wrappers
 * pick it up at the next start and bind the folder to /workdir in the
 * Debian proot container (and cd there).
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

    /** Storage root; lists all volumes (internal storage, SD card, ...). */
    private static final String BASE_DIR = "/storage";

    /** Where the choice is stored, relative to the files dir (= $PREFIX). */
    private static final String WORKDIR_REL_PATH = "home/.juliaterm/workdir";

    /** Current picker dialog, so navigating dismisses the previous level. */
    private static AlertDialog sDialog;

    private JuliaTermWorkdirDialog() {
    }

    /** Show the picker. Call from the drawer's "Select workdir" button. */
    public static void show(Activity activity) {
        File base = new File(BASE_DIR);
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
        final boolean atRoot = dir.getAbsolutePath().equals(base.getAbsolutePath());

        final List<File> targets = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries != null) {
            List<String> names = new ArrayList<>();
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
            Collections.sort(names);
            for (String name : names) {
                File target = new File(dir, name);
                if (atRoot) {
                    if (name.equals("emulated")) {
                        // Jump straight into the internal volume.
                        File inner = new File(dir, "emulated/0");
                        targets.add(inner.isDirectory() ? inner : target);
                        labels.add("Internal storage");
                        continue;
                    }
                    // Any other entry below /storage is a physical volume
                    // (SD card, USB OTG); label it as such.
                    targets.add(target);
                    labels.add("SD card (" + name + ")");
                    continue;
                }
                targets.add(target);
                labels.add(name);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(titleFor(dir, base));
        if (targets.isEmpty()) {
            builder.setMessage(atRoot
                ? "No storage volumes found."
                : "No subfolders here. You can still select this folder.");
        } else {
            builder.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_list_item_1, labels), (dialog, which) -> {
                File child = targets.get(which);
                if (child.canRead()) {
                    showLevel(activity, child, base);
                } else {
                    toast(activity, "Cannot open " + child.getName()
                        + " (not readable).");
                }
            });
        }

        if (atRoot) {
            // A volume root itself makes no sense as a workdir; pick a
            // volume first.
            if (!targets.isEmpty()) {
                builder.setMessage("Choose a storage location.");
            }
        } else {
            builder.setPositiveButton("Select here", (dialog, which) ->
                saveWorkdir(activity, dir));
            File up = upFrom(dir, base);
            if (up != null) {
                builder.setNeutralButton("Up", (dialog, which) ->
                    showLevel(activity, up, base));
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null);

        if (sDialog != null && sDialog.isShowing()) {
            sDialog.dismiss();
        }
        sDialog = builder.show();
    }

    /**
     * The level above dir, or null at the root. The internal volume's
     * parent (/storage/emulated) is skipped so "Up" jumps straight back
     * to the volume list.
     */
    private static File upFrom(File dir, File base) {
        File parent = dir.getParentFile();
        if (parent == null) {
            return null;
        }
        if (parent.getAbsolutePath().equals("/storage/emulated")) {
            parent = base;
        }
        if (!parent.getAbsolutePath().equals(base.getAbsolutePath())
                && !parent.getAbsolutePath().startsWith(base.getAbsolutePath())) {
            return null;
        }
        return parent;
    }

    private static String titleFor(File dir, File base) {
        if (dir.getAbsolutePath().equals(base.getAbsolutePath())) {
            return "Storage";
        }
        return dir.getAbsolutePath();
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
