package mil.nga.geopackage.user;

import android.content.ContentValues;

import mil.nga.geopackage.db.AlterTable;
import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.db.GeoPackageDatabase;
import mil.nga.geopackage.db.TableMapping;

/**
 * Abstract User DAO for reading user tables
 *
 * @param <TColumn> column type
 * @param <TTable>  table type
 * @param <TRow>    row type
 * @param <TResult> result type
 * @author osbornb
 */
public abstract class UserDao<TColumn extends UserColumn, TTable extends UserTable<TColumn>, TRow extends UserRow<TColumn, TTable>, TResult extends UserCursor<TColumn, TTable, TRow>>
        extends UserCoreDao<TColumn, TTable, TRow, TResult> {

    /**
     * Database connection
     */
    private final GeoPackageDatabase db;

    /**
     * User database
     */
    private UserConnection userDb;

    /**
     * Invalid requery flag to requery to handle invalid large user rows
     */
    private boolean invalidRequery = true;

    /**
     * Constructor
     *
     * @param database database name
     * @param db       GeoPackage connection
     * @param userDb   user connection
     * @param table    table
     */
    protected UserDao(String database, GeoPackageConnection db,
                      UserConnection<TColumn, TTable, TRow, TResult> userDb,
                      TTable table) {
        super(database, db.copy(userDb.getDatabase()), userDb, table);
        this.db = userDb.getDatabase();
        this.userDb = userDb;
        userDb.setTable(table);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoPackageConnection getDb() {
        return (GeoPackageConnection) super.getDb();
    }

    /**
     * Get the database connection
     *
     * @return database
     * @since 1.3.1
     */
    public GeoPackageDatabase getDatabaseConnection() {
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
     * Is the invalid requery flag enabled?
     * When enabled (default is true) large invalid user rows are requeried and handled.
     *
     * @return invalid requery flag
     * @since 2.0.0
     */
    public boolean isInvalidRequery() {
        return invalidRequery;
    }

    /**
     * Set the invalid requery flag.
     * When enabled (default is true) large invalid user rows are requeried and handled.
     *
     * @param invalidRequery invalid requery flag
     * @since 2.0.0
     */
    public void setInvalidRequery(boolean invalidRequery) {
        this.invalidRequery = invalidRequery;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TResult prepareResult(TResult result) {
        if (invalidRequery) {
            result.enableInvalidRequery(this);
        }
        return result;
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
     * Handles requery of invalid id row
     */
    @Override
    public TRow queryForIdRow(long id) {
        TRow row = null;
        TResult readCursor = queryForId(id);
        if (readCursor.moveToNext()) {
            row = readCursor.getRow();
            if (!row.isValid() && readCursor.moveToNext()) {
                row = readCursor.getRow();
            }
        }
        readCursor.close();
        return row;
    }

    /**
     * Query using the previous result query arguments
     *
     * @param previousResult previous result
     * @return result
     * @since 2.0.0
     */
    public TResult query(TResult previousResult) {
        return (TResult) userDb.query(previousResult);
    }

    /**
     * Query using the user query arguments
     *
     * @param query user query
     * @return result
     * @since 2.0.0
     */
    public TResult query(UserQuery query) {
        return (TResult) userDb.query(query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(TRow row) {
        ContentValues contentValues = row.toContentValues();
        int updated = 0;
        if (contentValues.size() > 0) {
            updated = db.update(getTableName(), contentValues,
                    getPkWhere(row.getId()), getPkWhereArgs(row.getId()));
        }
        return updated;
    }

    /**
     * Update all rows matching the where clause with the provided values
     *
     * @param values      content values
     * @param whereClause where clause
     * @param whereArgs   where arguments
     * @return updated rows
     */
    public int update(ContentValues values, String whereClause,
                      String[] whereArgs) {
        return db.update(getTableName(), values, whereClause, whereArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long insert(TRow row) {
        long id = db.insertOrThrow(getTableName(), null, row.toContentValues());
        if (row.hasIdColumn()) {
            row.setId(id, true);
        }
        return id;
    }

    /**
     * Inserts a new row
     *
     * @param values content values
     * @return row id, -1 on error
     */
    public long insert(ContentValues values) {
        return db.insert(getTableName(), null, values);
    }

    /**
     * Inserts a new row
     *
     * @param values content values
     * @return row id
     */
    public long insertOrThrow(ContentValues values) {
        return db.insertOrThrow(getTableName(), null, values);
    }

    /**
     * {@inheritDoc}
     * Alter Table in SQLite does not support renaming columns until version 3.25.0
     * Once Android supports column rename alter table statements, this method override can be removed.
     */
    @Override
    protected void renameTableColumn(String columnName, String newColumnName) {

        UserTable<? extends UserColumn> newTable = getTable().copy();

        newTable.renameColumn(columnName, newColumnName);

        TableMapping tableMapping = new TableMapping(newTable);
        tableMapping.getColumn(newColumnName).setFromColumn(columnName);

        AlterTable.alterTable(getDb(), newTable, tableMapping);
    }

}
