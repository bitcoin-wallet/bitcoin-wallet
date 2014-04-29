package de.schildbach.wallet.util;

import android.app.Activity;
import android.content.Context;
import android.text.ClipboardManager;

/**
 * Created by hank on 1/1/14.
 * An abstract clipboard manager class that deals with clipboards from old and new Android versions
 */
@SuppressWarnings("deprecation")
public class AbstractClipboardManager {
    Object clipboardManager;
    int sdk = android.os.Build.VERSION.SDK_INT;

    public AbstractClipboardManager(Activity activity) {
        // Get the clipboard manager, no matter what it is.
        clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    // Labeled text copy - the label is only used on newer versions of Android
    // It is ignored in old versions.
    public void setText(String label, String text) {
        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
            ((ClipboardManager)this.clipboardManager).setText(text);
        } else {
            android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
            ((android.content.ClipboardManager)this.clipboardManager).setPrimaryClip(clip);
        }
    }

    public CharSequence getText() {
        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
            return ((ClipboardManager)this.clipboardManager).getText();
        } else {
            return ((android.content.ClipboardManager)this.clipboardManager).getText();
        }
    }

    public boolean hasText() {
        if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
            return ((ClipboardManager)this.clipboardManager).hasText();
        } else {
            return ((android.content.ClipboardManager)this.clipboardManager).hasText();
        }
    }
}
