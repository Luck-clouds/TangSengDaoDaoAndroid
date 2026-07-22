package com.chat.base.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Xml;

import androidx.collection.LruCache;

import com.chat.base.WKBaseApplication;
import com.chat.base.utils.WKLogUtils;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class EmojiManager {

    private final String EMOT_DIR = "emoji/";

    // max cache size
    private final int CACHE_MAX_SIZE = 1024;

    private Pattern pattern;

    // default entries
    private final List<Entry> defaultEntries = new ArrayList<>();
    // text to entry
    private final Map<String, Entry> text2entry = new HashMap<>();
    // asset bitmap cache, key: asset path
    private LruCache<String, Bitmap> drawableCache;

    private String patternStr = "";
    private boolean initialized = false;

    private EmojiManager() {

    }

    private static class EmojiManagerBinder {
        final static EmojiManager emoji = new EmojiManager();
    }

    public static EmojiManager getInstance() {
        return EmojiManagerBinder.emoji;
    }

    public synchronized void init() {
        if (initialized) {
            return;
        }

        Context context = WKBaseApplication.getInstance().getContext();

        defaultEntries.clear();
        text2entry.clear();
        patternStr = "";
        load(context, EMOT_DIR + "emoji.xml");
        pattern = makePattern();
        drawableCache = new LruCache<String, Bitmap>(CACHE_MAX_SIZE) {
            @Override
            protected void entryRemoved(boolean evicted, @NotNull String key, @NotNull Bitmap oldValue, Bitmap newValue) {
                if (oldValue != newValue)
                    oldValue.recycle();
            }
        };
        initialized = true;

    }

    private static class Entry {
        String text;
        String assetPath;
        String id;

        Entry(String id, String text, String assetPath) {
            this.text = text;
            this.id = id;
            this.assetPath = assetPath;
        }
    }

    public int getDisplayCount() {
        return defaultEntries.size();
    }

    public Drawable getDisplayDrawable(Context context, int index) {
        Entry entry = index >= 0 && index < defaultEntries.size() ? defaultEntries.get(index) : null;
        String text = entry != null ? entry.text : null;
        return text == null ? null : getDrawable(context, text);
    }

    public String getDisplayText(int index) {
        Entry entry = index >= 0 && index < defaultEntries.size() ? defaultEntries.get(index) : null;
        return entry != null ? entry.text : null;
    }

    public synchronized Pattern getPattern() {
        if (pattern == null) {
            pattern = makePattern();
        }
        return pattern;
    }

    public Drawable getDrawableWithTag(Context context, String tag) {
        Drawable drawable = null;
        for (int i = 0; i < defaultEntries.size(); i++) {
            Entry entry = defaultEntries.get(i);
            if (entry != null && entry.id.equals(tag)) {
                drawable = getDrawable(context, entry.text);
                break;
            }
        }
        return drawable;
    }
    public EmojiEntry getEmojiWithTag(String tag){
        EmojiEntry entry = null;
        for (int i = 0; i < defaultEntries.size(); i++) {
            Entry defaultEntry = defaultEntries.get(i);
            if (defaultEntry != null && defaultEntry.id.equals(tag)) {
                entry = new EmojiEntry(defaultEntry.id,defaultEntry.text,defaultEntry.assetPath);
                break;
            }
        }
        return entry;
    }
    public EmojiEntry getEmojiEntry(String text) {
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }
        return new EmojiEntry(entry.id, entry.text, entry.assetPath);
    }

    public Drawable getDrawable(Context context, String text) {
        Entry entry = text2entry.get(text);
        if (entry == null) {
            return null;
        }
        if (drawableCache == null) {
            drawableCache = new LruCache<String, Bitmap>(CACHE_MAX_SIZE) {
                @Override
                protected void entryRemoved(boolean evicted, @NotNull String key, @NotNull Bitmap oldValue, Bitmap newValue) {
                    if (oldValue != newValue)
                        oldValue.recycle();
                }
            };
        }

        Bitmap cache = drawableCache.get(entry.assetPath);
        if (cache == null) {
            cache = loadAssetBitmap(context, entry.assetPath);
        }
        return new BitmapDrawable(context.getResources(), cache);
    }

    //
    // internal
    //

    private Pattern makePattern() {
        return Pattern.compile(patternOfDefault());
    }

    private String patternOfDefault() {
//        return "\\[[^\\[]{1,10}\\]";
        if (TextUtils.isEmpty(patternStr)) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0, size = defaultEntries.size(); i < size; i++) {
                Entry entry = defaultEntries.get(i);
                if (entry == null || TextUtils.isEmpty(entry.text)) {
                    continue;
                }
                if (!sb.toString().endsWith("(")) {
                    sb.append("|");
                }
                sb.append(Pattern.quote(entry.text));
            }
            if (sb.toString().endsWith("(")) {
                patternStr = "(?!)";
            } else {
                sb.append(")");
                patternStr = sb.toString();
            }
        }
        return patternStr;
        // return "[^\\u0000-\\uFFFF]";
    }

    private Bitmap loadAssetBitmap(Context context, String assetPath) {
        InputStream is = null;
        try {
            Resources resources = context.getResources();
            Options options = new Options();
            options.inDensity = DisplayMetrics.DENSITY_HIGH;
            options.inScreenDensity = resources.getDisplayMetrics().densityDpi;
            options.inTargetDensity = resources.getDisplayMetrics().densityDpi;
            is = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is, new Rect(), options);
            if (bitmap != null) {
                drawableCache.put(assetPath, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            WKLogUtils.e("解析emoji错误");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void load(Context context, String xmlPath) {
        new EntryLoader().load(context, xmlPath);
    }

    //
    // load emoticons from asset
    //
    private class EntryLoader extends DefaultHandler {
        private String catalog = "";

        void load(Context context, String assetPath) {
            InputStream is = null;
            try {
                is = context.getAssets().open(assetPath);
                Xml.parse(is, Xml.Encoding.UTF_8, this);
            } catch (IOException | SAXException e) {
                e.printStackTrace();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (localName.equals("Catalog")) {
                catalog = attributes.getValue(uri, "Title");
                if (TextUtils.isEmpty(catalog)) {
                    catalog = attributes.getValue("Title");
                }
            } else if (localName.equals("Emoticon")) {
                String tag = attributes.getValue(uri, "Tag");
                String id = attributes.getValue(uri, "ID");
                String fileName = attributes.getValue(uri, "File");
                if (TextUtils.isEmpty(tag)) {
                    tag = attributes.getValue("Tag");
                }
                if (TextUtils.isEmpty(id)) {
                    id = attributes.getValue("ID");
                }
                if (TextUtils.isEmpty(fileName)) {
                    fileName = attributes.getValue("File");
                }
                if (TextUtils.isEmpty(catalog) || TextUtils.isEmpty(tag) || TextUtils.isEmpty(id) || TextUtils.isEmpty(fileName)) {
                    return;
                }
                Entry entry = new Entry(id, tag, EMOT_DIR + catalog + "/" + fileName);
                text2entry.put(entry.text, entry);
                if (catalog.equals("default")) {
                    defaultEntries.add(entry);
                }
            }
        }
    }

    public boolean isHeart(String tag) {
        if (!text2entry.containsKey(tag)) return false;
        return Objects.requireNonNull(text2entry.get(tag)).id.equals("2_0")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_1")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_2")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_3")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_4")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_5")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_6")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_7")
                || Objects.requireNonNull(text2entry.get(tag)).id.equals("2_8");
    }


    public List<EmojiEntry> getEmojiWithType(String type) {
        List<EmojiEntry> list = new ArrayList<>();
        for (int i = 0, size = defaultEntries.size(); i < size; i++) {
            Entry defaultEntry = defaultEntries.get(i);
            if (defaultEntry == null || TextUtils.isEmpty(defaultEntry.id) || TextUtils.isEmpty(defaultEntry.text)) {
                continue;
            }
            if (defaultEntry.id.contains("color")) {
                continue;
            }
            boolean isAdd = true;
            for (EmojiEntry entry : list) {
                if (entry.getText().equals(defaultEntry.text)) {
                    isAdd = false;
                    break;
                }
            }
            if (isAdd) {
                if (defaultEntry.id.startsWith(type)) {
                    EmojiEntry entry = new EmojiEntry(defaultEntry.id, defaultEntry.text, defaultEntry.assetPath);
                    list.add(entry);
                }
            }
        }
        return list;
    }
}
