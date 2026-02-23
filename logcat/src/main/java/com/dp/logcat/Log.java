package com.dp.logcat;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Log implements Parcelable {
    private final int id;
    @NonNull private final String date;
    @NonNull private final String time;
    @Nullable private final Uid uid;
    @NonNull private final String pid;
    @NonNull private final String tid;
    @NonNull private final String priority;
    @NonNull private final String tag;
    @NonNull private final String msg;

    public Log(
            int id,
            @NonNull String date,
            @NonNull String time,
            @Nullable Uid uid,
            @NonNull String pid,
            @NonNull String tid,
            @NonNull String priority,
            @NonNull String tag,
            @NonNull String msg
    ) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.uid = uid;
        this.pid = pid;
        this.tid = tid;
        this.priority = priority;
        this.tag = tag;
        this.msg = msg;
    }

    // region Getters
    public int getId() { return id; }
    @NonNull public String getDate() { return date; }
    @NonNull public String getTime() { return time; }
    @Nullable public Uid getUid() { return uid; }
    @NonNull public String getPid() { return pid; }
    @NonNull public String getTid() { return tid; }
    @NonNull public String getPriority() { return priority; }
    @NonNull public String getTag() { return tag; }
    @NonNull public String getMsg() { return msg; }
    // endregion

    @NonNull
    public String metadataToString() {
        // Kotlin string interpolation "[$date $time $uid:$pid:$tid $priority/$tag]"
        // Note: uid may be null -> will print "null" similarly to Kotlin's default toString on null in interpolation.
        return "[" + date + " " + time + " " + uid + ":" + pid + ":" + tid + " " + priority + "/" + tag + "]";
    }

    @NonNull
    @Override
    public String toString() {
        return metadataToString() + "\n" + msg + "\n\n";
    }

    @NonNull
    public static Log parse(int id, @NonNull String metadata, @NonNull String msg) {

        final String trimmedMetadata = metadata.substring(1, metadata.length() - 1).trim();
        int startIndex = 0;

        int index = trimmedMetadata.indexOf(' ', startIndex);
        String date = trimmedMetadata.substring(startIndex, index);
        startIndex = index + 1;

        index = trimmedMetadata.indexOf(' ', startIndex);
        String time = trimmedMetadata.substring(startIndex, index);
        startIndex = index + 1;

        // skip spaces
        while (startIndex < trimmedMetadata.length() && trimmedMetadata.charAt(startIndex) == ' ') {
            startIndex++;
        }

        // Determine if UID exists by checking if the substring before '/' has exactly 2 ':' chars.
        int slashIndex = trimmedMetadata.indexOf('/', startIndex);
        String beforeSlash = trimmedMetadata.substring(startIndex, slashIndex);
        boolean hasUid = countChar(beforeSlash, ':') == 2;

        String uidStr = null;
        if (hasUid) {
            index = trimmedMetadata.indexOf(':', startIndex);
            uidStr = trimmedMetadata.substring(startIndex, index);
            startIndex = index + 1;

            // skip spaces
            while (startIndex < trimmedMetadata.length() && trimmedMetadata.charAt(startIndex) == ' ') {
                startIndex++;
            }
        }

        index = trimmedMetadata.indexOf(':', startIndex);
        String pid = trimmedMetadata.substring(startIndex, index);
        startIndex = index + 1;

        // skip spaces
        while (startIndex < trimmedMetadata.length() && trimmedMetadata.charAt(startIndex) == ' ') {
            startIndex++;
        }

        index = trimmedMetadata.indexOf(' ', startIndex);
        String tid = trimmedMetadata.substring(startIndex, index);
        startIndex = index + 1;

        index = trimmedMetadata.indexOf('/', startIndex);
        String priority = trimmedMetadata.substring(startIndex, index);
        startIndex = index + 1;

        String tag = trimmedMetadata.substring(startIndex).trim();

        return new Log(
                id,
                date,
                time,
                uidStr != null ? new Uid(uidStr) : null,
                pid,
                tid,
                priority,
                tag,
                msg
        );
    }

    private static int countChar(@NonNull String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    protected Log(@NonNull Parcel in) {
        id = in.readInt();
        date = readNonNullString(in, "date");
        time = readNonNullString(in, "time");
        uid = in.readInt() == 1 ? new Uid(in) : null;
        pid = readNonNullString(in, "pid");
        tid = readNonNullString(in, "tid");
        priority = readNonNullString(in, "priority");
        tag = readNonNullString(in, "tag");
        msg = readNonNullString(in, "msg");
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(date);
        dest.writeString(time);

        if (uid != null) {
            dest.writeInt(1);
            uid.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }

        dest.writeString(pid);
        dest.writeString(tid);
        dest.writeString(priority);
        dest.writeString(tag);
        dest.writeString(msg);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Log> CREATOR = new Creator<Log>() {
        @Override public Log createFromParcel(Parcel in) { return new Log(in); }
        @Override public Log[] newArray(int size) { return new Log[size]; }
    };

    @NonNull
    private static String readNonNullString(@NonNull Parcel in, @NonNull String fieldName) {
        String v = in.readString();
        if (v == null) throw new IllegalStateException(fieldName + " was null in Parcel");
        return v;
    }

    public static class Uid implements Parcelable {
        @NonNull private final String value;
        private final boolean isNum;

        public Uid(@NonNull String value) {
            this.value = value;
            this.isNum = isDigitsOnly(value);
        }

        @NonNull public String getValue() { return value; }
        public boolean isNum() { return isNum; }

        // region Parcelable
        protected Uid(@NonNull Parcel in) {
            value = readNonNullString(in, "Uid.value");
            isNum = isDigitsOnly(value);
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(value);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Uid> CREATOR = new Creator<Uid>() {
            @Override public Uid createFromParcel(Parcel in) { return new Uid(in); }
            @Override public Uid[] newArray(int size) { return new Uid[size]; }
        };
        // endregion

        @NonNull
        @Override
        public String toString() {
            return value;
        }

        private static boolean isDigitsOnly(@NonNull CharSequence cs) {
            int len = cs.length();
            if (len == 0) return false;
            for (int i = 0; i < len; i++) {
                char ch = cs.charAt(i);
                if (ch < '0' || ch > '9') return false;
            }
            return true;
        }
    }
}

final class LogPriority {
    private LogPriority() {}

    public static final String ASSERT = "A";
    public static final String DEBUG = "D";
    public static final String ERROR = "E";
    public static final String FATAL = "F";
    public static final String INFO = "I";
    public static final String VERBOSE = "V";
    public static final String WARNING = "W";
}
