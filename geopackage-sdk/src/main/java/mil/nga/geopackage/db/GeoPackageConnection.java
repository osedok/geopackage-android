package mil.nga.geopackage.db;

import android.database.Cursor;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.support.ConnectionSource;

import java.util.List;

import mil.nga.geopackage.GeoPackageException;

/**
 * GeoPackage Android Connection wrapper
 *
 * @author osbornb
 */
public class GeoPackageConnection extends GeoPackageCoreConnection {

    /**
     * Database connection
     */
    private final GeoPackageDatabase db;

    /**
     * Constructor
     *
     * @param db GeoPackage connection
     */
    public GeoPackageConnection(GeoPackageDatabase db) {
        super(new AndroidConnectionSource(db.getDb()));
        this.db = db;
    }

    /**
     * Copy Constructor
     *
     * @param connection GeoPackage connection
     * @since 3.4.0
     */
    public GeoPackageConnection(GeoPackageConnection connection) {
        this(connection, connection.db);
    }

    /**
     * Copy Constructor
     *
     * @param connection GeoPackage connection
     * @param db         database
     * @since 3.4.0
     */
    public GeoPackageConnection(GeoPackageConnection connection, GeoPackageDatabase db) {
        super(connection);
        this.db = db;
    }

    /**
     * Copy method
     *
     * @return connection
     * @since 3.4.0
     */
    public GeoPackageConnection copy() {
        return new GeoPackageConnection(this);
    }

    /**
     * Copy method with provided database
     *
     * @param db database
     * @return connection
     * @since 3.4.0
     */
    public GeoPackageConnection copy(GeoPackageDatabase db) {
        return new GeoPackageConnection(this, db);
    }

    /**
     * Get the database connection
     *
     * @return GeoPackage database
     */
    public GeoPackageDatabase getDb() {
        return db;
    }

    /**
     * Set the active SQLite connection as the bindings or standard
     *
     * @param useBindings true to use bindings connection, false for standard
     * @return previous bindings value
     * @since 3.4.0
     */
    public boolean setUseBindings(boolean useBindings) {
        return db.setUseBindings(useBindings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionSource getConnectionSource() {
        return connectionSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execSQL(String sql) {
        db.execSQL(sql);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginTransaction() {
        db.beginTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endTransaction(boolean successful) {
        db.endTransaction(successful);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        endAndBeginTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean inTransaction() {
        return db.inTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return db.delete(table, whereClause, whereArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        super.close();
        db.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object querySingleResult(String sql, String[] args, int column,
                                    GeoPackageDataType dataType) {
        CursorResult result = wrapQuery(sql, args);
        Object value = ResultUtils.buildSingleResult(result, column, dataType);
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object> querySingleColumnResults(String sql, String[] args,
                                                 int column, GeoPackageDataType dataType, Integer limit) {
        CursorResult result = wrapQuery(sql, args);
        List<Object> results = ResultUtils.buildSingleColumnResults(result,
                column, dataType, limit);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<List<Object>> queryResults(String sql, String[] args,
                                           GeoPackageDataType[] dataTypes, Integer limit) {
        CursorResult result = wrapQuery(sql, args);
        List<List<Object>> results = ResultUtils.buildResults(result,
                dataTypes, limit);
        return results;
    }

    /**
     * Perform a raw database query
     *
     * @param sql  sql command
     * @param args arguments
     * @return cursor
     * @since 1.2.1
     */
    public Cursor rawQuery(String sql, String[] args) {
        return db.rawQuery(sql, args);
    }

    /**
     * Perform the query and wrap as a result
     *
     * @param sql           sql statement
     * @param selectionArgs selection arguments
     * @return result
     * @since 3.1.0
     */
    public CursorResult wrapQuery(String sql,
                                  String[] selectionArgs) {
        return new CursorResult(rawQuery(sql, selectionArgs));
    }

}
