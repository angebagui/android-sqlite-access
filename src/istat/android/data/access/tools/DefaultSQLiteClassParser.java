package istat.android.data.access.tools;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Istat Toukea on 21/11/2016.
 */

public class DefaultSQLiteClassParser {
    Class<?> cLass;

    public static String parse(Class<?> cLass, boolean findAll) {
        return new DefaultSQLiteClassParser(cLass).parse(findAll);
    }

    public static String parse(Class<?> cLass) {
        return new DefaultSQLiteClassParser(cLass).parse(false);
    }

    public DefaultSQLiteClassParser(Class<?> cLass) {
        this.cLass = cLass;
    }

    /*
         CREATE TABLE IF NOT EXISTS `commandes_tb` (
          `ID_CDE` INTEGER PRIMARY KEY AUTOINCREMENT,
          `NUM_CDE` varchar(50) NOT NULL,
          `MONTANT_COURSE` float NOT NULL,
          `AVANCE_A_PAYER` float NOT NULL,
          `ADRESSE_LIVRAISON` text,
          `ID_CLT_FK` bigint(20) NOT NULL,
          `CREATED_AT` datetime NOT NULL,
          `UPDATED_AT` datetime NOT NULL,
          `STATUS` char(3) NOT NULL DEFAULT '0',
          `JSON_CONTENT` text
        );
     */
    public String parse(boolean findAll) {
        List<Field> fields;
        if (findAll) {
            fields = Toolkit.getAllFieldIncludingPrivateAndSuper(cLass);
        } else {
            fields = new ArrayList<Field>();
            Collections.addAll(fields, cLass.getDeclaredFields());
        }
        String sql = "CREATE TABLE IF NOT EXISTS `" + cLass.getSimpleName() + "` (";
        int index = 0;
        for (Field field : fields) {
            sql += createLine(field);
            if (index < fields.size() - 1) {
                sql += ",";
            }
            index++;
        }
        sql += ");";
        return sql;
    }

    HashMap<Class, LineAdapter> adapterQueue = new HashMap() {
        {
            put(String.class, STRING_ADAPTER);
            put(Float.class, FLOAT_ADAPTER);
            put(Double.class, DOUBLE_ADAPTER);
            put(Integer.class, INTEGER_ADAPTER);
            put(Date.class, DATETIME_ADAPTER);
        }
    };

    static LineAdapter INTEGER_ADAPTER = new LineAdapter() {
        @Override
        String onCreateLine(Field field) {
            return "`" + field.getName() + "` INTEGER ";
        }
    };
    static LineAdapter FLOAT_ADAPTER = new LineAdapter() {
        @Override
        String onCreateLine(Field field) {
            return "`" + field.getName() + "` FLOAT ";
        }
    };
    static LineAdapter DOUBLE_ADAPTER = new LineAdapter() {
        @Override
        String onCreateLine(Field field) {
            return "`" + field.getName() + "` DOUBLE ";
        }
    };
    static LineAdapter STRING_ADAPTER = new LineAdapter() {
        @Override
        String onCreateLine(Field field) {
            return "`" + field.getName() + "` VARCHAR ";
        }
    };
    static LineAdapter DATETIME_ADAPTER = new LineAdapter() {
        @Override
        String onCreateLine(Field field) {
            return "`" + field.getName() + "` DATETIME ";
        }
    };

    private String createLine(Field field) {
        LineAdapter adapter = adapterQueue.get(field.getType());
        if (adapter != null) {
            return adapter.createLine(field);
        } else {
            return adapterQueue.get(String.class).createLine(field);
        }
    }

    public static abstract class LineAdapter {
        abstract String onCreateLine(Field field);

        public String createLine(Field field) {
            return onCreateLine(field);
        }
    }
}
