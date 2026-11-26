package keyv;

final class Lang {
    enum Code { FR, EN }

    private static Code code = Code.FR;

    private Lang() {
    }

    static Code code() {
        return code;
    }

    static boolean english() {
        return code == Code.EN;
    }

    static void set(Code next) {
        code = next == null ? Code.FR : next;
    }

    static String t(String french, String english) {
        return code == Code.EN ? english : french;
    }
}
