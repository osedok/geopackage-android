package mil.nga.geopackage.user;

import android.database.Cursor;

import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.db.GeoPackageDatabase;
import mil.nga.geopackage.db.SQLiteQueryBuilder;

/**
 * GeoPackage Connection used to define common functionality within different
 * connection types
 *
 * @param <TColumn> column type
 * @param <TTable>  table type
 * @param <TRow>    row type
 * @param <TResult> result type
 * @author osbornb
 */
public abstract class UserConnection<TColumn extends UserColumn, TTable extends UserTable<TColumn>, TRow extends UserRow<TColumn, TTable>, TResult extends UserCursor<TColumn, TTable, TRow>>
        extends UserCoreConnection<TColumn, TTable, TRow, TResult> {

    /**
     * Database connection
     */
    protected final GeoPackageDatabase database;

    /**
     * Table
     */
    protected TTable table;

    /**
     * Constructor
     *
     * @param database GeoPackage connection
     */
    protected UserConnection(GeoPackageConnection database) {
        this.database = database.getDb().copy();
    }

    /**
     * Get the database
     *
     * @return database
     * @since 3.4.0
     */
    public GeoPackageDatabase getDatabase() {
        return database;
    }

    /**
     * Get the table
     *
     * @return table
     * @since 3.2.0
     */
    public TTable getTable() {
        return table;
    }

    /**
     * Set the table
     *
     * @param table table
     * @since 3.2.0
     */
    public void setTable(TTable table) {
        this.table = table;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult rawQuery(String sql, String[] selectionArgs) {
        return query(new UserQuery(sql, selectionArgs));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(String table, String[] columns, String selection,
                         String[] selectionArgs, String groupBy, String having,
                         String orderBy) {
        return query(new UserQuery(table, columns, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(boolean distinct, String table, String[] columns,
                         String selection, String[] selectionArgs, String groupBy,
                         String having, String orderBy) {
        return query(new UserQuery(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(String table, String[] columns, String[] columnsAs, String selection,
                         String[] selectionArgs, String groupBy, String having,
                         String orderBy) {
        return query(new UserQuery(table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(boolean distinct, String table, String[] columns,
                         String[] columnsAs, String selection, String[] selectionArgs,
                         String groupBy, String having, String orderBy) {
        return query(new UserQuery(distinct, table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(String table, String[] columns, String selection,
                         String[] selectionArgs, String groupBy, String having,
                         String orderBy, String limit) {
        return query(new UserQuery(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(boolean distinct, String table, String[] columns,
                         String selection, String[] selectionArgs, String groupBy,
                         String having, String orderBy, String limit) {
        return query(new UserQuery(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(String table, String[] columns, String[] columnsAs, String selection,
                         String[] selectionArgs, String groupBy, String having,
                         String orderBy, String limit) {
        return query(new UserQuery(table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TResult query(boolean distinct, String table, String[] columns,
                         String[] columnsAs, String selection, String[] selectionArgs,
                         String groupBy, String having, String orderBy, String limit) {
        return query(new UserQuery(distinct, table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy, limit));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(String table, String[] columns, String selection,
                           String groupBy, String having, String orderBy) {
        return querySQL(false, table, columns, selection, groupBy, having,
                orderBy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(boolean distinct, String table, String[] columns,
                           String selection, String groupBy, String having, String orderBy) {
        return querySQL(distinct, table, columns, null, selection, groupBy,
                having, orderBy, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(String table, String[] columns, String[] columnsAs,
                           String selection, String groupBy, String having, String orderBy) {
        return querySQL(false, table, columns, columnsAs, selection, groupBy, having,
                orderBy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(boolean distinct, String table, String[] columns,
                           String[] columnsAs, String selection, String groupBy, String having,
                           String orderBy) {
        return querySQL(distinct, table, columns, columnsAs, selection, groupBy,
                having, orderBy, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(String table, String[] columns, String selection,
                           String groupBy, String having, String orderBy, String limit) {
        return querySQL(false, table, columns, selection, groupBy, having,
                orderBy, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(boolean distinct, String table, String[] columns,
                           String selection, String groupBy, String having, String orderBy,
                           String limit) {
        return querySQL(distinct, table, columns, null, selection, groupBy,
                having, orderBy, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(String table, String[] columns, String[] columnsAs,
                           String selection, String groupBy, String having, String orderBy,
                           String limit) {
        return querySQL(false, table, columns, columnsAs, selection, groupBy,
                having, orderBy, limit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String querySQL(boolean distinct, String table, String[] columns,
                           String[] columnsAs, String selection, String groupBy, String having,
                           String orderBy, String limit) {
        return SQLiteQueryBuilder.buildQueryString(distinct, table, columns,
                columnsAs, selection, groupBy, having, orderBy, limit);
    }

    /**
     * Query using the query from a previous query result
     *
     * @param previousResult previous result
     * @return result
     * @since 2.0.0
     */
    public TResult query(TResult previousResult) {
        return query(previousResult.getQuery());
    }

    /**
     * Query using the user query arguments
     *
     * @param query user query
     * @return result
     * @since 2.0.0
     */
    public TResult query(UserQuery query) {
        Cursor cursor = null;

        String[] selectionArgs = query.getSelectionArgs();

        String sql = query.getSql();
        if (sql != null) {
            cursor = database.rawQueryWithFactory(database.getCursorFactory(), sql, selectionArgs, table.getTableName());
        } else {

            boolean distinct = query.getDistinct();
            String table = query.getTable();
            String[] columns = query.getColumns();
            String selection = query.getSelection();
            String groupBy = query.getGroupBy();
            String having = query.getHaving();
            String orderBy = query.getOrderBy();

            String[] columnsAs = query.getColumnsAs();
            String limit = query.getLimit();

            if (distinct) {
                cursor = database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
            } else if (columnsAs != null && limit != null) {
                cursor = database.query(table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy, limit);
            } else if (columnsAs != null) {
                cursor = database.query(table, columns, columnsAs, selection, selectionArgs, groupBy, having, orderBy);
            } else if (limit != null) {
                cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
            } else {
                cursor = database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
            }

        }

        TResult result = handleCursor(cursor, query);

        return result;
    }

    /**
     * Convert the cursor to the result type cursor
     *
     * @param cursor cursor
     * @param query  user query
     * @return result cursor
     */
    private TResult handleCursor(Cursor cursor, UserQuery query) {
        TResult result = convertCursor(cursor);
        result.setQuery(query);
        if (table != null) {
            result.setTable(table);
            UserColumns<TColumn> userColumns;
            String[] columns = query.getColumns();
            if (columns != null) {
                userColumns = table.createUserColumns(columns);
            } else {
                userColumns = table.getUserColumns();
            }
            result.setColumns(userColumns);
        }
        return result;
    }

    /**
     * Convert the cursor to the result type cursor
     *
     * @param cursor cursor
     * @return result cursor
     * @since 2.0.0
     */
    protected TResult convertCursor(Cursor cursor) {
        return (TResult) cursor;
    }

}
