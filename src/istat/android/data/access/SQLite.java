package istat.android.data.access;

public class SQLite {
    public static SQLiteSelect select(Class<?> clazz) {
        return null;//new SQLiteSelect(clazz);
    }

    public static SQLiteUpdate update(Class<?> clazz) {
        return null;//new SQLiteUpdate(clazz);
    }

    public static SQLiteDelete delete(Class<?> clazz) {
        return null;// new SQLiteDelete(clazz);
    }

    public static SQLiteInsert insert(Object entity) {
        return null;//new SQLiteInsert().insert(entity);
    }

    public static class SELECT {
        public static SQLiteSelect from(Class<?> clazz) {
            return null;// new SQLiteSelect(clazz);
        }
    }

    public static class UPDATE {
        public static SQLiteUpdate table(Class<?> clazz) {
            return null;//new SQLiteUpdate(clazz);
        }
    }

    public static class DELETE {
        public static SQLiteDelete from(Class<?> clazz) {
            return null;//new SQLiteDelete(clazz);
        }
    }

    public static class INSERT {
        public static SQLiteInsert entity(QueryAble entity) {
            return new SQLiteInsert().insert(entity);
        }
    }
}
