package istat.android.data.access.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import istat.android.data.access.sqlite.utils.SQLiteParser;

public final class SQLite {
    static SQLiteDatabase
            lastOpenedDb;
    final static ConcurrentHashMap<String, SQLiteDataAccess> dbNameAccessPair = new ConcurrentHashMap<String, SQLiteDataAccess>();
    final static ConcurrentHashMap<String, SQLiteConnection> dbNameConnectionPair = new ConcurrentHashMap<String, SQLiteConnection>();

    private SQLite() {

    }

    public static SQLiteDatabase getLastOpenedDb() {
        return lastOpenedDb;
    }

    public static SQL from(SQLiteDatabase db) {
        lastOpenedDb = db;
        return new SQL(db);
    }

    public static SQL fromConnection(String dbName) throws Exception {
        return fromConnection(dbName, false);
    }

    public static SQL fromOneShotConnection(String dbName) throws Exception {
        return fromConnection(dbName, true);
    }

    public static SQL fromConnection(String dbName, boolean closeDataBaseOnExecute) throws Exception {
        SQLiteDataAccess access = findOrCreateConnectionAccess(dbName);
        SQL sql = SQLite.from(access.open());
        sql.setAutoClose(closeDataBaseOnExecute);
        return sql;
    }

    public static void addConnection(SQLiteConnection... connections) {
        for (SQLiteConnection launcher : connections) {
            addConnection(launcher, false);
        }
    }

    public static SQLiteDataAccess getAccess(String dbName) {
        try {
            return findOrCreateConnectionAccess(dbName);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static SQLiteDatabase getDataBase(String dbName) {
        try {
            SQLiteDataAccess access = findOrCreateConnectionAccess(dbName);
            if (access != null) {
                return access.open();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void addConnection(SQLiteConnection connection, boolean connectInstantly) {
        if (connectInstantly) {
            connect(connection);
        } else {
            dbNameConnectionPair.put(connection.dbName, connection);
        }
    }

    public static boolean removeConnection(String dbName) {
        boolean contain = dbNameAccessPair.containsKey(dbName);
        if (contain) {
            dbNameAccessPair.remove(dbName);
        }
        return contain;
    }

    public static boolean removeConnection(SQLiteConnection connection) {
        return removeConnection(connection.dbName);
    }

    public static void prepareSQL(String dbName, PrepareHandler handler) {
        prepareSQL(dbName, handler, false);
    }

    public static void prepareTransactionalSQL(String dbName, PrepareHandler handler) {
        prepareSQL(dbName, handler, true);
    }

    private static SQLiteDataAccess findOrCreateConnectionAccess(String dbName) throws IllegalAccessException {
        SQLiteDataAccess access = dbNameAccessPair.get(dbName);
        if (access != null && access.isOpened()) {
            try {
                access = access.cloneAccess();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        boolean hasLauncher = dbNameConnectionPair.containsKey(dbName);
        if (access != null) {
            return access;
        } else if (access == null && hasLauncher) {
            access = connect(dbNameConnectionPair.get(dbName));
            dbNameConnectionPair.remove(dbName);
        } else {
            throw new IllegalAccessException("Oups, no launcher is currently added dor Data base with name: " + dbName);
        }
        return access;
    }

    public static void prepareSQL(String dbName, PrepareHandler handler, boolean transactional) {
        SQLiteDatabase db = null;
        try {
            SQLiteDataAccess access = findOrCreateConnectionAccess(dbName);

            db = access.open();
            if (transactional) {
                db.beginTransaction();
            }
            SQL sql = SQLite.from(db);
            handler.onSQLReady(sql);
            if (transactional) {
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            handler.onSQLPrepareFail(e);

        } finally {
            if (transactional && db != null) {
                db.endTransaction();
                if (db.isOpen()) {
                    db.close();
                }
            }

        }
    }

    public static void prepareSQL(SQLiteConnection connection, PrepareHandler handler) {
        prepareSQL(connection, false, handler);
    }

    public static void prepareTransactionalSQL(SQLiteConnection connection, PrepareHandler handler) {
        prepareSQL(connection, true, handler);
    }

    public static void prepareSQL(SQLiteConnection connection, boolean transactional, PrepareHandler handler) {
        SQLiteDatabase db = null;
        try {
            SQLiteDataAccess access = connect(connection);
            db = access.open();
            if (transactional) {
                db.beginTransaction();
            }
            SQL sql = SQLite.from(db);
            handler.onSQLReady(sql);
            if (db.isOpen()) {
                db.close();
            }
            if (transactional) {
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            handler.onSQLPrepareFail(e);
        } finally {
            if (transactional && db != null) {
                db.endTransaction();
                if (db.isOpen()) {
                    db.close();
                }
            }
        }
    }

    public static void prepareSQL(SQLiteDatabase db, PrepareHandler handler) {
        prepareSQL(db, false, handler);
    }

    public static void prepareSQL(SQLiteDatabase db, boolean transactional, PrepareHandler handler) {
        try {
            if (transactional) {
                db.beginTransaction();
            }
            SQL sql = SQLite.from(db);
            handler.onSQLReady(sql);

            if (transactional) {
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (transactional && db != null) {
                db.endTransaction();
                if (db.isOpen()) {
                    db.close();
                }
            }
        }
    }

    //    public static SQL from(String dbName) throws IllegalAccessException {
//        SQLiteDataAccess access = findOrCreateConnectionAccess(dbName);
//        SQLiteDatabase db = access.open();
//        return from(db);
//    }
//
//    @Deprecated
//    public static SQL from(Context context, SQLiteConnection connection) {
//        SQLiteDatabase db = connect(connection).open();
//        return from(db);
//    }
    public static void addConnection(Context context, File file, boolean connectInstantly) {
        SQLiteConnection connection = SQLiteConnection.create(context, file, -1, null);
        if (connectInstantly) {
            connect(connection);
        } else {
            dbNameConnectionPair.put(connection.dbName, connection);
        }
    }

    public static SQL fromFile(File dbFile) throws Exception {
        return fromFile(dbFile, false);
    }

    public static SQL fromFile(File dbFile, boolean autoCloseAble) throws Exception {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        SQL sql = new SQL(db);
        sql.setAutoClose(autoCloseAble);
        return sql;
    }

    public static SQL fromUri(Uri dbUri) throws Exception {
        return fromUri(dbUri, false);
    }

    public static SQL fromUri(Uri dbUri, boolean autoCloseAble) throws Exception {
        File file = new File(dbUri.getPath());
        return fromFile(file, autoCloseAble);
    }

    public static void addConnection(Context context, File... file) {
        for (File f : file) {
            addConnection(context, f, false);
        }
    }

    public static SQLiteDataAccess connect(Context context, File file) {
        return connect(context, file, -1, null);
    }

    public static SQLiteDataAccess connect(Context context, File file, int version, BootDescription bootDescription) {
        SQLiteConnection connection = SQLiteConnection.create(context, file, version, bootDescription);
        return connect(connection);
    }

    public static SQLiteDataAccess connect(SQLiteConnection connection) {
        return connect(connection.context, connection.dbName, connection.dbVersion, connection);
    }

    public static void close(String connectionName) {
        SQLiteDataAccess access = dbNameAccessPair.get(connectionName);
        if (access != null) {
            access.close();
        }
    }

    public static void desconnect(String dbName) {
        SQLiteDataAccess access = dbNameAccessPair.get(dbName);
        if (access != null) {
            access.close();
            dbNameAccessPair.remove(dbName);
        }
    }

    public static SQLiteDataAccess connect(Context context, String dbName, int dbVersion, final BootDescription description) {
        SQLiteDataAccess access = new SQLiteDataAccess(context, dbName, dbVersion) {
            @Override
            public void onUpgradeDb(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (description != null) {
                    description.onUpgradeDb(db, oldVersion, newVersion);
                }
            }

            @Override
            public void onCreateDb(SQLiteDatabase db) {
                if (description != null) {
                    description.onCreateDb(db);
                }
            }
        };
        dbNameAccessPair.put(dbName, access);
        return access;
    }


    public static class SQL {
        SQLiteDatabase db;
        boolean autoClose = false;

        public void setAutoClose(boolean autoClose) {
            this.autoClose = autoClose;
        }

        SQL(SQLiteDatabase db) {
            this.db = db;
        }

        public SQLiteSelect select(Class<?> clazz) {
            return new SQLiteSelect(this, clazz);
        }

        public SQLiteSelect select(boolean distinct, Class<?> clazz) {
            SQLiteSelect select = new SQLiteSelect(this, clazz);
            return select.distinct(distinct);
        }

        public SQLiteSelect select(boolean distinct, Class<?>... clazz) {
            SQLiteSelect select = new SQLiteSelect(this, clazz);
            return select.distinct(distinct);
        }

        public SQLiteSelect select(Class<?>... clazz) {
            return new SQLiteSelect(this, clazz);
        }

        public SQLiteUpdate update(Class<?> clazz) {
            return new SQLiteUpdate(clazz, this);
        }

        public SQLiteDelete delete(Class<?> clazz) {
            return new SQLiteDelete(clazz, this);
        }


        public int delete(Object... object) {
            int count = 0;
            for (Object obj : object) {
                if (delete(obj)) {
                    count++;
                }
            }
            return count;
        }

        public boolean delete(Object object) {
            try {
                Class<?> cLass = object.getClass();
                SQLiteModel model = SQLiteModel.fromClass(cLass);
                return delete(cLass).where(model.getPrimaryFieldName()).equalTo(model.getPrimaryKey()).execute() > 0;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return false;
        }

//        public <T> SQLiteDelete delete(T... object) {
//            SQLiteDelete delete = null;
//            if (object != null && object.length > 0) {
//                try {
//                    for (T obj : object) {
//                        SQLiteModel model = SQLiteModel.fromObject(obj);
//                        if (delete == null) {
//                            delete = delete(model.getModelClass());
//                        }
//                        delete.where(model.getPrimaryFieldName()).equalTo(model.getPrimaryKey());
//                    }
//                    return delete;
//                } catch (Exception e) {
//
//                }
//            }
//            return null;
//        }
        //------------------------------------------
        public SQLiteInsert insert(Object entity) {
            SQLiteInsert insert = new SQLiteInsert(this);
            return insert.insert(entity);
        }

        public SQLiteInsert insert(Object... entity) {
            SQLiteInsert insert = new SQLiteInsert(this);
            return insert.insert(entity);
        }

        public <T> SQLiteInsert insert(List<T> entity) {
            SQLiteInsert insert = new SQLiteInsert(this);
            return insert.insert(entity);
        }

        public SQLitePersist persist(Object entity) {
            SQLitePersist persist = new SQLitePersist(this);
            return persist.persist(entity);
        }

        public SQLitePersist persist(Object... entity) {
            SQLitePersist persist = new SQLitePersist(this);
            return persist.persist(entity);
        }

        public <T> SQLitePersist persist(List<T> entity) {
            SQLitePersist persist = new SQLitePersist(this);
            return persist.persist(entity);
        }

        public <T> void replaces(List<T> entity) {
            try {
                if (entity != null && !entity.isEmpty()) {
                    delete(entity.get(0).getClass()).execute();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            SQLitePersist insert = new SQLitePersist(this);
            insert.persist(entity).execute();
        }

        //---------------------------------------------
        public SQLiteMerge merge(Object entity) {
            SQLiteMerge merge = new SQLiteMerge(this);
            return merge.merge(entity);
        }

        public SQLiteMerge merge(Object... entity) {
            SQLiteMerge merge = new SQLiteMerge(this);
            return merge.merge(entity);
        }

        public <T> SQLiteMerge merge(List<T> entity) {
            SQLiteMerge merge = new SQLiteMerge(this);
            return merge.merge(entity);
        }
        //---------------------------------------------

        public void executeStatements(List<String> statements) {
            for (String ask : statements) {
                db.execSQL(ask);
            }
            if (autoClose) {
                db.close();
            }
        }

        public void executeStatements(String... statements) {
            for (String ask : statements) {
                db.execSQL(ask);
            }
            if (autoClose) {
                db.close();
            }
        }

        public void executeSQLScript(InputStream sqlFileInputStream) throws IOException {
            List<String> statements = SQLiteParser.parseSqlStream(sqlFileInputStream);
            executeStatements(statements);
        }

        public boolean isTableExist(Class<?> cLass) {
            try {
                select(cLass).count();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public final void close() {
            db.close();
        }

        /**
         * deprecated use {@link #close()} instead.
         */
        @Deprecated
        public final void closeDb() {
            db.close();
        }

        public final void beginTransaction() {
            db.beginTransaction();
        }

        public final void setTransactionSuccessful() {
            db.setTransactionSuccessful();
        }

        public final void endTransaction() {
            db.endTransaction();
        }


        public boolean isReady() {
            return db != null && db.isOpen();
        }
    }

    public static abstract class SQLiteConnection implements BootDescription {
        String dbName;
        int dbVersion = 1;
        Context context;

        public static SQLiteConnection create(Context context, File file) {
            return create(context, file, -1, null);
        }

        public static SQLiteConnection create(Context context, File file, int version, final BootDescription description) {
            if (version < 0) {
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
                db.getVersion();
                db.close();
            }
            return create(context, file.getAbsolutePath(), version, description);
        }

        public static SQLiteConnection create(Context context, String dbName, int dbVersion, final BootDescription description) {
            SQLiteConnection connection = new SQLiteConnection(context, dbName, dbVersion) {
                @Override
                public void onCreateDb(SQLiteDatabase db) {
                    if (description != null) {
                        description.onCreateDb(db);
                    }
                }

                @Override
                public void onUpgradeDb(SQLiteDatabase db, int oldVersion, int newVersion) {
                    if (description != null) {
                        description.onUpgradeDb(db, oldVersion, newVersion);
                    }
                }
            };
            return connection;
        }

        public SQLiteConnection(Context context, String dbName, int dbVersion) {
            this.dbName = dbName;
            this.dbVersion = dbVersion;
            this.context = context;
        }

        public final static void executeScripts(SQLiteDatabase db, List<String> scripts) {
            for (String script : scripts) {
                db.execSQL(script);
            }
        }
    }

    public static interface BootDescription {
        abstract void onCreateDb(SQLiteDatabase db);

        abstract void onUpgradeDb(SQLiteDatabase db, int oldVersion,
                                  int newVersion);
    }

    public static void executeSQLScript(SQLiteDatabase db,
                                        InputStream sqlFileInputStream) throws IOException {
        List<String> statements = SQLiteParser.parseSqlStream(sqlFileInputStream);
        for (String statement : statements)
            db.execSQL(statement);
    }

    public interface PrepareHandler {
        public void onSQLReady(SQL sql);

        public void onSQLPrepareFail(Exception e);
    }

    public static abstract class SQLReadyHandler implements PrepareHandler {

        public void onSQLPrepareFail(Exception e) {
            e.printStackTrace();
        }
    }
}
